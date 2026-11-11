package se.familjekalender.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDaySelectionTest {
    private val wednesday = LocalDate.of(2026, 11, 11)

    @Test fun `today appears automatically when it has activities`() {
        assertEquals("2026-11-11", preferredWidgetDay(
            wednesday, listOf(wednesday, wednesday.plusDays(1)), null, null
        ))
    }

    @Test fun `next upcoming day appears if today is empty`() {
        assertEquals("2026-11-12", preferredWidgetDay(
            wednesday, listOf(wednesday.minusDays(1), wednesday.plusDays(1)), null, null
        ))
    }

    @Test fun `today appears when the week is empty`() {
        assertEquals("2026-11-11", preferredWidgetDay(
            wednesday, emptyList(), null, null
        ))
    }

    @Test fun `does not automatically open an event in next week`() {
        assertEquals("2026-11-15", preferredWidgetDay(
            wednesday, listOf(wednesday.plusDays(5), wednesday.plusDays(4)), null, null
        ))
    }

    @Test fun `manual selection overrides the automatic day for the same date`() {
        assertEquals("2026-11-14", preferredWidgetDay(
            wednesday, listOf(wednesday), "2026-11-14", wednesday.toString()
        ))
    }

    @Test fun `manual collapse stays collapsed until tomorrow`() {
        assertEquals("", preferredWidgetDay(
            wednesday, listOf(wednesday), "", wednesday.toString()
        ))
    }

    @Test fun `manual selection resets the next day`() {
        assertEquals("2026-11-12", preferredWidgetDay(
            wednesday.plusDays(1), listOf(wednesday, wednesday.plusDays(1)),
            "2026-11-11", wednesday.toString()
        ))
    }

    @Test fun `sunday does not open monday from next week`() {
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals("2026-11-15", preferredWidgetDay(
            sunday, listOf(sunday.plusDays(1)), null, null
        ))
    }
}
