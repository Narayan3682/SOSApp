package com.homepager.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Compact, authenticated alert packet used by the offline routes (LAN broadcast, Nearby).
 * Layout: ver(1) type(1) uuid(16) level(1) trigger(1) ts(4, unix secs) nameLen(1) name(<=24) mac(8)
 * mac = first 8 bytes of HMAC-SHA256(householdKey, everything before it).
 * Max size 58 bytes, well under Nearby's endpoint-info limit. Pure JVM so it is unit-testable.
 */
public final class AlertPacket {
    public static final int TYPE_ALERT = 1;
    public static final int TYPE_ACK = 2;
    public static final int VERSION = 1;
    public static final int MAX_NAME_BYTES = 24;
    private static final int MAC_LEN = 8;
    private static final int HEADER = 1 + 1 + 16 + 1 + 1 + 4 + 1;

    public final int type;
    public final UUID alertId;
    public final int level;
    public final int triggerCode;
    public final long tsSec;
    public final String name;

    public AlertPacket(int type, UUID alertId, int level, int triggerCode, long tsSec, String name) {
        this.type = type;
        this.alertId = alertId;
        this.level = level & 0xFF;
        this.triggerCode = triggerCode & 0xFF;
        this.tsSec = tsSec;
        this.name = truncateName(name == null ? "" : name);
    }

    /** Shortens to MAX_NAME_BYTES of UTF-8 without splitting a character. */
    public static String truncateName(String n) {
        String s = n;
        while (s.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
        }
        return s;
    }

    public byte[] encode(byte[] key) {
        byte[] nb = name.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(HEADER + nb.length + MAC_LEN);
        b.put((byte) VERSION).put((byte) type)
                .putLong(alertId.getMostSignificantBits()).putLong(alertId.getLeastSignificantBits())
                .put((byte) level).put((byte) triggerCode).putInt((int) tsSec)
                .put((byte) nb.length).put(nb);
        byte[] body = Arrays.copyOf(b.array(), HEADER + nb.length);
        b.put(mac(key, body), 0, MAC_LEN);
        return b.array();
    }

    /**
     * @return the packet, or null if it is malformed, not signed with {@code key},
     *         or its timestamp is more than {@code maxSkewSec} from {@code nowSec}.
     */
    public static AlertPacket decode(byte[] d, byte[] key, long nowSec, long maxSkewSec) {
        try {
            if (d == null || key == null || key.length == 0 || d.length < HEADER + MAC_LEN) return null;
            ByteBuffer b = ByteBuffer.wrap(d);
            if (b.get() != VERSION) return null;
            int type = b.get();
            long msb = b.getLong(), lsb = b.getLong();
            int level = b.get() & 0xFF, trig = b.get() & 0xFF;
            long ts = b.getInt() & 0xFFFFFFFFL;
            int nameLen = b.get() & 0xFF;
            if (nameLen > MAX_NAME_BYTES || d.length != HEADER + nameLen + MAC_LEN) return null;
            byte[] nb = new byte[nameLen];
            b.get(nb);
            byte[] got = new byte[MAC_LEN];
            b.get(got);
            byte[] want = Arrays.copyOf(mac(key, Arrays.copyOf(d, HEADER + nameLen)), MAC_LEN);
            if (!MessageDigest.isEqual(got, want)) return null;
            if (type != TYPE_ALERT && type != TYPE_ACK) return null;
            if (Math.abs(nowSec - ts) > maxSkewSec) return null;
            return new AlertPacket(type, new UUID(msb, lsb), level, trig, ts, new String(nb, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static byte[] mac(byte[] key, byte[] data) {
        if (key == null || key.length == 0) throw new IllegalArgumentException("empty key");
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int triggerCodeFor(String trigger) {
        switch (trigger == null ? "" : trigger) {
            case "button": return 0;
            case "volume-triple": return 1;
            case "volume-hold": return 2;
            case "widget": return 3;
            case "tile": return 4;
            case "voice": return 5;
            default: return 255;
        }
    }

    public static String triggerNameFor(int code) {
        switch (code) {
            case 0: return "button";
            case 1: return "volume-triple";
            case 2: return "volume-hold";
            case 3: return "widget";
            case 4: return "tile";
            case 5: return "voice";
            default: return "other";
        }
    }
}
