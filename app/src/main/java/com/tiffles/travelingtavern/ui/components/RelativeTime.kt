package com.tiffles.travelingtavern.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.tiffles.travelingtavern.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How long ago something happened, in the short form a messaging list shows. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val count: Long) : RelativeTime
    data class Hours(val count: Long) : RelativeTime
    data object Yesterday : RelativeTime
    data class OnDate(val date: LocalDate, val showYear: Boolean) : RelativeTime
}

/**
 * Under a minute (or in the future, from clock skew) → just now; under an hour → minutes;
 * earlier today → hours; the previous calendar day → yesterday; otherwise the date, with
 * the year only when it isn't this year.
 */
fun relativeTime(then: Instant, now: Instant, zone: ZoneId): RelativeTime {
    val elapsed = Duration.between(then, now)
    if (elapsed < Duration.ofMinutes(1)) return RelativeTime.JustNow
    if (elapsed < Duration.ofHours(1)) return RelativeTime.Minutes(elapsed.toMinutes())
    val day = then.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (day) {
        today -> RelativeTime.Hours(elapsed.toHours())
        today.minusDays(1) -> RelativeTime.Yesterday
        else -> RelativeTime.OnDate(day, showYear = day.year != today.year)
    }
}

/** "now", "5m", "2h", "Yesterday", "Mar 3", "Mar 3, 2025" (date order follows the locale). */
@Composable
@ReadOnlyComposable
fun RelativeTime.label(): String = when (this) {
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.Minutes -> stringResource(R.string.time_minutes, count)
    is RelativeTime.Hours -> stringResource(R.string.time_hours, count)
    RelativeTime.Yesterday -> stringResource(R.string.time_yesterday)
    is RelativeTime.OnDate -> {
        val locale = LocalConfiguration.current.locales[0]
        val skeleton = if (showYear) "MMMdyyyy" else "MMMd"
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }
}
