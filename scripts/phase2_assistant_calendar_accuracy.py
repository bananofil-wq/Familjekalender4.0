from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text()

old_coord = '''private fun coordinationLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
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
new_coord = '''private fun coordinationLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val timed = events
        .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
        .mapNotNull { event -> assistantTimeRange(event)?.let { event to it } }
        .sortedBy { it.second.start }
    val warnings = mutableListOf<String>()
    for (index in timed.indices) {
        val (first, firstRange) = timed[index]
        for (nextIndex in timed.indices) {
            if (index == nextIndex) continue
            val (second, secondRange) = timed[nextIndex]
            if (first.memberId == second.memberId) continue
            val gap = secondRange.start - firstRange.end
            if (gap in 0..45) {
                warnings += "Kort byte i familjens schema: ${memberName(first.memberId, members)}s ${shortEventTitle(first)} slutar ${clockText(firstRange.end)} och ${memberName(second.memberId, members)}s ${shortEventTitle(second)} börjar ${clockText(secondRange.start)}, bara $gap min emellan."
            }
        }
    }
    return warnings.distinct()
}
'''
if old_coord not in text:
    raise SystemExit('coordinationLines source block not found')
text = text.replace(old_coord, new_coord)

old_suggest = '''private data class AdultAvailability(val member: SyncMember, val freeMinutesBeforeNext: Int?)

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
'''
new_suggest = '''private data class AdultAvailability(val member: SyncMember, val freeMinutesBeforeNext: Int?)

private fun overlapsWindow(range: AssistantTimeRange, start: Int, end: Int): Boolean =
    range.start < end && range.end > start

private fun suggestedAdultAt(events: List<SyncEvent>, adults: List<SyncMember>, minute: Int): AdultAvailability? {
    val pickupWindowStart = minute - 15
    val pickupWindowEnd = minute + 30
    return adults.mapNotNull { adult ->
        val ranges = events
            .filter { it.memberId == adult.id }
            .mapNotNull(::assistantTimeRange)
            .sortedBy { it.start }
        if (ranges.any { overlapsWindow(it, pickupWindowStart, pickupWindowEnd) }) return@mapNotNull null
        val nextStart = ranges.firstOrNull { it.start >= pickupWindowEnd }?.start
        AdultAvailability(adult, nextStart?.minus(minute))
    }.sortedWith(
        compareByDescending<AdultAvailability> { it.freeMinutesBeforeNext ?: Int.MAX_VALUE }
            .thenBy { it.member.name.lowercase(Locale("sv", "SE")) }
    ).firstOrNull()
}
'''
if old_suggest not in text:
    raise SystemExit('suggestedAdultAt source block not found')
text = text.replace(old_suggest, new_suggest)

text = text.replace(
    'actions += "Förslag: ${suggestion.member.name} kan hämta ${child.name} runt ${clockText(pickup)}$suffix"',
    'actions += "Enligt kalendern har ${suggestion.member.name} bäst lucka runt ${clockText(pickup)} för ${child.name}s hämtning$suffix"'
)

old_available = '''            val availableAdults = adults.filter { adult ->
                val adultRanges = eventsByMember[adult.id].orEmpty().mapNotNull(::assistantTimeRange)
                adultRanges.none { range -> pickupMinute >= range.start && pickupMinute < range.end }
            }
'''
new_available = '''            val pickupWindowStart = pickupMinute - 15
            val pickupWindowEnd = pickupMinute + 30
            val availableAdults = adults.filter { adult ->
                val adultRanges = eventsByMember[adult.id].orEmpty().mapNotNull(::assistantTimeRange)
                adultRanges.none { range -> overlapsWindow(range, pickupWindowStart, pickupWindowEnd) }
            }
'''
if old_available not in text:
    raise SystemExit('availableAdults source block not found')
text = text.replace(old_available, new_available)
text = text.replace(
    'ingen vuxen verkar ledig enligt kalendern.',
    'ingen vuxen har en fri kalenderlucka från 15 min före till 30 min efter hämtningen.'
)

path.write_text(text)
print('Assistant calendar planning hardened')
