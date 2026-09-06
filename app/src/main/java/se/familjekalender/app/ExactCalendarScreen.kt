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
internal fun ExactCalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    palette: SeasonPalette,
    onAdd: () -> Unit
) {
    var month by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    val dayEvents = events.filter { it.date == selectedDate }

    Box(Modifier.fillMaxSize().background(Color(0xFF111015))) {
        ExactSeasonLandscape(palette, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x22000000), Color(0xAA0D0C10)))))

        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Familjekalendern", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("☁  12°", color = Color.White.copy(alpha = .82f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ChevronLeft, null, tint = Color.White) }
                Text("$monthName ${month.year}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ChevronRight, null, tint = Color.White) }
            }
            Spacer(Modifier.height(5.dp))

            Row(Modifier.fillMaxWidth().heightIn(min = 290.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactMonthCard(month, selectedDate, onSelect, events, palette.accent, Modifier.weight(1.42f))
                ExactDayCard(selectedDate, dayEvents, members, palette.accent, Modifier.weight(.92f))
            }

            Spacer(Modifier.weight(1f))
            Text(
                seasonQuote(palette.mode),
                color = Color.White.copy(alpha = .94f),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
        }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = palette.accent,
            contentColor = Color(0xFF17131C),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 22.dp).size(52.dp)
        ) { Icon(Icons.Default.Add, "Lägg till aktivitet") }
    }
}

@Composable
private fun CompactMonthCard(
    month: YearMonth,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    accent: Color,
    modifier: Modifier
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xD91B1920))) {
        Column(Modifier.padding(9.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("M","T","O","T","F","L","S").forEach { Text(it, color = Color(0xFFAAA4B2), fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { dow ->
                        val day = week * 7 + dow - offset + 1
                        Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                val selectedDay = date == selected
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(date) }) {
                                    Box(Modifier.size(25.dp).clip(CircleShape).background(if (selectedDay) accent else Color.Transparent), contentAlignment = Alignment.Center) {
                                        Text(day.toString(), color = if (selectedDay) Color(0xFF17131C) else Color.White, fontSize = 11.sp, fontWeight = if (selectedDay) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        events.filter { it.date == date }.take(3).forEachIndexed { i, _ -> Box(Modifier.size(3.dp).clip(CircleShape).background(if (i == 0) accent else Color.White.copy(alpha=.65f))) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExactDayCard(date: LocalDate, events: List<SyncEvent>, members: List<SyncMember>, accent: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE61B1920))) {
        Column(Modifier.fillMaxSize().padding(11.dp)) {
            Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}", color = Color(0xFFAAA4B2), fontSize = 9.sp)
            Spacer(Modifier.height(9.dp))
            if (events.isEmpty()) Text("Inget planerat", color = Color.White.copy(alpha=.58f), fontSize = 10.sp)
            events.take(6).forEach { event ->
                val member = members.find { it.id == event.memberId }
                val c = member?.let { Color(it.colorArgb.toInt()) } ?: accent
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top=3.dp).size(7.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.time, color = c, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(event.title, color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
                        if (member != null) Text(member.name, color = Color.White.copy(alpha=.55f), fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

private fun seasonQuote(mode: ThemeMode): String = when (mode) {
    ThemeMode.WINTER -> "“Små stunder blir stora minnen.”"
    ThemeMode.SPRING -> "“Här växer våra dagar tillsammans.”"
    ThemeMode.SUMMER -> "“Samla dagar du vill minnas.”"
    ThemeMode.AUTUMN -> "“Tid tillsammans är den finaste tiden.”"
    else -> "“Allt som händer. På ett ställe.”"
}

@Composable
private fun ExactSeasonLandscape(palette: SeasonPalette, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom, palette.sceneDark)))
        when (palette.mode) {
            ThemeMode.WINTER -> {
                drawCircle(Color(0xBBDDE8FF), w*.11f, Offset(w*.78f,h*.14f))
                repeat(24) { i -> drawCircle(Color.White.copy(alpha=.72f), 2f+(i%3), Offset((i*71f)%w, 55f+(i*47f)%(h*.55f))) }
                fun mountain(x:Float,y:Float,s:Float,c:Color){ drawPath(Path().apply{moveTo(x,y);lineTo(x+s*.5f,y-s*.46f);lineTo(x+s,y);close()},c) }
                mountain(-w*.08f,h*.68f,w*.72f,Color(0xFF202A46)); mountain(w*.35f,h*.69f,w*.78f,Color(0xFF17213B))
                repeat(7){i-> val x=w*(.05f+i*.15f); drawPath(Path().apply{moveTo(x,h*.82f);lineTo(x-28f,h*.98f);lineTo(x+28f,h*.98f);close()},Color(0xFF111B30)) }
                drawOval(Color(0xFFE3ECFA).copy(alpha=.82f),Offset(-40f,h*.83f),Size(w+80f,h*.25f))
            }
            ThemeMode.SPRING -> {
                drawCircle(Color(0x88FFD8E8),w*.13f,Offset(w*.8f,h*.15f))
                drawOval(Color(0xFF264D3B),Offset(-40f,h*.7f),Size(w+80f,h*.38f))
                drawLine(Color(0xFF34252C),Offset(-20f,h*.18f),Offset(w*.63f,h*.62f),14f)
                repeat(28){i-> val x=(i*47f)%w; val y=h*.13f+((i*67f)%(h*.48f)); drawCircle(if(i%2==0) Color(0xFFFFB9D3) else Color(0xFFFFE0EC),7f+(i%3),Offset(x,y)) }
            }
            ThemeMode.SUMMER -> {
                drawCircle(Color(0xFFFFD477),w*.13f,Offset(w*.78f,h*.14f))
                drawRect(Color(0xFF1D6684).copy(alpha=.88f),Offset(0f,h*.55f),Size(w,h*.45f))
                repeat(5){i-> val y=h*.59f+i*24f; drawLine(Color.White.copy(alpha=.24f),Offset(0f,y),Offset(w,y+10f),3f)}
                drawPath(Path().apply{moveTo(0f,h);lineTo(0f,h*.77f);quadraticBezierTo(w*.45f,h*.66f,w,h*.78f);lineTo(w,h);close()},Color(0xFFC69562))
                drawLine(Color(0xFF3A2C22),Offset(w*.15f,h*.8f),Offset(w*.28f,h*.42f),10f)
                repeat(6){i-> drawLine(Color(0xFF31533B),Offset(w*.28f,h*.42f),Offset(w*(.08f+i*.08f),h*(.31f+(i%2)*.05f)),7f)}
            }
            ThemeMode.AUTUMN -> {
                drawCircle(Color(0x99FFB05F),w*.13f,Offset(w*.78f,h*.15f))
                drawOval(Color(0xFF3B241D),Offset(-50f,h*.7f),Size(w+100f,h*.38f))
                drawLine(Color(0xFF2A1715),Offset(w*.12f,h),Offset(w*.27f,h*.26f),18f)
                drawLine(Color(0xFF2A1715),Offset(w*.25f,h*.47f),Offset(w*.61f,h*.2f),10f)
                repeat(34){i-> val x=(i*59f)%w; val y=h*.17f+((i*43f)%(h*.55f)); val c=listOf(Color(0xFFFFA04D),Color(0xFFD65A32),Color(0xFFFFC15A))[i%3]; drawCircle(c,8f+(i%4),Offset(x,y)) }
            }
            else -> drawCircle(palette.accent.copy(alpha=.3f),w*.18f,Offset(w*.75f,h*.2f))
        }
    }
}
