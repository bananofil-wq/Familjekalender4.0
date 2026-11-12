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
    val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return reminders.asSequence()
        .filter { !it.date.isBefore(today) && !it.date.isAfter(sunday) }
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
