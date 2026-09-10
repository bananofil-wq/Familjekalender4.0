package se.familjekalender.app

import java.time.LocalDate

enum class RecurrenceMode(val label: String) {
    NONE("Upprepas inte"),
    WEEKLY("Varje vecka"),
    MONTHLY("Varje månad"),
    YEARLY("Varje år")
}

fun recurringDates(start: LocalDate, mode: RecurrenceMode): List<LocalDate> = when (mode) {
    RecurrenceMode.NONE -> listOf(start)
    RecurrenceMode.WEEKLY -> generateSequence(start) { it.plusWeeks(1) }.take(105).toList()
    RecurrenceMode.MONTHLY -> generateSequence(start) { it.plusMonths(1) }.take(61).toList()
    RecurrenceMode.YEARLY -> generateSequence(start) { current ->
        runCatching { current.plusYears(1) }.getOrNull()
    }.take(21).toList()
}
