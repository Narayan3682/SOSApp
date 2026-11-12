package com.homepager.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SmsPlanTest {
    private static SmsPlan.Contact c(String n, String p, boolean prio) { return new SmsPlan.Contact(n, p, prio); }

    private final List<SmsPlan.Contact> all = Arrays.asList(
            c("Me", "+91 98765 43210", true), c("Mom", "98765-11111", true),
            c("Dad", "+91 98765 22222", false), c("Kid", "12", false),
            c("MomDup", "+91 98765 11111", false), c("Nobody", null, false));

    @Test public void normalize() {
        assertEquals("+919876543210", SmsPlan.normalize(" +91 98765-43210 "));
        assertEquals("9876511111", SmsPlan.normalize("98765-11111"));
        assertNull(SmsPlan.normalize("12")); assertNull(SmsPlan.normalize(null)); assertNull(SmsPlan.normalize("abc"));
    }

    @Test public void offlineMessagesEveryoneValidExceptSelfAndDuplicates() {
        List<SmsPlan.Contact> r = SmsPlan.select(all, false, false, "9876543210");
        assertEquals(2, r.size());
        assertEquals("Mom", r.get(0).name);      // priority first
        assertEquals("Dad", r.get(1).name);
    }

    @Test public void onlineWithoutOptInSendsNothing() {
        assertTrue(SmsPlan.select(all, true, false, "9876543210").isEmpty());
    }

    @Test public void onlineWithOptInSendsPriorityOnly() {
        List<SmsPlan.Contact> r = SmsPlan.select(all, true, true, "9876543210");
        assertEquals(1, r.size());
        assertEquals("Mom", r.get(0).name);
    }

    @Test public void noOwnNumberKnownStillWorks() {
        assertEquals(3, SmsPlan.select(all, false, false, null).size());   // Me, Mom, Dad
    }
}
