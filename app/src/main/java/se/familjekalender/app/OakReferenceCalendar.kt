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
                    "▦", onToday, Modifier.weight(1f))
                PremiumWoodStat("Veckan", weekCount, "aktiviteter",
                    "▥", onWeek, Modifier.weight(1f))
                PremiumWoodStat("Krockar", conflictCount, if (conflictCount == 0) "lugnt" else "krockar",
                    "⚠", onConflicts, Modifier.weight(1f))
                PremiumWoodStat("Påminn.", reminderCount, "påminnelser",
                    "♧", onReminders, Modifier.weight(1f))
            }
            PremiumWoodCalendar(month, selectedDate, today, locale,
                eventsByDate, membersById, onSelect, onMonthChange)
            Row(
                Modifier.fillMaxWidth().height(158.dp),
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
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).clickable(onClick = onFamily),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Familjekalendern ❧", color = Color(0xFF2D160B),
                fontSize = 25.sp, fontFamily = FontFamily.Serif,
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
        Text(symbol, color = WoodGold,
            fontSize = if (symbol == "+") 31.sp else 25.sp,
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PremiumWoodStat(
    label: String, value: Int, caption: String, icon: String,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier.height(87.dp).shadow(5.dp, shape)
            .clickable(onClick = onClick),
        shape = shape, color = Color(0xFF53301A),
        border = BorderStroke(1.dp, WoodBorder),
    ) {
        Box {
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .52f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x5431160A), Color(0x98402012), Color(0xE72A1208))
                    )
                )
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFB78553), Color(0xFF4D2710))
                                ), CircleShape
                            )
                            .border(1.dp, Color(0xFFD9A868), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(icon, color = WoodIvory, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(3.dp))
                    Text(
                        label, color = WoodIvory, fontFamily = FontFamily.Serif,
                        fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(0.dp))
                Text(
                    value.toString(), color = Color(0xFFFFDCA7),
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = 32.sp, maxLines = 1,
                )
                Text(
                    caption, color = Color(0xFFF1D0A7), fontSize = 9.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "›", color = WoodGold, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 11.dp)
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
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .29f)
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
                                // The selected date is a carved dark inset, not a generic purple chip.
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
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
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .34f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x554B230D), Color(0xCB2E160B))
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
                        Modifier.fillMaxWidth().weight(1f)
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
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .31f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x44934D1B), Color(0x447C3C17), Color(0xDD2D1D13))
                    )
                )
            )
            // Golden-hour horizon is decorative; weather figures come from the service.
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0x59FFD092),
                    radius = size.width * .32f,
                    center = Offset(size.width * .74f, size.height * .76f),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x00D88945), Color(0xA52B1C13)),
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

private val OakInk = Color(0xFF302114)
private val OakMutedInk = Color(0xFF76563C)
private val OakPaper = Color(0xFFF8EAD2)
private val OakBorder = Color(0x6F93673E)
private val OakBrown = Color(0xFF724326)

