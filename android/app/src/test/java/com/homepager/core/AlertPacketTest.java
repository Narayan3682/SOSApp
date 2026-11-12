package com.homepager.core;

import static org.junit.Assert.*;

import java.util.UUID;
import org.junit.Test;

public class AlertPacketTest {
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes();
    private static final byte[] OTHER = "ffffffffffffffffffffffffffffffff".getBytes();
    private static final long NOW = 1_800_000_000L;
    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private AlertPacket alert() { return new AlertPacket(AlertPacket.TYPE_ALERT, ID, 2, 1, NOW, "Narayan"); }

    @Test public void roundTrip() {
        AlertPacket p = AlertPacket.decode(alert().encode(KEY), KEY, NOW + 5, 600);
        assertNotNull(p);
        assertEquals(ID, p.alertId);
        assertEquals(AlertPacket.TYPE_ALERT, p.type);
        assertEquals(2, p.level);
        assertEquals("volume-triple", AlertPacket.triggerNameFor(p.triggerCode));
        assertEquals("Narayan", p.name);
        assertEquals(NOW, p.tsSec);
    }

    @Test public void ackRoundTrip() {
        byte[] b = new AlertPacket(AlertPacket.TYPE_ACK, ID, 0, 0, NOW, "Mom").encode(KEY);
        AlertPacket p = AlertPacket.decode(b, KEY, NOW, 600);
        assertEquals(AlertPacket.TYPE_ACK, p.type);
        assertEquals("Mom", p.name);
    }

    @Test public void wrongKeyRejected() { assertNull(AlertPacket.decode(alert().encode(KEY), OTHER, NOW, 600)); }

    @Test public void everySingleByteTamperIsRejected() {
        byte[] good = alert().encode(KEY);
        for (int i = 0; i < good.length; i++) {
            byte[] bad = good.clone();
            bad[i] ^= 0x01;
            assertNull("tamper at byte " + i, AlertPacket.decode(bad, KEY, NOW, 600));
        }
    }

    @Test public void staleAndFutureTimestampsRejected() {
        byte[] b = alert().encode(KEY);
        assertNull(AlertPacket.decode(b, KEY, NOW + 601, 600));
        assertNull(AlertPacket.decode(b, KEY, NOW - 601, 600));
        assertNotNull(AlertPacket.decode(b, KEY, NOW + 600, 600));
    }

    @Test public void truncatedExtendedAndGarbageRejected() {
        byte[] good = alert().encode(KEY);
        assertNull(AlertPacket.decode(java.util.Arrays.copyOf(good, good.length - 1), KEY, NOW, 600));
        assertNull(AlertPacket.decode(java.util.Arrays.copyOf(good, good.length + 1), KEY, NOW, 600));
        assertNull(AlertPacket.decode(new byte[0], KEY, NOW, 600));
        assertNull(AlertPacket.decode(null, KEY, NOW, 600));
        assertNull(AlertPacket.decode("hello world, not a packet at all".getBytes(), KEY, NOW, 600));
        assertNull(AlertPacket.decode(good, new byte[0], NOW, 600));
    }

    @Test public void longMultibyteNameIsTruncatedOnCharBoundaryAndStillValid() {
        String name = "நாராயண் நாராயண் நாராயண் நாராயண்";   // Tamil, many bytes per char
        AlertPacket p = new AlertPacket(AlertPacket.TYPE_ALERT, ID, 0, 0, NOW, name);
        assertTrue(p.name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= AlertPacket.MAX_NAME_BYTES);
        assertTrue(name.startsWith(p.name));
        AlertPacket back = AlertPacket.decode(p.encode(KEY), KEY, NOW, 600);
        assertEquals(p.name, back.name);
    }

    @Test public void fitsComfortablyInNearbyEndpointInfo() {
        String max = "a".repeat(24);
        assertTrue(new AlertPacket(AlertPacket.TYPE_ALERT, ID, 255, 255, NOW, max).encode(KEY).length <= 60);
    }

    @Test public void triggerCodesRoundTrip() {
        for (String t : new String[] {"button", "volume-triple", "volume-hold", "widget", "tile", "voice"}) {
            assertEquals(t, AlertPacket.triggerNameFor(AlertPacket.triggerCodeFor(t)));
        }
        assertEquals("other", AlertPacket.triggerNameFor(AlertPacket.triggerCodeFor("???")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void encodeWithEmptyKeyFails() { alert().encode(new byte[0]); }
}
