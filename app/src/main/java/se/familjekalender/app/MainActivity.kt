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
import androidx.compose.material.icons.filled.*
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
private val CardBg = Color(0xFF1B191F)
private val Purple = Color(0xFFB47CFF)
private val SoftPurple = Color(0xFF2B2038)
private val Muted = Color(0xFFAAA4B2)
private val MemberColors = listOf(Purple, Color(0xFFFF77A8), Color(0xFF62A9FF), Color(0xFF6DD6A7), Color(0xFFFFB86B))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FamilyCalendarApp() }
    }
}

data class FamilyMember(val name: String, val role: String, val color: Color)
data class FamilyEvent(val title: String, val time: String, val member: String, val color: Color)
data class ShoppingItem(val name: String, val checked: Boolean = false)

@Composable
fun FamilyCalendarApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = CardBg)) {
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var members by remember { mutableStateOf(emptyList<FamilyMember>()) }
        var events by remember { mutableStateOf(emptyList<FamilyEvent>()) }
        var shopping by remember { mutableStateOf(emptyList<ShoppingItem>()) }
        var tab by remember { mutableIntStateOf(0) }
        var showAddEvent by remember { mutableStateOf(false) }
        var showPeople by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = Bg,
            floatingActionButton = {
                if (tab == 0) FloatingActionButton(onClick = { showAddEvent = true }, containerColor = Purple, shape = CircleShape) { Icon(Icons.Default.Add, "Lägg till aktivitet") }
            },
            bottomBar = { BottomNav(tab) { newTab -> tab = newTab; if (newTab == 2) showPeople = true } }
        ) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 18.dp).fillMaxSize()) {
                Spacer(Modifier.height(14.dp))
                when (tab) {
                    0 -> CalendarScreen(selectedDate, { selectedDate = it }, events)
                    1 -> ShoppingScreen(shopping, onAdd = { shopping = shopping + ShoppingItem(it) }, onToggle = { index -> shopping = shopping.mapIndexed { i, item -> if (i == index) item.copy(checked = !item.checked) else item } }, onClear = { shopping = shopping.filterNot { it.checked } })
                    else -> CalendarScreen(selectedDate, { selectedDate = it }, events)
                }
            }
        }
        if (showAddEvent) AddEventDialog(members, { showAddEvent = false }) { events = events + it; showAddEvent = false }
        if (showPeople) PeopleDialog(members, { showPeople = false; tab = 0 }) { m -> members = members + m.copy(color = MemberColors[members.size % MemberColors.size]) }
    }
}

@Composable
private fun CalendarScreen(selected: LocalDate, onSelect: (LocalDate) -> Unit, events: List<FamilyEvent>) {
    Text("Familjekalendern", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Allt som händer. På ett ställe.", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(20.dp)); MonthCalendar(selected, onSelect); Spacer(Modifier.height(18.dp)); DayOverview(selected, events)
}

@Composable
private fun ShoppingScreen(items: List<ShoppingItem>, onAdd: (String) -> Unit, onToggle: (Int) -> Unit, onClear: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Text("Inköpslista", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Gemensamma saker att komma ihåg", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Purple)) { Icon(Icons.Default.Add, "Lägg till") }
    }
    Spacer(Modifier.height(14.dp))
    if (items.isEmpty()) Text("Listan är tom. Lägg till något ovan.", color = Muted)
    items.forEachIndexed { index, item ->
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onToggle(index) }) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.checked, onCheckedChange = { onToggle(index) })
                Spacer(Modifier.width(8.dp)); Text(item.name, color = if (item.checked) Muted else Color.White, modifier = Modifier.weight(1f))
            }
        }
    }
    if (items.any { it.checked }) TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) { Text("Rensa avbockade") }
}

