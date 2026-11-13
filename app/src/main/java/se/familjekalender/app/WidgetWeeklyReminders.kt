package se.familjekalender.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** One real calendar reminder (not a task or a regular future activity). */
internal data class WidgetWeeklyReminder(
    val date: LocalDate,
    val title: String,
    val time: String = "",
    val person: String = "",
)

/** A compact, bottom-pinned preview. The calendar contains the full list. */
internal data class WidgetWeeklyReminderPreview(
    val visible: List<WidgetWeeklyReminder>,
    val extraCount: Int,
)

internal fun upcomingWeekReminders(
    today: LocalDate,
    reminders: List<WidgetWeeklyReminder>,
): List<WidgetWeeklyReminder> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    return reminders.asSequence()
        .filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
        .sortedWith(
            compareBy<WidgetWeeklyReminder> { it.date }
                .thenBy { it.time.ifBlank { "00:00" } }
                .thenBy { it.title }
        )
        .toList()
}

internal fun weeklyReminderPreview(
    today: LocalDate,
    reminders: List<WidgetWeeklyReminder>,
    maxVisible: Int = 3,
): WidgetWeeklyReminderPreview {
    require(maxVisible >= 1)
    val sorted = upcomingWeekReminders(today, reminders)
    return WidgetWeeklyReminderPreview(
        visible = sorted.take(maxVisible),
        extraCount = (sorted.size - maxVisible).coerceAtLeast(0),
    )
}

internal fun weeklyReminderLabel(
    reminder: WidgetWeeklyReminder,
    locale: Locale = Locale("sv", "SE"),
): String {
    val day = reminder.date.dayOfWeek
        .getDisplayName(TextStyle.SHORT, locale)
        .trimEnd('.')
        .replaceFirstChar { it.uppercaseChar() }
    val time = reminder.time.takeIf { it.isNotBlank() }
    val person = reminder.person.takeIf {
        it.isNotBlank() && it != "Alla" && it != "⭐ Alla" && it != "Familj"
    }
    return buildString {
        append(day)
        time?.let { append(" ").append(it) }
        append(" · ")
        person?.let { append(it).append(": ") }
        append(reminder.title.trim())
    }
}


/** A perpetual reminder is one stored event, not hundreds of generated events. */
internal fun isPermanentReminderSource(source: String): Boolean =
    source.startsWith("reminder_persistent", ignoreCase = true)

internal data class WidgetPermanentReminder(
    val eventId: String,
    val startDate: LocalDate,
    val title: String,
    val time: String = "",
    val person: String = "",
)

internal fun activePermanentReminders(
    today: LocalDate,
    reminders: List<WidgetPermanentReminder>,
): List<WidgetPermanentReminder> =
    reminders.asSequence()
        .filter { !it.startDate.isAfter(today) && it.title.isNotBlank() }
        .distinctBy { it.eventId }
        .sortedWith(compareBy<WidgetPermanentReminder> { it.startDate }.thenBy { it.title })
        .toList()

internal fun permanentReminderLabel(reminder: WidgetPermanentReminder): String {
    val who = reminder.person.takeUnless {
        it.isBlank() || it == "Alla" || it == "⭐ Alla" || it == "Familj"
    }
    val time = reminder.time.takeIf { it.isNotBlank() }
    return buildString {
        append("📌 ")
        who?.let { append(it).append(": ") }
        append(reminder.title.trim())
        time?.let { append(" · ").append(it) }
    }
}
