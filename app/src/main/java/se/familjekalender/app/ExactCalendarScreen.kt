package se.familjekalender.app

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val mode = palette.mode
    val p = palette
    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF061019))) {
        val heroH = maxWidth * (2f / 3f)
        val panelTop = heroH - 2.dp
        val panelsH = maxHeight * .545f

        SeasonalPhoto(mode, Modifier.fillMaxWidth().height(heroH))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).offset(y = panelTop).height(panelsH),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonthPanel(month, date, onSelect, events, members, p.accent, Modifier.weight(1.78f))
            DayPanel(date, events.filter { it.date == date }, members, p, Modifier.weight(1f), onAdd)
        }
    }
}

@Composable
private fun SeasonalPhoto(mode: ThemeMode, modifier: Modifier) {
    val imageRes = when (mode) {
        ThemeMode.WINTER -> R.drawable.season_winter
        ThemeMode.SPRING -> R.drawable.season_spring
        ThemeMode.SUMMER -> R.drawable.season_summer
        ThemeMode.AUTUMN -> R.drawable.season_autumn
        ThemeMode.CLASSIC, ThemeMode.AUTO -> null
    }

    if (imageRes == null) {
        Box(modifier.background(Color(0xFF061019)))
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = false
                setImageResource(imageRes)
            }
        },
        update = {
            it.scaleType = ImageView.ScaleType.CENTER_CROP
            it.adjustViewBounds = false
            it.setImageResource(imageRes)
        }
    )
}

@Composable
private fun MonthPanel(
    month: YearMonth,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    accent: Color,
    modifier: Modifier
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF3131820))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN").forEach {
                    Text(it, color = Color(0xFFD5D4DB), fontSize = 7.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
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
                                    Box(
                                        Modifier.size(27.dp).clip(CircleShape).background(if (day == selected) accent else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$number", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        events.filter { it.date == day }.take(3).forEach { event ->
                                            val member = members.find { it.id == event.memberId }
                                            val dotColor = member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                                            Box(Modifier.size(3.5.dp).clip(CircleShape).background(dotColor))
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
    var selectedEvent by remember { mutableStateOf<SyncEvent?>(null) }

    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF3131820))) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
                val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))
                Text("$dayName ${date.dayOfMonth} $monthName", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (events.isEmpty()) Text("Inget planerat", color = Color.White.copy(alpha = .55f), fontSize = 9.sp)
                events.take(5).forEach { event ->
                    val member = members.find { it.id == event.memberId }
                    val eventColor = member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedEvent = event }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(eventColor))
                        Spacer(Modifier.width(7.dp))
                        Text(event.time, color = Color.White.copy(alpha = .9f), fontSize = 9.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(event.title, color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    quote(p.mode), color = p.accent, fontSize = 14.sp, lineHeight = 16.sp,
                    fontStyle = FontStyle.Italic, fontFamily = FontFamily.Cursive,
                    modifier = Modifier.padding(bottom = 43.dp)
                )
            }
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color(0xFF9C35FF),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(48.dp)
            ) {
                Icon(Icons.Default.Add, "Lägg till", modifier = Modifier.size(29.dp))
            }
        }
    }

    selectedEvent?.let { event ->
        val member = members.find { it.id == event.memberId }
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text(event.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tid: ${event.time}")
                    Text("Datum: ${event.date}")
                    Text("Berör: ${member?.name ?: "Ingen särskild person"}")
                    if (!member?.role.isNullOrBlank()) Text("Roll: ${member?.role}")
                    Text("Källa: ${if (event.source == "sportadmin") "SportAdmin" else "Manuellt tillagd"}")
                }
            },
            confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text("Stäng") } }
        )
    }
}

private fun quote(mode: ThemeMode) = when (mode) {
    ThemeMode.WINTER -> "Kalla dagar,\nvarma stunder ♡"
    ThemeMode.SPRING -> "Nya dagar,\nnya möjligheter ♡"
    ThemeMode.SUMMER -> "Sommar,\nmer tillsammans ♡"
    ThemeMode.AUTUMN -> "Hösten\nsamlar oss ♡"
    else -> "Tillsammans ♡"
}
