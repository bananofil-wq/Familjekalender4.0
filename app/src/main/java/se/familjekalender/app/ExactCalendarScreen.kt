package se.familjekalender.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun ExactCalendarScreen(selectedDate: LocalDate,onSelect:(LocalDate)->Unit,events:List<SyncEvent>,members:List<SyncMember>,palette:SeasonPalette,onAdd:()->Unit){
 var month by remember{mutableStateOf(YearMonth.from(selectedDate))}
 val seasonPalette=paletteFor(when(month.monthValue){3,4,5->ThemeMode.SPRING;6,7,8->ThemeMode.SUMMER;9,10,11->ThemeMode.AUTUMN;else->ThemeMode.WINTER})
 val monthName=month.month.getDisplayName(TextStyle.FULL,Locale("sv","SE")).replaceFirstChar{it.uppercase()}
 val visibleDate=if(YearMonth.from(selectedDate)==month)selectedDate else month.atDay(1)
 val dayEvents=events.filter{it.date==visibleDate}
 Box(Modifier.fillMaxSize().background(Color(0xFF111018))){
  SeasonalArtwork(seasonPalette,Modifier.fillMaxWidth().height(315.dp))
  Column(Modifier.fillMaxSize()){
   Column(Modifier.padding(horizontal=14.dp).padding(top=10.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Text("👥",fontSize=15.sp);Spacer(Modifier.width(5.dp));Text("Familjekalendern",color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("⌕  ⚙",color=Color.White.copy(.8f),fontSize=17.sp)}
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment=Alignment.CenterVertically){IconButton({month=month.minusMonths(1);onSelect(month.atDay(1))},Modifier.size(28.dp)){Icon(Icons.Default.ChevronLeft,null,tint=Color.White)};Text("$monthName ${month.year}",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.weight(1f));IconButton({month=month.plusMonths(1);onSelect(month.atDay(1))},Modifier.size(28.dp)){Icon(Icons.Default.ChevronRight,null,tint=Color.White)}}
    Spacer(Modifier.height(145.dp))
    Row(Modifier.fillMaxWidth().height(315.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){MonthPanel(month,visibleDate,onSelect,events,seasonPalette.accent,Modifier.weight(1.55f));DayPanel(visibleDate,dayEvents,members,seasonPalette,Modifier.weight(.9f),onAdd)}
   }
   Spacer(Modifier.weight(1f))
  }
 }
}

@Composable private fun MonthPanel(month:YearMonth,selected:LocalDate,onSelect:(LocalDate)->Unit,events:List<SyncEvent>,accent:Color,modifier:Modifier){
 val offset=month.atDay(1).dayOfWeek.value-1
 Card(modifier,shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xCC171922))){Column(Modifier.padding(horizontal=8.dp,vertical=10.dp)){Row(Modifier.fillMaxWidth()){listOf("MÅN","TIS","ONS","TOR","FRE","LÖR","SÖN").forEach{Text(it,color=Color(0xFFCBC8D1),fontSize=6.5.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))}};Spacer(Modifier.height(4.dp));repeat(6){week->Row(Modifier.fillMaxWidth()){repeat(7){dow->val d=week*7+dow-offset+1;Box(Modifier.weight(1f).height(38.dp),contentAlignment=Alignment.Center){if(d in 1..month.lengthOfMonth()){val date=month.atDay(d);Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{onSelect(date)}){Box(Modifier.size(27.dp).clip(CircleShape).background(if(date==selected)accent else Color.Transparent),contentAlignment=Alignment.Center){Text("$d",color=Color.White,fontSize=10.sp,fontWeight=if(date==selected)FontWeight.Bold else FontWeight.Normal)};Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){events.filter{it.date==date}.take(3).forEachIndexed{i,_->Box(Modifier.size(3.dp).clip(CircleShape).background(listOf(Color(0xFF55B7FF),Color(0xFFFF73B4),Color(0xFF72E5A0))[i%3]))}}}}}}}}}}
}

@Composable private fun DayPanel(date:LocalDate,events:List<SyncEvent>,members:List<SyncMember>,palette:SeasonPalette,modifier:Modifier,onAdd:()->Unit){
 Card(modifier,shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color(0xD51A1B23))){Box(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(10.dp)){Text("${date.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale("sv","SE")).replaceFirstChar{it.uppercase()}} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT,Locale("sv","SE"))}",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(9.dp));if(events.isEmpty())Text("Inget planerat",color=Color.White.copy(.55f),fontSize=9.sp);events.take(5).forEachIndexed{i,e->val m=members.find{it.id==e.memberId};val c=m?.let{Color(it.colorArgb.toInt())}?:listOf(Color(0xFF55B7FF),Color(0xFFFF73B4),Color(0xFF72E5A0))[i%3];Row(Modifier.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).clip(CircleShape).background(c));Spacer(Modifier.width(5.dp));Text(e.time,color=Color.White.copy(.8f),fontSize=8.sp);Spacer(Modifier.width(4.dp));Text(e.title,color=Color.White,fontSize=8.sp)}};Spacer(Modifier.weight(1f));Text(quote(palette.mode),color=palette.accent,fontSize=12.sp,lineHeight=15.sp,fontStyle=FontStyle.Italic,fontFamily=FontFamily.Cursive,modifier=Modifier.padding(bottom=45.dp))};FloatingActionButton(onClick,containerColor=Color(0xFF9C5CFF),contentColor=Color.White,shape=CircleShape,modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp).size(43.dp)){Icon(Icons.Default.Add,"Lägg till")}}}
}

