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

private fun seasonMode(month: Int): ThemeMode = when (month) {
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
    val seasonPalette = paletteFor(mode)
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    val visibleDate = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)
    val dayEvents = events.filter { it.date == visibleDate }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF111018))) {
        val sceneHeight = maxHeight * 0.43f
        val cardsTop = maxHeight * 0.30f
        val cardsHeight = maxHeight * 0.57f

        SeasonalPhoto(mode, Modifier.fillMaxWidth().height(sceneHeight))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)))

        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👥", fontSize = 15.sp)
                Spacer(Modifier.width(5.dp))
                Text("Familjekalendern", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("⌕", color = Color.White.copy(alpha = .9f), fontSize = 21.sp)
                Spacer(Modifier.width(12.dp))
                Text("⚙", color = Color.White.copy(alpha = .9f), fontSize = 18.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        month = month.minusMonths(1)
                        onSelect(month.atDay(1))
                    },
                    modifier = Modifier.size(30.dp)
                ) { Icon(Icons.Default.ChevronLeft, null, tint = Color.White) }

                Text(
                    "$monthName ${month.year}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        month = month.plusMonths(1)
                        onSelect(month.atDay(1))
                    },
                    modifier = Modifier.size(30.dp)
                ) { Icon(Icons.Default.ChevronRight, null, tint = Color.White) }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .offset(y = cardsTop)
                .height(cardsHeight),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            MonthPanel(
                month = month,
                selected = visibleDate,
                onSelect = onSelect,
                events = events,
                accent = seasonPalette.accent,
                modifier = Modifier.weight(1.55f)
            )
            DayPanel(
                date = visibleDate,
                events = dayEvents,
                members = members,
                palette = seasonPalette,
                modifier = Modifier.weight(.9f),
                onAdd = onAdd
            )
        }
    }
}

@Composable
private fun SeasonalPhoto(mode: ThemeMode, modifier: Modifier) {
    val sceneBitmap = remember(mode) {
        runCatching {
            val bytes = Base64.decode(SeasonAtlas.DATA, Base64.DEFAULT)
            val atlas = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val index = when (mode) {
                ThemeMode.WINTER -> 0
                ThemeMode.SPRING -> 1
                ThemeMode.SUMMER -> 2
                ThemeMode.AUTUMN -> 3
                else -> 0
            }
            val sectionHeight = atlas.height / 4
            val trimTop = (sectionHeight * 0.28f).toInt()
            val y = index * sectionHeight + trimTop
            val h = sectionHeight - trimTop
            Bitmap.createBitmap(atlas, 0, y, atlas.width, h)
        }.getOrNull()
    }

    if (sceneBitmap != null) {
        Image(
            bitmap = sceneBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = modifier
        )
    } else {
        Box(modifier.background(Color(0xFF25212D)))
    }
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
    Card(
        modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD9181921))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN").forEach {
                    Text(it, color = Color(0xFFC6C1CC), fontSize = 6.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(3.dp))

            repeat(6) { week ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { dow ->
                        val day = week * 7 + dow - offset + 1
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                val isSelected = date == selected
                                val dayEvents = events.filter { it.date == date }.take(3)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(date) }) {
                                    Box(
                                        Modifier.size(25.dp).clip(CircleShape).background(if (isSelected) accent else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            day.toString(),
                                            color = if (isSelected) Color(0xFF17131C) else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayEvents.forEachIndexed { i, _ ->
                                            Box(
                                                Modifier.size(3.dp).clip(CircleShape).background(
                                                    listOf(Color(0xFF55B7FF), Color(0xFFFF73B4), Color(0xFF72E5A0))[i % 3]
                                                )
                                            )
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
    palette: SeasonPalette,
    modifier: Modifier,
    onAdd: () -> Unit
) {
    Card(
        modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDE1A1920))
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(9.dp)) {
                Text(
                    "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                if (events.isEmpty()) {
                    Text("Inget planerat", color = Color.White.copy(alpha = .55f), fontSize = 8.5.sp)
                }

                events.take(5).forEachIndexed { i, event ->
                    val member = members.find { it.id == event.memberId }
                    val color = member?.let { Color(it.colorArgb.toInt()) }
                        ?: listOf(Color(0xFF55B7FF), Color(0xFFFF73B4), Color(0xFF72E5A0))[i % 3]
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(4.dp))
                        Text(event.time, color = Color.White.copy(alpha = .82f), fontSize = 8.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(event.title, color = Color.White, fontSize = 8.sp, maxLines = 1)
                    }
                }

                Spacer(Modifier.weight(1f))
                Text(
                    quote(palette.mode),
                    color = palette.accent,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Cursive,
                    modifier = Modifier.padding(bottom = 41.dp)
                )
            }

            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color(0xFF9C5CFF),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(42.dp)
            ) { Icon(Icons.Default.Add, "Lägg till") }
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
