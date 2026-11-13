package se.familjekalender.app

/**
 * Reminders are calendar events with a reminder source, including all-day reminders.
 * Some imported or legacy events only have the bell marker in their title.
 */
internal fun widgetIsReminder(event: SyncEvent): Boolean =
    event.source.contains("reminder", ignoreCase = true) ||
        event.title.trimStart().startsWith("🔔")

internal fun widgetActivityCountForDay(events: List<SyncEvent>, day: java.time.LocalDate): Int =
    events.count { it.date == day && !widgetIsReminder(it) && !widgetIsBirthday(it) }
