from pathlib import Path

main_path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
main = main_path.read_text()
main = main.replace(
'''    Row {
        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, modifier = Modifier.weight(1f))
        FilledIconButton(onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }) { Icon(Icons.Default.Add, null) }
    }''',
'''    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, modifier = Modifier.weight(1f))
        FilledIconButton(
            onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } },
            modifier = Modifier.size(56.dp)
        ) { Icon(Icons.Default.Add, null) }
    }''')
main = main.replace('                label = { Text(label) },', '                label = { Text(label, maxLines = 1, softWrap = false, fontSize = 9.sp) },')
main_path.write_text(main)

cal_path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
cal = cal_path.read_text()
if 'private data class WorkRotationWeekDraft' not in cal:
    cal = cal.replace(
'''private data class WorkRuleDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String
)
''',
'''private data class WorkRuleDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String
)

private data class WorkRotationWeekDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String
)
''')

if 'var showWorkRotation by remember' not in cal:
    cal = cal.replace('    var showWorkMonth by remember { mutableStateOf(false) }\n', '    var showWorkMonth by remember { mutableStateOf(false) }\n    var showWorkRotation by remember { mutableStateOf(false) }\n')

anchor = '''                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkMonth = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Arbetsmånad") }
'''
if 'Roterande arbetsvecka · 4 veckor' not in cal:
    cal = cal.replace(anchor, anchor + '''                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkRotation = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Roterande arbetsvecka · 4 veckor") }
''')

if 'if (showWorkRotation)' not in cal:
    cal = cal.replace('    if (showManageMonth) {', '''    if (showWorkRotation) {
        WorkRotationDialog(
            members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
            selectedDate = selectedDate,
            onDismiss = { showWorkRotation = false },
            onChanged = {
                showWorkRotation = false
                refreshActivity()
            }
        )
    }

    if (showManageMonth) {''', 1)

cal = cal.replace(
'isBirthdayEvent(event) -> Text("🌈", fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)',
'isBirthdayEvent(event) -> Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { Text("🌈", fontSize = 15.sp, lineHeight = 18.sp, maxLines = 1, softWrap = false) }')

if 'private fun WorkRotationDialog(' not in cal:
    dialog = r'''
@Composable
private fun WorkRotationDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = currentFamilySession(context)
    var title by remember { mutableStateOf("Jobb") }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var startDate by remember { mutableStateOf(selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var weeks by remember {
        mutableStateOf(
            listOf(
                WorkRotationWeekDraft(setOf(1,2,3,4,5), "06:00", "14:00"),
                WorkRotationWeekDraft(setOf(1,2,3,4,5), "14:00", "22:00"),
                WorkRotationWeekDraft(setOf(1,2,3,4,5), "08:00", "16:00"),
                WorkRotationWeekDraft(emptySet(), "06:00", "14:00")
            )
        )
    }

    fun pickTime(index: Int, start: Boolean) {
        val current = if (start) weeks[index].startTime else weeks[index].endTime
        val parsed = runCatching { LocalTime.parse(current) }.getOrDefault(LocalTime.of(6, 0))
        TimePickerDialog(context, { _, h, m ->
            val value = "%02d:%02d".format(h, m)
            weeks = weeks.toMutableList().also { list ->
                val old = list[index]
                list[index] = if (start) old.copy(startTime = value) else old.copy(endTime = value)
            }
        }, parsed.hour, parsed.minute, true).show()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Roterande arbetsvecka") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("4 veckors rotation · upprepas automatiskt i 52 veckor", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), fontSize = 12.sp)
                OutlinedTextField(title, { title = it }, label = { Text("Rubrik") }, modifier = Modifier.fillMaxWidth())
                Text("Person", fontWeight = FontWeight.SemiBold)
                members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMemberId == member.id, onClick = { selectedMemberId = member.id })
                        Text(member.name)
                    }
                }
                OutlinedButton(
                    onClick = {
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val picked = LocalDate.of(y, m + 1, d)
                                startDate = picked.minusDays((picked.dayOfWeek.value - 1).toLong())
                            },
                            startDate.year,
                            startDate.monthValue - 1,
                            startDate.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Startvecka: ${startDate}") }

                weeks.forEachIndexed { index, week ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Vecka ${index + 1}", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf("M" to 1, "T" to 2, "O" to 3, "T" to 4, "F" to 5, "L" to 6, "S" to 7).forEach { (label, day) ->
                                    FilterChip(
                                        selected = day in week.weekdays,
                                        onClick = {
                                            val days = if (day in week.weekdays) week.weekdays - day else week.weekdays + day
                                            weeks = weeks.toMutableList().also { it[index] = week.copy(weekdays = days) }
                                        },
                                        label = { Text(label, fontSize = 9.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (week.weekdays.isEmpty()) {
                                Text("Ledig vecka", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { pickTime(index, true) }, modifier = Modifier.weight(1f)) { Text("Från ${week.startTime}", fontSize = 11.sp) }
                                    OutlinedButton(onClick = { pickTime(index, false) }, modifier = Modifier.weight(1f)) { Text("Till ${week.endTime}", fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                }
                Text("Rotation: vecka 1 → 2 → 3 → 4 → 1 …", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && session != null && selectedMemberId.isNotBlank() && weeks.any { it.weekdays.isNotEmpty() },
                onClick = {
                    val activeSession = session ?: return@Button
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            repeat(52) { weekIndex ->
                                val template = weeks[weekIndex % 4]
                                val monday = startDate.plusWeeks(weekIndex.toLong())
                                template.weekdays.sorted().forEach { day ->
                                    val date = monday.plusDays((day - 1).toLong())
                                    val eventTitle = "${title.trim().ifBlank { "Jobb" }} · ${template.startTime}–${template.endTime}"
                                    SupabaseSync.addEvent(activeSession, eventTitle, date, template.startTime, selectedMemberId)
                                }
                            }
                        }.onSuccess { onChanged() }.onFailure { error = it.message ?: "Kunde inte spara rotationsschemat" }
                        saving = false
                    }
                }
            ) { Text(if (saving) "Sparar…" else "Spara rotation") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Avbryt") } }
    )
}

'''
    cal = cal.replace('@Composable\nprivate fun WorkMonthDialog(', dialog + '@Composable\nprivate fun WorkMonthDialog(', 1)

cal_path.write_text(cal)

main_check = main_path.read_text()
cal_check = cal_path.read_text()
assert 'modifier = Modifier.size(56.dp)' in main_check
assert 'softWrap = false, fontSize = 9.sp' in main_check
assert 'Roterande arbetsvecka · 4 veckor' in cal_check
assert 'private fun WorkRotationDialog(' in cal_check
assert 'Modifier.width(20.dp)' in cal_check
