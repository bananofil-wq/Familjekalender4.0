package se.familjekalender.app

import java.time.LocalDate
import java.time.YearMonth

internal data class WorkMonthEventInput(
    val title: String,
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val memberId: String?,
)

internal fun isBirthdayEvent(event: SyncEvent): Boolean =
    event.title.startsWith("🎂") || event.title.startsWith("🌈")

internal suspend fun saveWorkMonthDirect(
    session: FamilySession,
    month: YearMonth,
    title: String,
    memberId: String?,
    rows: List<WorkMonthEventInput>,
    replaceExisting: Boolean,
): Int {
    if (replaceExisting) {
        deleteWorkMonth(session, month, memberId)
    }

    if (rows.isEmpty()) return 0

    val seriesId = java.util.UUID.randomUUID().toString()
    rows.forEach { row ->
        SupabaseSync.addEvent(
            session,
            row.title,
            row.date,
            row.startTime,
            row.endTime,
            row.memberId,
            seriesId,
        )
    }
    return rows.size
}
