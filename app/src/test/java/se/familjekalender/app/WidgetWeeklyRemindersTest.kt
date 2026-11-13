package se.familjekalender.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWeeklyRemindersTest {
    private val thursday = LocalDate.of(2026, 11, 12)

    @Test fun `includes the full Monday to Sunday week even for past weekdays`() {
        val rows = listOf(
            WidgetWeeklyReminder(thursday.minusDays(1), "redan passerad"),
            WidgetWeeklyReminder(thursday, "gympapåse"),
            WidgetWeeklyReminder(thursday.plusDays(3), "söndag"),
            WidgetWeeklyReminder(thursday.plusDays(4), "nästa måndag"),
        )
        assertEquals(
            listOf("redan passerad", "gympapåse", "söndag"),
            upcomingWeekReminders(thursday, rows).map { it.title },
        )
    }

    @Test fun `sorts dates and times and supports untimed reminders`() {
        val rows = listOf(
            WidgetWeeklyReminder(thursday.plusDays(1), "senare", "16:00"),
            WidgetWeeklyReminder(thursday, "idag sent", "19:00"),
            WidgetWeeklyReminder(thursday.plusDays(1), "utan tid"),
            WidgetWeeklyReminder(thursday, "idag utan tid"),
            WidgetWeeklyReminder(thursday.plusDays(1), "tidigare", "08:00"),
        )
        assertEquals(
            listOf("idag utan tid", "idag sent", "utan tid", "tidigare", "senare"),
            upcomingWeekReminders(thursday, rows).map { it.title },
        )
    }

    @Test fun `three lines are displayed with an accurate remaining count`() {
        val rows = (1..7).map {
            WidgetWeeklyReminder(thursday.plusDays((it - 1) % 4L), "Påminnelse $it")
        }
        val preview = weeklyReminderPreview(thursday, rows)
        assertEquals(3, preview.visible.size)
        assertEquals(4, preview.extraCount)
    }

    @Test fun `few or no reminders do not show a misleading more count`() {
        assertEquals(0, weeklyReminderPreview(thursday, emptyList()).extraCount)
        assertEquals(0, weeklyReminderPreview(
            thursday,
            listOf(WidgetWeeklyReminder(thursday, "Kläder")),
        ).extraCount)
    }

    @Test fun `compact labels show weekday and optional time or person`() {
        assertEquals(
            "Tors · Ta med kläder",
            weeklyReminderLabel(WidgetWeeklyReminder(thursday, "Ta med kläder")),
        )
        val label = weeklyReminderLabel(
            WidgetWeeklyReminder(thursday.plusDays(1), "Gympapåse", "08:00", "Hugo"),
        )
        assertTrue(label.startsWith("Fre 08:00 · Hugo: Gympapåse"))
    }

    @Test fun `sunday includes Monday to Sunday but not next Monday`() {
        val sunday = LocalDate.of(2026, 11, 15)
        val reminders = listOf(
            WidgetWeeklyReminder(sunday.minusDays(6), "Måndag"),
            WidgetWeeklyReminder(sunday, "Söndag"),
            WidgetWeeklyReminder(sunday.plusDays(1), "Måndag"),
        )
        assertEquals(
            listOf("Måndag", "Söndag"),
            upcomingWeekReminders(sunday, reminders).map { it.title },
        )
    }
    @Test fun `persistent reminder remains visible after many months without creating duplicates`() {
        val today = LocalDate.of(2026, 11, 13)
        val reminder = WidgetPermanentReminder(
            eventId = "forever-1",
            startDate = LocalDate.of(2025, 1, 1),
            title = "Kolla räkningarna",
        )
        val list = listOf(reminder, reminder.copy(), reminder.copy(eventId = "new", startDate = today.plusDays(1)))
        assertEquals(listOf("forever-1"), activePermanentReminders(today, list).map { it.eventId })
        assertTrue(permanentReminderLabel(reminder).contains("Kolla räkningarna"))
    }

    @Test fun `permanent reminder source and no-time source are both detected`() {
        assertTrue(isPermanentReminderSource("reminder_persistent"))
        assertTrue(isPermanentReminderSource("reminder_persistent_no_time"))
        assertEquals(false, isPermanentReminderSource("reminder_no_time"))
    }

    @Test fun `reminders outside current week do not appear in weekly preview`() {
        val friday = LocalDate.of(2026, 11, 13)
        assertEquals(
            listOf("måndag", "söndag"),
            upcomingWeekReminders(
                friday,
                listOf(
                    WidgetWeeklyReminder(friday.minusDays(5), "förra söndagen"),
                    WidgetWeeklyReminder(friday.minusDays(4), "måndag"),
                    WidgetWeeklyReminder(friday.plusDays(2), "söndag"),
                    WidgetWeeklyReminder(friday.plusDays(3), "nästa måndag"),
                ),
            ).map { it.title },
        )
    }

}
