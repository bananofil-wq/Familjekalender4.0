package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

private const val RUN_PREFIX = "🏃 Löpning"

private data class RunningEntry(
    val event: SyncEvent,
    val distanceKm: Double,
    val durationMinutes: Int,
    val completed: Boolean
) {
    val paceMinutesPerKm: Double
        get() = if (distanceKm > 0.0) durationMinutes / distanceKm else 0.0
}

private fun parseRunningEntry(event: SyncEvent): RunningEntry? {
    if (!event.title.startsWith(RUN_PREFIX)) return null
    val completed = event.title.contains("[klar]", ignoreCase = true)
    val distanceMatch = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*km", RegexOption.IGNORE_CASE).find(event.title)
    val durationMatch = Regex("([0-9]+)\\s*min", RegexOption.IGNORE_CASE).find(event.title)
    val distance = distanceMatch?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
    val duration = durationMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
    return RunningEntry(event, distance, duration, completed)
}

private fun formatDistance(value: Double): String =
    String.format(Locale.US, if (value % 1.0 == 0.0) "%.0f" else "%.1f", value).replace('.', ',')

private fun formatPace(value: Double): String {
    if (!value.isFinite() || value <= 0.0) return "–"
    val minutes = value.toInt()
    val seconds = ((value - minutes) * 60.0).toInt().coerceIn(0, 59)
    return "%d:%02d min/km".format(minutes, seconds)
}

@Composable
fun RunningScheduleDialog(
    session: FamilySession,
    members: List<SyncMember>,
    events: List<SyncEvent>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val runningEntries = remember(events) {
        events.mapNotNull(::parseRunningEntry).sortedBy { it.event.date }
    }
    val completedEntries = remember(runningEntries) { runningEntries.filter { it.completed } }

    var completedMode by remember { mutableStateOf(false) }
    var dateText by remember { mutableStateOf(selectedDate.toString()) }
    var timeText by remember { mutableStateOf("18:00") }
    var distanceText by remember { mutableStateOf("5,0") }
    var durationText by remember { mutableStateOf("30") }
    var repeatWeeks by remember { mutableIntStateOf(1) }
    var memberId by remember { mutableStateOf(members.firstOrNull()?.id) }
    var memberMenuOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val selectedMember = members.firstOrNull { it.id == memberId }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Löpning", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Planera pass och följ utvecklingen", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss) { Text("Stäng") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { completedMode = false },
                        modifier = Modifier.weight(1f),
                        colors = if (!completedMode) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text("Planera") }
                    Button(
                        onClick = { completedMode = true; repeatWeeks = 1 },
                        modifier = Modifier.weight(1f),
                        colors = if (completedMode) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text("Genomfört") }
                }

                if (members.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { memberMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedMember?.let { "Person: ${it.name}" } ?: "Välj person")
                        }
                        DropdownMenu(expanded = memberMenuOpen, onDismissRequest = { memberMenuOpen = false }) {
                            members.forEach { member ->
                                DropdownMenuItem(
                                    text = { Text(member.name) },
                                    onClick = {
                                        memberId = member.id
                                        memberMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Datum") },
                        placeholder = { Text("ÅÅÅÅ-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Tid") },
                        placeholder = { Text("18:00") },
                        singleLine = true,
                        modifier = Modifier.weight(.8f)
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it },
                        label = { Text("Distans km") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text(if (completedMode) "Tid min" else "Måltid min") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!completedMode) {
                    Text("Upprepa varje vecka", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 4, 8, 12).forEach { weeks ->
                            FilterChip(
                                selected = repeatWeeks == weeks,
                                onClick = { repeatWeeks = weeks },
                                label = { Text(if (weeks == 1) "1 gång" else "$weeks v") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                        val distance = distanceText.trim().replace(',', '.').toDoubleOrNull()
                        val duration = durationText.trim().toIntOrNull()
                        when {
                            memberId.isNullOrBlank() -> status = "Välj vem passet gäller"
                            date == null -> status = "Ange datum som ÅÅÅÅ-MM-DD"
                            !Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(timeText.trim()) -> status = "Ange tiden som TT:MM"
                            distance == null || distance <= 0.0 -> status = "Ange en giltig distans"
                            duration == null || duration <= 0 -> status = "Ange en giltig tid i minuter"
                            else -> scope.launch {
                                busy = true
                                status = ""
                                val title = buildString {
                                    append(RUN_PREFIX)
                                    append(" · ${formatDistance(distance)} km · $duration min")
                                    if (completedMode) append(" [klar]")
                                }
                                runCatching {
                                    val count = if (completedMode) 1 else repeatWeeks
                                    repeat(count) { index ->
                                        SupabaseSync.addEvent(
                                            session = session,
                                            title = title,
                                            date = date.plusWeeks(index.toLong()),
                                            time = timeText.trim(),
                                            memberId = memberId
                                        )
                                    }
                                }.onSuccess {
                                    busy = false
                                    onChanged()
                                }.onFailure {
                                    status = it.message ?: "Kunde inte spara löppasset"
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (completedMode) "Spara genomfört pass" else "Lägg in i schemat")
                }

                HorizontalDivider()
                Text("Progression", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                if (completedEntries.isEmpty()) {
                    Text(
                        "När genomförda pass registreras visas tempo-utvecklingen här.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                } else {
                    RunningProgressChart(completedEntries.takeLast(12))
                    val latest = completedEntries.last()
                    val best = completedEntries.minByOrNull { it.paceMinutesPerKm }
                    Text(
                        "Senast: ${formatDistance(latest.distanceKm)} km · ${latest.durationMinutes} min · ${formatPace(latest.paceMinutesPerKm)}",
                        fontSize = 12.sp
                    )
                    if (best != null && completedEntries.size > 1) {
                        Text("Bästa tempo: ${formatPace(best.paceMinutesPerKm)}", color = Muted, fontSize = 12.sp)
                    }
                }

                val upcoming = runningEntries.filter { !it.completed && !it.event.date.isBefore(LocalDate.now()) }.take(5)
                if (upcoming.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Kommande pass", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    upcoming.forEach { entry ->
                        Text(
                            "${entry.event.date} ${entry.event.time} · ${formatDistance(entry.distanceKm)} km · mål ${entry.durationMinutes} min",
                            fontSize = 12.sp
                        )
                    }
                }

                if (status.isNotBlank()) {
                    Text(status, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RunningProgressChart(entries: List<RunningEntry>) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = Color.White.copy(alpha = .12f)
    val values = entries.map { it.paceMinutesPerKm }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val range = (max - min).takeIf { it > 0.05 } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val left = 10.dp.toPx()
            val right = size.width - 10.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()

            repeat(4) { index ->
                val y = top + (bottom - top) * index / 3f
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }

            if (entries.size == 1) {
                drawCircle(accent, radius = 5.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
            } else {
                val points = entries.mapIndexed { index, entry ->
                    val x = left + (right - left) * index / (entries.size - 1).toFloat()
                    val normalized = ((entry.paceMinutesPerKm - min) / range).toFloat()
                    val y = bottom - (bottom - top) * normalized
                    Offset(x, y)
                }
                points.zipWithNext().forEach { (a, b) ->
                    drawLine(accent, a, b, strokeWidth = 3.dp.toPx())
                }
                points.forEach { point -> drawCircle(accent, radius = 4.dp.toPx(), center = point) }
            }
        }
        Text(
            "Tempo för de senaste ${entries.size} genomförda passen · lägre min/km är bättre",
            color = Muted,
            fontSize = 11.sp
        )
    }
}
