from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
s = p.read_text(encoding='utf-8')

old = '''    val days = (0L..6L).map { offset ->
        val date = weekStart.plusDays(offset)
        val dayEvents = events.filter { it.date == date }.sortedBy { it.time }
        val warnings = (
            conflictLines(dayEvents, members) +
                familyPlanningLines(dayEvents, members) +
                coordinationLines(dayEvents, members)
            ).distinct()
        WeekDaySummary(date = date, events = dayEvents, warnings = warnings)
    }

    val totalEvents = days.sumOf { it.events.size }'''
new = '''    val days = (0L..6L).map { offset ->
        val date = weekStart.plusDays(offset)
        val dayEvents = events.filter { it.date == date }.sortedBy { it.time }
        val warnings = (
            conflictLines(dayEvents, members) +
                familyPlanningLines(dayEvents, members) +
                coordinationLines(dayEvents, members)
            ).distinct()
        WeekDaySummary(date = date, events = dayEvents, warnings = warnings)
    }
    var selectedWeekDay by remember { mutableStateOf<WeekDaySummary?>(null) }

    val totalEvents = days.sumOf { it.events.size }'''
assert old in s, 'days anchor not found'
s = s.replace(old, new, 1)

old = '''                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Veckans fokus · $focusDay",'''
new = '''                    modifier = Modifier.fillMaxWidth().clickable { selectedWeekDay = firstProblemDay }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Veckans fokus · $focusDay",'''
assert old in s, 'focus card anchor not found'
s = s.replace(old, new, 1)

old = '''                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {'''
new = '''                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { selectedWeekDay = day }
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {'''
assert old in s, 'day card anchor not found'
s = s.replace(old, new, 1)

old = '''                            Column(Modifier.weight(1f)) {
                                Text("${day.events.size} aktiviteter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(loadLabel, fontSize = 10.sp, color = loadColor)
                            }'''
new = '''                            Column(Modifier.weight(1f)) {
                                Text("${day.events.size} aktiviteter", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(loadLabel, fontSize = 10.sp, color = loadColor)
                                Text("Tryck för detaljer", fontSize = 9.sp, color = Muted)
                            }'''
assert old in s, 'day label anchor not found'
s = s.replace(old, new, 1)

marker = '''        }
    }
}


@Composable
internal fun FamilyAutopilotCard('''
insert = '''        }
    }

    selectedWeekDay?.let { day ->
        WeekDayDetailPopup(
            day = day,
            members = members,
            onDismiss = { selectedWeekDay = null }
        )
    }
}

private fun weekActionSuggestion(warning: String): String = when {
    warning.contains("Kort byte", ignoreCase = true) ->
        "Lägg in restid/överlämning eller flytta en av aktiviteterna så att det finns marginal."
    warning.contains("överlappning", ignoreCase = true) ->
        "Flytta en av tiderna eller bestäm vem som tar respektive aktivitet."
    warning.contains("Hämtning", ignoreCase = true) ->
        "Bestäm vem som hämtar och lägg in ansvaret i kalendern."
    warning.contains("transport", ignoreCase = true) ->
        "Bestäm hämtning och transport mellan aktiviteterna och lägg in marginal."
    else -> "Justera tid, ansvar eller transport för de berörda aktiviteterna."
}

@Composable
private fun WeekDayDetailPopup(
    day: WeekDaySummary,
    members: List<SyncMember>,
    onDismiss: () -> Unit
) {
    val locale = Locale("sv", "SE")
    val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$dayName ${day.date.dayOfMonth}/${day.date.monthValue}") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (day.warnings.isNotEmpty()) {
                    Text("Behöver åtgärdas", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    day.warnings.forEachIndexed { index, warning ->
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = .08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Text("${index + 1}. $warning", fontSize = 12.sp, color = Color.White)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Förslag: ${weekActionSuggestion(warning)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    Text("✓ Inget särskilt behöver åtgärdas den här dagen.", color = Color(0xFF6DD6A7), fontSize = 12.sp)
                }

                Text("Aktiviteter (${day.events.size})", fontWeight = FontWeight.Bold, color = Color.White)
                if (day.events.isEmpty()) {
                    Text("Inga aktiviteter.", color = Muted, fontSize = 12.sp)
                } else {
                    day.events.forEach { event ->
                        Surface(
                            color = Color.White.copy(alpha = .04f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    event.time.takeIf { it.isNotBlank() } ?: "Hela dagen",
                                    modifier = Modifier.width(72.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(event.title, fontSize = 12.sp, color = Color.White)
                                    Text(memberName(event.memberId, members), fontSize = 10.sp, color = Muted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}

@Composable
internal fun FamilyAutopilotCard('''
assert marker in s, 'insert marker not found'
s = s.replace(marker, insert, 1)

p.write_text(s, encoding='utf-8')
print('Applied clickable week activity details')
