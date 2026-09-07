package se.familjekalender.app

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

private fun seasonMode(month:Int)=when(month){3,4,5->ThemeMode.SPRING;6,7,8->ThemeMode.SUMMER;9,10,11->ThemeMode.AUTUMN;else->ThemeMode.WINTER}

@Composable
internal fun ExactCalendarScreen(selectedDate:LocalDate,onSelect:(LocalDate)->Unit,events:List<SyncEvent>,members:List<SyncMember>,palette:SeasonPalette,onAdd:()->Unit){
 var month by remember{ mutableStateOf(YearMonth.from(selectedDate)) }
 val mode=seasonMode(month.monthValue); val p=paletteFor(mode)
 val monthName=month.month.getDisplayName(TextStyle.FULL,Locale("sv","SE")).replaceFirstChar{it.uppercase()}
 val date=if(YearMonth.from(selectedDate)==month) selectedDate else month.atDay(1)
 BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF07090F))){
  val heroH=maxHeight*.34f
  val panelsH=maxHeight*.39f
  SeasonalPhoto(mode,Modifier.fillMaxWidth().height(heroH))
  Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Default.Groups,null,tint=Color(0xFF9C4DFF),modifier=Modifier.size(22.dp));Spacer(Modifier.width(6.dp));Text("Familjekalendern",Color.White,14.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Icon(Icons.Default.Search,"Sök",tint=Color.White,modifier=Modifier.size(21.dp));Spacer(Modifier.width(16.dp));Icon(Icons.Default.Settings,"Inställningar",tint=Color.White,modifier=Modifier.size(21.dp))
   }
   Spacer(Modifier.height(12.dp))
   Row(verticalAlignment=Alignment.CenterVertically){
    IconButton({month=month.minusMonths(1);onSelect(month.atDay(1))},Modifier.size(30.dp)){Icon(Icons.Default.ChevronLeft,null,tint=Color.White)}
    Text("$monthName ${month.year}",Color.White,19.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
    IconButton({month=month.plusMonths(1);onSelect(month.atDay(1))},Modifier.size(30.dp)){Icon(Icons.Default.ChevronRight,null,tint=Color.White)}
   }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=10.dp).offset(y=heroH-18.dp).height(panelsH),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   MonthPanel(month,date,onSelect,events,p.accent,Modifier.weight(1.62f));DayPanel(date,events.filter{it.date==date},members,p,Modifier.weight(1f),onAdd)
  }
 }
}

@Composable private fun SeasonalPhoto(mode:ThemeMode,modifier:Modifier){
 val bmp=remember(mode){runCatching{val bytes=Base64.decode(SeasonAtlas.DATA,Base64.DEFAULT);val atlas=BitmapFactory.decodeByteArray(bytes,0,bytes.size);val idx=when(mode){ThemeMode.WINTER->0;ThemeMode.SPRING->1;ThemeMode.SUMMER->2;ThemeMode.AUTUMN->3;else->0};val sh=atlas.height/4;val clean=if(idx>=2)(sh*.54f).toInt() else sh;android.graphics.Bitmap.createBitmap(atlas,0,idx*sh,atlas.width,clean)}.getOrNull()}
 if(bmp!=null) Image(bmp.asImageBitmap(),null,modifier,contentScale=ContentScale.Crop,alignment=Alignment.Center) else Box(modifier.background(Color(0xFF202431)))
}

@Composable private fun MonthPanel(month:YearMonth,selected:LocalDate,onSelect:(LocalDate)->Unit,events:List<SyncEvent>,accent:Color,modifier:Modifier){
 val offset=month.atDay(1).dayOfWeek.value-1
 Card(modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xF0151820))){Column(Modifier.fillMaxSize().padding(horizontal=9.dp,vertical=10.dp)){
  Row(Modifier.fillMaxWidth()){listOf("MÅN","TIS","ONS","TOR","FRE","LÖR","SÖN").forEach{Text(it,Color(0xFFC9C8D0),6.sp,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))}}
  Spacer(Modifier.height(4.dp));repeat(6){w->Row(Modifier.fillMaxWidth().weight(1f)){repeat(7){d->val n=w*7+d-offset+1;Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center){if(n in 1..month.lengthOfMonth()){val day=month.atDay(n);Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{onSelect(day)}){Box(Modifier.size(23.dp).clip(CircleShape).background(if(day==selected)accent else Color.Transparent),contentAlignment=Alignment.Center){Text("$n",Color.White,9.sp,fontWeight=if(day==selected)FontWeight.Bold else FontWeight.Normal)};Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){events.filter{it.date==day}.take(3).forEachIndexed{i,_->Box(Modifier.size(3.dp).clip(CircleShape).background(listOf(Color(0xFF45AEFF),Color(0xFFFF5AA7),Color(0xFF62DB91),Color(0xFFFF9747))[i%4]))}}}}}}}
 }}
}

@Composable private fun DayPanel(date:LocalDate,events:List<SyncEvent>,members:List<SyncMember>,p:SeasonPalette,modifier:Modifier,onAdd:()->Unit){
 Card(modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xF0151820))){Box(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(11.dp)){Text("${date.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale("sv","SE")).replaceFirstChar{it.uppercase()}} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT,Locale("sv","SE"))}",Color.White,11.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));if(events.isEmpty())Text("Inget planerat",Color.White.copy(alpha=.55f),8.sp);events.take(5).forEachIndexed{i,e->val m=members.find{it.id==e.memberId};val c=m?.let{Color(it.colorArgb.toInt())}?:listOf(Color(0xFF45AEFF),Color(0xFFFF5AA7),Color(0xFF62DB91),Color(0xFFFF9747))[i%4];Row(Modifier.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(6.dp).clip(CircleShape).background(c));Spacer(Modifier.width(4.dp));Text(e.time,Color.White.copy(alpha=.8f),7.sp);Spacer(Modifier.width(3.dp));Text(e.title,Color.White,7.sp,maxLines=1)}};Spacer(Modifier.weight(1f));Text(quote(p.mode),p.accent,10.sp,lineHeight=12.sp,fontStyle=FontStyle.Italic,fontFamily=FontFamily.Cursive,modifier=Modifier.padding(bottom=34.dp))};FloatingActionButton(onClick=onAdd,containerColor=Color(0xFF9C4DFF),contentColor=Color.White,shape=CircleShape,modifier=Modifier.align(Alignment.BottomEnd).padding(9.dp).size(38.dp)){Icon(Icons.Default.Add,"Lägg till",modifier=Modifier.size(22.dp))}}}
}
private fun quote(m:ThemeMode)=when(m){ThemeMode.WINTER->"Kalla dagar,\nvarma stunder ♡";ThemeMode.SPRING->"Nya dagar,\nnya möjligheter ♡";ThemeMode.SUMMER->"Sommar,\nmer tillsammans ♡";ThemeMode.AUTUMN->"Hösten\nsamlar oss ♡";else->"Tillsammans ♡"}
