from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text()
start = text.index('@Composable\nprivate fun MemberTwoDayPopup(')
end = text.index('\n@Composable\nprivate fun AssistantStat(', start)
replacement = '''@Composable
private fun MemberTwoDayPopup(
    member: SyncMember,
    events: List<SyncEvent>,
    today: LocalDate,
    tomorrow: LocalDate,
    onDismiss: () -> Unit
) {
    val weekEnd = today.plusDays(6)
    val memberEvents = events.filter {
        (it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID) &&
            !it.date.isBefore(today) && !it.date.isAfter(weekEnd)
    }.sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
    val todayEvents = memberEvents.filter { it.date == today }
    val tomorrowEvents = memberEvents.filter { it.date == tomorrow }
    val weekDays = (0L..6L).map { today.plusDays(it) }
    val busiestDay = weekDays.maxByOrNull { date -> memberEvents.count { it.date == date } }
    val busyCount = busiestDay?.let { date -> memberEvents.count { it.date == date } } ?: 0
    val locale = Locale("sv", "SE")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF171A20),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                    Spacer(Modifier.width(8.dp))
                    Text(member.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text(
                    "${memberEvents.size} aktiviteter kommande 7 dagar",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Idag", color = Muted, fontSize = 10.sp)
                            Text("${todayEvents.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("aktiviteter", color = Muted, fontSize = 10.sp)
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = .05f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Imorgon", color = Muted, fontSize = 10.sp)
                            Text("${tomorrowEvents.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("aktiviteter", color = Muted, fontSize = 10.sp)
                        }
                    }
                }

                if (busiestDay != null && busyCount > 0) {
                    val dayName = busiestDay.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }
                    Text(
                        "Mest belastad: $dayName · $busyCount aktiviteter",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text("Idag", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (todayEvents.isEmpty()) {
                    Text("Inga aktiviteter idag.", color = Muted, fontSize = 12.sp)
                } else {
                    todayEvents.forEach { event ->
                        val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                        DetailRow("•", "$time  ${event.title}", if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else null)
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text("Kommande 7 dagar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                weekDays.forEach { date ->
                    val dayEvents = memberEvents.filter { it.date == date }
                    val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() }
                    Surface(
                        color = if (date == today) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.White.copy(alpha = .035f),
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.width(54.dp)) {
                                Text(dayName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("${date.dayOfMonth}/${date.monthValue}", fontSize = 9.sp, color = Muted)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (dayEvents.isEmpty()) {
                                    Text("Ledig", fontSize = 11.sp, color = Color(0xFF6DD6A7))
                                } else {
                                    dayEvents.take(3).forEach { event ->
                                        val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                                        Text("$time · ${event.title}", fontSize = 11.sp, color = Color.White.copy(alpha = .9f), maxLines = 1)
                                    }
                                    if (dayEvents.size > 3) {
                                        Text("+${dayEvents.size - 3} till", fontSize = 10.sp, color = Muted)
                                    }
                                }
                            }
                            if (dayEvents.size >= 4) {
                                Text("Intensiv", fontSize = 9.sp, color = Color(0xFFFFB86B), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}
'''
path.write_text(text[:start] + replacement + text[end:])
print('World-class member overview applied')
