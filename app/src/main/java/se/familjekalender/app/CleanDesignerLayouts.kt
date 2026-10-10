package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Genuine alternate Clean layouts. Current and Nordic intentionally bypass this renderer. */
internal fun isDesignedCleanTheme(theme: CleanVisualTheme): Boolean =
    theme in selectableCleanVisualThemes &&
        theme != CleanVisualTheme.CURRENT &&
        theme != CleanVisualTheme.NORDIC_DAY_PLANNER

private fun designerMemberColor(event: SyncEvent, members: Map<String, SyncMember>, fallback: Color): Color =
    members[event.memberId]?.let { Color(it.colorArgb.toInt()) }
        ?: if (event.memberId == ALL_FAMILY_MEMBER_ID) Color(0xFFE9B44D) else fallback

private fun designerTitle(event: SyncEvent): String =
    event.title.replace(Regex("""\s*[·•]\s*\d{1,2}:\d{2}\s*[–-]\s*\d{1,2}:\d{2}"""), "")
        .trim().trim('·', ' ')

private fun designerTime(event: SyncEvent): String =
    if (event.time.isBlank()) "Hela dagen"
    else event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" } ?: event.time

private fun themeIsSquare(theme: CleanVisualTheme) =
    theme == CleanVisualTheme.EDITORIAL_PLANNER ||
        theme == CleanVisualTheme.RETRO_DIGITAL ||
        theme == CleanVisualTheme.NEON_PULSE

