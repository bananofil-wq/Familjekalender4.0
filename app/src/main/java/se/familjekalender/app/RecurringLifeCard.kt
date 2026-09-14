package se.familjekalender.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class CopyWeekResult(val copied: Int, val skipped: Int)

private fun mondayOf(date: LocalDate): LocalDate {
    var value = date
    while (value.dayOfWeek != DayOfWeek.MONDAY) value = value.minusDays(1)
    return value
}

private fun manualEventsInWeek(events: List<SyncEvent>, weekStart: LocalDate): List<SyncEvent> {
    val weekEnd = weekStart.plusDays(6)
    return events.filter { it.source == "manual" && !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
}

private fun sameCopiedEvent(existing: SyncEvent, source: SyncEvent, targetDate: LocalDate): Boolean {
    val existingMember = existing.memberId ?: ALL_FAMILY_MEMBER_ID
    val sourceMember = source.memberId ?: ALL_FAMILY_MEMBER_ID
    return existing.date == targetDate && existing.title == source.title && existing.time == source.time &&
        existing.endTime == source.endTime && existingMember == sourceMember
}

private suspend fun copyWeek(
    session: FamilySession,
    sourceEvents: List<SyncEvent>,
    existingEvents: List<SyncEvent>,
    sourceStart: LocalDate,
    targetStart: LocalDate
): CopyWeekResult {
    var copied = 0
    var skipped = 0
    val legacySeriesIds = mutableMapOf<String, String>()
    sourceEvents.forEach { event ->
        val offset = ChronoUnit.DAYS.between(sourceStart, event.date)
        val targetDate = targetStart.plusDays(offset)
        if (existingEvents.any { sameCopiedEvent(it, event, targetDate) }) skipped++ else {
            val seriesId = event.seriesId ?: run {
                val key = listOf(
                    event.title,
                    event.time,
                    event.endTime ?: "",
                    event.memberId ?: ALL_FAMILY_MEMBER_ID
                ).joinToString("|")
                legacySeriesIds.getOrPut(key) { java.util.UUID.randomUUID().toString() }
            }
            SupabaseSync.addEvent(
                session,
                event.title,
                targetDate,
                event.time,
                event.endTime,
                event.memberId,
                seriesId
            )
            copied++
        }
    }
    return CopyWeekResult(copied, skipped)
}

private fun copyStatus(result: CopyWeekResult, direction: String): String = when {
    result.copied > 0 && result.skipped > 0 -> "${result.copied} kopierade $direction · ${result.skipped} fanns redan"
    result.copied > 0 -> "${result.copied} aktiviteter kopierade $direction"
    result.skipped > 0 -> "Inget dubblerades · ${result.skipped} aktiviteter fanns redan"
    else -> "Inga aktiviteter att kopiera"
}

private fun templateStatus(result: TemplateApplyResult): String {
    val parts = mutableListOf<String>()
    if (result.created > 0) parts += "${result.created} skapade"
    if (result.skipped > 0) parts += "${result.skipped} fanns redan"
    if (result.paused > 0) parts += "${result.paused} hoppades över p.g.a. lov/semester"
    return if (parts.isEmpty()) "Veckomallen innehåller inga aktiviteter" else parts.joinToString(" · ")
}

private fun pauseLabel(pause: SchedulePause): String = if (pause.startsOn == pause.endsOn) {
    pause.startsOn.toString()
} else {
    "${pause.startsOn} – ${pause.endsOn}"
}

@Composable
fun RecurringLifeCard(session: FamilySession, events: List<SyncEvent>, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var expanded by remember { mutableStateOf(false) }
    var showRunningSchedule by remember { mutableStateOf(false) }
    var showSchoolSchedule by remember { mutableStateOf(false) }
    var schoolMembers by remember { mutableStateOf<List<SyncMember>>(emptyList()) }

    LaunchedEffect(session.id) {
        schoolMembers = runCatching { SupabaseSync.loadMembers(session).filter { it.id != ALL_FAMILY_MEMBER_ID } }
            .getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { showRunningSchedule = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("🏃 Löpning")
        }

        Button(
            onClick = { showSchoolSchedule = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Förskola / skola")
        }

        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Återkommande vardag · veckomall · lov/semester")
        }
    }

    if (showRunningSchedule) {
        RunningScheduleDialog(
            session = session,
            members = schoolMembers,
            events = events,
            selectedDate = today,
            onDismiss = { showRunningSchedule = false },
            onChanged = {
                showRunningSchedule = false
                onChanged()
            }
        )
    }

    if (showSchoolSchedule) {
        SchoolScheduleDialog(
            members = schoolMembers,
            selectedDate = today,
            onDismiss = { showSchoolSchedule = false },
            onChanged = {
                showSchoolSchedule = false
                onChanged()
            }
        )
    }

    if (!expanded) return

    val thisWeek = mondayOf(today)
    val lastWeek = thisWeek.minusWeeks(1)
    val nextWeek = thisWeek.plusWeeks(1)
    val lastWeekEvents = remember(events, thisWeek) { manualEventsInWeek(events, lastWeek) }
    val thisWeekEvents = remember(events, thisWeek) { manualEventsInWeek(events, thisWeek) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var templateNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var pauses by remember { mutableStateOf<List<SchedulePause>>(emptyList()) }
    var pauseFrom by remember { mutableStateOf(nextWeek.toString()) }
    var pauseTo by remember { mutableStateOf(nextWeek.plusDays(6).toString()) }

    suspend fun reloadRecurringData() {
        templateNames = runCatching { RecurringScheduleSync.loadTemplates(session).map { it.name }.distinct() }
            .getOrDefault(emptyList())
        pauses = runCatching {
            RecurringScheduleSync.loadPauses(session).filter { !it.endsOn.isBefore(today) }.sortedBy { it.startsOn }
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(session.id, events.size) { reloadRecurringData() }

    Dialog(onDismissRequest = { expanded = false }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Återkommande vardag", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    TextButton(onClick = { expanded = false }) { Text("Stäng") }
                }
                Text(
                    "Kopiera en fungerande vecka, spara en veckomall och pausa återkommande vardag under lov eller semester.",
                    color = Muted,
                    fontSize = 12.sp
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                status = ""
                                runCatching { copyWeek(session, lastWeekEvents, events, lastWeek, thisWeek) }
                                    .onSuccess { status = copyStatus(it, "från förra veckan"); onChanged() }
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
                                runCatching { copyWeek(session, thisWeekEvents, events, thisWeek, nextWeek) }
                                    .onSuccess { status = copyStatus(it, "till nästa vecka"); onChanged() }
                                    .onFailure { status = it.message ?: "Kunde inte kopiera veckan" }
                                busy = false
                            }
                        },
                        enabled = !busy && thisWeekEvents.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Denna → nästa") }
                }

                HorizontalDivider()
                Text("Veckomall", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                status = ""
                                runCatching { RecurringScheduleSync.replaceTemplateFromWeek(session, "Min veckomall", events, thisWeek) }
                                    .onSuccess { count ->
                                        status = "Veckomallen sparad med $count typer av aktiviteter"
                                        reloadRecurringData()
                                    }
                                    .onFailure { status = it.message ?: "Kunde inte spara veckomallen" }
                                busy = false
                            }
                        },
                        enabled = !busy && thisWeekEvents.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Spara denna vecka") }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                status = ""
                                runCatching { RecurringScheduleSync.applyTemplateToWeek(session, "Min veckomall", nextWeek, events) }
                                    .onSuccess { result -> status = templateStatus(result); onChanged() }
                                    .onFailure { status = it.message ?: "Kunde inte använda veckomallen" }
                                busy = false
                            }
                        },
                        enabled = !busy && templateNames.contains("Min veckomall"),
                        modifier = Modifier.weight(1f)
                    ) { Text("Mall → nästa") }
                }

                HorizontalDivider()
                Text("Lov / semester", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "Lägg en paus för återkommande scheman. Vanliga engångshändelser ligger kvar.",
                    color = Muted,
                    fontSize = 12.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        pauseFrom, { pauseFrom = it }, label = { Text("Från") }, placeholder = { Text("ÅÅÅÅ-MM-DD") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        pauseTo, { pauseTo = it }, label = { Text("Till") }, placeholder = { Text("ÅÅÅÅ-MM-DD") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pauseFrom = nextWeek.toString(); pauseTo = nextWeek.plusDays(6).toString() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Nästa vecka") }
                    Button(
                        onClick = {
                            val start = runCatching { LocalDate.parse(pauseFrom.trim()) }.getOrNull()
                            val end = runCatching { LocalDate.parse(pauseTo.trim()) }.getOrNull()
                            when {
                                start == null || end == null -> status = "Ange datum som ÅÅÅÅ-MM-DD"
                                end.isBefore(start) -> status = "Slutdatum kan inte vara före startdatum"
                                else -> scope.launch {
                                    busy = true
                                    status = ""
                                    runCatching { RecurringScheduleSync.addGlobalPause(session, start, end) }
                                        .onSuccess { status = "Schemapaus sparad: $start – $end"; reloadRecurringData() }
                                        .onFailure { status = it.message ?: "Kunde inte spara schemapausen" }
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Pausa scheman") }
                }

                if (pauses.isNotEmpty()) {
                    Text("Aktiva och kommande pauser", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    pauses.forEach { pause ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(pauseLabel(pause), fontSize = 13.sp)
                                Text(
                                    if (!pause.startsOn.isAfter(today) && !pause.endsOn.isBefore(today)) "Aktiv nu" else "Kommande",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        runCatching { RecurringScheduleSync.deletePause(session, pause.id) }
                                            .onSuccess { status = "Schemapaus borttagen"; reloadRecurringData() }
                                            .onFailure { status = it.message ?: "Kunde inte ta bort schemapausen" }
                                        busy = false
                                    }
                                },
                                enabled = !busy
                            ) { Text("Ta bort") }
                        }
                    }
                }

                if (status.isNotBlank()) {
                    Text(
                        status,
                        color = if (status.contains("Kunde") || status.contains("kan inte") || status.contains("Ange")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
