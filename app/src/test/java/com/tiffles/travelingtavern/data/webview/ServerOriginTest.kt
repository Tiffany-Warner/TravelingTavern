package com.tiffles.travelingtavern.data.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerOriginTest {

    private val baseUrl = "http://192.0.2.1:8000"

    @Test
    fun `pages on the saved server match`() {
        assertTrue(isSameServer(url = "http://192.0.2.1:8000/", baseUrl = baseUrl))
        assertTrue(isSameServer(url = "http://192.0.2.1:8000/characters/Seraphina.png?x=1#top", baseUrl = baseUrl))
    }

    @Test
    fun `host comparison ignores case`() {
        assertTrue(isSameServer(url = "https://Desktop.Tail1234.ts.net/", baseUrl = "https://desktop.tail1234.ts.net"))
    }

    @Test
    fun `default ports are treated as explicit`() {
        assertTrue(isSameServer(url = "http://desktop:80/login", baseUrl = "http://desktop"))
    }

    @Test
    fun `another host, port or scheme does not match`() {
        assertFalse(isSameServer(url = "https://github.com/SillyTavern", baseUrl = baseUrl))
        assertFalse(isSameServer(url = "http://192.0.2.1:5001/", baseUrl = baseUrl))
        assertFalse(isSameServer(url = "https://192.0.2.1:8000/", baseUrl = baseUrl))
    }

    @Test
    fun `non-web and malformed urls never match`() {
        assertFalse(isSameServer(url = "mailto:someone@example.com", baseUrl = baseUrl))
        assertFalse(isSameServer(url = "not a url", baseUrl = baseUrl))
        assertFalse(isSameServer(url = "http://192.0.2.1:8000/", baseUrl = ""))
    }
}
