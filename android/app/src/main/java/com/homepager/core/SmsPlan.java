package com.homepager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Decides who receives the SMS fallback. Pure logic so it is unit-testable. */
public final class SmsPlan {
    public static final class Contact {
        public final String name, phone;
        public final boolean priority;

        public Contact(String name, String phone, boolean priority) {
            this.name = name;
            this.phone = phone;
            this.priority = priority;
        }
    }

    private SmsPlan() {}

    /** Keeps digits and a leading '+'; returns null if fewer than 7 digits. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c) || (c == '+' && sb.length() == 0)) sb.append(c);
        }
        String s = sb.toString();
        return s.replace("+", "").length() >= 7 ? s : null;
    }

    private static String key(String normalized) {
        String d = normalized.replace("+", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    /**
     * serverReachable=false  -> everyone (nobody else can be reached any other way).
     * serverReachable=true   -> only priority contacts, and only when smsAlways is on.
     * The sender's own number and duplicates are always dropped; priority contacts come first.
     */
    public static List<Contact> select(List<Contact> all, boolean serverReachable, boolean smsAlways, String ownPhone) {
        if (serverReachable && !smsAlways) return Collections.emptyList();
        String own = normalize(ownPhone);
        Set<String> seen = new HashSet<>();
        if (own != null) seen.add(key(own));
        List<Contact> prio = new ArrayList<>(), rest = new ArrayList<>();
        for (Contact c : all) {
            String n = normalize(c.phone);
            if (n == null || !seen.add(key(n))) continue;
            if (serverReachable && !c.priority) continue;
            Contact norm = new Contact(c.name, n, c.priority);
            (c.priority ? prio : rest).add(norm);
        }
        prio.addAll(rest);
        return prio;
    }
}