@Composable
internal fun DesignedCleanCalendarScreen(
    theme: CleanVisualTheme,
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    selectedEvents: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    weekCount: Int,
    conflictCount: Int,
    reminderCount: Int,
    weather: CleanWeatherSnapshot?,
    weatherLoading: Boolean,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
    onEventClick: (SyncEvent) -> Unit,
    onShowAll: () -> Unit,
    onToday: () -> Unit,
    onWeek: () -> Unit,
    onConflicts: () -> Unit,
    onReminders: () -> Unit,
    onWeather: () -> Unit,
    onFamily: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val header: @Composable () -> Unit = {
        DesignerHeader(theme, selectedDate, locale, onSearch, onAdd, onFamily)
    }
    val stats: @Composable () -> Unit = {
        DesignerStats(theme, selectedEvents.size, weekCount, conflictCount, reminderCount,
            onToday, onWeek, onConflicts, onReminders)
    }
    val calendar: @Composable () -> Unit = {
        DesignerMonth(theme, month, selectedDate, today, locale, eventsByDate, memberById,
            onSelectDate, onMonthChange)
    }
    val agenda: @Composable () -> Unit = {
        DesignerAgenda(theme, selectedDate, selectedEvents, locale, memberById,
            onEventClick, onShowAll)
    }
    val hero: @Composable () -> Unit = {
        DesignerDateHero(theme, selectedDate, locale, selectedEvents.size, onToday)
    }
    val weatherChip: @Composable () -> Unit = {
        DesignerWeather(theme, weather, weatherLoading, onWeather)
    }
    val weekly: @Composable () -> Unit = {
        DesignerWeekRibbon(theme, selectedDate, today, eventsByDate, locale, onSelectDate)
    }
    val family: @Composable () -> Unit = {
        DesignerFamilyRibbon(theme, memberById, onFamily)
    }

    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(spec.backgroundTop, spec.backgroundBottom))
    )) {
        CleanThemeBackdrop(theme, Modifier.fillMaxSize())
        DesignerTexture(theme)
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = when (theme) {
                        CleanVisualTheme.EDITORIAL_PLANNER -> 21.dp
                        CleanVisualTheme.NEON_PULSE, CleanVisualTheme.RETRO_DIGITAL -> 12.dp
                        else -> 15.dp
                    },
                    vertical = 9.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Each visual system has a deliberately distinct composition and information hierarchy.
            when (theme) {
                CleanVisualTheme.PURE_CALENDAR -> {
                    header()
                    calendar()
                    stats()
                    agenda()
                    weatherChip()
                }
                CleanVisualTheme.MIDNIGHT_GOLD -> {
                    header()
                    hero()
                    calendar()
                    agenda()
                    stats()
                    weatherChip()
                }
                CleanVisualTheme.PASTEL_FLOW -> {
                    header()
                    family()
                    stats()
                    calendar()
                    agenda()
                    weatherChip()
                }
                CleanVisualTheme.CRYSTAL_GLASS -> {
                    header()
                    hero()
                    weatherChip()
                    calendar()
                    agenda()
                    stats()
                }
                CleanVisualTheme.EDITORIAL_PLANNER -> {
                    header()
                    hero()
                    calendar()
                    agenda()
                    stats()
                    weatherChip()
                }
                CleanVisualTheme.NEON_PULSE -> {
                    header()
                    stats()
                    calendar()
                    agenda()
                    weatherChip()
                }
                CleanVisualTheme.EARTH_SAGE -> {
                    header()
                    weekly()
                    calendar()
                    agenda()
                    stats()
                    weatherChip()
                }
                CleanVisualTheme.RETRO_DIGITAL -> {
                    header()
                    stats()
                    calendar()
                    weatherChip()
                    agenda()
                }
                CleanVisualTheme.FOREST_PRO -> {
                    header()
                    weekly()
                    agenda()
                    calendar()
                    stats()
                    weatherChip()
                }
                CleanVisualTheme.FAMILY_SPECTRUM -> {
                    header()
                    family()
                    weekly()
                    agenda()
                    calendar()
                    stats()
                    weatherChip()
                }
                else -> Unit // The original Clean and Nordic layouts are never routed here.
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DesignerTexture(theme: CleanVisualTheme) {
    val spec = cleanThemeSpec(theme)
    Canvas(Modifier.fillMaxSize()) {
        when (theme) {
            CleanVisualTheme.NEON_PULSE -> {
                val step = 38.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(spec.accent.copy(alpha = .045f), Offset(x, 0f),
                        Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(spec.accent.copy(alpha = .045f), Offset(0f, y),
                        Offset(size.width, y), 1f)
                    y += step
                }
            }
            CleanVisualTheme.RETRO_DIGITAL -> {
                var y = 0f
                while (y < size.height) {
                    drawLine(spec.secondary.copy(alpha = .035f), Offset(0f, y),
                        Offset(size.width, y), 1f)
                    y += 7.dp.toPx()
                }
            }
            CleanVisualTheme.EDITORIAL_PLANNER -> {
                val y = size.height * .08f
                drawLine(spec.accent.copy(alpha = .33f), Offset(0f, y),
                    Offset(size.width, y), 1.dp.toPx())
            }
            CleanVisualTheme.MIDNIGHT_GOLD -> {
                drawLine(spec.accent.copy(alpha = .35f), Offset(0f, 0f),
                    Offset(size.width, 0f), 2.dp.toPx())
            }
            CleanVisualTheme.FOREST_PRO -> {
                drawCircle(spec.accent.copy(alpha = .035f), size.width * .65f,
                    Offset(size.width * 1.02f, size.height * .78f))
            }
            else -> Unit
        }
    }
}

@Composable
private fun DesignerIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    theme: CleanVisualTheme,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val square = themeIsSquare(theme) || theme == CleanVisualTheme.PURE_CALENDAR
    val shape = if (square) RoundedCornerShape(8.dp) else CircleShape
    Box(
        Modifier.size(if (filled) 42.dp else 36.dp)
            .background(if (filled) spec.accent else spec.panelTop.copy(alpha = .80f), shape)
            .border(1.dp, if (filled) spec.accentStrong else spec.border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description,
            tint = if (filled) {
                if (isLightCleanTheme(theme) && theme != CleanVisualTheme.EDITORIAL_PLANNER) Color.White
                else spec.selectedText
            } else spec.text,
            modifier = Modifier.size(if (filled) 23.dp else 19.dp))
    }
}

@Composable
private fun DesignerHeader(
    theme: CleanVisualTheme,
    selectedDate: LocalDate,
    locale: Locale,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onFamily: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val tiny = selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    val title = when (theme) {
        CleanVisualTheme.PURE_CALENDAR -> "Kalender"
        CleanVisualTheme.MIDNIGHT_GOLD -> "THE FAMILY EDITION"
        CleanVisualTheme.PASTEL_FLOW -> "Vår familj ✿"
        CleanVisualTheme.CRYSTAL_GLASS -> "Crystal."
        CleanVisualTheme.EDITORIAL_PLANNER -> "Familjejournal"
        CleanVisualTheme.NEON_PULSE -> "FAMILY // OS"
        CleanVisualTheme.EARTH_SAGE -> "Vardagsro"
        CleanVisualTheme.RETRO_DIGITAL -> "FAMILY.EXE"
        CleanVisualTheme.FOREST_PRO -> "FAMILY / PLANNER"
        CleanVisualTheme.FAMILY_SPECTRUM -> "Vår kalender"
        else -> "Familjekalender"
    }
    val subtitle = when (theme) {
        CleanVisualTheme.PURE_CALENDAR -> "ÖVERSIKT"
        CleanVisualTheme.MIDNIGHT_GOLD -> "EST. TOGETHER • EVERY DAY"
        CleanVisualTheme.PASTEL_FLOW -> "Små stunder. Stora minnen."
        CleanVisualTheme.CRYSTAL_GLASS -> "Planera tillsammans"
        CleanVisualTheme.EDITORIAL_PLANNER -> "UTGÅVA  /  ${selectedDate.year}"
        CleanVisualTheme.NEON_PULSE -> "SYSTEM ONLINE  /  $tiny"
        CleanVisualTheme.EARTH_SAGE -> "Dagar att dela"
        CleanVisualTheme.RETRO_DIGITAL -> "READY_  >  $tiny"
        CleanVisualTheme.FOREST_PRO -> "VECKOPLANERING   •   $tiny"
        CleanVisualTheme.FAMILY_SPECTRUM -> "Allas planer, på samma plats"
        else -> ""
    }
    val centered = theme == CleanVisualTheme.MIDNIGHT_GOLD
    Column {
        if (theme == CleanVisualTheme.MIDNIGHT_GOLD) {
            Spacer(Modifier.height(2.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(spec.accent.copy(alpha = .6f)))
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment =
                if (centered) Alignment.CenterHorizontally else Alignment.Start) {
                Text(title,
                    color = spec.text,
                    fontSize = when (theme) {
                        CleanVisualTheme.EDITORIAL_PLANNER -> 32.sp
                        CleanVisualTheme.MIDNIGHT_GOLD -> 18.sp
                        CleanVisualTheme.RETRO_DIGITAL, CleanVisualTheme.NEON_PULSE -> 23.sp
                        else -> 27.sp
                    },
                    letterSpacing = when (theme) {
                        CleanVisualTheme.NEON_PULSE, CleanVisualTheme.RETRO_DIGITAL -> 1.3.sp
                        CleanVisualTheme.MIDNIGHT_GOLD -> 2.3.sp
                        else -> 0.sp
                    },
                    fontFamily = if (theme == CleanVisualTheme.RETRO_DIGITAL ||
                        theme == CleanVisualTheme.NEON_PULSE) FontFamily.Monospace
                        else spec.titleFont,
                    fontWeight = spec.titleWeight,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = if (theme == CleanVisualTheme.NEON_PULSE)
                    spec.accentStrong else spec.muted,
                    fontSize = 9.sp,
                    letterSpacing = .8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(5.dp))
            DesignerIconAction(Icons.Default.Search, "Sök", theme, onClick = onSearch)
            Spacer(Modifier.width(6.dp))
            DesignerIconAction(Icons.Default.Add, "Lägg till", theme, filled = true, onClick = onAdd)
        }
        if (theme == CleanVisualTheme.EDITORIAL_PLANNER ||
            theme == CleanVisualTheme.RETRO_DIGITAL ||
            theme == CleanVisualTheme.NEON_PULSE) {
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(
                if (theme == CleanVisualTheme.EDITORIAL_PLANNER) 2.dp else 1.dp
            ).background(spec.accent.copy(alpha = .68f)))
        }
        if (theme == CleanVisualTheme.FAMILY_SPECTRUM) {
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(spec.accent, spec.info, spec.secondary, spec.warning).forEach {
                    Box(Modifier.weight(1f).height(4.dp).background(it, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

@Composable
private fun DesignerDateHero(
    theme: CleanVisualTheme,
    date: LocalDate,
    locale: Locale,
    count: Int,
    onClick: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val isEditorial = theme == CleanVisualTheme.EDITORIAL_PLANNER
    val shape = when (theme) {
        CleanVisualTheme.EDITORIAL_PLANNER -> RoundedCornerShape(0.dp)
        CleanVisualTheme.NEON_PULSE -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(spec.cardRadius)
    }
    Surface(
        color = if (isEditorial) Color.Transparent else spec.panelTop,
        shape = shape,
        border = if (isEditorial) null else BorderStroke(1.dp, spec.border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = if (isEditorial) 0.dp else 16.dp,
                vertical = if (isEditorial) 3.dp else 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(date.dayOfMonth.toString(),
                color = if (theme == CleanVisualTheme.CRYSTAL_GLASS) spec.accentStrong else spec.text,
                fontFamily = if (isEditorial) FontFamily.Serif else spec.titleFont,
                fontWeight = FontWeight.Bold,
                fontSize = if (isEditorial) 72.sp else 48.sp,
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { it.uppercase(locale) },
                    fontFamily = spec.titleFont,
                    color = spec.text,
                    fontSize = if (isEditorial) 20.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                    color = spec.muted, fontSize = 12.sp)
                Text("$count aktiviteter  ·  Visa dagen",
                    color = spec.accentStrong, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DesignerFamilyRibbon(
    theme: CleanVisualTheme,
    members: Map<String, SyncMember>,
    onFamily: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val people = members.values.filter { it.id != ALL_FAMILY_MEMBER_ID }.take(5)
    if (people.isEmpty()) return
    Surface(
        color = spec.panelTop,
        shape = RoundedCornerShape(if (theme == CleanVisualTheme.FAMILY_SPECTRUM) 18.dp else 25.dp),
        border = BorderStroke(1.dp, spec.border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onFamily)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.People, contentDescription = null,
                    tint = spec.accentStrong, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("FAMILJEN", color = spec.muted, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                people.forEach { member ->
                    val accent = Color(member.colorArgb.toInt())
                    Column(
                        Modifier.weight(1f).clickable(onClick = onFamily),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(27.dp).background(accent.copy(alpha = .23f), CircleShape)
                            .border(2.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                            Text(member.name.take(1).uppercase(), color = spec.text,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(member.name, color = spec.muted, fontSize = 9.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignerWeekRibbon(
    theme: CleanVisualTheme,
    date: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val shape = if (theme == CleanVisualTheme.FOREST_PRO) RoundedCornerShape(8.dp)
        else RoundedCornerShape(18.dp)
    Surface(color = spec.panelTop, shape = shape,
        border = BorderStroke(1.dp, spec.border),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VECKA ${date.get(WeekFields.ISO.weekOfWeekBasedYear())}",
                    color = spec.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(if (theme == CleanVisualTheme.EARTH_SAGE) "DINA DAGAR"
                    else "ÖVERSIKT", color = spec.muted, fontSize = 9.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { index ->
                    val day = start.plusDays(index.toLong())
                    val selected = day == date
                    val count = eventsByDate[day].orEmpty().size
                    val bg = if (selected) spec.accent else spec.panelMid
                    Column(
                        Modifier.weight(1f)
                            .background(bg, RoundedCornerShape(if (theme == CleanVisualTheme.FOREST_PRO) 5.dp else 12.dp))
                            .clickable { onSelect(day) }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                            .take(2).uppercase(locale),
                            color = if (selected) spec.selectedText else spec.muted,
                            fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(day.dayOfMonth.toString(),
                            color = if (selected) spec.selectedText else spec.text,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.size(4.dp).background(
                            if (count > 0) (if (selected) spec.selectedText else spec.accentStrong)
                            else Color.Transparent, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignerStats(
    theme: CleanVisualTheme,
    todayCount: Int,
    weekCount: Int,
    conflictCount: Int,
    reminderCount: Int,
    onToday: () -> Unit,
    onWeek: () -> Unit,
    onConflicts: () -> Unit,
    onReminders: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val items = listOf(
        Triple("IDAG", todayCount, onToday),
        Triple("VECKA", weekCount, onWeek),
        Triple("KROCK", conflictCount, onConflicts),
        Triple("PÅMINN", reminderCount, onReminders),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEachIndexed { index, (label, count, action) ->
            val tint = when (index) {
                0 -> spec.accentStrong
                1 -> spec.secondary
                2 -> if (count > 0) spec.warning else spec.accentStrong
                else -> spec.info
            }
            val squared = themeIsSquare(theme) || theme == CleanVisualTheme.PURE_CALENDAR
            val shape = if (squared) RoundedCornerShape(5.dp)
                else RoundedCornerShape(if (theme == CleanVisualTheme.PASTEL_FLOW) 20.dp else 13.dp)
            val bg = when (theme) {
                CleanVisualTheme.PURE_CALENDAR, CleanVisualTheme.EDITORIAL_PLANNER ->
                    Color.Transparent
                CleanVisualTheme.PASTEL_FLOW -> tint.copy(alpha = .13f)
                CleanVisualTheme.NEON_PULSE -> spec.panelBottom
                CleanVisualTheme.FAMILY_SPECTRUM -> tint.copy(alpha = .12f)
                else -> spec.panelTop
            }
            Column(
                Modifier.weight(1f)
                    .background(bg, shape)
                    .border(1.dp, if (theme == CleanVisualTheme.EDITORIAL_PLANNER) Color.Transparent
                        else tint.copy(alpha = .20f), shape)
                    .clickable(onClick = action)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$count", color = if (theme == CleanVisualTheme.NEON_PULSE) tint else spec.text,
                    fontFamily = if (theme == CleanVisualTheme.RETRO_DIGITAL ||
                        theme == CleanVisualTheme.NEON_PULSE) FontFamily.Monospace else spec.titleFont,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.height(2.dp))
                Text(label, color = if (theme == CleanVisualTheme.PASTEL_FLOW) tint else spec.muted,
                    fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (theme == CleanVisualTheme.EDITORIAL_PLANNER) {
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth(.7f).height(1.dp).background(spec.text))
                }
            }
        }
    }
}

@Composable
private fun DesignerMonth(
    theme: CleanVisualTheme,
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    members: Map<String, SyncMember>,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val shape = when (theme) {
        CleanVisualTheme.PURE_CALENDAR, CleanVisualTheme.EDITORIAL_PLANNER -> RoundedCornerShape(3.dp)
        CleanVisualTheme.NEON_PULSE, CleanVisualTheme.RETRO_DIGITAL -> RoundedCornerShape(7.dp)
        CleanVisualTheme.PASTEL_FLOW, CleanVisualTheme.CRYSTAL_GLASS -> RoundedCornerShape(25.dp)
        else -> RoundedCornerShape(spec.calendarRadius)
    }
    val background = when (theme) {
        CleanVisualTheme.EDITORIAL_PLANNER -> Color.Transparent
        CleanVisualTheme.CRYSTAL_GLASS -> spec.panelTop.copy(alpha = .72f)
        else -> spec.panelTop
    }
    Surface(
        color = background,
        shape = shape,
        border = BorderStroke(
            if (theme == CleanVisualTheme.NEON_PULSE) 1.5.dp else 1.dp,
            if (theme == CleanVisualTheme.NEON_PULSE) spec.accent.copy(alpha = .65f) else spec.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val monthLabel = month.month.getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { it.uppercase(locale) }
                val label = when (theme) {
                    CleanVisualTheme.RETRO_DIGITAL -> "[ ${month.monthValue.toString().padStart(2,'0')}.${month.year} ]"
                    CleanVisualTheme.NEON_PULSE -> "${monthLabel.uppercase(locale)} // ${month.year}"
                    CleanVisualTheme.EDITORIAL_PLANNER -> monthLabel
                    CleanVisualTheme.MIDNIGHT_GOLD -> monthLabel.uppercase(locale)
                    else -> "$monthLabel ${month.year}"
                }
                Text(label, modifier = Modifier.weight(1f),
                    color = spec.text,
                    fontSize = when (theme) {
                        CleanVisualTheme.EDITORIAL_PLANNER -> 27.sp
                        CleanVisualTheme.RETRO_DIGITAL, CleanVisualTheme.NEON_PULSE -> 17.sp
                        else -> 20.sp
                    },
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (theme == CleanVisualTheme.RETRO_DIGITAL ||
                        theme == CleanVisualTheme.NEON_PULSE) FontFamily.Monospace
                        else spec.titleFont,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                DesignerMonthControl(theme, Icons.Default.ChevronLeft, "Föregående månad") { onMonthChange(-1) }
                Spacer(Modifier.width(3.dp))
                DesignerMonthControl(theme, Icons.Default.ChevronRight, "Nästa månad") { onMonthChange(1) }
            }
            if (theme == CleanVisualTheme.EDITORIAL_PLANNER || theme == CleanVisualTheme.MIDNIGHT_GOLD) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(spec.accent.copy(alpha = .5f)))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("V", modifier = Modifier.width(18.dp), textAlign = TextAlign.Center,
                    fontSize = 9.sp, color = spec.muted)
                listOf("M", "T", "O", "T", "F", "L", "S").forEach { label ->
                    Text(label, modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = spec.muted)
                }
            }
            Spacer(Modifier.height(4.dp))
            val first = month.atDay(1)
            val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
            repeat(6) { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val firstInWeek = start.plusWeeks(row.toLong())
                    Text(
                        firstInWeek.get(WeekFields.ISO.weekOfWeekBasedYear()).toString(),
                        modifier = Modifier.width(18.dp), textAlign = TextAlign.Center,
                        fontSize = 9.sp, color = spec.muted.copy(alpha = .8f),
                    )
                    repeat(7) { column ->
                        val date = firstInWeek.plusDays(column.toLong())
                        DesignerDayCell(
                            theme = theme,
                            date = date,
                            month = month,
                            selected = date == selectedDate,
                            today = date == today,
                            dayEvents = eventsByDate[date].orEmpty(),
                            members = members,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (theme == CleanVisualTheme.NEON_PULSE ||
                theme == CleanVisualTheme.RETRO_DIGITAL) {
                Spacer(Modifier.height(5.dp))
                Text(if (theme == CleanVisualTheme.NEON_PULSE)
                    "STATUS  ●  ACTIVE  //  SWIPE TO PLAN"
                else "> SELECT_DATE  •  SYSTEM READY",
                    color = spec.accentStrong, fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun DesignerMonthControl(
    theme: CleanVisualTheme,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    Box(
        Modifier.size(30.dp)
            .background(if (theme == CleanVisualTheme.EDITORIAL_PLANNER) Color.Transparent else spec.panelMid,
                RoundedCornerShape(if (themeIsSquare(theme)) 4.dp else 15.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = spec.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DesignerDayCell(
    theme: CleanVisualTheme,
    date: LocalDate,
    month: YearMonth,
    selected: Boolean,
    today: Boolean,
    dayEvents: List<SyncEvent>,
    members: Map<String, SyncMember>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = cleanThemeSpec(theme)
    val activeMonth = YearMonth.from(date) == month
    val shape = when (theme) {
        CleanVisualTheme.PURE_CALENDAR -> CircleShape
        CleanVisualTheme.MIDNIGHT_GOLD -> RoundedCornerShape(4.dp)
        CleanVisualTheme.PASTEL_FLOW -> RoundedCornerShape(15.dp)
        CleanVisualTheme.CRYSTAL_GLASS -> RoundedCornerShape(12.dp)
        CleanVisualTheme.EDITORIAL_PLANNER -> RoundedCornerShape(1.dp)
        CleanVisualTheme.NEON_PULSE -> RoundedCornerShape(3.dp)
        CleanVisualTheme.EARTH_SAGE -> RoundedCornerShape(14.dp)
        CleanVisualTheme.RETRO_DIGITAL -> RoundedCornerShape(0.dp)
        CleanVisualTheme.FOREST_PRO -> RoundedCornerShape(5.dp)
        CleanVisualTheme.FAMILY_SPECTRUM -> RoundedCornerShape(11.dp)
        else -> RoundedCornerShape(8.dp)
    }
    val familyTint = dayEvents.firstOrNull()?.let { designerMemberColor(it, members, spec.accentStrong) }
        ?: spec.accent
    val background = when {
        selected -> when (theme) {
            CleanVisualTheme.EDITORIAL_PLANNER -> spec.text
            CleanVisualTheme.RETRO_DIGITAL -> spec.accent
            CleanVisualTheme.NEON_PULSE -> spec.accent.copy(alpha = .22f)
            else -> spec.accent
        }
        !activeMonth -> Color.Transparent
        theme == CleanVisualTheme.PURE_CALENDAR ||
            theme == CleanVisualTheme.EDITORIAL_PLANNER -> Color.Transparent
        theme == CleanVisualTheme.FAMILY_SPECTRUM && dayEvents.isNotEmpty() ->
            familyTint.copy(alpha = .15f)
        theme == CleanVisualTheme.PASTEL_FLOW -> when (date.dayOfWeek.value % 3) {
            0 -> spec.secondary.copy(alpha = .15f)
            1 -> spec.info.copy(alpha = .12f)
            else -> spec.accent.copy(alpha = .10f)
        }
        theme == CleanVisualTheme.CRYSTAL_GLASS -> spec.panelMid.copy(alpha = .62f)
        theme == CleanVisualTheme.NEON_PULSE ||
            theme == CleanVisualTheme.RETRO_DIGITAL -> spec.panelBottom
        else -> spec.dayTop
    }
    val borderColor = when {
        selected -> spec.selectedBorder
        today -> spec.accentStrong
        theme == CleanVisualTheme.RETRO_DIGITAL ||
            theme == CleanVisualTheme.NEON_PULSE -> spec.border.copy(alpha = .65f)
        theme == CleanVisualTheme.MIDNIGHT_GOLD -> spec.border.copy(alpha = .58f)
        else -> Color.Transparent
    }
    val selectedText = if (theme == CleanVisualTheme.NEON_PULSE) spec.accentStrong
        else if (theme == CleanVisualTheme.EDITORIAL_PLANNER) Color.White
        else spec.selectedText
    Box(
        modifier.padding(horizontal = 1.dp, vertical = 1.dp).height(
            when (theme) {
                CleanVisualTheme.FOREST_PRO -> 42.dp
                CleanVisualTheme.EDITORIAL_PLANNER -> 44.dp
                CleanVisualTheme.PURE_CALENDAR -> 46.dp
                else -> 49.dp
            }
        ).background(background, shape)
            .border(if (borderColor == Color.Transparent) 0.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(),
                color = when {
                    selected -> selectedText
                    !activeMonth -> spec.muted.copy(alpha = .40f)
                    today -> spec.accentStrong
                    else -> spec.text
                },
                fontFamily = if (theme == CleanVisualTheme.RETRO_DIGITAL ||
                    theme == CleanVisualTheme.NEON_PULSE) FontFamily.Monospace
                    else FontFamily.Default,
                fontSize = if (selected) 16.sp else 14.sp,
                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(7.dp)) {
                dayEvents.filterNot { it.title.trimStart().startsWith("🌈") }
                    .take(3).forEach { event ->
                        Box(Modifier.size(4.dp).background(
                            if (selected) selectedText.copy(alpha = .88f)
                            else designerMemberColor(event, members, spec.accentStrong),
                            CircleShape))
                    }
            }
        }
        if (dayEvents.any { it.title.trimStart().startsWith("🌈") ||
            it.source.contains("birthday", ignoreCase = true) ||
            it.title.contains("födelsedag", ignoreCase = true) }) {
            Text("🌈", modifier = Modifier.align(Alignment.TopEnd).padding(end = 0.dp),
                fontSize = 8.sp, lineHeight = 8.sp)
        }
        if (theme == CleanVisualTheme.FAMILY_SPECTRUM && dayEvents.isNotEmpty() && !selected) {
            Box(Modifier.fillMaxWidth(.6f).height(2.dp)
                .align(Alignment.TopCenter).background(familyTint, RoundedCornerShape(2.dp)))
        }
        if (theme == CleanVisualTheme.EDITORIAL_PLANNER && today && !selected) {
            Box(Modifier.fillMaxWidth(.55f).height(1.dp)
                .align(Alignment.BottomCenter).background(spec.text))
        }
    }
}

@Composable
private fun DesignerAgenda(
    theme: CleanVisualTheme,
    selectedDate: LocalDate,
    events: List<SyncEvent>,
    locale: Locale,
    members: Map<String, SyncMember>,
    onEventClick: (SyncEvent) -> Unit,
    onShowAll: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val noFrame = theme == CleanVisualTheme.PURE_CALENDAR ||
        theme == CleanVisualTheme.EDITORIAL_PLANNER
    val shape = if (themeIsSquare(theme)) RoundedCornerShape(5.dp)
        else RoundedCornerShape(spec.cardRadius)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (noFrame) Color.Transparent else spec.panelTop,
        shape = shape,
        border = if (noFrame) null else BorderStroke(1.dp, spec.border),
    ) {
        Column(Modifier.fillMaxWidth().padding(
            horizontal = if (noFrame) 2.dp else 12.dp,
            vertical = if (noFrame) 3.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (theme) {
                    CleanVisualTheme.MIDNIGHT_GOLD -> "DAGENS PROGRAM"
                    CleanVisualTheme.PASTEL_FLOW -> "Det händer idag ♡"
                    CleanVisualTheme.CRYSTAL_GLASS -> "Dagens ögonblick"
                    CleanVisualTheme.EDITORIAL_PLANNER -> "Dagens anteckningar"
                    CleanVisualTheme.NEON_PULSE -> "> TODAY.LOG"
                    CleanVisualTheme.EARTH_SAGE -> "Dagens planer"
                    CleanVisualTheme.RETRO_DIGITAL -> "EVENTS.TXT"
                    CleanVisualTheme.FOREST_PRO -> "DAGENS SCHEMA"
                    CleanVisualTheme.FAMILY_SPECTRUM -> "Familjens aktiviteter"
                    else -> "Dagens aktiviteter"
                }, modifier = Modifier.weight(1f), color = spec.text,
                    fontFamily = if (theme == CleanVisualTheme.EDITORIAL_PLANNER) FontFamily.Serif
                        else if (theme == CleanVisualTheme.RETRO_DIGITAL ||
                            theme == CleanVisualTheme.NEON_PULSE) FontFamily.Monospace
                        else spec.titleFont,
                    fontSize = if (theme == CleanVisualTheme.EDITORIAL_PLANNER) 20.sp else 15.sp,
                    fontWeight = FontWeight.Bold)
                Text("${events.size} st", color = spec.muted, fontSize = 10.sp,
                    modifier = Modifier.clickable(onClick = onShowAll))
            }
            Spacer(Modifier.height(7.dp))
            if (events.isEmpty()) {
                Text("Inget planerat den här dagen",
                    color = spec.muted, fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onShowAll).padding(vertical = 5.dp))
            }
            events.take(3).forEachIndexed { index, event ->
                val color = designerMemberColor(event, members, spec.secondary)
                val memberName = members[event.memberId]?.name ?: "Familjen"
                val eventShape = when (theme) {
                    CleanVisualTheme.PASTEL_FLOW, CleanVisualTheme.FAMILY_SPECTRUM ->
                        RoundedCornerShape(13.dp)
                    CleanVisualTheme.NEON_PULSE, CleanVisualTheme.RETRO_DIGITAL ->
                        RoundedCornerShape(3.dp)
                    else -> RoundedCornerShape(7.dp)
                }
                val itemColor = when (theme) {
                    CleanVisualTheme.FAMILY_SPECTRUM, CleanVisualTheme.PASTEL_FLOW ->
                        color.copy(alpha = .12f)
                    CleanVisualTheme.CRYSTAL_GLASS -> spec.panelMid.copy(alpha = .7f)
                    CleanVisualTheme.NEON_PULSE, CleanVisualTheme.RETRO_DIGITAL ->
                        spec.panelBottom
                    else -> Color.Transparent
                }
                if (index > 0 && noFrame) {
                    Box(Modifier.fillMaxWidth().height(1.dp)
                        .background(spec.border.copy(alpha = .65f)))
                }
                Row(
                    Modifier.fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(itemColor, eventShape)
                        .clickable { onEventClick(event) }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (theme == CleanVisualTheme.EDITORIAL_PLANNER ||
                        theme == CleanVisualTheme.MIDNIGHT_GOLD ||
                        theme == CleanVisualTheme.NEON_PULSE ||
                        theme == CleanVisualTheme.FOREST_PRO) {
                        Box(Modifier.width(3.dp).height(28.dp)
                            .background(color, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(designerTitle(event), color = spec.text, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(memberName, color = spec.muted, fontSize = 9.sp, maxLines = 1)
                    }
                    Text(designerTime(event),
                        color = if (theme == CleanVisualTheme.NEON_PULSE) spec.accentStrong
                            else spec.muted,
                        fontSize = 10.sp,
                        fontFamily = if (theme == CleanVisualTheme.RETRO_DIGITAL ||
                            theme == CleanVisualTheme.NEON_PULSE) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 1)
                }
            }
            if (events.size > 3) {
                Text("Visa alla ${events.size} aktiviteter  →",
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onShowAll)
                        .padding(vertical = 6.dp),
                    color = spec.accentStrong, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun DesignerWeather(
    theme: CleanVisualTheme,
    weather: CleanWeatherSnapshot?,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val spec = cleanThemeSpec(theme)
    val flat = theme == CleanVisualTheme.PURE_CALENDAR ||
        theme == CleanVisualTheme.EDITORIAL_PLANNER
    val shape = RoundedCornerShape(if (themeIsSquare(theme)) 4.dp else spec.cardRadius)
    Row(
        Modifier.fillMaxWidth()
            .background(if (flat) Color.Transparent else spec.panelTop, shape)
            .border(1.dp, if (flat) Color.Transparent else spec.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null,
            tint = spec.accentStrong, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("Väder", color = spec.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(weather?.description ?: if (loading) "Hämtar väder…" else "Tryck för prognos",
                color = spec.muted, fontSize = 10.sp, maxLines = 1)
        }
        Text(weather?.let { "${it.temperatureC}°" } ?: "—°",
            color = spec.text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}

/** A true mini calendar preview rather than a row of color swatches. */
@Composable
internal fun CleanThemeMiniPreview(theme: CleanVisualTheme, modifier: Modifier = Modifier) {
    val spec = cleanThemeSpec(theme)
    val square = themeIsSquare(theme)
    val shape = RoundedCornerShape(if (square) 3.dp else 10.dp)
    Column(
        modifier.fillMaxWidth()
            .background(spec.backgroundTop, shape)
            .border(1.dp, spec.border, shape)
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when (theme) {
                CleanVisualTheme.NEON_PULSE -> "FAMILY // OS"
                CleanVisualTheme.RETRO_DIGITAL -> "FAMILY.EXE"
                CleanVisualTheme.EDITORIAL_PLANNER -> "Journal"
                CleanVisualTheme.MIDNIGHT_GOLD -> "THE FAMILY EDITION"
                CleanVisualTheme.CRYSTAL_GLASS -> "Crystal."
                CleanVisualTheme.EARTH_SAGE -> "Vardagsro"
                CleanVisualTheme.FOREST_PRO -> "FAMILY / PLANNER"
                CleanVisualTheme.FAMILY_SPECTRUM -> "Vår kalender"
                CleanVisualTheme.PASTEL_FLOW -> "Vår familj ✿"
                else -> "Kalender"
            }, modifier = Modifier.weight(1f), color = spec.text, maxLines = 1,
                fontSize = 8.sp, fontFamily = spec.titleFont, fontWeight = FontWeight.Bold)
            Box(Modifier.size(9.dp).background(spec.accent, CircleShape))
        }
        Text("OKTOBER  2026", color = spec.muted, fontSize = 6.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val active = index == 3
                val dot = if (index == 1) spec.secondary else spec.accentStrong
                Column(Modifier.weight(1f)
                    .background(if (active) spec.accent else spec.panelTop.copy(alpha = .8f),
                        RoundedCornerShape(if (square) 1.dp else 4.dp))
                    .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((12 + index).toString(),
                        color = if (active) spec.selectedText else spec.text,
                        fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(3.dp).background(
                        if (active) spec.selectedText else dot, CircleShape))
                }
            }
        }
        if (theme == CleanVisualTheme.FAMILY_SPECTRUM ||
            theme == CleanVisualTheme.PASTEL_FLOW) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(spec.secondary, spec.warning, spec.info, spec.accent).forEach { color ->
                    Box(Modifier.weight(1f).height(3.dp)
                        .background(color, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}
