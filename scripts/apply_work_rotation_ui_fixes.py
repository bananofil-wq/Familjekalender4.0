from pathlib import Path
import re

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
    cal = cal.replace(
        '    var showWorkMonth by remember { mutableStateOf(false) }\n',
        '    var showWorkMonth by remember { mutableStateOf(false) }\n    var showWorkRotation by remember { mutableStateOf(false) }\n'
    )

# Keep the existing entry point, but make its wording match the approved screen.
cal = cal.replace('Text("Roterande arbetsvecka · 4 veckor")', 'Text("Lägg till arbetsvecka")')

anchor = '''                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkMonth = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Arbetsmånad") }
'''
if 'showWorkRotation = true' not in cal:
    cal = cal.replace(anchor, anchor + '''                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkRotation = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lägg till arbetsvecka") }
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
    'isBirthdayEvent(event) -> Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { Text("🌈", fontSize = 15.sp, lineHeight = 18.sp, maxLines = 1, softWrap = false) }'
)

compact_dialog = r'''@Composable
private fun WorkRotationDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = currentFamilySession(context)
    var rotating by remember { mutableStateOf(true) }
    var rotationWeeks by remember { mutableStateOf(4) }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var startDate by remember {
        mutableStateOf(selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong()).plusWeeks(1))
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingWeek by remember { mutableStateOf<Int?>(null) }
    var weeks by remember {
        mutableStateOf(
            listOf(
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "06:00", "14:00"),
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "14:00", "22:00"),
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "08:00", "16:00"),
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
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (!saving) onDismiss() }, contentPadding = PaddingValues(0.dp)) {
                    Text("‹", fontSize = 28.sp)
                }
                Text(
                    "Lägg till arbetsvecka",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))
                ) {
                    TextButton(
                        onClick = { rotating = false; rotationWeeks = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .background(if (!rotating) MaterialTheme.colorScheme.primary else Color.Transparent)
                    ) {
                        Text("Fast schema", color = if (!rotating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(
                        onClick = { rotating = true; rotationWeeks = 4 },
                        modifier = Modifier
                            .weight(1f)
                            .background(if (rotating) MaterialTheme.colorScheme.primary else Color.Transparent)
                    ) {
                        Text("Roterande schema", color = if (rotating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (members.isNotEmpty()) {
                    Text("Person", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        members.take(4).forEach { member ->
                            FilterChip(
                                selected = selectedMemberId == member.id,
                                onClick = { selectedMemberId = member.id },
                                label = { Text(member.name, maxLines = 1) }
                            )
                        }
                    }
                }

                if (rotating) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Antal veckor i rotation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                            OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                                Text("4 veckor")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Startdatum för rotation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
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
                            ) {
                                Text("${startDate.dayOfMonth}/${startDate.monthValue} ${startDate.year}", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val visibleWeeks = if (rotating) 4 else 1
                    repeat(visibleWeeks) { index ->
                        val week = weeks[index]
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Vecka ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    if (week.weekdays.isEmpty()) "Ledig" else "${week.startTime} – ${week.endTime}",
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                                Button(
                                    onClick = { editingWeek = index },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
                                ) {
                                    Text("Redigera", fontSize = 9.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (rotating) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "↻   Schemat upprepas automatiskt: Vecka 1 → 2 → 3 → 4 → 1 …",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && session != null && selectedMemberId.isNotBlank() && weeks.take(rotationWeeks).any { it.weekdays.isNotEmpty() },
                onClick = {
                    val activeSession = session ?: return@Button
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            repeat(52) { weekIndex ->
                                val template = weeks[weekIndex % rotationWeeks]
                                val monday = startDate.plusWeeks(weekIndex.toLong())
                                template.weekdays.sorted().forEach { day ->
                                    val date = monday.plusDays((day - 1).toLong())
                                    val eventTitle = "Jobb · ${template.startTime}–${template.endTime}"
                                    SupabaseSync.addEvent(activeSession, eventTitle, date, template.startTime, selectedMemberId)
                                }
                            }
                        }.onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Kunde inte spara arbetsveckan" }
                        saving = false
                    }
                }
            ) {
                Text(if (saving) "Sparar…" else "Spara")
            }
        },
        dismissButton = {}
    )

    editingWeek?.let { index ->
        val week = weeks[index]
        AlertDialog(
            onDismissRequest = { editingWeek = null },
            title = { Text("Redigera vecka ${index + 1}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Arbetsdagar", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pickTime(index, true) }, modifier = Modifier.weight(1f)) {
                                Text("Från ${week.startTime}")
                            }
                            OutlinedButton(onClick = { pickTime(index, false) }, modifier = Modifier.weight(1f)) {
                                Text("Till ${week.endTime}")
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { editingWeek = null }) { Text("Klar") } },
            dismissButton = {}
        )
    }
}

'''

pattern = r'@Composable\nprivate fun WorkRotationDialog\([\s\S]*?\n@Composable\nprivate fun WorkMonthDialog\('
replacement = compact_dialog + '@Composable\nprivate fun WorkMonthDialog('
cal, count = re.subn(pattern, replacement, cal, count=1)
if count != 1:
    raise RuntimeError(f'Expected to replace exactly one WorkRotationDialog, replaced {count}')

cal_path.write_text(cal)

main_check = main_path.read_text()
cal_check = cal_path.read_text()
assert 'modifier = Modifier.size(56.dp)' in main_check
assert 'softWrap = false, fontSize = 9.sp' in main_check
assert 'Text("Lägg till arbetsvecka")' in cal_check
assert 'Text("Fast schema"' in cal_check
assert 'Text("Roterande schema"' in cal_check
assert 'Text("Redigera"' in cal_check
assert 'Schemat upprepas automatiskt' in cal_check
assert 'title = { Text("Roterande arbetsvecka") }' not in cal_check
assert 'Modifier.width(20.dp)' in cal_check