@Composable
private fun OakHeader(
    month: YearMonth,
    locale: Locale,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onFamily: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(43.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onFamily),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Menu, "Familj", tint = OakInk, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Familjekalendern",
                color = OakInk, fontSize = 19.sp,
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                month.month.getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { it.uppercase(locale) } + " ${month.year}",
                color = OakMutedInk, fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        OakRoundButton(false, onSearch) {
            Icon(Icons.Default.Search, "Sök", tint = OakInk, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(7.dp))
        OakRoundButton(true, onAdd) {
            Icon(Icons.Default.Add, "Lägg till aktivitet", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun OakRoundButton(
    solid: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = CircleShape
    Box(
        modifier = Modifier.size(if (solid) 35.dp else 32.dp)
            .shadow(2.dp, shape)
            .background(if (solid) OakBrown else OakPaper, shape)
            .border(1.dp, if (solid) Color(0xFF51301C) else OakBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun OakFamilyPortraitStrip(
    membersById: Map<String, SyncMember>,
    onFamily: () -> Unit,
) {
    val members = membersById.values.filter { it.id != ALL_FAMILY_MEMBER_ID }.take(5)
    if (members.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OakFamilyAvatar(
            name = "Alla",
            accent = OakBrown,
            onClick = onFamily,
            modifier = Modifier.weight(1f),
        )
        members.forEach { member ->
            OakFamilyAvatar(
                name = member.name,
                accent = Color(member.colorArgb.toInt()),
                onClick = onFamily,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OakFamilyAvatar(
    name: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(36.dp)
                .shadow(1.5.dp, CircleShape)
                .background(Brush.verticalGradient(
                    listOf(accent.copy(alpha = .75f), accent.copy(alpha = .98f))
                ), CircleShape)
                .border(2.dp, Color(0xFFF8E8CA), CircleShape)
                .border(1.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (name == "Alla") {
                Icon(
                    Icons.Default.People,
                    "Hela familjen",
                    tint = Color(0xFFFCEAD0),
                    modifier = Modifier.size(21.dp),
                )
            } else {
                val variant = (name.hashCode() and Int.MAX_VALUE) % 4
                val hair = listOf(
                    Color(0xFF583725), Color(0xFF302924),
                    Color(0xFFB27B42), Color(0xFF6E3E29),
                )[variant]
                val skin = listOf(
                    Color(0xFFF7D3AE), Color(0xFFD9A87F),
                    Color(0xFFF5C99B), Color(0xFFE7B990),
                )[variant]
                Canvas(Modifier.size(32.dp)) {
                    val unit = size.width
                    // Distinct illustrated portraits, not empty generic user silhouettes.
                    drawOval(
                        color = Color(0xFFE7D8BD),
                        topLeft = Offset(unit * .04f, unit * .04f),
                        size = Size(unit * .92f, unit * .92f),
                    )
                    drawOval(
                        color = Color(0xFF395B65).copy(alpha = .85f),
                        topLeft = Offset(unit * .09f, unit * .68f),
                        size = Size(unit * .82f, unit * .43f),
                    )
                    drawOval(
                        color = hair,
                        topLeft = Offset(unit * .19f, unit * .10f),
                        size = Size(unit * .62f, unit * .67f),
                    )
                    drawOval(
                        color = skin,
                        topLeft = Offset(unit * .245f, unit * .235f),
                        size = Size(unit * .51f, unit * .52f),
                    )
                    drawOval(
                        color = hair,
                        topLeft = Offset(unit * .23f, unit * .12f),
                        size = Size(unit * .53f, unit * .22f),
                    )
                    if (variant % 2 == 0) {
                        drawOval(
                            color = hair,
                            topLeft = Offset(unit * .16f, unit * .22f),
                            size = Size(unit * .14f, unit * .56f),
                        )
                    }
                    val eyeY = unit * .49f
                    drawCircle(Color(0xFF312C2C), unit * .026f,
                        center = Offset(unit * .405f, eyeY))
                    drawCircle(Color(0xFF312C2C), unit * .026f,
                        center = Offset(unit * .595f, eyeY))
                    drawLine(
                        color = Color(0xFFB06A61),
                        start = Offset(unit * .43f, unit * .64f),
                        end = Offset(unit * .57f, unit * .64f),
                        strokeWidth = unit * .02f,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            name, color = OakInk, fontSize = 10.sp, maxLines = 1,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OakPaperMonth(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    locale: Locale,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    members: Map<String, SyncMember>,
    onSelect: (LocalDate) -> Unit,
    onChange: (Long) -> Unit,
) {
    val panelShape = RoundedCornerShape(12.dp)
    Surface(
        color = OakPaper,
        shape = panelShape,
        border = BorderStroke(1.dp, OakBorder),
        shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth().pointerInput(month) {
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
    ) {
        Box {
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .10f)
            Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(35.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OakMonthArrow(Icons.Default.ChevronLeft, "Föregående månad") { onChange(-1L) }
                Text(
                    month.month.getDisplayName(TextStyle.FULL, locale)
                        .replaceFirstChar { it.uppercase(locale) } + " ${month.year}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = OakInk, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                )
                OakMonthArrow(Icons.Default.ChevronRight, "Nästa månad") { onChange(1L) }
            }
            Spacer(Modifier.height(3.dp))
            Row(
                Modifier.fillMaxWidth().height(23.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "V",
                    modifier = Modifier.width(17.dp),
                    textAlign = TextAlign.Center,
                    color = OakMutedInk.copy(alpha = .58f),
                    fontSize = 8.sp,
                )
                listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach { weekday ->
                    Text(
                        weekday,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = OakMutedInk,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            val monthStart = month.atDay(1)
            val firstGridDate = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())
            val numberOfDays = ChronoUnit.DAYS.between(firstGridDate, month.atEndOfMonth()).toInt() + 1
            val weekRows = (numberOfDays + 6) / 7
            Crossfade(targetState = month, animationSpec = tween(175),
                label = "oak-month") { displayedMonth ->
                val displayedStart = displayedMonth.atDay(1)
                val gridStart = displayedStart.minusDays((displayedStart.dayOfWeek.value - 1).toLong())
                val displayedDayCount = ChronoUnit.DAYS.between(gridStart, displayedMonth.atEndOfMonth()).toInt() + 1
                val displayedRows = (displayedDayCount + 6) / 7
                Column {
                    repeat(displayedRows) { rowIndex ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val weekStart = gridStart.plusWeeks(rowIndex.toLong())
                            Text(
                                weekStart.get(WeekFields.ISO.weekOfWeekBasedYear()).toString(),
                                color = OakMutedInk.copy(alpha = .65f),
                                fontSize = 8.sp,
                                modifier = Modifier.width(17.dp),
                                textAlign = TextAlign.Center,
                            )
                            repeat(7) { dayOffset ->
                                val date = weekStart.plusDays(dayOffset.toLong())
                                OakPaperDay(
                                    date = date,
                                    selected = date == selected,
                                    isToday = date == today,
                                    inMonth = YearMonth.from(date) == displayedMonth,
                                    events = eventsByDate[date].orEmpty(),
                                    members = members,
                                    onClick = { onSelect(date) },
                                    modifier = Modifier.weight(1f),
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

@Composable
private fun OakMonthArrow(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(31.dp)
            .background(Color(0x99E3C7A0), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(image, label, tint = OakInk, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun OakPaperDay(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    inMonth: Boolean,
    events: List<SyncEvent>,
    members: Map<String, SyncMember>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = when {
        selected -> Color.White
        inMonth -> OakInk
        else -> OakMutedInk.copy(alpha = .46f)
    }
    Box(
        modifier.height(37.dp)
            .border(.35.dp, Color(0x2269482B))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (selected) 31.dp else 29.dp)
                .then(
                    if (selected) {
                        Modifier.shadow(2.dp, CircleShape)
                            .background(OakBrown, CircleShape)
                            .clip(CircleShape)
                            .border(1.2.dp, Color(0xFFD9AF78), CircleShape)
                    } else if (isToday) {
                        Modifier.border(.9.dp, OakBrown.copy(alpha = .60f), CircleShape)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                OakPhotographicSurface(Modifier.matchParentSize(), opacity = .18f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    color = ink, fontSize = if (selected) 14.sp else 12.sp,
                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.height(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    events.filterNot { it.title.trimStart().startsWith("🌈") }
                        .take(3).forEach { event ->
                            val eventColor = members[event.memberId]?.let {
                                Color(it.colorArgb.toInt())
                            } ?: Color(0xFFD7A85E)
                            Box(
                                Modifier.size(3.7.dp)
                                    .background(if (selected) Color(0xFFF6DDC0) else eventColor,
                                        CircleShape)
                            )
                        }
                }
            }
        }
        val birthday = events.any {
            it.title.trimStart().startsWith("🌈") ||
                it.title.contains("födelsedag", ignoreCase = true) ||
                it.source.contains("birthday", ignoreCase = true)
        }
        if (birthday) {
            Text("🌈", fontSize = 7.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp))
        }
    }
}

@Composable
private fun OakActivityLedger(
    date: LocalDate,
    events: List<SyncEvent>,
    members: Map<String, SyncMember>,
    locale: Locale,
    onEvent: (SyncEvent) -> Unit,
    onShowAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                    .replaceFirstChar { it.uppercase(locale) },
                modifier = Modifier.weight(1f),
                color = OakInk, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
            )
            Text(
                if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                color = OakMutedInk, fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onShowAll),
            )
        }
        if (events.isEmpty()) {
            Surface(
                color = Color(0xF4F5DFBE),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onShowAll)
            ) {
                Text(
                    "Inget planerat den här dagen",
                    color = OakMutedInk, fontSize = 12.sp,
                    modifier = Modifier.padding(15.dp),
                )
            }
        }
        events.take(4).forEach { event ->
            val accent = members[event.memberId]?.let { Color(it.colorArgb.toInt()) }
                ?: Color(0xFFD8A55A)
            val isReminder = event.source.contains("reminder", ignoreCase = true) ||
                event.title.startsWith("🔔") || event.title.startsWith("🛎")
            val displayTitle = event.title
                .replace(Regex("""^(?:🔔|🛎️?|⭐|✅)\s*"""), "")
                .replace(Regex("""\s*[·•]\s*\d{1,2}:\d{2}\s*[–-]\s*\d{1,2}:\d{2}"""), "")
                .trim()
            val icon = when {
                isReminder -> "🔔"
                event.title.contains("skola", ignoreCase = true) -> "🏠"
                event.title.contains("fotboll", ignoreCase = true) ||
                    event.title.contains("träning", ignoreCase = true) -> "⚽"
                event.title.contains("mat", ignoreCase = true) ||
                    event.title.contains("middag", ignoreCase = true) -> "🍴"
                event.title.contains("fika", ignoreCase = true) ||
                    event.title.contains("frukost", ignoreCase = true) -> "☕"
                event.title.contains("födelsedag", ignoreCase = true) -> "🌈"
                else -> "📅"
            }
            Surface(
                color = Color(0xF4E7CBA3),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(.7.dp, Color(0x6B956438)),
                shadowElevation = 1.5.dp,
                modifier = Modifier.fillMaxWidth().clickable { onEvent(event) },
            ) {
                Box {
                    OakPhotographicSurface(Modifier.matchParentSize(), opacity = .30f)
                Row(
                    Modifier.fillMaxWidth().height(49.dp)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(4.dp).height(37.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(55.dp)) {
                        Text(
                            event.time.ifBlank { "Heldag" },
                            color = OakInk, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        event.endTime?.takeIf { it.isNotBlank() }?.let { end ->
                            Text(end, color = OakMutedInk, fontSize = 9.sp)
                        }
                    }
                    Text(icon, fontSize = 17.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayTitle,
                            color = OakInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                            else members[event.memberId]?.name ?: "Familjen",
                            color = OakMutedInk, fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                    val person = members[event.memberId]
                    if (person != null && person.id != ALL_FAMILY_MEMBER_ID) {
                        Box(
                            Modifier.size(23.dp).background(accent.copy(alpha = .28f), CircleShape)
                                .border(1.dp, accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(person.name.take(1).uppercase(locale),
                                color = OakInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                }
            }
        }
        if (events.size > 4) {
            Text(
                "Visa alla ${events.size} aktiviteter ›",
                modifier = Modifier.fillMaxWidth().clickable(onClick = onShowAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = OakInk, fontSize = 11.sp, textAlign = TextAlign.End,
                fontWeight = FontWeight.SemiBold,
            )
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

@Composable
private fun OakWeatherPanel(
    weather: CleanWeatherSnapshot?,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = Color(0xF5F3DDBA),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(.8.dp, OakBorder),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Box {
            OakPhotographicSurface(Modifier.matchParentSize(), opacity = .19f)
        Row(
            Modifier.fillMaxWidth().height(
                if (weather?.upcomingDays.isNullOrEmpty()) 61.dp else 72.dp
            )
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(oakWeatherEmoji(weather?.weatherCode), fontSize = 24.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                weather?.let { "${it.temperatureC}°" } ?: "—°",
                color = OakInk, fontSize = 23.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        weather != null -> weather.description
                        loading -> "Hämtar väder…"
                        else -> "Visa vädret"
                    },
                    color = OakInk, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (weather?.upcomingDays.isNullOrEmpty()) "Aktuellt väder" else "Prognos",
                    color = OakMutedInk, fontSize = 9.sp
                )
            }
            if (!weather?.upcomingDays.isNullOrEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    weather!!.upcomingDays.take(3).forEach { day ->
                        Column(
                            Modifier.width(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))
                                    .take(3),
                                color = OakMutedInk, fontSize = 9.sp,
                            )
                            Text(oakWeatherEmoji(day.weatherCode), fontSize = 16.sp)
                            Text(
                                "${day.highC}°", color = OakInk,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            } else {
                Text("›", color = OakMutedInk, fontSize = 22.sp)
            }
        }
        }
    }
}
