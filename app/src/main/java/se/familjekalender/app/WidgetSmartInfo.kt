package se.familjekalender.app

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Switches are optional extras. The existing widget design is always kept. */
internal object WidgetFeatureSettings {
    const val WEEK_REMINDERS = "week_reminders"
    const val LIVE_STATUS = "live_status"
    const val BIRTHDAYS = "birthdays"
    const val QUICK_ADD = "quick_add"
    const val SYNC_WARNING = "sync_warning"
    const val WEEK_NUMBER = "week_number"

    private const val PREFS = "family_calendar_widget_features"

    fun enabled(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, true)

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key, enabled).apply()
        FamilyCalendarWidget.refreshFromCache(context)
    }
}

/** Birthdays are never counted as ordinary conflicting calendar activities. */
internal fun widgetIsBirthday(event: SyncEvent): Boolean {
    val title = event.title.trimStart()
    val source = event.source
    return title.startsWith("🌈") ||
        title.contains("födelsedag", ignoreCase = true) ||
        source.contains("birthday", ignoreCase = true) ||
        source.contains("födelsedag", ignoreCase = true)
}

internal data class WidgetUpcomingBirthday(
    val date: LocalDate,
    val title: String,
    val person: String = "",
)

internal fun widgetUpcomingBirthdays(
    today: LocalDate,
    birthdays: List<WidgetUpcomingBirthday>,
): List<WidgetUpcomingBirthday> =
    birthdays.filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(7)) }
        .sortedWith(compareBy<WidgetUpcomingBirthday> { it.date }.thenBy { it.title })

internal fun widgetBirthdayLabel(birthday: WidgetUpcomingBirthday): String {
    val date = birthday.date
    val person = birthday.person.takeIf { it.isNotBlank() && it != "Alla" && it != "Familj" }
    return "🌈 ${date.dayOfMonth}/${date.monthValue} · " +
        (if (person == null) birthday.title else "${person}: ${birthday.title}")
}

internal fun widgetLiveStatus(timeRange: String, now: LocalTime): String {
    val times = Regex("""\b\d{1,2}:\d{2}\b""").findAll(timeRange)
        .mapNotNull { match -> runCatching { LocalTime.parse(match.value, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull() }
        .toList()
    val start = times.firstOrNull() ?: return ""
    val end = times.getOrNull(1)
    if (end != null && end.isAfter(start) && !now.isBefore(start) && now.isBefore(end)) {
        return "Pågår till ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
    val minutesUntil = java.time.Duration.between(now, start).toMinutes()
    return if (minutesUntil in 0..30) "Börjar snart" else ""
}
