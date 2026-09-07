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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    val monthName = month.month
        .getDisplayName(TextStyle.FULL, Locale("sv", "SE"))
        .replaceFirstChar { it.uppercase() }
    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07090F))
    ) {
        // On tall Android phones the approved composition needs to use the full body.
        // Keep the scenic hero dominant while preventing the calendar cards from becoming huge.
        val heroH = maxHeight * 0.47f
        val panelTop = heroH - 10.dp
        val panelsH = maxHeight * 0.50f

        SeasonalPhoto(
            mode = mode,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroH)
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = Color(0xFF9C4DFF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Familjekalendern",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Sök",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(15.dp))
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Inställningar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        month = month.minusMonths(1)
                        onSelect(month.atDay(1))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, null, tint = Color.White)
                }

                Text(
                    text = "$monthName ${month.year}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        month = month.plusMonths(1)
                        onSelect(month.atDay(1))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .offset(y = panelTop)
                .height(panelsH),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonthPanel(
                month = month,
                selected = date,
                onSelect = onSelect,
                events = events,
                accent = p.accent,
                modifier = Modifier.weight(1.78f)
            )
            DayPanel(
                date = date,
                events = events.filter { it.date == date },
                members = members,
                p = p,
                modifier = Modifier.weight(1f),
                onAdd = onAdd
            )
        }
    }
}

@Composable
private fun SeasonalPhoto(mode: ThemeMode, modifier: Modifier) {
    when (mode) {
        ThemeMode.SUMMER -> {
            Image(
                painter = painterResource(R.drawable.season_summer),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
            return
        }
        ThemeMode.AUTUMN -> {
            Image(
                painter = painterResource(R.drawable.season_autumn),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
            return
        }
        else -> Unit
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

    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
    } else {
        Box(modifier.background(Color(0xFF202431)))
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
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF0151820))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN").forEach { label ->
                    Text(
                        text = label,
                        color = Color(0xFFC9C8D0),
                        fontSize = 6.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(5.dp))

            repeat(6) { week ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    repeat(7) { weekday ->
                        val number = week * 7 + weekday - offset + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (number in 1..month.lengthOfMonth()) {
                                val day = month.atDay(number)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onSelect(day) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(23.dp)
                                            .clip(CircleShape)
                                            .background(if (day == selected) accent else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$number",
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = if (day == selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        events
                                            .filter { it.date == day }
                                            .take(3)
                                            .forEachIndexed { index, _ ->
                                                val dotColor = listOf(
                                                    Color(0xFF45AEFF),
                                                    Color(0xFFFF5AA7),
                                                    Color(0xFF62DB91),
                                                    Color(0xFFFF9747)
                                                )[index % 4]
                                                Box(
                                                    Modifier
                                                        .size(3.dp)
                                                        .clip(CircleShape)
                                                        .background(dotColor)
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
    p: SeasonPalette,
    modifier: Modifier,
    onAdd: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF0151820))
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(11.dp)
            ) {
                Text(
                    text = "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))}",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(9.dp))

                if (events.isEmpty()) {
                    Text(
                        text = "Inget planerat",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 8.sp
                    )
                }

                events.take(5).forEachIndexed { index, event ->
                    val member = members.find { it.id == event.memberId }
                    val eventColor = member?.let { Color(it.colorArgb.toInt()) } ?: listOf(
                        Color(0xFF45AEFF),
                        Color(0xFFFF5AA7),
                        Color(0xFF62DB91),
                        Color(0xFFFF9747)
                    )[index % 4]

                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(eventColor)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = event.time,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 7.sp
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = event.title,
                            color = Color.White,
                            fontSize = 7.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                Text(
                    text = quote(p.mode),
                    color = p.accent,
                    fontSize = 10.5.sp,
                    lineHeight = 12.5.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = FontFamily.Cursive,
                    modifier = Modifier.padding(bottom = 31.dp)
                )
            }

            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color(0xFF9C4DFF),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Lägg till",
                    modifier = Modifier.size(22.dp)
                )
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
