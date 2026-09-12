package se.familjekalender.app

// Source touch to publish the verified calendar-position update in the signed APK.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EditableMemberColors = listOf(
    0xFFB47CFF,
    0xFFFF77A8,
    0xFF62A9FF,
    0xFF6DD6A7,
    0xFFFFB86B,
    0xFFFF5C5C,
    0xFF2ED9C3,
    0xFFFFD166
)

@Composable
internal fun EditableFamilyScreen(
    members: List<SyncMember>,
    events: List<SyncEvent>,
    onAdd: (String, String) -> Unit,
    onColorChange: (SyncMember, Long) -> Unit,
    onEventEdit: (SyncEvent, String, java.time.LocalDate, String) -> Unit,
    onEventDelete: (SyncEvent) -> Unit,
    onEventsDelete: (List<SyncEvent>) -> Unit = { eventsToDelete -> eventsToDelete.forEach(onEventDelete) }
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var editingMember by remember { mutableStateOf<SyncMember?>(null) }
    var agendaMember by remember { mutableStateOf<SyncMember?>(null) }

    Text("Familjen", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Tryck på en person för att se planeringen framöver", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))

    members.forEach { member ->
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { agendaMember = member }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(Color(member.colorArgb.toInt()))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(member.name)
                    if (member.role.isNotBlank()) Text(member.role, color = Muted, fontSize = 11.sp)
                }
                TextButton(onClick = { editingMember = member }) { Text("Färg", fontSize = 11.sp) }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(name, { name = it }, label = { Text("Namn") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(role, { role = it }, label = { Text("Valfri roll") }, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            if (name.isNotBlank()) {
                onAdd(name.trim(), role.trim())
                name = ""
                role = ""
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Lägg till person") }

    agendaMember?.let { member ->
        MemberAgendaDialog(
            member = member,
            events = events,
            onDismiss = { agendaMember = null },
            onEdit = onEventEdit,
            onDelete = onEventDelete,
            onDeleteAll = onEventsDelete
        )
    }

    editingMember?.let { member ->
        AlertDialog(
            onDismissRequest = { editingMember = null },
            title = { Text("Färg för ${member.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Välj ny färg. Kalenderprickar och dagens aktiviteter uppdateras efter synk.")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EditableMemberColors.take(4).forEach { color ->
                            ColorChoice(color, member.colorArgb == color) {
                                onColorChange(member, color)
                                editingMember = null
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EditableMemberColors.drop(4).forEach { color ->
                            ColorChoice(color, member.colorArgb == color) {
                                onColorChange(member, color)
                                editingMember = null
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingMember = null }) { Text("Avbryt") }
            }
        )
    }
}

@Composable
private fun ColorChoice(colorArgb: Long, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(46.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(colorArgb.toInt()),
        border = if (selected) androidx.compose.foundation.BorderStroke(3.dp, Color.White) else null
    ) {}
}
