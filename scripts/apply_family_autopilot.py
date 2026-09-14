from pathlib import Path

assistant = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
main = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')

text = assistant.read_text()
marker = '@Composable\nprivate fun MemberTwoDayPopup('
if 'internal fun FamilyAutopilotCard(' not in text:
    block = r'''

@Composable
internal fun FamilyAutopilotCard(
    events: List<SyncEvent>,
    members: List<SyncMember>
) {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val weekEnd = today.plusDays(6)
    val locale = Locale("sv", "SE")
    val suggestions = mutableListOf<Pair<String, String>>()

    val tomorrowEvents = events.filter { it.date == tomorrow }.sortedBy { it.time }
    val tomorrowIssues = (
        conflictLines(tomorrowEvents, members) +
            familyPlanningLines(tomorrowEvents, members) +
            coordinationLines(tomorrowEvents, members)
        ).distinct()
    if (tomorrowIssues.isNotEmpty()) {
        suggestions += "Planera imorgon" to "${tomorrowIssues.size} sak${if (tomorrowIssues.size == 1) "" else "er"} behöver lösas innan morgondagen."
    }

    val comingDays = (1L..6L).map { today.plusDays(it) }
    val busiest = comingDays.maxByOrNull { date -> events.count { it.date == date } }
    val busiestCount = busiest?.let { date -> events.count { it.date == date } } ?: 0
    if (busiest != null && busiestCount >= 5) {
        val dayName = busiest.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
        suggestions += "Förbered $dayName" to "$busiestCount aktiviteter gör dagen intensiv. Fördela ansvar och transporter i förväg."
    }

    val recentStart = today.minusDays(42)
    val recentTitles = events
        .filter { !it.date.isBefore(recentStart) && it.date.isBefore(today) }
        .map { it.title.trim() }
        .filter { it.length >= 3 }
        .groupingBy { it.lowercase(locale) }
        .eachCount()
        .filterValues { it >= 3 }
        .toList()
        .sortedByDescending { it.second }

    val recurring = recentTitles.firstOrNull { (normalized, _) ->
        events.none { event ->
            !event.date.isBefore(today) && !event.date.isAfter(today.plusDays(14)) &&
                event.title.trim().lowercase(locale) == normalized
        }
    }
    if (recurring != null) {
        val (normalized, count) = recurring
        val displayTitle = events.lastOrNull { it.title.trim().lowercase(locale) == normalized }?.title ?: normalized
        suggestions += "Återkommande mönster" to "\"$displayTitle\" har lagts in $count gånger senaste 6 veckorna. Ett återkommande schema kan spara tid."
    }

    val weekEvents = events.count { !it.date.isBefore(today) && !it.date.isAfter(weekEnd) }
    if (weekEvents == 0 && suggestions.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Familjeautopilot", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Förslag baserade på familjens egen kalender.", fontSize = 12.sp, color = Muted)
                }
                Text("AUTO", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))
            if (suggestions.isEmpty()) {
                Surface(
                    color = Color(0xFF6DD6A7).copy(alpha = .08f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "✓ Inget särskilt behöver förebyggas just nu.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF6DD6A7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                suggestions.take(3).forEachIndexed { index, suggestion ->
                    Surface(
                        color = if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.White.copy(alpha = .035f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(suggestion.first, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(3.dp))
                            Text(suggestion.second, fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }
}
'''
    if marker not in text:
        raise SystemExit('Member popup marker not found')
    text = text.replace(marker, block + '\n' + marker, 1)
    assistant.write_text(text)

main_text = main.read_text()
anchor = '                    WeekOverviewCard(events, members)\n'
if 'FamilyAutopilotCard(events, members)' not in main_text:
    if anchor not in main_text:
        raise SystemExit('WeekOverviewCard anchor not found')
    main_text = main_text.replace(anchor, anchor + '                    FamilyAutopilotCard(events, members)\n', 1)
    main.write_text(main_text)

print('Family autopilot applied')
