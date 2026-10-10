package se.familjekalender.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.sin


/**
 * The 2026 photographic oak reference: dark raised stat/agenda boards,
 * light carved calendar, brass hardware and a separate sunset weather panel.
 * All dates, counts and activities are live; no screenshot is used as fake UI.
 */
@Composable
internal fun OakReferenceCalendarScreen(
    month: YearMonth, selectedDate: LocalDate, today: LocalDate, locale: Locale,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    selectedEvents: List<SyncEvent>, membersById: Map<String, SyncMember>,
    weather: CleanWeatherSnapshot?, weatherLoading: Boolean,
    weekCount: Int, conflictCount: Int, reminderCount: Int,
    onToday: () -> Unit, onWeek: () -> Unit,
    onConflicts: () -> Unit, onReminders: () -> Unit,
    onSearch: () -> Unit, onAdd: () -> Unit, onFamily: () -> Unit,
    onSelect: (LocalDate) -> Unit, onMonthChange: (Long) -> Unit,
    onOpenEvent: (SyncEvent) -> Unit, onShowAll: () -> Unit,
    onWeather: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // A broad, uninterrupted wood backdrop beneath individually carved surfaces.
        OakPhotographicBackground(Modifier.fillMaxSize())
        OakLeafDecoration(
            Modifier.align(Alignment.TopStart).width(52.dp).height(109.dp)
        )
        Box(
            Modifier.fillMaxWidth().height(78.dp)
                .background(Brush.verticalGradient(listOf(Color(0xE9E7B781), Color(0x44D9A575))))
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PremiumWoodHeader(onSearch, onAdd, onFamily)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PremiumWoodStat("Idag", selectedEvents.size, if (selectedEvents.size == 1) "aktivitet" else "aktiviteter",
                    Icons.Default.CalendarMonth, onToday, Modifier.weight(1f))
                PremiumWoodStat("Veckan", weekCount, "aktiviteter",
                    Icons.Default.BarChart, onWeek, Modifier.weight(1f))
                PremiumWoodStat("Krockar", conflictCount, if (conflictCount == 0) "lugnt" else "krockar",
                    Icons.Default.WarningAmber, onConflicts, Modifier.weight(1f))
                PremiumWoodStat("Påminn.", reminderCount, "påminnelser",
                    Icons.Default.Notifications, onReminders, Modifier.weight(1f))
            }
            PremiumWoodCalendar(month, selectedDate, today, locale,
                eventsByDate, membersById, onSelect, onMonthChange)
            Row(
                Modifier.fillMaxWidth().height(182.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                PremiumWoodAgenda(
                    selectedDate, selectedEvents, membersById, onOpenEvent, onShowAll,
                    Modifier.weight(1.82f).fillMaxHeight(),
                )
                PremiumWoodWeather(
                    weather, weatherLoading, onWeather,
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

private val WoodGold = Color(0xFFF5CE8F)
private val WoodDark = Color(0xFF34190C)
private val WoodBorder = Color(0xFFBD814C)
private val WoodIvory = Color(0xFFFCE5BF)

@Composable
private fun PremiumWoodHeader(
    onSearch: () -> Unit, onAdd: () -> Unit, onFamily: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .padding(start = 29.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clickable(onClick = onFamily),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Familjekalendern ❧", color = Color(0xFF2D160B),
                fontSize = 23.sp, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "T I L L S A M M A N S   V A R J E   D A G",
                color = Color(0xFF5B301B), fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
        PremiumWoodButton("⌕", onSearch, 40)
        Spacer(Modifier.width(9.dp))
        PremiumWoodButton("+", onAdd, 43)
    }
}

@Composable
private fun PremiumWoodButton(
    symbol: String, onClick: () -> Unit, diameter: Int = 33,
) {
    val shape = CircleShape
    Box(
        Modifier.size(diameter.dp).shadow(5.dp, shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFFB87E48), Color(0xFF603419), Color(0xFF32180B))),
                shape,
            )
            .border(1.5.dp, WoodGold, shape)
            .clickable(onClick = onClick).padding(3.dp)
            .border(.65.dp, Color(0xFFEBC48E).copy(alpha = .78f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol == "⌕") {
            Icon(
                Icons.Default.Search, contentDescription = "Sök",
                tint = WoodGold, modifier = Modifier.size(22.dp),
            )
        } else {
            Text(
                symbol, color = WoodGold, fontSize = 30.sp,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PremiumWoodStat(
    label: String, value: Int, caption: String, icon: ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier.height(100.dp).shadow(5.dp, shape)
            .clickable(onClick = onClick),
        shape = shape, color = Color(0xFF53301A),
        border = BorderStroke(1.dp, WoodBorder),
    ) {
        Box {
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .83f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x31231609), Color(0x48301C0E), Color(0x7830180C))
                    )
                )
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(25.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFB78553), Color(0xFF4D2710))
                                ), CircleShape
                            )
                            .border(1.dp, Color(0xFFD9A868), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon, contentDescription = label,
                            tint = WoodIvory, modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(3.dp))
                    Text(
                        label, color = WoodIvory, fontFamily = FontFamily.Serif,
                        fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    value.toString(), color = Color(0xFFFFDCA7),
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = 31.sp, lineHeight = 33.sp, maxLines = 1,
                )
                Text(
                    caption, color = Color(0xFFF1D0A7), fontSize = 9.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "›", color = WoodGold, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 15.dp)
            )
        }
    }
}

