package se.familjekalender.app

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRecurrenceTest {
    @Test fun `weekday repetition excludes weekends`() {
        val dates = recurringDates(LocalDate.of(2026, 11, 13), RecurrenceMode.WEEKDAYS)
        assertEquals(LocalDate.of(2026, 11, 13), dates.first())
        assertEquals(LocalDate.of(2026, 11, 16), dates[1])
        assertTrue(dates.all { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY })
    }

    @Test fun `every other week repeats fourteen days later`() {
        val dates = recurringDates(LocalDate.of(2026, 11, 11), RecurrenceMode.BIWEEKLY)
        assertEquals(LocalDate.of(2026, 11, 25), dates[1])
    }

    @Test fun `a weekend start for weekday repetition begins monday`() {
        val dates = recurringDates(LocalDate.of(2026, 11, 14), RecurrenceMode.WEEKDAYS)
        assertEquals(LocalDate.of(2026, 11, 16), dates.first())
    }
}
