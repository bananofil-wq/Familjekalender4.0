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

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FamilyCalendarApp() } } }
data class FamilyMember(val name:String,val role:String,val color:Color)
data class FamilyEvent(val title:String,val time:String,val member:String,val color:Color)
data class ShoppingItem(val name:String,val checked:Boolean=false)

@Composable fun FamilyCalendarApp(){ MaterialTheme(colorScheme=darkColorScheme(primary=Purple,background=Bg,surface=CardBg)){
 var selectedDate by remember{mutableStateOf(LocalDate.now())}; var members by remember{mutableStateOf(emptyList<FamilyMember>())}; var events by remember{mutableStateOf(emptyList<FamilyEvent>())}; var shopping by remember{mutableStateOf(emptyList<ShoppingItem>())}; var tab by remember{mutableIntStateOf(0)}; var showAdd by remember{mutableStateOf(false)}; var showPeople by remember{mutableStateOf(false)}
 Scaffold(containerColor=Bg,floatingActionButton={if(tab==0)FloatingActionButton(onClick={showAdd=true},containerColor=Purple,shape=CircleShape){Icon(Icons.Default.Add,"Lägg till")}},bottomBar={BottomNav(tab){tab=it;if(it==2)showPeople=true}}){p->Column(Modifier.padding(p).padding(horizontal=18.dp).fillMaxSize()){Spacer(Modifier.height(14.dp));when(tab){0->CalendarScreen(selectedDate,{selectedDate=it},events);1->ShoppingScreen(shopping,{shopping=shopping+ShoppingItem(it)},{i->shopping=shopping.mapIndexed{x,v->if(x==i)v.copy(checked=!v.checked)else v}},{shopping=shopping.filterNot{it.checked}});else->CalendarScreen(selectedDate,{selectedDate=it},events)}}}
 if(showAdd)AddEventDialog(members,{showAdd=false}){events=events+it;showAdd=false};if(showPeople)PeopleDialog(members,{showPeople=false;tab=0}){m->members=members+m.copy(color=MemberColors[members.size%MemberColors.size])}
}}
@Composable private fun CalendarScreen(d:LocalDate,onSelect:(LocalDate)->Unit,e:List<FamilyEvent>){Text("Familjekalendern",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Allt som händer. På ett ställe.",color=Muted,fontSize=14.sp);Spacer(Modifier.height(20.dp));MonthCalendar(d,onSelect);Spacer(Modifier.height(18.dp));DayOverview(d,e)}
@Composable private fun ShoppingScreen(items:List<ShoppingItem>,onAdd:(String)->Unit,onToggle:(Int)->Unit,onClear:()->Unit){var text by remember{mutableStateOf("")};Text("Inköpslista",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Gemensamma saker att komma ihåg",color=Muted,fontSize=14.sp);Spacer(Modifier.height(20.dp));Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(text,{text=it},label={Text("Lägg till vara")},singleLine=true,modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));FilledIconButton(onClick={if(text.isNotBlank()){onAdd(text.trim());text=""}},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Purple)){Icon(Icons.Default.Add,"Lägg till")}};Spacer(Modifier.height(14.dp));if(items.isEmpty())Text("Listan är tom. Lägg till något ovan.",color=Muted);items.forEachIndexed{i,item->Card(colors=CardDefaults.cardColors(containerColor=CardBg),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(vertical=4.dp).clickable{onToggle(i)}){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(item.checked,{onToggle(i)});Spacer(Modifier.width(8.dp));Text(item.name,color=if(item.checked)Muted else Color.White,modifier=Modifier.weight(1f))}}};if(items.any{it.checked})Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick=onClear){Text("Rensa avbockade")}}}
@Composable private fun MonthCalendar(s:LocalDate,onSelect:(LocalDate)->Unit){val m=YearMonth.from(s);val off=m.atDay(1).dayOfWeek.value-1;val name=m.month.getDisplayName(TextStyle.FULL,Locale("sv","SE")).replaceFirstChar{it.uppercase()};Card(colors=CardDefaults.cardColors(containerColor=CardBg),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("$name ${m.year}",fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Surface(color=SoftPurple,shape=RoundedCornerShape(20.dp)){Text("Idag",color=Purple,modifier=Modifier.padding(horizontal=14.dp,vertical=7.dp),fontSize=12.sp)}};Spacer(Modifier.height(16.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf("M","T","O","T","F","L","S").forEach{Text(it,color=Muted,modifier=Modifier.width(36.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=12.sp)}};Spacer(Modifier.height(8.dp));repeat(6){w->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){repeat(7){dow->val day=w*7+dow-off+1;if(day in 1..m.lengthOfMonth()){val date=m.atDay(day);val active=date==s;Box(Modifier.size(36.dp).clip(CircleShape).background(if(active)Purple else Color.Transparent).clickable{onSelect(date)},contentAlignment=Alignment.Center){Text(day.toString(),color=if(active)Color(0xFF1A1022)else Color.White)}}else Spacer(Modifier.size(36.dp))}};Spacer(Modifier.height(5.dp))}}}
@Composable private fun DayOverview(d:LocalDate,e:List<FamilyEvent>){Text("Dagens aktiviteter",fontSize=19.sp,fontWeight=FontWeight.SemiBold);Text("${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL,Locale("sv","SE"))}",color=Muted,fontSize=13.sp);Spacer(Modifier.height(10.dp));if(e.isEmpty())Text("Inget planerat ännu",color=Muted);e.forEach{x->Card(colors=CardDefaults.cardColors(containerColor=CardBg),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(4.dp).height(42.dp).clip(RoundedCornerShape(4.dp)).background(x.color));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(x.title,fontWeight=FontWeight.SemiBold);Text(x.member,color=x.color,fontSize=12.sp)};Text(x.time,color=Muted)}}}
@Composable private fun BottomNav(s:Int,onSelect:(Int)->Unit){NavigationBar(containerColor=Color(0xFF17151A)){NavigationBarItem(s==0,{onSelect(0)},{Icon(Icons.Default.CalendarMonth,null)},label={Text("Kalender")});NavigationBarItem(s==1,{onSelect(1)},{Icon(Icons.Default.ShoppingCart,null)},label={Text("Inköp")});NavigationBarItem(false,{onSelect(2)},{Icon(Icons.Default.People,null)},label={Text("Familj")});NavigationBarItem(false,{}, {Icon(Icons.Default.Settings,null)},label={Text("Inställningar")})}}
@Composable private fun AddEventDialog(m:List<FamilyMember>,dismiss:()->Unit,add:(FamilyEvent)->Unit){var title by remember{mutableStateOf("")};var time by remember{mutableStateOf("18:00")};var member by remember{mutableStateOf(m.firstOrNull()?.name?:"Familjen")};AlertDialog(onDismissRequest=dismiss,title={Text("Ny aktivitet")},text={Column{OutlinedTextField(title,{title=it},label={Text("Aktivitet")});OutlinedTextField(time,{time=it},label={Text("Tid")});if(m.isEmpty())Text("Ingen person tillagd ännu",color=Muted)else m.forEach{x->Text(x.name,Modifier.fillMaxWidth().clickable{member=x.name}.padding(6.dp),color=x.color)}}},confirmButton={TextButton(onClick={if(title.isNotBlank())add(FamilyEvent(title,time,member,m.find{it.name==member}?.color?:Purple))}){Text("Lägg till")}},dismissButton={TextButton(onClick=dismiss){Text("Avbryt")}})}
@Composable private fun PeopleDialog(m:List<FamilyMember>,dismiss:()->Unit,add:(FamilyMember)->Unit){var name by remember{mutableStateOf("")};var role by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text("Familjen")},text={Column{if(m.isEmpty())Text("Inga personer tillagda ännu",color=Muted);m.forEach{Text("${it.name} · ${it.role}",color=it.color)};OutlinedTextField(name,{name=it},label={Text("Namn")});OutlinedTextField(role,{role=it},label={Text("Valfri roll")})}},confirmButton={TextButton(onClick={if(name.isNotBlank()){add(FamilyMember(name.trim(),role.ifBlank{"Familj"},Purple));name="";role=""}}){Text("Lägg till person")}},dismissButton={TextButton(onClick=dismiss){Text("Klar")}})}
