package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private data class RunEntry(
    val event: SyncEvent,
    val distanceKm: Double,
    val durationMinutes: Int,
    val paceSecondsPerKm: Int,
    val plannedDistanceKm: Double? = null
)

private data class PlannedRun(
    val event: SyncEvent,
    val distanceKm: Double,
    val runType: String
)

private fun parseRun(event: SyncEvent): RunEntry? {
    val distance: Double
    val minutes: Int
    when {
        event.title.startsWith("🏃 RUN|") -> {
            val parts = event.title.removePrefix("🏃 RUN|").split('|')
            if (parts.size < 2) return null
            distance = parts[0].toDoubleOrNull() ?: return null
            minutes = parts[1].toIntOrNull() ?: return null
        }
        event.title.startsWith("🏃 Löpning · ") -> {
            val body = event.title.removePrefix("🏃 Löpning · ")
            val parts = body.split(" · ")
            if (parts.size < 2) return null
            distance = parts[0].removeSuffix(" km").replace(',', '.').toDoubleOrNull() ?: return null
            minutes = parts[1].removeSuffix(" min").trim().toIntOrNull() ?: return null
        }
        else -> return null
    }
    if (distance <= 0.0 || minutes <= 0) return null
    val pace = ((minutes * 60.0) / distance).toInt()
    val plannedDistance = if (event.title.startsWith("🏃 Löpning · ")) {
        Regex("Plan ([0-9]+(?:[.,][0-9]+)?) km").find(event.title)
            ?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
    } else null
    return RunEntry(event, distance, minutes, pace, plannedDistance)
}

private fun parsePlannedRun(event: SyncEvent): PlannedRun? {
    if (!event.title.startsWith("🏃 Plan: ")) return null
    val body = event.title.removePrefix("🏃 Plan: ")
    val parts = body.split(" · ")
    if (parts.size < 2) return null
    val runType = parts[0].trim().ifBlank { "Löpning" }
    val distance = parts[1].removeSuffix(" km").replace(',', '.').toDoubleOrNull() ?: return null
    if (distance <= 0.0) return null
    return PlannedRun(event, distance, runType)
}

private fun paceText(secondsPerKm: Int): String =
    "%d:%02d min/km".format(secondsPerKm / 60, secondsPerKm % 60)

