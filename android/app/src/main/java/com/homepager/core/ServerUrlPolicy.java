package com.homepager.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain http:// is tolerated only for home-network addresses; anything public must be https://. */
public final class ServerUrlPolicy {
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private ServerUrlPolicy() {}

    /** @return {host, port, error}; error is non-null when the URL is unusable. */
    private static String[] parse(String url) {
        if (url == null || url.trim().isEmpty()) return new String[] {null, null, "Enter the server URL"};
        String u = url.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        boolean https = lower.startsWith("https://");
        if (!https && !lower.startsWith("http://")) {
            return new String[] {null, null, "URL must start with http:// or https://"};
        }
        String rest = u.substring(https ? 8 : 7);
        int cut = rest.length();
        for (char c : new char[] {'/', '?', '#'}) {
            int i = rest.indexOf(c);
            if (i >= 0) cut = Math.min(cut, i);
        }
        String authority = rest.substring(0, cut);
        String bad = "Invalid server address";
        if (authority.isEmpty() || authority.contains("@") || authority.contains(" ")) return new String[] {null, null, bad};
        String host = authority, port = null;
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            if (end < 0) return new String[] {null, null, bad};
            host = authority.substring(1, end);
            String tail = authority.substring(end + 1);
            if (!tail.isEmpty()) {
                if (!tail.startsWith(":")) return new String[] {null, null, bad};
                port = tail.substring(1);
            }
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                host = authority.substring(0, colon);
                port = authority.substring(colon + 1);
            }
        }
        if (host.isEmpty()) return new String[] {null, null, bad};
        if (port != null && (!port.matches("\\d{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535)) {
            return new String[] {null, null, "Invalid port"};
        }
        return new String[] {host, port, null};
    }

    /** @return null if acceptable, otherwise a message to show the user. */
    public static String validate(String url) {
        String[] p = parse(url);
        if (p[2] != null) return p[2];
        String host = p[0];
        if (host.equals("0.0.0.0")) {
            return "0.0.0.0 can't be reached from a phone. Use your PC's IPv4 address (run ipconfig), e.g. http://192.168.1.23:8000";
        }
        boolean https = url.trim().toLowerCase(Locale.ROOT).startsWith("https://");
        if (https || isPrivateHost(host)) return null;
        return "Plain http:// is only allowed for home-network addresses. Use https:// (see docs/TLS.md).";
    }

    /** @return a non-blocking warning for an otherwise valid URL, or null. */
    public static String hint(String url) {
        String[] p = parse(url);
        if (p[2] != null) return null;
        String h = p[0].toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("::1") || h.startsWith("127.")) {
            return "localhost points to this phone itself. Use your PC's IPv4 address (run ipconfig).";
        }
        return null;
    }

    static boolean isPrivateHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".local") || h.equals("::1")) return true;
        Matcher m = IPV4.matcher(h);
        if (!m.matches()) return false;
        int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
        for (int i = 1; i <= 4; i++) if (Integer.parseInt(m.group(i)) > 255) return false;
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31)
                || (a == 100 && b >= 64 && b <= 127)   // Tailscale / CGNAT range
                || (a == 169 && b == 254);
    }
}
