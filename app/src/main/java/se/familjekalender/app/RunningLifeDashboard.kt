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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val LifeBg = Color(0xFF0B0B10)
private val LifeSurface = Color(0xFF17171F)
private val LifeSurfaceRaised = Color(0xFF1D1D26)
private val LifePurple = Color(0xFFA66CFF)
private val LifeMuted = Color(0xFFAAA8B7)
private val LifeDivider = Color(0xFF292933)

private enum class LifeFocus {
    EVERYDAY,
    RUNNING
}

private data class RunningSnapshot(
    val monthKm: Double,
    val monthPasses: Int,
    val latestKm: Double?,
    val nextPlan: SyncEvent?
)

private fun completedRunDistanceKm(event: SyncEvent): Double? {
    val title = event.title
    return when {
        title.startsWith("🏃 RUN|") ->
            title.removePrefix("🏃 RUN|").substringBefore('|').toDoubleOrNull()

        title.startsWith("🏃 Löpning · ") ->
            title.removePrefix("🏃 Löpning · ")
                .substringBefore(" · ")
                .removeSuffix(" km")
                .replace(',', '.')
                .toDoubleOrNull()

        else -> null
    }?.takeIf { it > 0.0 }
}

private fun runningSnapshot(events: List<SyncEvent>): RunningSnapshot {
    val today = LocalDate.now()
    val completed = events.mapNotNull { event ->
        completedRunDistanceKm(event)?.let { distance -> event to distance }
    }
    val monthRuns = completed.filter { (event, _) ->
        event.date.year == today.year && event.date.month == today.month
    }
    val latest = completed
        .filter { (event, _) -> !event.date.isAfter(today) }
        .maxWithOrNull(compareBy<Pair<SyncEvent, Double>> { it.first.date }.thenBy { it.first.time })
    val nextPlan = events.asSequence()
        .filter { it.title.startsWith("🏃 Plan: ") && !it.date.isBefore(today) }
        .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
        .firstOrNull()

    return RunningSnapshot(
        monthKm = monthRuns.sumOf { it.second },
        monthPasses = monthRuns.size,
        latestKm = latest?.second,
        nextPlan = nextPlan
    )
}

