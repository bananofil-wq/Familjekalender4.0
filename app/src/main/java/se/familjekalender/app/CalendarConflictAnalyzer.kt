package se.familjekalender.app

import java.time.LocalDate
import java.time.LocalTime

internal enum class CalendarConflictKind {
    SAME_START,
    OVERLAP,
}

internal data class CalendarConflict(
    val date: LocalDate,
    val memberId: String?,
    val memberName: String,
    val first: SyncEvent,
    val second: SyncEvent,
    val kind: CalendarConflictKind,
    val message: String,
)

private data class ConflictInterval(
    val start: Int,
    val end: Int?,
)

private fun isNonActivityConflictExempt(event: SyncEvent): Boolean {
    val source = event.source.trim().lowercase()
    val title = event.title.trimStart()

    // Reminders are notifications, not time reservations. Keep both the canonical
    // source marker and the bell-title fallback so older reminder rows also stay
    // out of double-booking/overlap analysis.
    val isReminder =
        source == "reminder" ||
            source.startsWith("reminder:") ||
            source.contains("reminder") ||
            title.startsWith("🔔")

    return title.startsWith("🌈") || isReminder
}

private fun conflictMinutes(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    val parsed = runCatching { LocalTime.parse(value) }.getOrNull() ?: return null
    return parsed.hour * 60 + parsed.minute
}

private fun conflictInterval(event: SyncEvent): ConflictInterval? {
    val start = conflictMinutes(event.time) ?: return null
    val endValue = conflictMinutes(event.endTime)
    if (endValue == null) return ConflictInterval(start, null)
    val end =
        when {
            event.endDate != null && event.endDate.isAfter(event.date) -> endValue + 24 * 60
            endValue > start -> endValue
            else -> endValue + 24 * 60
        }
    return ConflictInterval(start, end)
}

private fun conflictTitle(event: SyncEvent): String =
    event.title.removePrefix("🌈").removePrefix("🧺").substringBefore(" · ").trim()

private fun conflictClock(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}

internal fun analyzeCalendarConflicts(
    events: List<SyncEvent>,
    members: List<SyncMember>,
    date: LocalDate? = null,
): List<CalendarConflict> {
    val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
    val dates =
        events
            .asSequence()
            .filter { date == null || it.date == date }
            .map { it.date }
            .distinct()
            .sorted()
            .toList()

    val conflicts = mutableListOf<CalendarConflict>()
    val seen = mutableSetOf<String>()

    dates.forEach { currentDate ->
        val dayEvents =
            events.filter { it.date == currentDate && !isNonActivityConflictExempt(it) }
        val memberContexts: List<Pair<String?, String>> =
            if (realMembers.isEmpty()) {
                listOf(ALL_FAMILY_MEMBER_ID to "Hela familjen")
            } else {
                realMembers.map { it.id to it.name }
            }

        memberContexts.forEach { (memberId, memberName) ->
            val relevant =
                dayEvents
                    .filter {
                        it.memberId == memberId ||
                                it.memberId == ALL_FAMILY_MEMBER_ID ||
                                it.memberId == null
                    }
                    .distinctBy { it.id }
                    .sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })

            for (firstIndex in 0 until relevant.lastIndex) {
                val first = relevant[firstIndex]
                val firstInterval = conflictInterval(first) ?: continue

                for (secondIndex in firstIndex + 1..relevant.lastIndex) {
                    val second = relevant[secondIndex]
                    val secondInterval = conflictInterval(second) ?: continue
                    if (first.id == second.id) continue

                    val sameStart = firstInterval.start == secondInterval.start
                    val overlaps =
                        !sameStart &&
                                firstInterval.end != null &&
                                secondInterval.end != null &&
                                firstInterval.start < secondInterval.end &&
                                secondInterval.start < firstInterval.end

                    if (!sameStart && !overlaps) continue

                    val pair = listOf(first.id, second.id).sorted().joinToString(":")
                    val kind =
                        if (sameStart) CalendarConflictKind.SAME_START
                        else CalendarConflictKind.OVERLAP
                    val key = "${currentDate}:${memberId}:${pair}:${kind}"
                    if (!seen.add(key)) continue

                    val message =
                        if (sameStart) {
                            "${memberName} har två aktiviteter kl. ${conflictClock(firstInterval.start)}: " +
                                    "${conflictTitle(first)} och ${conflictTitle(second)}."
                        } else {
                            "${memberName} har överlappande aktiviteter: " +
                                    "${conflictTitle(first)} ${conflictClock(firstInterval.start)}–" +
                                    "${conflictClock(firstInterval.end!!)} och " +
                                    "${conflictTitle(second)} ${conflictClock(secondInterval.start)}–" +
                                    "${conflictClock(secondInterval.end!!)}."
                        }

                    conflicts +=
                        CalendarConflict(
                            date = currentDate,
                            memberId = memberId,
                            memberName = memberName,
                            first = first,
                            second = second,
                            kind = kind,
                            message = message,
                        )
                }
            }
        }
    }

    return conflicts.sortedWith(
        compareBy<CalendarConflict> { it.date }
            .thenBy { it.first.time }
            .thenBy { it.memberName }
    )
}
