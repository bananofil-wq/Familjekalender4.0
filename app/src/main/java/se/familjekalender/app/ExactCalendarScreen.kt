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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
internal fun ExactCalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    palette: SeasonPalette,
    onAdd: () -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val mode = seasonMode(month.monthValue)
    val p = paletteFor(mode)
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF061019))) {
        val heroH = maxHeight * .355f
        val panelTop = heroH - 2.dp
        val panelsH = maxHeight * .545f

        SeasonalPhoto(mode, Modifier.fillMaxWidth().height(heroH))

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = Color(0xFF9C4DFF), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(7.dp))
                Text(text = "Familjekalendern", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Search, "Sök", tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 72.dp)) {
                IconButton(onClick = { month = month.minusMonths(1); onSelect(month.atDay(1)) }, modifier = Modifier.size(31.dp)) {
                    Icon(Icons.Default.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Text(text = "$monthName ${month.year}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { month = month.plusMonths(1); onSelect(month.atDay(1)) }, modifier = Modifier.size(31.dp)) {
                    Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).offset(y = panelTop).height(panelsH),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonthPanel(month, date, onSelect, events, p.accent, Modifier.weight(1.78f))
            DayPanel(date, events.filter { it.date == date }, members, p, Modifier.weight(1f), onAdd)
        }
    }
}

@Composable
private fun SeasonalPhoto(mode: ThemeMode, modifier: Modifier) {
    val context = LocalContext.current
    val resourceId = when (mode) {
        ThemeMode.SUMMER -> R.drawable.season_summer
        ThemeMode.AUTUMN -> R.drawable.season_autumn
        else -> null
    }
    if (resourceId != null) {
        val bmp = remember(resourceId) {
            runCatching { BitmapFactory.decodeResource(context.resources, resourceId) }.getOrNull()
        }
        if (bmp != null) Image(bmp.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
        else Box(modifier.background(Color(0xFF202431)))
        return
    }
    val encoded = when (mode) {
        ThemeMode.WINTER -> SeasonWinter.DATA
        ThemeMode.SPRING -> SeasonSpring.DATA
        else -> null
    }
    val bmp = remember(mode) {
        encoded?.let {
            runCatching {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Color(0xFF202431)))
}

@Composable
private fun MonthPanel(
    month: YearMonth,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    accent: Color,
    modifier: Modifier
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF3131820))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN").forEach {
                    Text(text = it, color = Color(0xFFD5D4DB), fontSize = 7.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { column ->
                        val number = week * 7 + column - offset + 1
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (number in 1..month.lengthOfMonth()) {
                                val day = month.atDay(number)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(day) }) {
                                    Box(Modifier.size(27.dp).clip(CircleShape).background(if (day == selected) accent else Color.Transparent), contentAlignment = Alignment.Center) {
                                        Text(text = "$number", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        events.filter { it.date == day }.take(3).forEachIndexed { index, _ ->
                                            Box(Modifier.size(3.5.dp).clip(CircleShape).background(listOf(Color(0xFF16A8FF), Color(0xFFFF3D9A), Color(0xFF08DEA0), Color(0xFFFF9000))[index % 4]))
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
}

@Composable
private fun DayPanel(
    date: LocalDate,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    p: SeasonPalette,
    modifier: Modifier,
    onAdd: () -> Unit
) {
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF3131820))) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
                val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))
                Text(text = "$dayName ${date.dayOfMonth} $monthName", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (events.isEmpty()) Text(text = "Inget planerat", color = Color.White.copy(alpha = .55f), fontSize = 9.sp)
                events.take(5).forEachIndexed { index, event ->
                    val member = members.find { it.id == event.memberId }
                    val eventColor = member?.let { Color(it.colorArgb.toInt()) } ?: listOf(Color(0xFF16A8FF), Color(0xFF08DEA0), Color(0xFFFF3D9A), Color(0xFFFF9000))[index % 4]
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(eventColor))
                        Spacer(Modifier.width(7.dp))
                        Text(text = event.time, color = Color.White.copy(alpha = .9f), fontSize = 9.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(text = event.title, color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(text = quote(p.mode), color = p.accent, fontSize = 14.sp, lineHeight = 16.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Cursive, modifier = Modifier.padding(bottom = 43.dp))
            }
            FloatingActionButton(onClick = onAdd, containerColor = Color(0xFF9C35FF), contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(48.dp)) {
                Icon(Icons.Default.Add, "Lägg till", modifier = Modifier.size(29.dp))
            }
        }
    }
}

private fun quote(mode: ThemeMode) = when (mode) {
    ThemeMode.WINTER -> "Kalla dagar,\nvarma stunder ♡"
    ThemeMode.SPRING -> "Nya dagar,\nnya möjligheter ♡"
    ThemeMode.SUMMER -> "Sommar,\nmer tillsammans ♡"
    ThemeMode.AUTUMN -> "Hösten\nsamlar oss ♡"
    else -> "Tillsammans ♡"
}
