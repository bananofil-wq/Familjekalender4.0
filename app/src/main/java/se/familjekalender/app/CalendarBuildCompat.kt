package se.familjekalender.app

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal data class WorkMonthEventInput(
    val title: String,
    val date: LocalDate,
    val startTime: String,
    val memberId: String?
)

internal fun isBirthdayEvent(event: SyncEvent): Boolean =
    event.title.startsWith("🎂") || event.title.startsWith("🌈")

internal suspend fun saveWorkMonthDirect(
    session: FamilySession,
    month: YearMonth,
    title: String,
    memberId: String?,
    rows: List<WorkMonthEventInput>,
    replaceExisting: Boolean
): Int {
    if (replaceExisting) {
        deleteWorkMonth(session, month, memberId)
    }

    if (rows.isEmpty()) return 0

    val grouped = rows.groupBy { it.startTime }
    val rules = grouped.map { (start, events) ->
        val weekdays = events.map { it.date.dayOfWeek.value }.toSet()
        val end = runCatching {
            LocalTime.parse(start, DateTimeFormatter.ofPattern("HH:mm"))
                .plusHours(8)
                .plusMinutes(18)
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault(start)
        WorkRule(weekdays = weekdays, startTime = start, endTime = end)
    }

    return saveWorkMonth(
        session = session,
        month = month,
        title = title,
        memberId = memberId,
        rules = rules
    )
}
