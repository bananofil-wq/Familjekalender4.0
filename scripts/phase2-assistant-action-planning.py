from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text()

anchor = '''private fun familyPlanningLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {'''
insert = '''private data class AdultAvailability(val member: SyncMember, val freeMinutesBeforeNext: Int?)

private fun suggestedAdultAt(events: List<SyncEvent>, adults: List<SyncMember>, minute: Int): AdultAvailability? {
    return adults.mapNotNull { adult ->
        val ranges = events
            .filter { it.memberId == adult.id }
            .mapNotNull(::assistantTimeRange)
            .sortedBy { it.start }
        if (ranges.any { minute >= it.start && minute < it.end }) return@mapNotNull null
        val nextStart = ranges.firstOrNull { it.start >= minute }?.start
        AdultAvailability(adult, nextStart?.minus(minute))
    }.maxWithOrNull(compareBy<AdultAvailability> { it.freeMinutesBeforeNext ?: Int.MAX_VALUE }.thenBy { it.member.name })
}

private fun actionPlanningLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
    val children = realMembers.filter(::isChildMember)
    val adults = realMembers.filterNot(::isChildMember)
    if (children.isEmpty() || adults.isEmpty()) return emptyList()

    val eventsByMember = events
        .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
        .groupBy { it.memberId }
    val actions = mutableListOf<String>()

    children.forEach { child ->
        val childEvents = eventsByMember[child.id].orEmpty()
        childEvents.filter(::isCareOrSchoolEvent).forEach { care ->
            val pickup = assistantTimeRange(care)?.end ?: return@forEach
            val suggestion = suggestedAdultAt(events, adults, pickup)
            if (suggestion != null) {
                val margin = suggestion.freeMinutesBeforeNext
                val suffix = when {
                    margin == null -> " och har inget senare tidsatt åtagande."
                    margin >= 120 -> " och har minst ${margin / 60} timmar till nästa tidsatta åtagande."
                    else -> " och har cirka $margin minuter till nästa tidsatta åtagande."
                }
                actions += "Förslag: ${suggestion.member.name} kan hämta ${child.name} runt ${clockText(pickup)}$suffix"
            }
        }
    }
    return actions.distinct()
}

'''
if anchor not in text:
    raise SystemExit('familyPlanningLines anchor missing')
text = text.replace(anchor, insert + anchor, 1)

old = '''    val planning = (familyPlanningLines(todaysEvents, members) + coordinationLines(todaysEvents, members)).distinct()
    val tomorrowConflicts = conflictLines(tomorrowsEvents, members)
    val tomorrowPlanning = (familyPlanningLines(tomorrowsEvents, members) + coordinationLines(tomorrowsEvents, members)).distinct()
'''
new = '''    val planning = (familyPlanningLines(todaysEvents, members) + coordinationLines(todaysEvents, members)).distinct()
    val actions = actionPlanningLines(todaysEvents, members)
    val tomorrowConflicts = conflictLines(tomorrowsEvents, members)
    val tomorrowPlanning = (familyPlanningLines(tomorrowsEvents, members) + coordinationLines(tomorrowsEvents, members)).distinct()
    val tomorrowActions = actionPlanningLines(tomorrowsEvents, members)
'''
if old not in text:
    raise SystemExit('planning state anchor missing')
text = text.replace(old, new, 1)

old = '''            if (tomorrowConflicts.isNotEmpty() || tomorrowPlanning.isNotEmpty()) {
'''
new = '''            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Förslag", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                actions.take(2).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
            if (tomorrowConflicts.isNotEmpty() || tomorrowPlanning.isNotEmpty() || tomorrowActions.isNotEmpty()) {
'''
if old not in text:
    raise SystemExit('tomorrow block anchor missing')
text = text.replace(old, new, 1)

old = '''                tomorrowPlanning.take(2).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                }
'''
new = '''                tomorrowPlanning.take(2).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                }
                tomorrowActions.take(1).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                }
'''
if old not in text:
    raise SystemExit('tomorrow planning lines anchor missing')
text = text.replace(old, new, 1)

path.write_text(text)
print('Patched FamilyAssistantScreen.kt with actionable pickup suggestions')
