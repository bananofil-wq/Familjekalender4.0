package se.familjekalender.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Shows today's activities automatically (including activities that finished earlier).
 * If there are none, opens the next day with events this Monday–Sunday week.
 *
 * A non-empty manual day selection is respected until midnight. Legacy empty strings
 * represent a previously collapsed day and must never override automatic expansion.
 */
internal fun preferredWidgetDay(
    today: LocalDate,
    eventDates: List<LocalDate>,
    manuallySelectedDay: String?,
    manualSelectionDate: String?,
): String {
    val manualDay = manuallySelectedDay?.takeIf { it.isNotBlank() }
    if (manualSelectionDate == today.toString() && manualDay != null) {
        return manualDay
    }

    val lastDayOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return eventDates.asSequence()
        .filter { !it.isBefore(today) && !it.isAfter(lastDayOfWeek) }
        .minOrNull()
        ?.toString()
        ?: today.toString()
}
