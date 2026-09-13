from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text(encoding='utf-8')

old = '''private fun conflictLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> = events
    .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
    .groupBy { Triple(it.date, it.time, it.memberId) }
    .filterValues { it.size > 1 }
    .values
    .map { group ->
        val first = group.first()
        "${memberName(first.memberId, members)} har ${group.size} aktiviteter samtidigt ${first.time}."
    }
'''

new = '''private data class AssistantTimeRange(val start: Int, val end: Int)

private fun minutesOfDay(value: String): Int? = runCatching {
    val parsed = LocalTime.parse(value)
    parsed.hour * 60 + parsed.minute
}.getOrNull()

private fun assistantTimeRange(event: SyncEvent): AssistantTimeRange? {
    val start = minutesOfDay(event.time) ?: return null
    val range = Regex("(\\\\d{2}:\\\\d{2})[–-](\\\\d{2}:\\\\d{2})").find(event.title)
    val explicitEnd = range?.groupValues?.getOrNull(2)?.let(::minutesOfDay)
    val end = when {
        explicitEnd == null -> start + 60
        explicitEnd > start -> explicitEnd
        else -> explicitEnd + 24 * 60
    }
    return AssistantTimeRange(start, end)
}

private fun conflictLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val warnings = mutableListOf<String>()
    events
        .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
        .groupBy { it.memberId }
        .forEach { (memberId, memberEvents) ->
            val timed = memberEvents.mapNotNull { event -> assistantTimeRange(event)?.let { event to it } }
                .sortedBy { it.second.start }
            for (index in 0 until timed.lastIndex) {
                val (firstEvent, firstRange) = timed[index]
                for (nextIndex in index + 1..timed.lastIndex) {
                    val (secondEvent, secondRange) = timed[nextIndex]
                    if (secondRange.start >= firstRange.end) break
                    val firstTitle = firstEvent.title.substringBefore(" · ").trim()
                    val secondTitle = secondEvent.title.substringBefore(" · ").trim()
                    warnings += "${memberName(memberId, members)} har överlappning: $firstTitle och $secondTitle."
                }
            }
        }
    return warnings.distinct()
}

private fun coordinationLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val timed = events
        .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
        .mapNotNull { event -> minutesOfDay(event.time)?.let { event to it } }
        .sortedBy { it.second }
    val warnings = mutableListOf<String>()
    for (index in 0 until timed.lastIndex) {
        val (first, firstStart) = timed[index]
        for (nextIndex in index + 1..timed.lastIndex) {
            val (second, secondStart) = timed[nextIndex]
            val gap = secondStart - firstStart
            if (gap > 45) break
            if (first.memberId == second.memberId) continue
            warnings += "${memberName(first.memberId, members)} och ${memberName(second.memberId, members)} har aktiviteter med bara $gap minuters mellanrum."
        }
    }
    return warnings.distinct()
}
'''

if old not in text:
    raise SystemExit('Expected old assistant conflict logic not found')
text = text.replace(old, new, 1)

old_state = '''    val conflicts = conflictLines(todaysEvents, members)
    val greeting = when (LocalTime.now().hour) {
'''
new_state = '''    val conflicts = conflictLines(todaysEvents, members)
    val coordination = coordinationLines(todaysEvents, members)
    val greeting = when (LocalTime.now().hour) {
'''
if old_state not in text:
    raise SystemExit('Assistant state insertion point not found')
text = text.replace(old_state, new_state, 1)

old_ui = '''            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Konfliktvarning", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
'''
new_ui = '''            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Konfliktvarning", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
            if (coordination.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Behöver planeras", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                coordination.take(2).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
'''
if old_ui not in text:
    raise SystemExit('Assistant warning UI insertion point not found')
text = text.replace(old_ui, new_ui, 1)

path.write_text(text, encoding='utf-8')
print('Family assistant upgraded with overlap and coordination warnings')
