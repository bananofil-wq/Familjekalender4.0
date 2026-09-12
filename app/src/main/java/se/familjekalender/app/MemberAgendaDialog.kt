package se.familjekalender.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun MemberAgendaDialog(
    member: SyncMember,
    events: List<SyncEvent>,
    onDismiss: () -> Unit,
    onEdit: (SyncEvent, String, LocalDate, String) -> Unit,
    onDelete: (SyncEvent) -> Unit,
    onDeleteAll: (List<SyncEvent>) -> Unit
) {
    val upcoming = remember(events, member.id) {
        events.filter { it.memberId == member.id && !it.date.isBefore(LocalDate.now()) }
            .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
    }
    val deletableUpcoming = remember(upcoming) { upcoming.filter { it.source != "sportadmin" } }
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("sv", "SE")) }
    var editing by remember { mutableStateOf<SyncEvent?>(null) }
    var deleting by remember { mutableStateOf<SyncEvent?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                    Spacer(Modifier.width(8.dp))
                    Text(member.name, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (upcoming.size == 1) "1 planerad aktivitet" else "${upcoming.size} planerade aktiviteter",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (upcoming.isEmpty()) {
                    Text("Inget är planerat framöver för ${member.name}.", color = Muted)
                }
                upcoming.forEach { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF222027))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(event.title.removePrefix("🌈").removePrefix("🧺").trim(), fontWeight = FontWeight.SemiBold)
                            Text("${event.date.format(formatter)} · ${event.time}", color = Muted, fontSize = 12.sp)
                            if (event.source == "sportadmin") {
                                Text("SportAdmin · hanteras via importen", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { editing = event }) { Text("Redigera") }
                                    TextButton(onClick = { deleting = event }) {
                                        Text("Ta bort", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                if (deletableUpcoming.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmDeleteAll = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Ta bort alla framtida (${deletableUpcoming.size})")
                    }
                    if (upcoming.size != deletableUpcoming.size) {
                        Text("SportAdmin-aktiviteter påverkas inte.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )

    editing?.let { event ->
        MemberAgendaEditDialog(
            event = event,
            onDismiss = { editing = null },
            onSave = { title, date, time ->
                onEdit(event, title, date, time)
                editing = null
            }
        )
    }

    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Ta bort aktivitet?") },
            text = { Text(event.title.removePrefix("🌈").removePrefix("🧺").trim()) },
            confirmButton = {
                Button(onClick = {
                    onDelete(event)
                    deleting = null
                }) { Text("Ta bort") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Avbryt") } }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Ta bort alla ${deletableUpcoming.size} framtida aktiviteter för ${member.name}?") },
            text = {
                Text("${deletableUpcoming.size} framtida ${if (deletableUpcoming.size == 1) "aktivitet" else "aktiviteter"} för ${member.name} tas bort. Tidigare aktiviteter sparas.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAll(deletableUpcoming)
                        confirmDeleteAll = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Ta bort alla") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Avbryt") } }
        )
    }
}

@Composable
private fun MemberAgendaEditDialog(
    event: SyncEvent,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String) -> Unit
) {
    val context = LocalContext.current
    val birthday = event.title.startsWith("🌈")
    val laundry = event.title.startsWith("🧺")
    var title by remember(event.id) { mutableStateOf(event.title.removePrefix("🌈").removePrefix("🧺").trim()) }
    var date by remember(event.id) { mutableStateOf(event.date) }
    var time by remember(event.id) { mutableStateOf(event.time.ifBlank { "18:00" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Redigera aktivitet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Aktivitet") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Datum: ${date.dayOfMonth}/${date.monthValue} ${date.year}") }
                OutlinedButton(
                    onClick = {
                        val parsed = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(18, 0))
                        TimePickerDialog(context, { _, h, m -> time = "%02d:%02d".format(h, m) }, parsed.hour, parsed.minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tid: $time") }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    val prefix = when {
                        birthday -> "🌈 "
                        laundry -> "🧺 "
                        else -> ""
                    }
                    onSave(prefix + title.trim(), date, time)
                }
            ) { Text("Spara") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}
