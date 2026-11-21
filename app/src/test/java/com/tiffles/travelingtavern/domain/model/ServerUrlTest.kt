package com.tiffles.travelingtavern.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `bare host and port gets http scheme`() {
        assertEquals("http://192.0.2.1:8000", normalizeServerUrl("192.0.2.1:8000"))
    }

    @Test
    fun `bare hostname without port`() {
        assertEquals("http://desktop", normalizeServerUrl("desktop"))
    }

    @Test
    fun `trailing slash and path are dropped`() {
        assertEquals("http://192.0.2.1:8000", normalizeServerUrl("http://192.0.2.1:8000/"))
        assertEquals("http://192.0.2.1:8000", normalizeServerUrl("http://192.0.2.1:8000/index.html"))
    }

    @Test
    fun `https and mixed case host are preserved and lowercased`() {
        assertEquals("https://desktop.tail1234.ts.net", normalizeServerUrl("HTTPS://Desktop.Tail1234.ts.net"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("http://192.0.2.1:8000", normalizeServerUrl("  192.0.2.1:8000  "))
    }

    @Test
    fun `empty, garbage, and non-http schemes are invalid`() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("not a url"))
        assertNull(normalizeServerUrl("ftp://desktop:8000"))
        assertNull(normalizeServerUrl("http://"))
    }
}
