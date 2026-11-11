package se.familjekalender.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarConflictAnalyzerTest {
    private val day = LocalDate.of(2026, 11, 11)
    private val next = day.plusDays(1)
    private val hugo = SyncMember("h", "Hugo", "Barn", 0L)
    private val viggo = SyncMember("v", "Viggo", "Barn", 0L)

    private fun event(
        id: String,
        date: LocalDate,
        start: String,
        end: String,
        memberId: String = "h",
        source: String = "manual",
        title: String = id,
        endDate: LocalDate? = date,
    ) = SyncEvent(
        id = id, title = title, date = date, time = start, memberId = memberId,
        source = source, endDate = endDate, endTime = end,
    )

    @Test fun `overnight event conflicts with early next morning`() {
        val late = event("late", day, "23:00", "02:00", endDate = next)
        val early = event("early", next, "01:00", "03:00")
        val conflicts = analyzeCalendarConflicts(listOf(late, early), listOf(hugo), next)
        assertEquals(1, conflicts.size)
        assertEquals(CalendarConflictKind.OVERLAP, conflicts[0].kind)
    }

    @Test fun `overnight conflicts appear in all-date analysis`() {
        val a = event("a", day, "23:00", "02:00", endDate = next)
        val b = event("b", next, "01:00", "03:00")
        assertEquals(1, analyzeCalendarConflicts(listOf(a, b), listOf(hugo)).size)
    }

    @Test fun `not a conflict when first event ends before next begins`() {
        val a = event("a", day, "23:00", "01:00", endDate = next)
        val b = event("b", next, "01:00", "03:00")
        assertTrue(analyzeCalendarConflicts(listOf(a, b), listOf(hugo), next).isEmpty())
    }

    @Test fun `separate family members do not conflict`() {
        val a = event("a", next, "10:00", "12:00", memberId = "h")
        val b = event("b", next, "11:00", "13:00", memberId = "v")
        assertTrue(analyzeCalendarConflicts(listOf(a, b), listOf(hugo, viggo), next).isEmpty())
    }

    @Test fun `reminders and birthdays are excluded from booking conflicts`() {
        val activity = event("a", day, "10:00", "12:00")
        val reminder = event("b", day, "10:00", "11:00", source = "reminder", title = "🔔 Läxa")
        val birthday = event("c", day, "10:00", "11:00", title = "🌈 Födelsedag")
        assertTrue(analyzeCalendarConflicts(
            listOf(activity, reminder, birthday), listOf(hugo), day
        ).isEmpty())
    }

    @Test fun `laundry counts as a normal scheduled activity`() {
        val a = event("a", day, "16:00", "17:00")
        val b = event("b", day, "16:30", "17:30", source = "laundry", title = "Tvätt")
        assertEquals(1, analyzeCalendarConflicts(listOf(a, b), listOf(hugo), day).size)
    }
}
