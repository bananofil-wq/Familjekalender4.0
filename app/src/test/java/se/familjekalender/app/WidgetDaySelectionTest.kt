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

    @Test fun `legacy empty manual day does not hide today's activities`() {
        assertEquals("2026-11-11", preferredWidgetDay(
            wednesday, listOf(wednesday), "", wednesday.toString()
        ))
    }

    @Test fun `legacy empty manual day still reveals tomorrow when today is empty`() {
        assertEquals("2026-11-12", preferredWidgetDay(
            wednesday, listOf(wednesday.plusDays(1)), "", wednesday.toString()
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
    @Test fun `today remains open when tomorrow was selected manually`() {
        val tomorrow = wednesday.plusDays(1)
        assertEquals(true, widgetDayIsExpanded(
            date = wednesday, today = wednesday, hasActivities = true,
            selectedDay = tomorrow.toString(), collapsedTodayOn = null,
        ))
        assertEquals(true, widgetDayIsExpanded(
            date = tomorrow, today = wednesday, hasActivities = true,
            selectedDay = tomorrow.toString(), collapsedTodayOn = null,
        ))
    }

    @Test fun `today may be deliberately collapsed only for that date`() {
        val tomorrow = wednesday.plusDays(1)
        assertEquals(false, widgetDayIsExpanded(
            date = wednesday, today = wednesday, hasActivities = true,
            selectedDay = tomorrow.toString(), collapsedTodayOn = wednesday.toString(),
        ))
        assertEquals(true, widgetDayIsExpanded(
            date = tomorrow, today = tomorrow, hasActivities = true,
            selectedDay = wednesday.toString(), collapsedTodayOn = wednesday.toString(),
        ))
    }

    @Test fun `empty today still follows manual selection`() {
        val tomorrow = wednesday.plusDays(1)
        assertEquals(false, widgetDayIsExpanded(
            date = wednesday, today = wednesday, hasActivities = false,
            selectedDay = tomorrow.toString(), collapsedTodayOn = null,
        ))
        assertEquals(true, widgetDayIsExpanded(
            date = wednesday, today = wednesday, hasActivities = false,
            selectedDay = wednesday.toString(), collapsedTodayOn = null,
        ))
    }

}