@Composable
fun RunningProgressCard(
    session: FamilySession,
    members: List<SyncMember>,
    events: List<SyncEvent>,
    onChanged: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var completingPlan by remember { mutableStateOf<PlannedRun?>(null) }
    var selectedMemberId by remember(members) { mutableStateOf(members.firstOrNull()?.id) }

    val runs = remember(events, selectedMemberId) {
        events.asSequence()
            .filter { it.memberId == selectedMemberId }
            .mapNotNull(::parseRun)
            .sortedBy { it.event.date }
            .toList()
    }
    val plannedRuns = remember(events, selectedMemberId) {
        val today = LocalDate.now()
        events.asSequence()
            .filter { it.memberId == selectedMemberId && !it.date.isBefore(today) }
            .mapNotNull(::parsePlannedRun)
            .sortedWith(compareBy<PlannedRun> { it.event.date }.thenBy { it.event.time })
            .toList()
    }
    val totalKm = runs.sumOf { it.distanceKm }
    val bestPace = runs.minOfOrNull { it.paceSecondsPerKm }
    val longestDistance = runs.maxOfOrNull { it.distanceKm }
    val latest = runs.lastOrNull()
    val nextPlan = plannedRuns.firstOrNull()
    val today = LocalDate.now()
    val recent7Runs = runs.filter { !it.event.date.isBefore(today.minusDays(6)) && !it.event.date.isAfter(today) }
    val recent7Km = recent7Runs.sumOf { it.distanceKm }
    val monthRuns = runs.filter { it.event.date.year == today.year && it.event.date.month == today.month }
    val monthKm = monthRuns.sumOf { it.distanceKm }
    val goalPrefs = remember { context.getSharedPreferences("running_month_goals", 0) }
    val goalKey = "${selectedMemberId ?: "none"}_${today.year}_${today.monthValue}"
    var monthlyGoalText by remember(goalKey) {
        mutableStateOf(goalPrefs.getFloat(goalKey, 0f).takeIf { it > 0f }?.let { "%.0f".format(Locale.US, it) } ?: "")
    }
    val monthlyGoalKm = monthlyGoalText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    val monthlyGoalProgress = monthlyGoalKm?.let { (monthKm / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val activeWeeks = runs
        .map { java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear().let(it.event.date::get) to it.event.date.year }
        .distinct()
        .size
    val previousForTrend = runs.dropLast(1).takeLast(3)
    val paceTrendSeconds = if (latest != null && previousForTrend.isNotEmpty()) {
        previousForTrend.map { it.paceSecondsPerKm }.average().toInt() - latest.paceSecondsPerKm
    } else null
    val paceTrendText = paceTrendSeconds?.let { delta ->
        when {
            delta >= 5 -> "↑ ${delta} s/km snabbare"
            delta <= -5 -> "↓ ${-delta} s/km långsammare"
            else -> "→ Stabilt tempo"
        }
    } ?: "Behöver fler pass"
    val latestPbText = when {
        latest == null -> "Inga pass ännu"
        bestPace != null && latest.paceSecondsPerKm == bestPace && longestDistance != null && latest.distanceKm == longestDistance -> "🏆 Tempo + distans"
        bestPace != null && latest.paceSecondsPerKm == bestPace -> "🏆 Bästa tempo"
        longestDistance != null && latest.distanceKm == longestDistance -> "🏆 Längsta pass"
        else -> "Fortsätt bygga formen"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("🏃 Löpning & progression", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        when {
                            nextPlan != null -> "Nästa: ${nextPlan.event.date} ${nextPlan.event.time} · ${nextPlan.runType}"
                            runs.isEmpty() -> "Planera och följ utvecklingen"
                            else -> "${runs.size} pass · ${"%.1f".format(Locale.US, totalKm)} km totalt"
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
            }

            if (expanded) {
                Spacer(Modifier.height(14.dp))
                if (members.isNotEmpty()) {
                    Text("Person", color = Muted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.take(4).forEach { member ->
                            FilterChip(
                                selected = selectedMemberId == member.id,
                                onClick = { selectedMemberId = member.id },
                                label = { Text(member.name, maxLines = 1) },
                                leadingIcon = {
                                    Box(
                                        Modifier.size(9.dp)
                                            .background(Color(member.colorArgb.toInt()), CircleShape)
                                    )
                                }
                            )
                        }
                    }
                }

                selectedMemberId?.let { memberId ->
                    Spacer(Modifier.height(12.dp))
                    RunRecorderPanel(
                        session = session,
                        memberId = memberId,
                        onChanged = onChanged
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat("Senast", latest?.let { "${"%.1f".format(Locale.US, it.distanceKm)} km" } ?: "–", Modifier.weight(1f))
                    RunStat("Bästa tempo", bestPace?.let(::paceText) ?: "–", Modifier.weight(1f))
                    RunStat("Totalt", "${"%.1f".format(Locale.US, totalKm)} km", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat(
                        "Senaste 7 dagar",
                        "${recent7Runs.size} pass · ${"%.1f".format(Locale.US, recent7Km)} km",
                        Modifier.weight(1f)
                    )
                    RunStat(
                        "Utveckling",
                        paceTrendText,
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat(
                        "Den här månaden",
                        "${monthRuns.size} pass · ${"%.1f".format(Locale.US, monthKm)} km",
                        Modifier.weight(1f)
                    )
                    RunStat(
                        "Aktiva veckor",
                        if (activeWeeks > 0) "$activeWeeks totalt" else "–",
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Månadsmål", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyGoalText,
                        onValueChange = { value -> monthlyGoalText = value.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                        label = { Text("Mål km") },
                        singleLine = true,
                        modifier = Modifier.weight(0.42f)
                    )
                    Button(
                        onClick = {
                            val goal = monthlyGoalText.replace(',', '.').toFloatOrNull()
                            if (goal != null && goal > 0f) goalPrefs.edit().putFloat(goalKey, goal).apply()
                            else goalPrefs.edit().remove(goalKey).apply()
                        },
                        modifier = Modifier.weight(0.58f)
                    ) { Text("Spara mål") }
                }
                if (monthlyGoalKm != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { monthlyGoalProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${"%.1f".format(Locale.US, monthKm)} av ${"%.1f".format(Locale.US, monthlyGoalKm)} km · ${(monthlyGoalProgress * 100).toInt()}%",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat(
                        "Längsta pass",
                        longestDistance?.let { "${"%.1f".format(Locale.US, it)} km" } ?: "–",
                        Modifier.weight(1f)
                    )
                    RunStat(
                        "Personbästa",
                        latestPbText,
                        Modifier.weight(1f)
                    )
                }

                if (plannedRuns.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Kommande schema", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    plannedRuns.take(4).forEach { plan ->
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
                }

                if (runs.size >= 2) {
                    Spacer(Modifier.height(14.dp))
                    Text("Tempo senaste passen", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    PaceGraph(runs.takeLast(8), Modifier.fillMaxWidth().height(110.dp))
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showPlan = true },
                        enabled = selectedMemberId != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Planera pass") }
                    Button(
                        onClick = { showAdd = true },
                        enabled = selectedMemberId != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Registrera") }
                }

                if (runs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Senaste passen", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    runs.takeLast(4).reversed().forEach { run ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(run.event.date.toString(), fontSize = 13.sp)
                                Text("${"%.1f".format(Locale.US, run.distanceKm)} km · ${run.durationMinutes} min", color = Muted, fontSize = 12.sp)
                                run.plannedDistanceKm?.let { planned ->
                                    val diff = run.distanceKm - planned
                                    val diffText = when {
                                        diff > 0.049 -> "+${"%.1f".format(Locale.US, diff)} km"
                                        diff < -0.049 -> "${"%.1f".format(Locale.US, diff)} km"
                                        else -> "enligt plan"
                                    }
                                    Text(
                                        "Plan ${"%.1f".format(Locale.US, planned)} km · $diffText",
                                        color = Muted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Text(paceText(run.paceSecondsPerKm), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAdd && selectedMemberId != null) {
        AddRunDialog(
            onDismiss = { showAdd = false },
            onSave = { date, distance, minutes ->
                scope.launch {
                    SupabaseSync.addEvent(
                        session = session,
                        title = "🏃 Löpning · ${"%.2f".format(Locale.US, distance)} km · $minutes min",
                        date = date,
                        startTime = "18:00",
                        endTime = null,
                        memberId = selectedMemberId
                    )
                    onChanged()
                    FamilyCalendarWidget.enqueueRefresh(context)
                    showAdd = false
                }
            }
        )
    }

    if (showPlan && selectedMemberId != null) {
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

    completingPlan?.let { plan ->
        CompletePlannedRunDialog(
            plan = plan,
            onDismiss = { completingPlan = null },
            onSave = { distance, minutes ->
                scope.launch {
                    SupabaseSync.updateEvent(
                        session = session,
                        eventId = plan.event.id,
                        title = "🏃 Löpning · ${"%.2f".format(Locale.US, distance)} km · $minutes min · Plan ${"%.1f".format(Locale.US, plan.distanceKm)} km",
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
}

@Composable
private fun RunStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = SoftPurple, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Muted, fontSize = 10.sp)
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
        }
    }
}

@Composable
private fun PaceGraph(runs: List<RunEntry>, modifier: Modifier = Modifier) {
    val values = runs.map { it.paceSecondsPerKm.toFloat() }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 1f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value - min) / span
            val y = size.height * (0.15f + normalized * 0.7f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFFB47CFF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value - min) / span
            val y = size.height * (0.15f + normalized * 0.7f)
            drawCircle(Color(0xFFB47CFF), radius = 6f, center = Offset(x, y))
        }
    }
}

@Composable
private fun AddRunDialog(
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, Int) -> Unit
) {
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var distanceText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    val distance = distanceText.replace(',', '.').toDoubleOrNull()
    val minutes = minutesText.toIntOrNull()
    val valid = date != null && distance != null && distance > 0 && minutes != null && minutes > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrera löppass") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Datum (ÅÅÅÅ-MM-DD)") }, singleLine = true)
                OutlinedTextField(distanceText, { distanceText = it }, label = { Text("Distans, km") }, singleLine = true)
                OutlinedTextField(minutesText, { minutesText = it.filter { ch -> ch.isDigit() } }, label = { Text("Tid, minuter") }, singleLine = true)
                if (valid) {
                    val pace = (((minutes ?: 0) * 60.0) / (distance ?: 1.0)).toInt()
                    Text("Tempo: ${paceText(pace)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(date!!, distance!!, minutes!!) }, enabled = valid) { Text("Spara pass") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

@Composable
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
                    val actualDistance = distance ?: 0.0
                    val pace = (((minutes ?: 0) * 60.0) / (actualDistance.takeIf { it > 0.0 } ?: 1.0)).toInt()
                    val diff = actualDistance - plan.distanceKm
                    val comparison = when {
                        diff > 0.049 -> "+${"%.1f".format(Locale.US, diff)} km över plan"
                        diff < -0.049 -> "${"%.1f".format(Locale.US, -diff)} km under plan"
                        else -> "Distansen enligt plan"
                    }
                    Text("Tempo: ${paceText(pace)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Plan ${"%.1f".format(Locale.US, plan.distanceKm)} km → faktiskt ${"%.1f".format(Locale.US, actualDistance)} km · $comparison",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
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

@Composable
private fun PlanRunDialog(
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, Double, String) -> Unit
) {
    var dateText by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var timeText by remember { mutableStateOf("18:00") }
    var distanceText by remember { mutableStateOf("") }
    var runType by remember { mutableStateOf("Lugnt pass") }
    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    val time = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()
    val distance = distanceText.replace(',', '.').toDoubleOrNull()
    val valid = date != null && time != null && distance != null && distance > 0 && runType.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Planera löppass") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Datum (ÅÅÅÅ-MM-DD)") }, singleLine = true)
                OutlinedTextField(timeText, { timeText = it }, label = { Text("Tid (HH:MM)") }, singleLine = true)
                OutlinedTextField(distanceText, { distanceText = it }, label = { Text("Planerad distans, km") }, singleLine = true)
                OutlinedTextField(runType, { runType = it }, label = { Text("Passtyp") }, placeholder = { Text("Lugnt, intervaller, långpass…") }, singleLine = true)
                Text("Passet hamnar även i familjekalendern.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(date!!, timeText.trim(), distance!!, runType.trim()) }, enabled = valid) { Text("Lägg i schemat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}