@Composable
private fun PremiumWoodCalendar(
    month: YearMonth, selected: LocalDate, today: LocalDate, locale: Locale,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    members: Map<String, SyncMember>,
    onSelect: (LocalDate) -> Unit, onChange: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .shadow(8.dp, shape)
            .pointerInput(month) {
                var distance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        distance += amount
                    },
                    onDragEnd = {
                        if (distance < -48.dp.toPx()) onChange(1L)
                        else if (distance > 48.dp.toPx()) onChange(-1L)
                        distance = 0f
                    },
                    onDragCancel = { distance = 0f },
                )
            },
        shape = shape, color = Color(0xFFF1C793),
        border = BorderStroke(2.dp, Color(0xFF8B532E)),
    ) {
        Box {
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .82f, material = OakPhotoMaterial.PALE)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x67FFE8BF), Color(0x45EEC499), Color(0x48A66D38))
                    )
                )
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(45.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar { it.uppercase(locale) } + " " + month.year,
                        modifier = Modifier.weight(1f),
                        color = WoodDark, fontFamily = FontFamily.Serif,
                        fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    PremiumWoodButton("‹", { onChange(-1L) }, 36)
                    Spacer(Modifier.width(7.dp))
                    PremiumWoodButton("›", { onChange(1L) }, 36)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().height(23.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "V", Modifier.width(26.dp),
                        color = Color(0xFF775039), fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                    listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN").forEach { day ->
                        Text(
                            day, Modifier.weight(1f), textAlign = TextAlign.Center,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF533320),
                        )
                    }
                }
                val first = month.atDay(1)
                val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
                val rows = maxOf(6, (first.lengthOfMonth() + first.dayOfWeek.value + 5) / 7)
                val wf = WeekFields.ISO
                repeat(rows) { week ->
                    Row(
                        Modifier.fillMaxWidth().height(43.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val weekStart = start.plusDays((week * 7).toLong())
                        Text(
                            weekStart.get(wf.weekOfWeekBasedYear()).toString(),
                            Modifier.width(26.dp), textAlign = TextAlign.Center,
                            color = Color(0xFF775039), fontSize = 10.sp,
                        )
                        repeat(7) { day ->
                            val date = weekStart.plusDays(day.toLong())
                            val active = date == selected
                            val inMonth = YearMonth.from(date) == month
                            val dayShape = RoundedCornerShape(9.dp)
                            val events = eventsByDate[date].orEmpty()
                            Box(
                                Modifier.weight(1f).height(40.dp)
                                    .shadow(if (active) 3.dp else 1.dp, dayShape)
                                    .background(
                                        Brush.verticalGradient(
                                            if (active)
                                                listOf(Color(0xFF8F5129), Color(0xFF321608))
                                            else
                                                listOf(Color(0xFFFFE9C7), Color(0xFFDDB285))
                                        ), dayShape
                                    )
                                    .border(
                                        if (active) 1.6.dp else .65.dp,
                                        if (active) WoodGold else Color(0x66B77B44),
                                        dayShape,
                                    )
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                // Subtle real grain on each carved date, not a flat rounded rectangle.
                                if (!active) OakPhotographicSurface(
                                    Modifier.matchParentSize().clip(dayShape),
                                    opacity = .15f, material = OakPhotoMaterial.PALE,
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(
                                        top = if (events.any(::oakBirthdayEvent)) 8.dp else 0.dp
                                    ),
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color = if (active) WoodIvory
                                            else if (inMonth) WoodDark
                                            else Color(0x88725138),
                                        fontSize = 17.sp, fontFamily = FontFamily.Serif,
                                        fontWeight = if (date == today || active)
                                            FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        events.filterNot { oakBirthdayEvent(it) }
                                            .distinctBy { it.memberId }
                                            .take(4).forEach { event ->
                                                val accent = members[event.memberId]?.let {
                                                    Color(it.colorArgb.toInt())
                                                } ?: Color(0xFFFFC956)
                                                Box(
                                                    Modifier.size(5.dp).background(accent, CircleShape)
                                                )
                                            }
                                    }
                                }
                                if (events.any(::oakBirthdayEvent)) {
                                    Text(
                                        "🌈", fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.TopCenter)
                                            .padding(top = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (week < rows - 1) Spacer(Modifier.height(3.dp))
                }
            }
            listOf(
                Alignment.TopStart, Alignment.TopEnd,
                Alignment.BottomStart, Alignment.BottomEnd,
            ).forEach { pos ->
                Box(
                    Modifier.align(pos).padding(7.dp).size(7.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFFFE3AE), Color(0xFFB08355), Color(0xFF5F3215))
                            ), CircleShape
                        )
                        .border(.6.dp, Color(0xFF6B3C1F), CircleShape)
                )
            }
        }
    }
}

