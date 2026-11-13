package se.familjekalender.app

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSmartInfoTest {
    @Test fun `an event is active only between start and end`() {
        assertEquals("Pågår till 15:00", widgetLiveStatus("08:00–15:00", LocalTime.of(9, 30)))
        assertEquals("", widgetLiveStatus("08:00–15:00", LocalTime.of(15, 1)))
    }

    @Test fun `upcoming status shows only within thirty minutes`() {
        assertEquals("Börjar snart", widgetLiveStatus("10:00–11:00", LocalTime.of(9, 45)))
        assertEquals("", widgetLiveStatus("10:00–11:00", LocalTime.of(9, 0)))
        assertEquals("", widgetLiveStatus("", LocalTime.of(9, 0)))
    }

    @Test fun `birthdays in the next seven days only`() {
        val today = LocalDate.of(2026, 11, 13)
        val list = listOf(
            WidgetUpcomingBirthday(today.minusDays(1), "Igår"),
            WidgetUpcomingBirthday(today.plusDays(7), "Nästa vecka"),
            WidgetUpcomingBirthday(today.plusDays(8), "För sent"),
            WidgetUpcomingBirthday(today, "Idag"),
        )
        assertEquals(listOf("Idag", "Nästa vecka"), widgetUpcomingBirthdays(today, list).map { it.title })
    }

    @Test fun `birthday events are distinct from activities`() {
        val birthday = SyncEvent("1", "🌈 Hugo", LocalDate.of(2026, 11, 13), "", null, "birthday")
        val school = SyncEvent("2", "Skola", LocalDate.of(2026, 11, 13), "08:00", null, "manual")
        assertTrue(widgetIsBirthday(birthday))
        assertFalse(widgetIsBirthday(school))
        assertEquals(1, widgetActivityCountForDay(listOf(birthday, school), birthday.date))
    }
}
