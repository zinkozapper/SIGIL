package com.sigil.client

import java.net.ServerSocket
import java.io.IOException
import org.junit.Assert.assertThrows
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

    @Test fun checksHealthWithoutFollowingRedirects() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5_000
            val requests = mutableListOf<String>()
            val worker = Thread {
                for (status in listOf("503 Unavailable", "302 Found", "200 OK")) {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        requests.add(reader.readLine())
                        while (!reader.readLine().isNullOrEmpty()) { }
                        socket.getOutputStream().write(
                            "HTTP/1.1 $status\r\nLocation: /healthy\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                        )
                    }
                }
            }
            worker.start()
            val url = "http://127.0.0.1:${server.localPort}"
            assertEquals(503, checkServerHealth(url))
            assertEquals(302, checkServerHealth(url))
            assertEquals(200, checkServerHealth(url))
            worker.join(5_000)
            assertEquals(List(3) { "GET /up HTTP/1.1" }, requests)
        }
    }

    @Test fun reportsUnreachableServer() {
        val port = ServerSocket(0).use { it.localPort }
        assertThrows(IOException::class.java) { checkServerHealth("http://127.0.0.1:$port") }
    }
}
