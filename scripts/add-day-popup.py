from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
s = p.read_text(encoding='utf-8')

old = '''    var showManageMonth by remember { mutableStateOf(false) }\n    val context = LocalContext.current\n'''
new = '''    var showManageMonth by remember { mutableStateOf(false) }\n    var dayPopupDate by remember { mutableStateOf<LocalDate?>(null) }\n    val context = LocalContext.current\n'''
if old not in s:
    raise SystemExit('state anchor not found')
s = s.replace(old, new, 1)

old = '''                    onSelect = onSelect,\n                    events = events,\n'''
new = '''                    onSelect = { picked ->\n                        onSelect(picked)\n                        dayPopupDate = picked\n                    },\n                    events = events,\n'''
if old not in s:
    raise SystemExit('onSelect anchor not found')
s = s.replace(old, new, 1)

anchor = '''    if (showManageMonth) {\n        ManageMonthEventsDialog(\n'''
if anchor not in s:
    raise SystemExit('dialog anchor not found')
popup = '''    dayPopupDate?.let { popupDate ->\n        DayOverviewPopup(\n            date = popupDate,\n            events = events.filter { it.date == popupDate }.sortedBy { it.time },\n            members = members,\n            onDismiss = { dayPopupDate = null },\n            onAdd = {\n                dayPopupDate = null\n                onSelect(popupDate)\n                onAdd()\n            }\n        )\n    }\n\n'''
s = s.replace(anchor, popup + anchor, 1)

insert_before = '''@Composable\nprivate fun SeasonalPhoto'''
if insert_before not in s:
    raise SystemExit('seasonal anchor not found')
composable = r'''@Composable
private fun DayOverviewPopup(
    date: LocalDate,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF171A20),
        title = {
            Column {
                Text("$dayName ${date.dayOfMonth} $monthName", fontWeight = FontWeight.Bold)
                Text(
                    "${events.size} ${if (events.size == 1) "aktivitet" else "aktiviteter"}",
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (events.isEmpty()) {
                    Text("Inget inlagt den här dagen.", color = Color.White.copy(alpha = .62f))
                } else {
                    events.forEach { event ->
                        val member = members.find { it.id == event.memberId }
                        val allFamily = event.memberId == ALL_FAMILY_MEMBER_ID
                        val birthday = isBirthdayEvent(event)
                        val dotColor = if (allFamily) Color(0xFFFFD75E) else member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)

                        Surface(
                            color = Color(0xFF20242B),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when {
                                    birthday -> BirthdayRainbowIcon(Modifier.size(width = 26.dp, height = 20.dp))
                                    allFamily -> Text("★", color = Color(0xFFFFD75E), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    else -> Box(Modifier.size(13.dp).clip(CircleShape).background(dotColor))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        event.title.removePrefix("🌈").trim(),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (event.time.isNotBlank()) {
                                        Text(event.time, color = Color.White.copy(alpha = .62f), fontSize = 12.sp)
                                    }
                                }
                                Text(
                                    if (allFamily) "Hela familjen" else member?.name ?: "Familjen",
                                    color = Color.White.copy(alpha = .64f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Lägg till")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}

'''
s = s.replace(insert_before, composable + insert_before, 1)

p.write_text(s, encoding='utf-8')
# retrigger workflow
