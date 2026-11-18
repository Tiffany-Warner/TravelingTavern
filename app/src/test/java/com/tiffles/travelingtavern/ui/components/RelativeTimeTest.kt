package com.tiffles.travelingtavern.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RelativeTimeTest {

    private val zone = ZoneId.of("America/Denver")
    private val now = Instant.parse("2026-11-17T18:00:00Z") // 11:00 local

    private fun at(text: String) = relativeTime(Instant.parse(text), now, zone)

    @Test
    fun `under a minute and future times are just now`() {
        assertEquals(RelativeTime.JustNow, at("2026-11-17T17:59:30Z"))
        assertEquals(RelativeTime.JustNow, at("2026-11-17T18:05:00Z"))
    }

    @Test
    fun `under an hour is minutes`() {
        assertEquals(RelativeTime.Minutes(1), at("2026-11-17T17:59:00Z"))
        assertEquals(RelativeTime.Minutes(59), at("2026-11-17T17:00:30Z"))
    }

    @Test
    fun `earlier today is hours`() {
        assertEquals(RelativeTime.Hours(1), at("2026-11-17T17:00:00Z"))
        // 07:01 UTC is 00:01 local — still today in the user's zone.
        assertEquals(RelativeTime.Hours(10), at("2026-11-17T07:01:00Z"))
    }

    @Test
    fun `the previous calendar day is yesterday, even under 24 hours`() {
        // 06:59 UTC is 23:59 local on the 16th.
        assertEquals(RelativeTime.Yesterday, at("2026-11-17T06:59:00Z"))
        assertEquals(RelativeTime.Yesterday, at("2026-11-16T08:00:00Z"))
    }

    @Test
    fun `older is the date, with the year only for other years`() {
        assertEquals(RelativeTime.OnDate(LocalDate.of(2026, 11, 3), showYear = false), at("2026-11-03T18:00:00Z"))
        assertEquals(RelativeTime.OnDate(LocalDate.of(2025, 3, 3), showYear = true), at("2025-03-03T18:00:00Z"))
    }
}
