package se.familjekalender.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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

private fun seasonMode(month: Int) = when (month) {
    3, 4, 5 -> ThemeMode.SPRING
    6, 7, 8 -> ThemeMode.SUMMER
    9, 10, 11 -> ThemeMode.AUTUMN
    else -> ThemeMode.WINTER
}

@Composable
internal fun ExactCalendarScreen(selectedDate: LocalDate, onSelect: (LocalDate) -> Unit, events: List<SyncEvent>, members: List<SyncMember>, palette: SeasonPalette, onAdd: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val mode = seasonMode(month.monthValue)
    val p = paletteFor(mode)
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0C12))) {
        val sceneH = maxHeight * .43f
        val cardTop = maxHeight * .34f
        val cardH = maxHeight * .43f
        SeasonalPhoto(mode, Modifier.fillMaxWidth().height(sceneH))
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👥", fontSize = 16.sp); Spacer(Modifier.width(5.dp))
                Text("Familjekalendern", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f)); Text("⌕", color = Color.White, fontSize = 20.sp); Spacer(Modifier.width(14.dp)); Text("⚙", color = Color.White, fontSize = 18.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = month.minusMonths(1); onSelect(month.atDay(1)) }, Modifier.size(30.dp)) { Icon(Icons.Default.ChevronLeft, null, tint = Color.White) }
                Text("$monthName ${month.year}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton({ month = month.plusMonths(1); onSelect(month.atDay(1)) }, Modifier.size(30.dp)) { Icon(Icons.Default.ChevronRight, null, tint = Color.White) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).offset(y = cardTop).height(cardH), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MonthPanel(month, date, onSelect, events, p.accent, Modifier.weight(1.55f))
            DayPanel(date, events.filter { it.date == date }, members, p, Modifier.weight(.9f), onAdd)
        }
    }
}

@Composable
private fun SeasonalPhoto(mode: ThemeMode, modifier: Modifier) {
    val bmp = remember(mode) {
        runCatching {
            val bytes = Base64.decode(SeasonAtlas.DATA, Base64.DEFAULT)
            val atlas = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val idx = when (mode) { ThemeMode.WINTER -> 0; ThemeMode.SPRING -> 1; ThemeMode.SUMMER -> 2; ThemeMode.AUTUMN -> 3; else -> 0 }
            val sh = atlas.height / 4
            val trim = (sh * .43f).toInt()
            Bitmap.createBitmap(atlas, 0, idx * sh + trim, atlas.width, sh - trim)
        }.getOrNull()
    }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop, alignment = Alignment.Center) else Box(modifier.background(Color(0xFF202431)))
}

@Composable
private fun MonthPanel(month: YearMonth, selected: LocalDate, onSelect: (LocalDate) -> Unit, events: List<SyncEvent>, accent: Color, modifier: Modifier) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    Card(modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE3151820))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth()) { listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN").forEach { Text(it, color = Color(0xFFC9C6CF), fontSize = 6.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(3.dp))
            repeat(6) { w ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { d ->
                        val n = w * 7 + d - offset + 1
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (n in 1..month.lengthOfMonth()) {
                                val day = month.atDay(n)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(day) }) {
                                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (day == selected) accent else Color.Transparent), contentAlignment = Alignment.Center) { Text("$n", color = Color.White, fontSize = 9.5.sp, fontWeight = if (day == selected) FontWeight.Bold else FontWeight.Normal) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { events.filter { it.date == day }.take(3).forEachIndexed { i, _ -> Box(Modifier.size(3.dp).clip(CircleShape).background(listOf(Color(0xFF45AEFF), Color(0xFFFF5AA7), Color(0xFF62DB91), Color(0xFFFF9747))[i % 4])) } }
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
private fun DayPanel(date: LocalDate, events: List<SyncEvent>, members: List<SyncMember>, p: SeasonPalette, modifier: Modifier, onAdd: () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE8171920))) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(9.dp)) {
                Text("${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (events.isEmpty()) Text("Inget planerat", color = Color.White.copy(alpha = .55f), fontSize = 8.5.sp)
                events.take(5).forEachIndexed { i, e ->
                    val m = members.find { it.id == e.memberId }
                    val c = m?.let { Color(it.colorArgb.toInt()) } ?: listOf(Color(0xFF45AEFF), Color(0xFFFF5AA7), Color(0xFF62DB91), Color(0xFFFF9747))[i % 4]
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp)); Text(e.time, color = Color.White.copy(alpha = .82f), fontSize = 7.5.sp); Spacer(Modifier.width(3.dp)); Text(e.title, color = Color.White, fontSize = 7.5.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(quote(p.mode), color = p.accent, fontSize = 11.sp, lineHeight = 13.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Cursive, modifier = Modifier.padding(bottom = 40.dp))
            }
            FloatingActionButton(onClick = onAdd, containerColor = Color(0xFF9C4DFF), contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(41.dp)) { Icon(Icons.Default.Add, "Lägg till") }
        }
    }
}

private fun quote(m: ThemeMode) = when (m) {
    ThemeMode.WINTER -> "Kalla dagar,\nvarma stunder ♡"
    ThemeMode.SPRING -> "Nya dagar,\nnya möjligheter ♡"
    ThemeMode.SUMMER -> "Sommar,\nmer tillsammans ♡"
    ThemeMode.AUTUMN -> "Hösten\nsamlar oss ♡"
    else -> "Tillsammans ♡"
}
