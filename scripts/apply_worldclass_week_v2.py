from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text(encoding='utf-8')
start_marker = 'private data class WeekDaySummary('
end_marker = '@Composable\ninternal fun FamilyAutopilotCard('
start = text.find(start_marker)
end = text.find(end_marker)
if start == -1 or end == -1 or end <= start:
    raise SystemExit('Week overview anchors not found')

replacement = '''private data class WeekDaySummary(
    val date: LocalDate,
    val events: List<SyncEvent>,
    val warnings: List<String>
)

@Composable
internal fun WeekOverviewCard(
    events: List<SyncEvent>,
    members: List<SyncMember>
) {
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val locale = Locale("sv", "SE")
    val days = (0L..6L).map { offset ->
        val date = weekStart.plusDays(offset)
        val dayEvents = events.filter { it.date == date }.sortedBy { it.time }
        val warnings = (
            conflictLines(dayEvents, members) +
                familyPlanningLines(dayEvents, members) +
                coordinationLines(dayEvents, members)
            ).distinct()
        WeekDaySummary(date = date, events = dayEvents, warnings = warnings)
    }

    val totalEvents = days.sumOf { it.events.size }
    val totalWarnings = days.sumOf { it.warnings.size }
    val intenseDays = days.count { it.events.size >= 5 || it.warnings.isNotEmpty() }
    val calmDays = days.count { it.events.size <= 1 && it.warnings.isEmpty() }
    val busiest = days.maxByOrNull { it.events.size }
    val calmest = days.minWithOrNull(compareBy<WeekDaySummary> { it.events.size }.thenBy { it.warnings.size })
    val firstProblemDay = days.firstOrNull { !it.date.isBefore(today) && it.warnings.isNotEmpty() }

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
                    Text("$totalEvents aktiviteter · $totalWarnings planeringspunkter", fontSize = 12.sp, color = Muted)
                }
                val weekState = when {
                    totalWarnings > 0 -> "ATT PLANERA"
                    intenseDays >= 3 -> "INTENSIV"
                    else -> "BALANS"
                }
                val stateColor = when {
                    totalWarnings > 0 -> MaterialTheme.colorScheme.error
                    intenseDays >= 3 -> Color(0xFFFFB86B)
                    else -> Color(0xFF6DD6A7)
                }
                Text(weekState, fontSize = 10.sp, color = stateColor, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = Color.White.copy(alpha = .04f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text("Intensiva", fontSize = 10.sp, color = Muted)
                        Text("$intenseDays dagar", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = .04f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text("Lugna", fontSize = 10.sp, color = Muted)
                        Text("$calmDays dagar", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = .04f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text("Att lösa", fontSize = 10.sp, color = Muted)
                        Text("$totalWarnings", fontSize = 15.sp, color = if (totalWarnings > 0) MaterialTheme.colorScheme.error else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (firstProblemDay != null) {
                val focusDay = firstProblemDay.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = .08f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Veckans fokus · $focusDay", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(3.dp))
                        Text(firstProblemDay.warnings.first(), fontSize = 11.sp, color = Color.White.copy(alpha = .88f))
                        if (firstProblemDay.warnings.size > 1) {
                            Text("+ ${firstProblemDay.warnings.size - 1} till på samma dag", fontSize = 10.sp, color = Muted)
                        }
                    }
                }
            } else {
                Surface(
                    color = Color(0xFF6DD6A7).copy(alpha = .07f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "✓ Veckan har inga upptäckta krockar eller olösta hämtningar.",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 11.sp,
                        color = Color(0xFF6DD6A7),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            days.forEach { day ->
                val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() }
                val warningCount = day.warnings.size
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
                val loadFraction = (day.events.size.coerceAtMost(6) / 6f).coerceAtLeast(if (day.events.isEmpty()) 0f else .10f)

                Surface(
                    color = if (day.date == today) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.White.copy(alpha = .035f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .06f))
                        ) {
                            if (loadFraction > 0f) {
                                Box(
                                    Modifier.fillMaxWidth(loadFraction).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(loadColor.copy(alpha = .85f))
                                )
                            }
                        }
                        if (day.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(7.dp))
                            Text(day.warnings.first(), fontSize = 10.sp, color = Color.White.copy(alpha = .78f), maxLines = 2)
                        }
                    }
                }
            }

            if (busiest != null && busiest.events.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                val busiestName = busiest.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
                val calmestName = calmest?.date?.dayOfWeek?.getDisplayName(TextStyle.FULL, locale)?.replaceFirstChar { it.uppercase() }
                Text(
                    buildString {
                        append("Mest belastad: $busiestName · ${busiest.events.size} aktiviteter")
                        if (calmestName != null && calmest?.date != busiest.date) append("  •  Lugnast: $calmestName")
                    },
                    fontSize = 10.sp,
                    color = Muted
                )
            }
        }
    }
}


'''

path.write_text(text[:start] + replacement + text[end:], encoding='utf-8')
print('Applied world-class week v2')
