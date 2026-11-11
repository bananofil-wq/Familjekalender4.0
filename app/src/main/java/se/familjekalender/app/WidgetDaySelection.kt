package se.familjekalender.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Automatically shows the current day's remaining activities. If there are none, opens
 * the next day with activities during the current Monday–Sunday week instead.
 *
 * A manual selection (including the empty string meaning "all closed") is respected until
 * the next calendar day. This avoids leaving yesterday expanded after midnight.
 */
internal fun preferredWidgetDay(
    today: LocalDate,
    eventDates: List<LocalDate>,
    manuallySelectedDay: String?,
    manualSelectionDate: String?,
): String {
    if (manualSelectionDate == today.toString() && manuallySelectedDay != null) {
        return manuallySelectedDay
    }

    val lastDayOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return eventDates.asSequence()
        .filter { !it.isBefore(today) && !it.isAfter(lastDayOfWeek) }
        .minOrNull()
        ?.toString()
        ?: today.toString()
}
