package se.familjekalender.app

/**
 * Explain exactly which widget sections could not be refreshed without hiding
 * a successful update to the other sections.
 */
internal fun widgetRefreshStatus(
    todayCount: Int?,
    eventsUpdated: Boolean,
    membersUpdated: Boolean,
    todoUpdated: Boolean,
    shoppingUpdated: Boolean,
    hasSavedEvents: Boolean,
): String {
    val calendarStatus = when {
        !eventsUpdated && hasSavedEvents -> "Aktiviteter: visar sparade"
        !eventsUpdated -> "Aktiviteter kunde inte hämtas"
        todayCount == 0 -> "Inga aktiviteter idag"
        todayCount == 1 -> "1 aktivitet idag"
        todayCount != null -> "$todayCount aktiviteter idag"
        else -> "Aktiviteter uppdaterade"
    }

    val failedSections = buildList {
        if (!membersUpdated) add("personer")
        if (!todoUpdated) add("To-Do")
        if (!shoppingUpdated) add("inköp")
    }
    return if (failedSections.isEmpty()) {
        calendarStatus
    } else {
        "$calendarStatus · ${failedSections.joinToString(", ")} ej uppdaterat"
    }
}