private fun oakBirthdayEvent(event: SyncEvent): Boolean =
    event.title.trimStart().startsWith("🌈") ||
        event.title.contains("födelsedag", ignoreCase = true) ||
        event.source.contains("birthday", ignoreCase = true)

@Composable
private fun PremiumWoodAgenda(
    date: LocalDate, events: List<SyncEvent>, members: Map<String, SyncMember>,
    onEvent: (SyncEvent) -> Unit, onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.shadow(6.dp, shape),
        shape = shape, color = Color(0xFF3D2011),
        border = BorderStroke(1.dp, WoodBorder),
    ) {
        Box {
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .74f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x44402010), Color(0x9930170B))
                    )
                )
            )
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onShowAll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Idag", modifier = Modifier.weight(1f),
                        color = WoodIvory, fontFamily = FontFamily.Serif,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv", "SE")))
                            .replaceFirstChar { it.uppercase() } + "   ›",
                        color = WoodIvory, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (events.isEmpty()) {
                    Text("Inga aktiviteter den här dagen", color = WoodIvory, fontSize = 11.sp)
                }
                events.take(3).forEach { event ->
                    val accent = members[event.memberId]?.let {
                        Color(it.colorArgb.toInt())
                    } ?: Color(0xFFC59B66)
                    val title = event.title.lowercase(Locale("sv", "SE"))
                    val icon = when {
                        "katt" in title || "hund" in title -> "🐾"
                        "tvätt" in title -> "▣"
                        "fotboll" in title -> "⚽"
                        "träning" in title -> "●"
                        else -> "•"
                    }
                    Row(
                        Modifier.fillMaxWidth().height(43.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x956B4229), Color(0xAA361A0D))
                                ), RoundedCornerShape(8.dp)
                            )
                            .border(.65.dp, Color(0x886F4529), RoundedCornerShape(8.dp))
                            .clickable { onEvent(event) }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            event.time.ifBlank { "Heldag" }, color = WoodIvory,
                            fontSize = 10.sp, modifier = Modifier.width(39.dp),
                            maxLines = 1,
                        )
                        Box(
                            Modifier.size(24.dp)
                                .background(accent.copy(alpha = .8f), CircleShape)
                                .border(.8.dp, WoodGold.copy(alpha = .7f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(icon, color = Color.White, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            event.title, color = Color(0xFFFFE7C6),
                            fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        val owner = members[event.memberId]
                        if (owner != null) {
                            Box(
                                Modifier.size(19.dp)
                                    .background(accent.copy(alpha = .65f), CircleShape)
                                    .border(.6.dp, Color(0xFFDDB889), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    owner.name.take(1).uppercase(Locale("sv", "SE")),
                                    color = Color.White, fontSize = 9.sp,
                                )
                            }
                        }
                        Text(" ›", color = WoodGold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumWoodWeather(
    weather: CleanWeatherSnapshot?, loading: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.shadow(6.dp, shape).clickable(onClick = onClick),
        shape = shape, color = Color(0xFF90542A),
        border = BorderStroke(1.dp, WoodBorder),
    ) {
        Box {
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .83f, material = OakPhotoMaterial.AMBER)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x44934D1B), Color(0x447C3C17), Color(0xDD2D1D13))
                    )
                )
            )
            // The shoreline is a photograph cropped from the reference. Weather remains live.
            OakSunsetDecoration(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().fillMaxHeight(.41f).clip(shape)
            )
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x00D88945), Color(0x552B1C13)),
                        startY = size.height * .64f, endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height * .64f),
                    size = Size(size.width, size.height * .36f),
                )
            }
            Column(
                Modifier.fillMaxSize().padding(9.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Väder", modifier = Modifier.weight(1f),
                        color = WoodIvory, fontSize = 19.sp,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    )
                    Text("›", color = WoodGold, fontSize = 19.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(oakWeatherEmoji(weather?.weatherCode), fontSize = 27.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        weather?.temperatureC?.toString()?.plus("°") ?: "—°",
                        color = WoodIvory, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, fontSize = 35.sp,
                    )
                }
                Text(
                    weather?.description ?: if (loading) "Hämtar vädret…" else "Visa vädret",
                    color = Color(0xFFF7E0C4), fontSize = 10.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun oakWeatherEmoji(code: Int?): String =
    when (code) {
        0, 1 -> "☀️"
        2, 3 -> "⛅"
        45, 48 -> "🌫️"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "☁️"
    }

