from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
text = path.read_text()

old = '''    var showAdd by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var selectedMemberId by remember(members) { mutableStateOf(members.firstOrNull()?.id) }
'''
new = '''    var showAdd by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var completingPlan by remember { mutableStateOf<PlannedRun?>(null) }
    var selectedMemberId by remember(members) { mutableStateOf(members.firstOrNull()?.id) }
'''
if old not in text:
    raise SystemExit('state marker not found')
text = text.replace(old, new, 1)

old2 = '''                    plannedRuns.take(4).forEach { plan ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${plan.event.date} · ${plan.event.time}", fontSize = 13.sp)
                                Text(plan.runType, color = Muted, fontSize = 12.sp)
                            }
                            Text("${"%.1f".format(Locale.US, plan.distanceKm)} km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
'''
new2 = '''                    plannedRuns.take(4).forEach { plan ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${plan.event.date} · ${plan.event.time}", fontSize = 13.sp)
                                Text(plan.runType, color = Muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${"%.1f".format(Locale.US, plan.distanceKm)} km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                TextButton(
                                    onClick = { completingPlan = plan },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) { Text("Registrera resultat", fontSize = 11.sp) }
                            }
                        }
                    }
'''
if old2 not in text:
    raise SystemExit('planned row marker not found')
text = text.replace(old2, new2, 1)

marker = '''    if (showPlan && selectedMemberId != null) {
        PlanRunDialog(
'''
if marker not in text:
    raise SystemExit('plan dialog marker not found')

insert_after = '''    if (showPlan && selectedMemberId != null) {
        PlanRunDialog(
            onDismiss = { showPlan = false },
            onSave = { date, time, distance, runType ->
                scope.launch {
                    SupabaseSync.addEvent(
                        session = session,
                        title = "🏃 Plan: ${runType.trim()} · ${"%.1f".format(Locale.US, distance)} km",
                        date = date,
                        startTime = time,
                        endTime = null,
                        memberId = selectedMemberId
                    )
                    onChanged()
                    FamilyCalendarWidget.enqueueRefresh(context)
                    showPlan = false
                }
            }
        )
    }
'''
if insert_after not in text:
    raise SystemExit('full plan dialog block not found')
replacement = insert_after + '''
    completingPlan?.let { plan ->
        CompletePlannedRunDialog(
            plan = plan,
            onDismiss = { completingPlan = null },
            onSave = { distance, minutes ->
                scope.launch {
                    SupabaseSync.updateEvent(
                        session = session,
                        eventId = plan.event.id,
                        title = "🏃 Löpning · ${"%.2f".format(Locale.US, distance)} km · $minutes min",
                        date = plan.event.date,
                        time = plan.event.time,
                        endTime = null,
                        memberId = plan.event.memberId
                    )
                    onChanged()
                    FamilyCalendarWidget.enqueueRefresh(context)
                    completingPlan = null
                }
            }
        )
    }
'''
text = text.replace(insert_after, replacement, 1)

anchor = '''@Composable
private fun PlanRunDialog(
'''
if anchor not in text:
    raise SystemExit('PlanRunDialog anchor not found')
complete_dialog = '''@Composable
private fun CompletePlannedRunDialog(
    plan: PlannedRun,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit
) {
    var distanceText by remember(plan.event.id) { mutableStateOf("%.1f".format(Locale.US, plan.distanceKm)) }
    var minutesText by remember(plan.event.id) { mutableStateOf("") }
    val distance = distanceText.replace(',', '.').toDoubleOrNull()
    val minutes = minutesText.toIntOrNull()
    val valid = distance != null && distance > 0 && minutes != null && minutes > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrera resultat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${plan.event.date} · ${plan.event.time} · ${plan.runType}", color = Muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    label = { Text("Faktisk distans, km") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit) },
                    label = { Text("Tid, minuter") },
                    singleLine = true
                )
                if (valid) {
                    val pace = (((minutes ?: 0) * 60.0) / (distance ?: 1.0)).toInt()
                    Text("Tempo: ${paceText(pace)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Text("Det planerade passet ersätts med resultatet, så kalendern får ingen dubblett.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(distance!!, minutes!!) }, enabled = valid) { Text("Spara resultat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

'''
text = text.replace(anchor, complete_dialog + anchor, 1)

path.write_text(text)
print('Planned run completion applied')
