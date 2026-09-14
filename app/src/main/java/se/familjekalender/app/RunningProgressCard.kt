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
    val paceSecondsPerKm: Int
)

private data class PlannedRun(
    val event: SyncEvent,
    val distanceKm: Double,
    val runType: String
)

private fun parseRun(event: SyncEvent): RunEntry? {
    if (!event.title.startsWith("🏃 RUN|")) return null
    val parts = event.title.removePrefix("🏃 RUN|").split('|')
    if (parts.size < 2) return null
    val distance = parts[0].toDoubleOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (distance <= 0.0 || minutes <= 0) return null
    val pace = ((minutes * 60.0) / distance).toInt()
    return RunEntry(event, distance, minutes, pace)
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
    var expanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
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
    val latest = runs.lastOrNull()
    val nextPlan = plannedRuns.firstOrNull()

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

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat("Senast", latest?.let { "${"%.1f".format(Locale.US, it.distanceKm)} km" } ?: "–", Modifier.weight(1f))
                    RunStat("Bästa tempo", bestPace?.let(::paceText) ?: "–", Modifier.weight(1f))
                    RunStat("Totalt", "${"%.1f".format(Locale.US, totalKm)} km", Modifier.weight(1f))
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
                            Text("${"%.1f".format(Locale.US, plan.distanceKm)} km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                        title = "🏃 RUN|${"%.2f".format(Locale.US, distance)}|$minutes",
                        date = date,
                        startTime = "18:00",
                        endTime = null,
                        memberId = selectedMemberId
                    )
                    onChanged()
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
                    showPlan = false
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