@Composable
private fun MonthCalendar(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val month = YearMonth.from(selected); val offset = month.atDay(1).dayOfWeek.value - 1
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("$monthName ${month.year}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Surface(color = SoftPurple, shape = RoundedCornerShape(20.dp)) { Text("Idag", color = Purple, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 12.sp) } }
            Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("M","T","O","T","F","L","S").forEach { Text(it, color = Muted, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp) } }; Spacer(Modifier.height(8.dp))
            repeat(6) { week -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { repeat(7) { dow -> val day = week*7+dow-offset+1; if(day in 1..month.lengthOfMonth()) { val date=month.atDay(day); val active=date==selected; Box(Modifier.size(36.dp).clip(CircleShape).background(if(active) Purple else Color.Transparent).clickable{onSelect(date)}, contentAlignment=Alignment.Center){Text(day.toString(),color=if(active) Color(0xFF1A1022) else Color.White,fontWeight=if(active) FontWeight.Bold else FontWeight.Normal)}} else Spacer(Modifier.size(36.dp)) } }; Spacer(Modifier.height(5.dp)) }
        }
    }
}

@Composable
private fun DayOverview(date: LocalDate, events: List<FamilyEvent>) {
    Text("Dagens aktiviteter", fontSize=19.sp,fontWeight=FontWeight.SemiBold); Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL,Locale("sv","SE"))}",color=Muted,fontSize=13.sp); Spacer(Modifier.height(10.dp))
    if(events.isEmpty()) Text("Inget planerat ännu",color=Muted)
    events.forEach { event -> Card(colors=CardDefaults.cardColors(containerColor=CardBg),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(4.dp).height(42.dp).clip(RoundedCornerShape(4.dp)).background(event.color));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(event.title,fontWeight=FontWeight.SemiBold);Text(event.member,color=event.color,fontSize=12.sp)};Text(event.time,color=Muted)}} }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor=Color(0xFF17151A)) {
        NavigationBarItem(selected=selected==0,onClick={onSelect(0)},icon={Icon(Icons.Default.CalendarMonth,null)},label={Text("Kalender")})
        NavigationBarItem(selected=selected==1,onClick={onSelect(1)},icon={Icon(Icons.Default.ShoppingCart,null)},label={Text("Inköp")})
        NavigationBarItem(selected=false,onClick={onSelect(2)},icon={Icon(Icons.Default.People,null)},label={Text("Familj")})
        NavigationBarItem(selected=false,onClick={},icon={Icon(Icons.Default.Settings,null)},label={Text("Inställningar")})
    }
}

@Composable
private fun AddEventDialog(members: List<FamilyMember>, onDismiss:()->Unit,onAdd:(FamilyEvent)->Unit){
    var title by remember{mutableStateOf("")};var time by remember{mutableStateOf("18:00")};var member by remember{mutableStateOf(members.firstOrNull()?.name?:"Familjen")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Ny aktivitet")},text={Column{OutlinedTextField(title,{title=it},label={Text("Aktivitet")});Spacer(Modifier.height(8.dp));OutlinedTextField(time,{time=it},label={Text("Tid")});Spacer(Modifier.height(8.dp));if(members.isEmpty()) Text("Ingen person tillagd ännu. Aktiviteten läggs på Familjen.",color=Muted) else members.forEach{m->Text(m.name,Modifier.fillMaxWidth().clickable{member=m.name}.padding(vertical=6.dp),color=if(m.name==member)m.color else Color.White)}}},confirmButton={TextButton(onClick={if(title.isNotBlank())onAdd(FamilyEvent(title,time,member,members.find{it.name==member}?.color?:Purple))}){Text("Lägg till")}},dismissButton={TextButton(onClick=onDismiss){Text("Avbryt")}})
}

@Composable
private fun PeopleDialog(members:List<FamilyMember>,onDismiss:()->Unit,onAdd:(FamilyMember)->Unit){
    var name by remember{mutableStateOf("")};var role by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Familjen")},text={Column{if(members.isEmpty())Text("Inga personer tillagda ännu",color=Muted);members.forEach{Text("${it.name} · ${it.role}",color=it.color,modifier=Modifier.padding(vertical=4.dp))};Spacer(Modifier.height(10.dp));OutlinedTextField(name,{name=it},label={Text("Namn")});OutlinedTextField(role,{role=it},label={Text("Valfri roll")})}},confirmButton={TextButton(onClick={if(name.isNotBlank()){onAdd(FamilyMember(name.trim(),role.ifBlank{"Familj"},Purple));name="";role=""}}){Text("Lägg till person")}},dismissButton={TextButton(onClick=onDismiss){Text("Klar")}})
}
