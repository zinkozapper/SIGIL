package com.sigil.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test fun acceptsAddressesAndUrls() {
        assertEquals("http://100.1.2.3:3000", normalizeServerUrl(" 100.1.2.3 ", "3000"))
        assertEquals("https://example.com:443", normalizeServerUrl("https://example.com/", "443"))
    }

    @Test fun rejectsInvalidUrls() {
        for (input in listOf("", "http://", "ftp://example.com", "http://user@example.com", "http://example.com/path", "http://example.com:3000", "example.com:")) {
            assertNull(normalizeServerUrl(input, "3000"))
        }
        for (port in listOf("", "0", "65536", "-1", "+80", "abc")) {
            assertNull(normalizeServerUrl("example.com", port))
        }
    }
}
