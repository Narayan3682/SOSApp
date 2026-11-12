package com.homepager.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class ServerUrlPolicyTest {
    private static void ok(String u) { assertNull(u, ServerUrlPolicy.validate(u)); }
    private static void bad(String u) { assertNotNull(u, ServerUrlPolicy.validate(u)); }

    @Test public void httpsAlwaysAllowed() {
        ok("https://pager.example.com"); ok("https://pager.example.com:8443/x"); ok("https://8.8.8.8");
    }

    @Test public void httpAllowedOnHomeNetworks() {
        ok("http://192.168.1.10:8000"); ok("http://10.0.2.2:8000"); ok("http://172.16.0.5");
        ok("http://172.31.255.1"); ok("http://100.101.102.103:8000"); ok("http://localhost:8000");
        ok("http://pi.local:8000"); ok("http://127.0.0.1:8000"); ok("HTTP://192.168.0.2:8000");
    }

    @Test public void httpRejectedForPublicAddresses() {
        bad("http://example.com"); bad("http://8.8.8.8:8000"); bad("http://172.32.0.1");
        bad("http://172.15.0.1"); bad("http://100.63.0.1"); bad("http://100.128.0.1");
        bad("http://192.169.1.1");
    }

    @Test public void lookalikeHostnamesAreNotTreatedAsPrivateIps() {
        bad("http://10.evil.com"); bad("http://192.168.1.10.evil.com"); bad("http://300.1.1.1");
    }

    @Test public void wildcardAddressGivesActionableMessage() {
        String m = ServerUrlPolicy.validate("http://0.0.0.0:8000");
        assertNotNull(m);
        assertTrue(m.contains("ipconfig"));
        assertNotNull(ServerUrlPolicy.validate("https://0.0.0.0"));
    }

    @Test public void hintWarnsAboutLoopbackOnly() {
        assertNotNull(ServerUrlPolicy.hint("http://localhost:8000"));
        assertNotNull(ServerUrlPolicy.hint("http://127.0.0.1:8000"));
        assertNull(ServerUrlPolicy.hint("http://192.168.1.10:8000"));
        assertNull(ServerUrlPolicy.hint("http://10.0.2.2:8000"));   // Android emulator -> host PC
        assertNull(ServerUrlPolicy.hint("garbage"));
        assertNull(ServerUrlPolicy.hint(null));
    }

    @Test public void malformedRejected() {
        bad(null); bad(""); bad("   "); bad("192.168.1.10:8000"); bad("ftp://192.168.1.10");
        bad("http://"); bad("http://:8000"); bad("http://192.168.1.10:99999"); bad("http://192.168.1.10:abc");
        bad("http://user@192.168.1.10"); bad("https://");
    }
}
