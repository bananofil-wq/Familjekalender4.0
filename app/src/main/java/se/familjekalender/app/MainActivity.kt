package se.familjekalender.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val Bg = Color(0xFF111015)
private val Card = Color(0xFF1B191F)
private val Purple = Color(0xFFB47CFF)
private val SoftPurple = Color(0xFF2B2038)
private val Muted = Color(0xFFAAA4B2)
private val Pink = Color(0xFFFF77A8)
private val Blue = Color(0xFF62A9FF)
private val Green = Color(0xFF6DD6A7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FamilyCalendarApp() }
    }
}

data class FamilyMember(val name: String, val role: String, val color: Color)
data class FamilyEvent(val title: String, val time: String, val member: String, val color: Color)

@Composable
fun FamilyCalendarApp() {
    val colors = darkColorScheme(primary = Purple, background = Bg, surface = Card, onBackground = Color.White, onSurface = Color.White)
    MaterialTheme(colorScheme = colors) {
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var members by remember { mutableStateOf(listOf(FamilyMember("Kim", "Pappa", Purple), FamilyMember("Gabriella", "Mamma", Pink), FamilyMember("Hugo", "Barn", Blue), FamilyMember("Viggo", "Barn", Green), FamilyMember("Maja", "Barn", Color(0xFFFFB86B)))) }
        var events by remember { mutableStateOf(listOf(FamilyEvent("Innebandyträning", "17:30", "Hugo", Blue), FamilyEvent("Handla", "19:00", "Familjen", Purple))) }
        var showAdd by remember { mutableStateOf(false) }
        var showPeople by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = Bg,
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }, containerColor = Purple, contentColor = Color(0xFF1A1022), shape = CircleShape) {
                    Icon(Icons.Default.Add, "Lägg till")
                }
            },
            bottomBar = { BottomNav(onPeople = { showPeople = true }) }
        ) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 18.dp).fillMaxSize()) {
                Spacer(Modifier.height(14.dp))
                Text("Familjekalendern", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Allt som händer. På ett ställe.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                MonthCalendar(selectedDate) { selectedDate = it }
                Spacer(Modifier.height(18.dp))
                DayOverview(selectedDate, events)
            }
        }

        if (showAdd) AddEventDialog(members, onDismiss = { showAdd = false }) { events = events + it; showAdd = false }
        if (showPeople) PeopleDialog(members, onDismiss = { showPeople = false }) { members = members + it }
    }
}

@Composable
private fun MonthCalendar(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val month = YearMonth.from(selected)
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    Card(colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$monthName ${month.year}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(color = SoftPurple, shape = RoundedCornerShape(20.dp)) { Text("Idag", color = Purple, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 12.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "O", "T", "F", "L", "S").forEach { Text(it, color = Muted, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(8.dp))
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { dow ->
                        val day = week * 7 + dow - offset + 1
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val active = date == selected
                            Box(Modifier.size(36.dp).clip(CircleShape).background(if (active) Purple else Color.Transparent).clickable { onSelect(date) }, contentAlignment = Alignment.Center) {
                                Text(day.toString(), color = if (active) Color(0xFF1A1022) else Color.White, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            }
                        } else Spacer(Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun DayOverview(date: LocalDate, events: List<FamilyEvent>) {
    Text("Dagens aktiviteter", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    if (events.isEmpty()) Text("Inget planerat ännu", color = Muted)
    events.forEach { event ->
        Card(colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(42.dp).clip(RoundedCornerShape(4.dp)).background(event.color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(event.title, fontWeight = FontWeight.SemiBold); Text(event.member, color = event.color, fontSize = 12.sp) }
                Text(event.time, color = Muted)
            }
        }
    }
}

@Composable
private fun BottomNav(onPeople: () -> Unit) {
    NavigationBar(containerColor = Color(0xFF17151A)) {
        NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Hem") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Kalender") })
        NavigationBarItem(selected = false, onClick = onPeople, icon = { Icon(Icons.Default.People, null) }, label = { Text("Familj") })
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Inställningar") })
    }
}

@Composable
private fun AddEventDialog(members: List<FamilyMember>, onDismiss: () -> Unit, onAdd: (FamilyEvent) -> Unit) {
    var title by remember { mutableStateOf("") }; var time by remember { mutableStateOf("18:00") }; var member by remember { mutableStateOf(members.firstOrNull()?.name ?: "Familjen") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ny aktivitet") }, text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Aktivitet") }); Spacer(Modifier.height(8.dp)); OutlinedTextField(time, { time = it }, label = { Text("Tid") }); Spacer(Modifier.height(8.dp)); Text("Person: $member", color = Purple); members.forEach { m -> Text(m.name, Modifier.fillMaxWidth().clickable { member = m.name }.padding(vertical = 6.dp), color = if (m.name == member) m.color else Color.White) } } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onAdd(FamilyEvent(title, time, member, members.find { it.name == member }?.color ?: Purple)) }) { Text("Lägg till") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } })
}

@Composable
private fun PeopleDialog(members: List<FamilyMember>, onDismiss: () -> Unit, onAdd: (FamilyMember) -> Unit) {
    var name by remember { mutableStateOf("") }; var role by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Familjen") }, text = { Column { members.forEach { Text("${it.name} · ${it.role}", color = it.color, modifier = Modifier.padding(vertical = 4.dp)) }; Spacer(Modifier.height(10.dp)); OutlinedTextField(name, { name = it }, label = { Text("Namn") }); OutlinedTextField(role, { role = it }, label = { Text("Roll, t.ex. mamma, pappa, barn") }) } }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) { onAdd(FamilyMember(name, role.ifBlank { "Familj" }, Purple)); name = ""; role = "" } }) { Text("Lägg till person") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Klar") } })
}