@Composable
internal fun RunningLifeDashboard(
    session: FamilySession,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: suspend () -> Unit
) {
    val locale = remember { Locale("sv", "SE") }
    val motionEnabled = appMotionEnabled()
    var focus by rememberSaveable { mutableStateOf(LifeFocus.EVERYDAY) }
    val weekStart = remember(selectedDate) {
        selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    }
    val memberById = remember(members) { members.associateBy { it.id } }
    val runSnapshot = remember(events) { runningSnapshot(events) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LifeBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text("Löpning & vardag", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (focus == LifeFocus.EVERYDAY) "Det viktigaste i veckan, utan brus."
                    else "Träning, utveckling och historik i fokus.",
                    color = LifeMuted,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Inställningar", tint = Color(0xFFD1CFDC))
            }
        }

        Spacer(Modifier.height(16.dp))

        LifeFocusSelector(
            focus = focus,
            onFocusChanged = { focus = it },
            motionEnabled = motionEnabled
        )

        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = focus,
            transitionSpec = {
                val inMs = motionDuration(240, motionEnabled)
                val outMs = motionDuration(180, motionEnabled)
                if (targetState == LifeFocus.RUNNING) {
                    (fadeIn(tween(inMs)) + slideInHorizontally(tween(inMs)) { it / 8 }) togetherWith
                        (fadeOut(tween(outMs)) + slideOutHorizontally(tween(outMs)) { -it / 10 })
                } else {
                    (fadeIn(tween(inMs)) + slideInHorizontally(tween(inMs)) { -it / 8 }) togetherWith
                        (fadeOut(tween(outMs)) + slideOutHorizontally(tween(outMs)) { it / 10 })
                }
            },
            label = "life-focus-content"
        ) { currentFocus ->
            when (currentFocus) {
                LifeFocus.EVERYDAY -> Column {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LifeSurface),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Den här veckan", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    "${weekStart.month.getDisplayName(TextStyle.SHORT, locale)} ${weekStart.year}",
                                    color = LifeMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(7) { index ->
                                    val date = weekStart.plusDays(index.toLong())
                                    val dayEvents = remember(events, date) { events.filter { it.date == date } }
                                    LifeDayCell(
                                        date = date,
                                        selected = date == selectedDate,
                                        hasEvents = dayEvents.isNotEmpty(),
                                        hasRun = dayEvents.any { it.title.startsWith("🏃") },
                                        locale = locale,
                                        motionEnabled = motionEnabled,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSelectDate(date) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    AnimatedContent(
                        targetState = selectedDate,
                        transitionSpec = {
                            (fadeIn(tween(motionDuration(180, motionEnabled))) +
                                slideInVertically(tween(motionDuration(220, motionEnabled))) { it / 8 }) togetherWith
                                (fadeOut(tween(motionDuration(140, motionEnabled))) +
                                    slideOutVertically(tween(motionDuration(180, motionEnabled))) { -it / 10 })
                        },
                        label = "life-day-agenda"
                    ) { date ->
                        LifeAgendaCard(
                            date = date,
                            events = events.filter { it.date == date }.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title }),
                            memberById = memberById,
                            locale = locale,
                            motionEnabled = motionEnabled
                        )
                    }
                }

                LifeFocus.RUNNING -> Column {
                    RunningOverviewHero(
                        snapshot = runSnapshot,
                        locale = locale,
                        motionEnabled = motionEnabled
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Progression & pass",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Text(
                        "Öppna kortet för detaljer, historik, mål och registrering.",
                        color = LifeMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Spacer(Modifier.height(4.dp))

                    RunningProgressCard(
                        session = session,
                        members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                        events = events,
                        onChanged = onRefresh
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LifePurple)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Lägg till aktivitet", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RunningOverviewHero(
    snapshot: RunningSnapshot,
    locale: Locale,
    motionEnabled: Boolean
) {
    val monthKmText = "%.1f".format(Locale.US, snapshot.monthKm)
    val nextPlan = snapshot.nextPlan
    val nextDateText = remember(nextPlan?.id, locale) {
        nextPlan?.date?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
            ?.replaceFirstChar { it.uppercase(locale) }
    }
    val nextType = nextPlan?.title
        ?.removePrefix("🏃 Plan: ")
        ?.substringBefore(" · ")
        ?.trim()
        ?.ifBlank { null }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF573A8E), Color(0xFF24192F), Color(0xFF17171F))
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "DEN HÄR MÅNADEN",
                        color = Color.White.copy(alpha = .64f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = .8.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(
                            targetState = monthKmText,
                            transitionSpec = {
                                fadeIn(tween(motionDuration(180, motionEnabled))) togetherWith
                                    fadeOut(tween(motionDuration(120, motionEnabled)))
                            },
                            label = "running-month-km"
                        ) { value ->
                            Text(
                                value,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "km",
                            color = Color.White.copy(alpha = .72f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = .10f),
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RunningHeroStat(
                    label = "Pass",
                    value = snapshot.monthPasses.toString(),
                    modifier = Modifier.weight(1f)
                )
                RunningHeroStat(
                    label = "Senaste",
                    value = snapshot.latestKm?.let { "%.1f km".format(Locale.US, it) } ?: "–",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                color = Color.White.copy(alpha = .07f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (nextPlan != null) LifePurple else Color.White.copy(alpha = .32f))
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (nextPlan != null) "Nästa pass" else "Planering",
                            color = Color.White.copy(alpha = .58f),
                            fontSize = 10.sp
                        )
                        Text(
                            if (nextPlan != null) {
                                listOfNotNull(nextDateText, nextPlan.time.takeIf { it.isNotBlank() }, nextType)
                                    .joinToString(" · ")
                            } else {
                                "Inget kommande pass planerat"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningHeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = .07f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Text(label, color = Color.White.copy(alpha = .56f), fontSize = 10.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LifeFocusSelector(
    focus: LifeFocus,
    onFocusChanged: (LifeFocus) -> Unit,
    motionEnabled: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LifeSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LifeFocusButton(
                label = "Vardag",
                selected = focus == LifeFocus.EVERYDAY,
                icon = { Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                motionEnabled = motionEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onFocusChanged(LifeFocus.EVERYDAY) }
            )
            LifeFocusButton(
                label = "Löpning",
                selected = focus == LifeFocus.RUNNING,
                icon = { Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp)) },
                motionEnabled = motionEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onFocusChanged(LifeFocus.RUNNING) }
            )
        }
    }
}

@Composable
private fun LifeFocusButton(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else .985f,
        animationSpec = tween(motionDuration(180, motionEnabled)),
        label = "life-focus-scale"
    )
    Surface(
        color = if (selected) LifePurple else Color.Transparent,
        contentColor = if (selected) Color.White else LifeMuted,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(7.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LifeDayCell(
    date: LocalDate,
    selected: Boolean,
    hasEvents: Boolean,
    hasRun: Boolean,
    locale: Locale,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = tween(motionDuration(180, motionEnabled)),
        label = "life-day-scale"
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(motionDuration(180, motionEnabled)),
        label = "life-day-background"
    )

    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = backgroundAlpha }
                .clip(RoundedCornerShape(16.dp))
                .background(LifePurple)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2).uppercase(locale),
                color = if (selected) Color.White.copy(alpha = .82f) else LifeMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                date.dayOfMonth.toString(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            when {
                hasRun -> Text("●", color = if (selected) Color.White else MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                hasEvents -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Color.White else LifeMuted))
                else -> Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun LifeAgendaCard(
    date: LocalDate,
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    locale: Locale,
    motionEnabled: Boolean
) {
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val header = date.format(formatter).replaceFirstChar { it.uppercase(locale) }

    Card(
        colors = CardDefaults.cardColors(containerColor = LifeSurfaceRaised),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(motionDuration(220, motionEnabled)))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(header, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (events.isEmpty()) "Lugn dag" else if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                        color = LifeMuted,
                        fontSize = 11.sp
                    )
                }
            }

            if (events.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Inget planerat. Kalendern håller sig ur vägen.", color = LifeMuted, fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(9.dp))
                events.take(4).forEachIndexed { index, event ->
                    val member = memberById[event.memberId]
                    val accent = member?.let { Color(it.colorArgb.toInt()) } ?: LifePurple
                    val memberName = when {
                        event.memberId == ALL_FAMILY_MEMBER_ID -> "Familjen"
                        member != null -> member.name
                        else -> "Familjen"
                    }
                    val title = event.title.removePrefix("🌈").removePrefix("🧺").trim()
                    val timeText = event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" }
                        ?: event.time.ifBlank { "Hela dagen" }

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(99.dp)).background(accent))
                        Spacer(Modifier.width(10.dp))
                        Text(timeText, color = LifeMuted, fontSize = 11.sp, modifier = Modifier.width(74.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(memberName, color = LifeMuted, fontSize = 10.sp)
                        }
                    }
                    if (index < minOf(events.lastIndex, 3)) {
                        HorizontalDivider(color = LifeDivider, thickness = 1.dp)
                    }
                }
                if (events.size > 4) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "+ ${events.size - 4} till",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
