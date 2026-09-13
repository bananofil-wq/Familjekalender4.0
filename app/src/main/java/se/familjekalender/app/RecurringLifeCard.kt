package se.familjekalender.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun mondayOf(date: LocalDate): LocalDate {
    var value = date
    while (value.dayOfWeek != DayOfWeek.MONDAY) value = value.minusDays(1)
    return value
}

private fun manualEventsInWeek(events: List<SyncEvent>, weekStart: LocalDate): List<SyncEvent> {
    val weekEnd = weekStart.plusDays(6)
    return events.filter { it.source == "manual" && !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
}

private suspend fun copyWeek(
    session: FamilySession,
    sourceEvents: List<SyncEvent>,
    sourceStart: LocalDate,
    targetStart: LocalDate
): Int {
    var copied = 0
    sourceEvents.forEach { event ->
        val offset = ChronoUnit.DAYS.between(sourceStart, event.date)
        val targetDate = targetStart.plusDays(offset)
        SupabaseSync.addEvent(session, event.title, targetDate, event.time, event.endTime, event.memberId)
        copied++
    }
    return copied
}

@Composable
fun RecurringLifeCard(session: FamilySession, events: List<SyncEvent>, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val thisWeek = mondayOf(LocalDate.now())
    val lastWeek = thisWeek.minusWeeks(1)
    val nextWeek = thisWeek.plusWeeks(1)
    val lastWeekEvents = remember(events, thisWeek) { manualEventsInWeek(events, lastWeek) }
    val thisWeekEvents = remember(events, thisWeek) { manualEventsInWeek(events, thisWeek) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Återkommande vardag", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Kopiera en fungerande vecka i stället för att skriva in samma vardag igen.", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = ""
                            runCatching { copyWeek(session, lastWeekEvents, lastWeek, thisWeek) }
                                .onSuccess { status = "$it aktiviteter kopierade från förra veckan"; onChanged() }
                                .onFailure { status = it.message ?: "Kunde inte kopiera veckan" }
                            busy = false
                        }
                    },
                    enabled = !busy && lastWeekEvents.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Förra → denna") }

                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = ""
                            runCatching { copyWeek(session, thisWeekEvents, thisWeek, nextWeek) }
                                .onSuccess { status = "$it aktiviteter kopierade till nästa vecka"; onChanged() }
                                .onFailure { status = it.message ?: "Kunde inte kopiera veckan" }
                            busy = false
                        }
                    },
                    enabled = !busy && thisWeekEvents.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Denna → nästa") }
            }
            if (status.isNotBlank()) {
                Text(
                    status,
                    color = if (status.contains("Kunde")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