private fun quote(mode:ThemeMode)=when(mode){ThemeMode.WINTER->"Kalla dagar,\nvarma stunder ♡";ThemeMode.SPRING->"Nya dagar,\nnya möjligheter ♡";ThemeMode.SUMMER->"Sommar,\nmer tillsammans ♡";ThemeMode.AUTUMN->"Hösten\nsamlar oss ♡";else->"Tillsammans ♡"}

@Composable private fun SeasonalArtwork(p:SeasonPalette,modifier:Modifier){Canvas(modifier){val w=size.width;val h=size.height;drawRect(Brush.verticalGradient(listOf(p.skyTop,p.skyBottom)));when(p.mode){
 ThemeMode.WINTER->{drawCircle(Color(0x55E7E3FF),w*.12f,Offset(w*.73f,h*.22f));fun mt(x:Float,y:Float,s:Float,c:Color){drawPath(Path().apply{moveTo(x,y);lineTo(x+s*.5f,y-s*.35f);lineTo(x+s,y);close()},c)};mt(-30f,h*.73f,w*.65f,Color(0xFF6377AA));mt(w*.35f,h*.74f,w*.75f,Color(0xFF344D7B));repeat(9){i->val x=w*(.03f+i*.12f);drawPath(Path().apply{moveTo(x,h*.55f);lineTo(x-25f,h*.91f);lineTo(x+25f,h*.91f);close()},Color(0xFF173154))};drawOval(Color(0xFFDDE8FF),Offset(-30f,h*.77f),Size(w+60f,h*.3f));drawRect(Color(0xFF392D32),Offset(w*.72f,h*.65f),Size(w*.17f,h*.13f));drawPath(Path().apply{moveTo(w*.69f,h*.66f);lineTo(w*.805f,h*.57f);lineTo(w*.92f,h*.66f);close()},Color(0xFFE5E9F5));repeat(3){i->drawRect(Color(0xFFFFC45E),Offset(w*(.75f+i*.045f),h*.7f),Size(8f,10f))}}
 ThemeMode.SPRING->{drawCircle(Color(0xAAFFF0C8),w*.12f,Offset(w*.78f,h*.23f));drawRect(Color(0xFF477E77),Offset(0f,h*.58f),Size(w,h*.42f));drawPath(Path().apply{moveTo(0f,h);lineTo(0f,h*.7f);quadraticBezierTo(w*.45f,h*.58f,w,h*.7f);lineTo(w,h);close()},Color(0xFF426C4B));drawLine(Color(0xFF49313C),Offset(-20f,h*.18f),Offset(w*.52f,h*.55f),14f);repeat(38){i->val x=(i*53f)%w;val y=h*.13f+((i*41f)%(h*.43f));drawCircle(if(i%2==0)Color(0xFFFFB9D3) else Color(0xFFFFE1EC),6f+(i%3),Offset(x,y))}}
 ThemeMode.SUMMER->{drawCircle(Color(0xFFFFE68A),w*.13f,Offset(w*.16f,h*.22f));drawRect(Color(0xFF28A6CC),Offset(0f,h*.42f),Size(w,h*.28f));repeat(4){i->drawLine(Color.White.copy(.55f),Offset(0f,h*(.5f+i*.045f)),Offset(w,h*(.51f+i*.045f)),3f)};drawPath(Path().apply{moveTo(0f,h);lineTo(0f,h*.67f);quadraticBezierTo(w*.5f,h*.57f,w,h*.7f);lineTo(w,h);close()},Color(0xFFD8B06D));drawLine(Color(0xFF7B5635),Offset(w*.75f,h*.66f),Offset(w*.8f,h*.36f),7f);drawPath(Path().apply{moveTo(w*.62f,h*.39f);quadraticBezierTo(w*.8f,h*.23f,w*.96f,h*.4f);close()},Color(0xFFFFE5C7))}
 ThemeMode.AUTUMN->{drawCircle(Color(0xFFFFC268),w*.12f,Offset(w*.72f,h*.33f));drawRect(Color(0xFF704234),Offset(0f,h*.57f),Size(w,h*.43f));drawPath(Path().apply{moveTo(0f,h*.62f);lineTo(w*.3f,h*.42f);lineTo(w*.5f,h*.57f);lineTo(w*.72f,h*.4f);lineTo(w,h*.62f);close()},Color(0xFF513347));drawRect(Color(0xFF6B4B43),Offset(0f,h*.64f),Size(w,h*.36f));repeat(10){i->val x=w*(i*.11f);drawPath(Path().apply{moveTo(x,h*.72f);lineTo(x-18f,h*.94f);lineTo(x+18f,h*.94f);close()},Color(0xFF1D2B29))};drawLine(Color(0xFF321B20),Offset(0f,h*.85f),Offset(w*.22f,h*.23f),18f);repeat(32){i->val x=(i*61f)%w;val y=h*.14f+((i*37f)%(h*.45f));drawCircle(listOf(Color(0xFFE8522B),Color(0xFFFF7D32),Color(0xFFFFB34B))[i%3],7f+(i%4),Offset(x,y))}}
 else->{drawRect(p.sceneDark)}
}}}
