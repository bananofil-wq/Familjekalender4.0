from pathlib import Path

assistant_path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
main_path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')

assistant = assistant_path.read_text()
main = main_path.read_text()

if 'internal fun WeekOverviewCard(' not in assistant:
    marker = '\n@Composable\nprivate fun MemberTwoDayPopup('
    block = r'''

private data class WeekDaySummary(
    val date: LocalDate,
    val events: List<SyncEvent>,
    val conflictCount: Int,
    val planningCount: Int
)

@Composable
internal fun WeekOverviewCard(
    events: List<SyncEvent>,
    members: List<SyncMember>
) {
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = (0L..6L).map { offset ->
        val date = weekStart.plusDays(offset)
        val dayEvents = events.filter { it.date == date }.sortedBy { it.time }
        WeekDaySummary(
            date = date,
            events = dayEvents,
            conflictCount = conflictLines(dayEvents, members).size,
            planningCount = (familyPlanningLines(dayEvents, members) + coordinationLines(dayEvents, members)).distinct().size
        )
    }
    val totalEvents = days.sumOf { it.events.size }
    val totalWarnings = days.sumOf { it.conflictCount + it.planningCount }
    val busiest = days.maxByOrNull { it.events.size }
    val locale = Locale("sv", "SE")

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Veckan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("$totalEvents aktiviteter · $totalWarnings saker att hålla koll på", fontSize = 12.sp, color = Muted)
                }
                if (busiest != null && busiest.events.isNotEmpty()) {
                    val dayName = busiest.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() }
                    Text("Mest: $dayName", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))
            days.forEach { day ->
                val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() }
                val warningCount = day.conflictCount + day.planningCount
                val loadLabel = when {
                    warningCount > 0 -> "Behöver planeras"
                    day.events.size >= 5 -> "Intensiv"
                    day.events.size >= 3 -> "Normal"
                    day.events.isEmpty() -> "Lugn"
                    else -> "Lätt"
                }
                val loadColor = when {
                    warningCount > 0 -> MaterialTheme.colorScheme.error
                    day.events.size >= 5 -> Color(0xFFFFB86B)
                    day.events.isEmpty() -> Color(0xFF6DD6A7)
                    else -> Muted
                }
                Surface(
                    color = if (day.date == today) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.White.copy(alpha = .035f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.width(58.dp)) {
                            Text(dayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${day.date.dayOfMonth}/${day.date.monthValue}", fontSize = 10.sp, color = Muted)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${day.events.size} aktiviteter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(loadLabel, fontSize = 10.sp, color = loadColor)
                        }
                        if (warningCount > 0) {
                            Surface(color = MaterialTheme.colorScheme.error.copy(alpha = .14f), shape = CircleShape) {
                                Text("$warningCount", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
'''
    if marker not in assistant:
        raise SystemExit('Member popup marker not found')
    assistant = assistant.replace(marker, block + marker, 1)
    assistant_path.write_text(assistant)

call_marker = '                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }\n                    RecurringLifeCard(session = session, events = events) { scope.launch { refresh() } }'
replacement = '                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }\n                    WeekOverviewCard(events, members)\n                    RecurringLifeCard(session = session, events = events) { scope.launch { refresh() } }'
if 'WeekOverviewCard(events, members)' not in main:
    if call_marker not in main:
        raise SystemExit('MainActivity assistant marker not found')
    main = main.replace(call_marker, replacement, 1)
    main_path.write_text(main)

print('World-class week overview applied')
