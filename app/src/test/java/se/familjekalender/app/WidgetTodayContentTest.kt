package se.familjekalender.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTodayContentTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun event(
        id: String,
        title: String,
        date: LocalDate = today,
        source: String = "manual",
    ): SyncEvent = SyncEvent(
        id = id,
        title = title,
        date = date,
        time = "09:00",
        memberId = null,
        source = source,
    )

    @Test fun `the current day counts real activities but not reminders`() {
        val events = listOf(
            event("1", "Skola"),
            event("2", "Förskola"),
            event("3", "Förskola"),
            event("4", "🔔 Ta med regnkläder", source = "reminder"),
            event("5", "Aktivitet imorgon", date = today.plusDays(1)),
        )
        assertEquals(3, widgetActivityCountForDay(events, today))
    }

    @Test fun `untimed and old bell only reminders are recognized`() {
        assertTrue(widgetIsReminder(event("1", "Packa väskan", source = "reminder_no_time")))
        assertTrue(widgetIsReminder(event("2", "🔔 Lägg fram kläder")))
        assertFalse(widgetIsReminder(event("3", "Skola")))
    }

    @Test fun `no future activity is counted as today`() {
        assertEquals(0, widgetActivityCountForDay(
            listOf(event("1", "Simning", date = today.plusDays(2))), today,
        ))
    }
}
