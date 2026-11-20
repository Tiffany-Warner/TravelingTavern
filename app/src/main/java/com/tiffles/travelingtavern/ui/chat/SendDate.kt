package com.tiffles.travelingtavern.ui.chat

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * SillyTavern's `send_date` for display. SillyTavern 1.19 writes ISO-8601; older chats may
 * hold epoch milliseconds or SillyTavern's earlier human-readable text, which is shown as is.
 */
fun formatSendDate(sendDate: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val instant = parseSendDate(sendDate) ?: return sendDate
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)
}

private fun parseSendDate(sendDate: String): Instant? {
    val trimmed = sendDate.trim()
    trimmed.toLongOrNull()?.let { return Instant.ofEpochMilli(it) }
    return try {
        Instant.parse(trimmed)
    } catch (_: DateTimeParseException) {
        null
    }
}
