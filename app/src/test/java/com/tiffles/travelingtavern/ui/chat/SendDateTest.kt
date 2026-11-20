package com.tiffles.travelingtavern.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class SendDateTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `ISO dates from SillyTavern 1_19 are formatted`() {
        val formatted = formatSendDate("2026-11-19T15:04:00.000Z", utc, Locale.US)
        assertNotEquals("2026-11-19T15:04:00.000Z", formatted)
        assertEquals(true, formatted.contains("2026") && formatted.contains("3:04"))
    }

    @Test
    fun `epoch milliseconds are formatted the same way`() {
        assertEquals(
            formatSendDate("2026-11-19T15:04:00.000Z", utc, Locale.US),
            formatSendDate("1795100640000", utc, Locale.US)
        )
    }

    @Test
    fun `older human-readable dates are shown as they are`() {
        assertEquals("November 19, 2026 3:04pm", formatSendDate("November 19, 2026 3:04pm", utc, Locale.US))
    }
}
