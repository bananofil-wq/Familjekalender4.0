package se.familjekalender.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun MinimalCalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val locale = remember { Locale("sv", "SE") }
    val motionEnabled = appMotionEnabled()
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val memberById = remember(members) { members.associateBy { it.id } }
    val selectedEvents = remember(events, selectedDate) {
        events.filter { it.date == selectedDate }.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
    }

    LaunchedEffect(selectedDate) {
        val selectedMonth = YearMonth.from(selectedDate)
        if (selectedMonth != month) month = selectedMonth
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Familjeappen",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Mer tid tillsammans",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = LuxurySurfaceElevated,
                border = BorderStroke(1.dp, LuxuryOutlineSoft)
            ) {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Inställningar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Surface(
            color = LuxurySurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, LuxuryOutlineSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val next = month.minusMonths(1)
                    month = next
                    onSelect(next.atDay(1))
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Föregående månad", tint = LuxuryTextMuted)
                }
                Text(
                    month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase(locale) } + " ${month.year}",
                    color = LuxuryText,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = {
                    val next = month.plusMonths(1)
                    month = next
                    onSelect(next.atDay(1))
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Nästa månad", tint = LuxuryTextMuted)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        AnimatedContent(
            targetState = month,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val inMs = motionDuration(LuxuryMotion.Standard, motionEnabled)
                val outMs = motionDuration(LuxuryMotion.Fast, motionEnabled)
                if (targetState > initialState) {
                    (slideInHorizontally(tween(inMs)) { it / 10 } + fadeIn(tween(inMs))) togetherWith
                        (slideOutHorizontally(tween(outMs)) { -it / 12 } + fadeOut(tween(outMs)))
                } else {
                    (slideInHorizontally(tween(inMs)) { -it / 10 } + fadeIn(tween(inMs))) togetherWith
                        (slideOutHorizontally(tween(outMs)) { it / 12 } + fadeOut(tween(outMs)))
                }
            },
            label = "clean-month"
        ) { visibleMonth ->
            PremiumMonthGrid(
                month = visibleMonth,
                selectedDate = selectedDate,
                events = events,
                members = memberById,
                motionEnabled = motionEnabled,
                onSelect = { date ->
                    month = YearMonth.from(date)
                    onSelect(date)
                }
            )
        }

        Spacer(Modifier.height(22.dp))

        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                (fadeIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled))) +
                    slideInVertically(tween(motionDuration(LuxuryMotion.Standard, motionEnabled))) { it / 18 }) togetherWith
                    (fadeOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled))) +
                        slideOutVertically(tween(motionDuration(LuxuryMotion.Fast, motionEnabled))) { -it / 20 })
            },
            label = "clean-agenda"
        ) { date ->
            PremiumAgenda(
                date = date,
                events = if (date == selectedDate) selectedEvents else events.filter { it.date == date }.sortedBy { it.time },
                memberById = memberById,
                locale = locale,
                motionEnabled = motionEnabled
            )
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Text("Lägg till aktivitet", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PremiumMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<SyncEvent>,
    members: Map<String, SyncMember>,
    motionEnabled: Boolean,
    onSelect: (LocalDate) -> Unit
) {
    val weekdays = listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN")
    val first = month.atDay(1)
    val leadingDays = first.dayOfWeek.value - 1
    val gridStart = first.minusDays(leadingDays.toLong())
    val rowCount = ((leadingDays + month.lengthOfMonth() + 6) / 7).coerceIn(4, 6)
    val eventsByDate = remember(events) { events.groupBy { it.date } }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    day,
                    color = LuxuryTextMuted.copy(alpha = .82f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        repeat(rowCount) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val date = gridStart.plusDays((row * 7 + column).toLong())
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selectedDate
                    val dayEvents = eventsByDate[date].orEmpty()
                    val birthday = dayEvents.any { it.title.trim().startsWith("🌈") }
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else .93f,
                        animationSpec = tween(motionDuration(LuxuryMotion.Fast, motionEnabled)),
                        label = "premium-date-scale"
                    )

                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clickable { onSelect(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .18f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .62f), CircleShape)
                            )
                        }
                        Column(
                            modifier = if (isSelected) Modifier.size(48.dp) else Modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (isSelected) Arrangement.Center else Arrangement.Top
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = when {
                                    isSelected -> LuxuryText
                                    inMonth -> LuxuryText.copy(alpha = .96f)
                                    else -> LuxuryTextMuted.copy(alpha = .42f)
                                },
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(if (isSelected) 2.dp else 5.dp))
                            when {
                                birthday -> Box(
                                    Modifier.size(if (isSelected) 13.dp else 17.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BirthdayRainbowIcon(Modifier.size(if (isSelected) 13.dp else 17.dp))
                                }
                                dayEvents.isNotEmpty() -> Row(
                                    horizontalArrangement = Arrangement.spacedBy(if (isSelected) 2.dp else 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    dayEvents.take(3).forEach { event ->
                                        val dotColor = members[event.memberId]?.let { Color(it.colorArgb.toInt()) }
                                            ?: MaterialTheme.colorScheme.primary
                                        Box(Modifier.size(if (isSelected) 4.dp else 5.dp).clip(CircleShape).background(dotColor))
                                    }
                                }
                                else -> Spacer(Modifier.height(if (isSelected) 4.dp else 5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumAgenda(
    date: LocalDate,
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    locale: Locale,
    motionEnabled: Boolean
) {
    val headerFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val header = date.format(headerFormatter).replaceFirstChar { it.uppercase(locale) }
    val groups = remember(events) {
        events.groupBy { it.memberId }
            .entries
            .sortedBy { group -> group.value.minOfOrNull { it.time } ?: "" }
    }
    var expandedGroups by remember(date) { mutableStateOf(emptySet<String>()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurfaceElevated),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, LuxuryOutlineSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(motionDuration(LuxuryMotion.Standard, motionEnabled)))
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(header, color = LuxuryText, style = MaterialTheme.typography.titleLarge)
                Surface(color = LuxurySurfaceHigh, shape = RoundedCornerShape(99.dp)) {
                    Text(
                        if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                        color = LuxuryTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (events.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Inga aktiviteter den här dagen", color = LuxuryTextMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text("En lugn dag i familjens kalender.", color = LuxuryTextMuted.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        val memberId = group.key
                        val personEvents = group.value.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
                        val member = memberId?.let { memberById[it] }
                        val memberName = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> "Hela familjen"
                            member != null -> member.name
                            else -> "Familjen"
                        }
                        val accent = when {
                            memberId == ALL_FAMILY_MEMBER_ID -> Color(0xFFFFD75E)
                            member != null -> Color(member.colorArgb.toInt())
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val groupKey = memberId ?: "__unassigned__"
                        val expanded = groupKey in expandedGroups

                        Surface(
                            color = LuxurySurface,
                            shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedGroups = if (expanded) expandedGroups - groupKey else expandedGroups + groupKey
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(99.dp)).background(accent))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(memberName, color = LuxuryText, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (personEvents.size == 1) "1 aktivitet" else "${personEvents.size} aktiviteter",
                                        color = LuxuryTextMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    if (expanded) "Dölj" else "Visa",
                                    color = accent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                personEvents.forEach { event -> PremiumAgendaRow(event, member) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumAgendaRow(event: SyncEvent, member: SyncMember?) {
    val accent = member?.let { Color(it.colorArgb.toInt()) } ?: MaterialTheme.colorScheme.primary
    val memberName = when {
        event.memberId == ALL_FAMILY_MEMBER_ID -> "Familjen"
        member != null -> member.name
        else -> "Familjen"
    }
    val title = event.title.removePrefix("🌈").removePrefix("🧺").trim()
    val timeText = event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" } ?: event.time

    Surface(
        color = LuxurySurface,
        shape = RoundedCornerShape(17.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(3.dp).height(44.dp).clip(RoundedCornerShape(99.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(72.dp)) {
                Text(timeText, color = LuxuryTextMuted, style = MaterialTheme.typography.labelMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = LuxuryText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(6.dp))
                    Text(memberName, color = LuxuryTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
