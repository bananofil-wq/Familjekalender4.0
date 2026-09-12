package se.familjekalender.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private data class SchoolWeekDraft(
    val weekdays: Set<Int>,
    val dayTimes: Map<Int, Pair<String, String>>
)

@Composable
internal fun SchoolScheduleDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = currentFamilySession(context)
    val scheduleTypes = listOf("Förskola", "Skola", "Fritids", "Dagis")
    var type by remember { mutableStateOf("Förskola") }
    var rotationWeeks by remember { mutableIntStateOf(1) }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var startDate by remember {
        mutableStateOf(selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong()).plusWeeks(1))
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingWeek by remember { mutableStateOf<Int?>(null) }
    var weeks by remember {
        mutableStateOf(
            List(4) {
                SchoolWeekDraft(
                    weekdays = setOf(1, 2, 3, 4, 5),
                    dayTimes = (1..5).associateWith { "07:30" to "16:00" }
                )
            }
        )
    }

    fun pickTime(index: Int, day: Int, start: Boolean) {
        val current = weeks[index].dayTimes[day] ?: ("07:30" to "16:00")
        val value = if (start) current.first else current.second
        val parsed = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(7, 30))
        TimePickerDialog(context, { _, h, m ->
            val picked = "%02d:%02d".format(h, m)
            weeks = weeks.toMutableList().also { list ->
                val old = list[index]
                val oldTimes = old.dayTimes[day] ?: ("07:30" to "16:00")
                val newTimes = if (start) picked to oldTimes.second else oldTimes.first to picked
                list[index] = old.copy(dayTimes = old.dayTimes + (day to newTimes))
            }
        }, parsed.hour, parsed.minute, true).show()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF17131D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Förskola / skola", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Typ", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    scheduleTypes.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option, maxLines = 1) }
                        )
                    }
                }

                Text("Barn", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                members.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedMemberId = member.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMemberId == member.id, onClick = { selectedMemberId = member.id })
                        Text(member.name)
                    }
                }

                Text("Veckorotation", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { count ->
                        FilterChip(
                            selected = rotationWeeks == count,
                            onClick = { rotationWeeks = count },
                            label = { Text(if (count == 1) "Fast" else "$count v") }
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
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
                    Text("Startvecka: ${startDate.dayOfMonth}/${startDate.monthValue} ${startDate.year}")
                }

                repeat(rotationWeeks) { index ->
                    val week = weeks[index]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF21182B),
                        border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(if (rotationWeeks == 1) "Veckoschema" else "Vecka ${index + 1}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (week.weekdays.isEmpty()) "Ledig" else "${week.weekdays.size} dagar",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = .65f)
                                )
                            }
                            Button(onClick = { editingWeek = index }) { Text("Redigera") }
                        }
                    }
                }

                Text(
                    if (rotationWeeks == 1) "Schemat upprepas varje vecka." else "Schemat upprepas automatiskt var $rotationWeeks:e vecka.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = .65f)
                )
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
                                    val times = template.dayTimes[day] ?: ("07:30" to "16:00")
                                    SupabaseSync.addEvent(
                                        activeSession,
                                        "$type · ${times.first}–${times.second}",
                                        date,
                                        times.first,
                                        selectedMemberId
                                    )
                                }
                            }
                        }.onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Kunde inte spara schemat" }
                        saving = false
                    }
                }
            ) { Text(if (saving) "Sparar…" else "Spara") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Avbryt") } }
    )

    editingWeek?.let { index ->
        val week = weeks[index]
        AlertDialog(
            onDismissRequest = { editingWeek = null },
            title = { Text(if (rotationWeeks == 1) "Redigera veckoschema" else "Redigera vecka ${index + 1}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")
                    labels.forEachIndexed { dayIndex, label ->
                        val day = dayIndex + 1
                        val enabled = day in week.weekdays
                        val times = week.dayTimes[day] ?: ("07:30" to "16:00")
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF21182B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = enabled,
                                        onCheckedChange = { checked ->
                                            weeks = weeks.toMutableList().also { list ->
                                                val old = list[index]
                                                val newDays = if (checked) old.weekdays + day else old.weekdays - day
                                                val newTimes = if (checked && day !in old.dayTimes) old.dayTimes + (day to ("07:30" to "16:00")) else old.dayTimes
                                                list[index] = old.copy(weekdays = newDays, dayTimes = newTimes)
                                            }
                                        }
                                    )
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                }
                                if (enabled) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { pickTime(index, day, true) }, modifier = Modifier.weight(1f)) { Text("Från ${times.first}") }
                                        OutlinedButton(onClick = { pickTime(index, day, false) }, modifier = Modifier.weight(1f)) { Text("Till ${times.second}") }
                                    }
                                }
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
