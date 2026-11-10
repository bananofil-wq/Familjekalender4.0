package se.familjekalender.app

/** Persisted calendar types share the existing calendar_events source column. */
internal enum class CalendarEntryKind { ACTIVITY, REMINDER, LAUNDRY }

internal fun calendarEventSource(kind: CalendarEntryKind, hasTime: Boolean): String =
    when (kind) {
        CalendarEntryKind.ACTIVITY -> if (hasTime) "manual" else "manual_no_time"
        CalendarEntryKind.REMINDER -> if (hasTime) "reminder" else "reminder_no_time"
        CalendarEntryKind.LAUNDRY -> if (hasTime) "laundry" else "laundry_no_time"
    }

/** Backward compatible with laundry created by older versions. */
internal fun isLaundryEvent(event: SyncEvent): Boolean {
    val source = event.source.trim().lowercase()
    val title = event.title.trim()
    return source == "laundry" ||
        source == "laundry_no_time" ||
        title.startsWith("🧺") ||
        title.startsWith("Tvätt", ignoreCase = true)
}
