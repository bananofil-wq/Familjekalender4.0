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
 * Bespoke compact wood layout matching the selected reference, independent of the
 * card-based designer renderer. Functional actions and live family data are preserved.
 */
@Composable
internal fun OakReferenceCalendarScreen(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    selectedEvents: List<SyncEvent>,
    membersById: Map<String, SyncMember>,
    weather: CleanWeatherSnapshot?,
    weatherLoading: Boolean,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onFamily: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
    onOpenEvent: (SyncEvent) -> Unit,
    onShowAll: () -> Unit,
    onWeather: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        OakPhotographicBackground(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            OakHeader(month, locale, onSearch, onAdd, onFamily)
            OakFamilyPortraitStrip(membersById, onFamily)
            OakPaperMonth(
                month, selectedDate, today, locale, eventsByDate, membersById,
                onSelect, onMonthChange,
            )
            OakActivityLedger(
                selectedDate, selectedEvents, membersById, locale, onOpenEvent, onShowAll,
            )
            OakWeatherPanel(weather, weatherLoading, onWeather)
            Spacer(Modifier.height(6.dp))
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
                            .border(1.2.dp, Color(0xFFD9AF78), CircleShape)
                    } else if (isToday) {
                        Modifier.border(.9.dp, OakBrown.copy(alpha = .60f), CircleShape)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
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
