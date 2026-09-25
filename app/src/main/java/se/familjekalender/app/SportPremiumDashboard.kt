package se.familjekalender.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private val SportBg = Color(0xFF0D0F10)
private val SportPanel = Color(0xFF15181B)
private val SportPanelRaised = Color(0xFF1B1F22)
private val SportBorder = Color.White.copy(alpha = .11f)
private val SportMuted = Color.White.copy(alpha = .60f)
private val SportAccent = Color(0xFFB8FF3D)
private val SportAccentSoft = Color(0x24B8FF3D)
private val SportBlue = Color(0xFF6383FF)
private val SportOrange = Color(0xFFFF9B51)
private val SportPurple = Color(0xFF9B6CFF)

private enum class SportSection {
    HOME,
    TRAINING,
    RUNNING,
    OVERVIEW,
}

private data class SportRunSummary(
    val event: SyncEvent,
    val distanceKm: Double,
    val durationMinutes: Int?,
) {
    val paceSecondsPerKm: Int?
        get() =
            durationMinutes
                ?.takeIf { it > 0 && distanceKm > 0.0 }
                ?.let { ((it * 60.0) / distanceKm).roundToInt() }
}

@Composable
internal fun SportPremiumDashboard(
    session: FamilySession,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onAdd: () -> Unit,
    onEdit: (SyncEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocation: () -> Unit,
    onRefresh: suspend () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(SportSection.HOME) }
    val realMembers = remember(members) { members.filter { it.id != ALL_FAMILY_MEMBER_ID } }

    BackHandler(enabled = section != SportSection.HOME) {
        section = SportSection.HOME
    }

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xE8060A0D),
                        SportBg,
                        Color(0xFF040709),
                    )
                )
            )
    ) {
        when (section) {
            SportSection.HOME ->
                SportHome(
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    events = events,
                    members = realMembers,
                    onAdd = onAdd,
                    onEdit = onEdit,
                    onOpenSettings = onOpenSettings,
                    onOpenTraining = { section = SportSection.TRAINING },
                    onOpenRunning = { section = SportSection.RUNNING },
                    onOpenOverview = { section = SportSection.OVERVIEW },
                )

            SportSection.TRAINING ->
                SportTraining(
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    events = events,
                    members = realMembers,
                    onBack = { section = SportSection.HOME },
                    onAdd = onAdd,
                    onEdit = onEdit,
                )

            SportSection.RUNNING ->
                SportRunning(
                    session = session,
                    selectedDate = selectedDate,
                    onSelectDate = onSelectDate,
                    events = events,
                    members = realMembers,
                    onBack = { section = SportSection.HOME },
                    onOpenSettings = onOpenSettings,
                    onRefresh = onRefresh,
                )

            SportSection.OVERVIEW ->
                SportOverview(
                    events = events,
                    members = realMembers,
                    onBack = { section = SportSection.HOME },
                )
        }
    }
}

@Composable
private fun SportHome(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onAdd: () -> Unit,
    onEdit: (SyncEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenRunning: () -> Unit,
    onOpenOverview: () -> Unit,
) {
    val today = LocalDate.now()
    val locale = remember { Locale("sv", "SE") }
    val weekFields = WeekFields.ISO
    val weekStart = remember(today) { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weekDates = remember(weekStart) { (0L..6L).map(weekStart::plusDays) }
    val weekEnd = weekDates.last()
    val eventsByDate = remember(events) { events.groupBy { it.date } }
    val memberById = remember(members) { members.associateBy { it.id } }
    val activityEvents =
        remember(events, today) {
            events
                .asSequence()
                .filter { !it.date.isBefore(today) }
                .filterNot(::isSportNoiseEvent)
                .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { safeTimeMinutes(it.time) })
                .toList()
        }
    val nextActivity = activityEvents.firstOrNull()
    val weekEvents =
        remember(events, weekStart, weekEnd) {
            events
                .filter { it.date in weekStart..weekEnd && !isSportNoiseEvent(it) }
                .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { safeTimeMinutes(it.time) })
        }
    val weekTrainingEvents =
        remember(events, weekStart, weekEnd) {
            events
                .filter { it.date in weekStart..weekEnd && isTrainingEvent(it) }
                .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { safeTimeMinutes(it.time) })
        }
    val selectedEvents =
        remember(events, selectedDate) {
            events
                .filter { it.date == selectedDate && !isSportNoiseEvent(it) }
                .sortedBy { safeTimeMinutes(it.time) }
        }
    val latestRun = remember(events) { sportRuns(events).firstOrNull() }
    val totalWeekMinutes = remember(weekEvents) { weekEvents.sumOf(::eventDurationMinutes) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SportHeader(
            title = "SPORT",
            subtitle =
                "VECKA ${today.get(weekFields.weekOfWeekBasedYear())} · " +
                    "${weekStart.dayOfMonth}–${weekEnd.dayOfMonth} " +
                    weekEnd.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
            onSettings = onOpenSettings,
            onAdd = onAdd,
        )

        NextSportActivityCard(
            event = nextActivity,
            member = nextActivity?.memberId?.let(memberById::get),
            onClick = nextActivity?.let { event -> { onEdit(event) } },
        )

        WeekStrip(
            dates = weekDates,
            selectedDate = selectedDate,
            eventsByDate = eventsByDate,
            locale = locale,
            onSelectDate = onSelectDate,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                    .uppercase(locale),
                color = SportMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = .8.sp,
            )
            if (selectedEvents.isEmpty()) {
                Surface(
                    color = SportPanel,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, SportBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Inga aktiviteter den här dagen",
                        color = SportMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                selectedEvents.forEach { event ->
                    SportAgendaRow(
                        event = event,
                        member = event.memberId?.let(memberById::get),
                        onClick = { onEdit(event) },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SportHubCard(
                title = "TRÄNING",
                subtitle = "Veckans pass",
                value = "${weekTrainingEvents.size} st",
                accent = SportAccent,
                icon = { Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(25.dp)) },
                modifier = Modifier.weight(1f),
                onClick = onOpenTraining,
            )
            SportHubCard(
                title = "LÖPNING",
                subtitle = "Senaste runda",
                value = latestRun?.let { formatKm(it.distanceKm) } ?: "Starta",
                accent = SportAccent,
                icon = { Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(27.dp)) },
                modifier = Modifier.weight(1f),
                onClick = onOpenRunning,
            )
            SportHubCard(
                title = "ÖVERSIKT",
                subtitle = "Träningstid",
                value = formatSportDuration(totalWeekMinutes),
                accent = SportPurple,
                icon = { Icon(Icons.Default.EventNote, null, modifier = Modifier.size(25.dp)) },
                modifier = Modifier.weight(1f),
                onClick = onOpenOverview,
            )
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SportTraining(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (SyncEvent) -> Unit,
) {
    val today = LocalDate.now()
    val locale = remember { Locale("sv", "SE") }
    val weekStart = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weekEnd = weekStart.plusDays(6)
    val memberById = remember(members) { members.associateBy { it.id } }
    val trainingEvents = remember(events) {
        events.filter(::isTrainingEvent)
            .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { safeTimeMinutes(it.time) })
    }
    val thisWeek = remember(trainingEvents, weekStart, weekEnd) {
        trainingEvents.filter { it.date in weekStart..weekEnd }
    }
    val upcoming = remember(trainingEvents, today) {
        trainingEvents.filter { !it.date.isBefore(today) }.take(12)
    }
    val memberCounts = remember(thisWeek, members) {
        members.map { member ->
            member to thisWeek.count {
                it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID
            }
        }.filter { it.second > 0 }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SportRoundIcon(Icons.Default.ChevronLeft, "Tillbaka", onBack)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "TRÄNING",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .8.sp,
                )
                Text(
                    "VECKANS PASS OCH KOMMANDE AKTIVITETER",
                    color = SportMuted,
                    fontSize = 9.sp,
                    letterSpacing = 1.1.sp,
                )
            }
            Surface(
                color = SportAccent,
                contentColor = Color(0xFF101213),
                shape = CircleShape,
                modifier = Modifier.size(48.dp).clickable(onClick = onAdd),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, "Lägg till träning", modifier = Modifier.size(24.dp))
                }
            }
        }

        Surface(
            color = SportPanel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, SportBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OverviewStat(
                    "PASS I VECKAN",
                    thisWeek.size.toString(),
                    SportAccent,
                    Modifier.weight(1f),
                )
                OverviewStat(
                    "KOMMANDE",
                    upcoming.size.toString(),
                    SportBlue,
                    Modifier.weight(1f),
                )
                OverviewStat(
                    "AKTIVA",
                    memberCounts.size.toString(),
                    SportPurple,
                    Modifier.weight(1f),
                )
            }
        }

        if (memberCounts.isNotEmpty()) {
            Surface(
                color = SportPanel,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, SportBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        "DEN HÄR VECKAN",
                        color = SportMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    memberCounts.forEach { (member, count) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(9.dp)
                                    .clip(CircleShape)
                                    .background(memberColor(member))
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                member.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (count == 1) "1 pass" else "$count pass",
                                color = SportAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Text(
            "KOMMANDE TRÄNING",
            color = SportMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp),
        )

        if (upcoming.isEmpty()) {
            Surface(
                color = SportPanel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, SportBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ingen träning planerad",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Lägg till ett pass med plusknappen.",
                        color = SportMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        } else {
            upcoming.forEach { event ->
                val member = event.memberId?.let(memberById::get)
                Surface(
                    color = if (event.date == selectedDate) SportAccentSoft else SportPanel,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        if (event.date == selectedDate) SportAccent.copy(alpha = .42f) else SportBorder,
                    ),
                    modifier = Modifier.fillMaxWidth().clickable {
                        onSelectDate(event.date)
                        onEdit(event)
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(4.dp)
                                .height(44.dp)
                                .clip(CircleShape)
                                .background(member?.let(::memberColor) ?: SportAccent)
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.width(70.dp)) {
                            Text(
                                event.date.format(DateTimeFormatter.ofPattern("EEE d/M", locale))
                                    .replaceFirstChar { it.uppercase(locale) },
                                color = SportMuted,
                                fontSize = 10.sp,
                            )
                            Text(
                                event.time.ifBlank { "—" },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                cleanSportTitle(event.title),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                member?.name ?: "Familjen",
                                color = member?.let(::memberColor) ?: SportMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = SportMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SportRunning(
    session: FamilySession,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: suspend () -> Unit,
) {
    val runs = remember(events) { sportRuns(events) }
    val latest = runs.firstOrNull()
    var showRecorder by rememberSaveable { mutableStateOf(false) }
    var visibleMonth by rememberSaveable {
        mutableStateOf(YearMonth.from(latest?.event?.date ?: LocalDate.now()))
    }

    BackHandler(enabled = showRecorder) {
        showRecorder = false
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        RunningHeader(onBack = onBack, onSettings = onOpenSettings)

        RunningHero(latest)

        Button(
            onClick = { showRecorder = !showRecorder },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = SportAccent,
                    contentColor = Color(0xFF101213),
                ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp),
        ) {
            Icon(Icons.Default.DirectionsRun, null)
            Spacer(Modifier.width(9.dp))
            Text(
                if (showRecorder) "DÖLJ GPS" else "STARTA LÖPNING",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = .4.sp,
            )
        }

        if (showRecorder) {
            RunningProgressCard(
                session = session,
                members = members,
                events = events,
                onChanged = onRefresh,
            )
        }

        RunMonthGrid(
            month = visibleMonth,
            selectedDate = selectedDate,
            runs = runs,
            onMonthChanged = { visibleMonth = it },
            onSelectDate = onSelectDate,
        )

        RecentRunsCard(runs = runs)

        RunningTrainingCalendar(events = events)

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SportOverview(
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onBack: () -> Unit,
) {
    val today = LocalDate.now()
    val weekStart = remember(today) { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weekEnd = weekStart.plusDays(6)
    val weekEvents =
        remember(events, weekStart) {
            events.filter { it.date in weekStart..weekEnd && !isSportNoiseEvent(it) }
        }
    val runs = remember(events) { sportRuns(events) }
    val thisMonthRuns =
        remember(runs, today) {
            runs.filter { it.event.date.year == today.year && it.event.date.month == today.month }
        }
    val weekMinutes = remember(weekEvents) { weekEvents.sumOf(::eventDurationMinutes) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronLeft, "Tillbaka", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("ÖVERSIKT", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("VECKANS SPORT I EN BLICK", color = SportMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OverviewStat("AKTIVITETER", weekEvents.size.toString(), SportAccent, Modifier.weight(1f))
            OverviewStat("TID", formatSportDuration(weekMinutes), SportBlue, Modifier.weight(1f))
            OverviewStat(
                "LÖPNING",
                "${formatKm(thisMonthRuns.sumOf { it.distanceKm })}",
                SportOrange,
                Modifier.weight(1f),
            )
        }

        Surface(
            color = SportPanel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, SportBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Familjens sportvecka", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    val count = weekEvents.count { it.memberId == member.id }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(9.dp)
                                .clip(CircleShape)
                                .background(memberColor(member))
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(member.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (count == 1) "1 aktivitet" else "$count aktiviteter",
                            color = if (count > 0) SportAccent else SportMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        RunningTrainingCalendar(events = events)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SportHeader(
    title: String,
    subtitle: String,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 35.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
            Text(
                subtitle,
                color = SportMuted,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        SportRoundIcon(Icons.Default.Settings, "Inställningar", onSettings)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = SportAccent,
            contentColor = Color(0xFF07120D),
            shape = CircleShape,
            modifier = Modifier.size(48.dp).clickable(onClick = onAdd),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "Lägg till aktivitet", modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun RunningHeader(onBack: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportRoundIcon(Icons.Default.ChevronLeft, "Tillbaka", onBack)
        Spacer(Modifier.width(10.dp))
        Text(
            "LÖPNING",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .8.sp,
            modifier = Modifier.weight(1f),
        )
        SportRoundIcon(Icons.Default.Settings, "Inställningar", onSettings)
    }
}

@Composable
private fun SportRoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        color = SportPanelRaised,
        shape = CircleShape,
        border = BorderStroke(1.dp, SportBorder),
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun NextSportActivityCard(
    event: SyncEvent?,
    member: SyncMember?,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = SportPanel,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SportBorder),
        modifier =
            Modifier.fillMaxWidth().then(
                if (event != null && onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
    ) {
        if (event == null) {
            Column(Modifier.padding(17.dp)) {
                Text("NÄSTA AKTIVITET", color = SportMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(7.dp))
                Text("Inget planerat", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Kalendern är chockerande lugn.", color = SportMuted, fontSize = 12.sp)
            }
        } else {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = member?.let(::memberColor)?.copy(alpha = .20f) ?: SportAccentSoft,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            null,
                            tint = member?.let(::memberColor) ?: SportAccent,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("NÄSTA AKTIVITET", color = SportMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOfNotNull(member?.name, cleanSportTitle(event.title)).joinToString(" · "),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sportTimeText(event),
                        color = SportMuted,
                        fontSize = 12.sp,
                    )
                }
                Surface(
                    color = SportAccentSoft,
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text(
                        countdownText(event),
                        color = SportAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    locale: Locale,
    onSelectDate: (LocalDate) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        dates.forEach { date ->
            val selected = date == selectedDate
            val hasActivity = eventsByDate[date].orEmpty().any { !isSportNoiseEvent(it) }
            Surface(
                color = if (selected) SportAccentSoft else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                border = if (selected) BorderStroke(1.dp, SportAccent) else null,
                modifier = Modifier.weight(1f).clickable { onSelectDate(date) },
            ) {
                Column(
                    Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3).uppercase(locale),
                        color = if (selected) Color.White else SportMuted,
                        fontSize = 9.sp,
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(5.dp)
                            .clip(CircleShape)
                            .background(if (hasActivity) SportAccent else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun SportAgendaRow(
    event: SyncEvent,
    member: SyncMember?,
    onClick: () -> Unit,
) {
    val accent = member?.let(::memberColor) ?: SportBlue
    Surface(
        color = SportPanel,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, SportBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(4.dp)
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.width(57.dp)) {
                Text(event.time.ifBlank { "—" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                event.endTime?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = SportMuted, fontSize = 10.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(member?.name, cleanSportTitle(event.title)).joinToString(" · "),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.source.isNotBlank()) {
                    Text(event.source, color = SportMuted, fontSize = 10.sp, maxLines = 1)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = SportMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SportHubCard(
    title: String,
    subtitle: String,
    value: String,
    accent: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = SportPanel,
        contentColor = accent,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
        modifier = modifier.height(148.dp).clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = accent.copy(alpha = .13f),
                contentColor = accent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Column {
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(subtitle, color = SportMuted, fontSize = 9.sp, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                Text(value, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RunningHero(latest: SportRunSummary?) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, SportBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF252A20),
                            Color(0xFF171A16),
                            Color(0xFF101213),
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Text("SENASTE RUNDAN", color = SportMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    latest?.let { "%.2f".format(Locale.US, it.distanceKm).replace('.', ',') } ?: "0,00",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    " km",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            latest?.let {
                Text(it.event.date.toString(), color = SportMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunningHeroStat(
                    "TID",
                    latest?.durationMinutes?.let(::formatMinutesCompact) ?: "—",
                    Modifier.weight(1f),
                )
                RunningHeroStat(
                    "TEMPO",
                    latest?.paceSecondsPerKm?.let(::formatPaceCompact) ?: "—",
                    Modifier.weight(1f),
                )
                RunningHeroStat(
                    "PASS",
                    if (latest == null) "—" else "Sparat",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RunningHeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = .055f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, color = SportMuted, fontSize = 9.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun RunMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    runs: List<SportRunSummary>,
    onMonthChanged: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val locale = remember { Locale("sv", "SE") }
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val runDates = remember(runs) { runs.map { it.event.date }.toSet() }
    val today = LocalDate.now()

    Surface(
        color = SportPanel,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SportBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onMonthChanged(month.minusMonths(1)) }) {
                    Icon(Icons.Default.ChevronLeft, "Föregående månad", tint = SportMuted)
                }
                Text(
                    month.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale) + " " + month.year,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { onMonthChanged(month.plusMonths(1)) }) {
                    Icon(Icons.Default.ChevronRight, "Nästa månad", tint = SportMuted)
                }
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("MÅN", "TIS", "ONS", "TORS", "FRE", "LÖR", "SÖN").forEach { day ->
                    Text(
                        day,
                        color = SportMuted,
                        fontSize = 8.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            val cells = leading + month.lengthOfMonth()
            val rows = ((cells + 6) / 7)
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val cellIndex = row * 7 + col
                        val day = cellIndex - leading + 1
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val selected = date == selectedDate
                            val isToday = date == today
                            Surface(
                                color =
                                    when {
                                        selected -> SportAccent
                                        isToday -> SportAccentSoft
                                        else -> Color.Transparent
                                    },
                                contentColor = if (selected) Color(0xFF04110A) else Color.White,
                                shape = CircleShape,
                                modifier =
                                    Modifier.weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clickable { onSelectDate(date) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        day.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (date in runDates) {
                                        Box(
                                            Modifier.align(Alignment.BottomCenter)
                                                .padding(bottom = 5.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (selected) Color(0xFF04110A) else SportAccent)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRunsCard(runs: List<SportRunSummary>) {
    Surface(
        color = SportPanel,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, SportBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MINA LÖPRUNDOR", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (runs.isEmpty()) {
                Text("Inga sparade löprundor ännu.", color = SportMuted, fontSize = 12.sp)
            } else {
                runs.take(5).forEachIndexed { index, run ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = SportAccentSoft,
                            contentColor = SportAccent,
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(21.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(formatKm(run.distanceKm), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(run.event.date.toString(), color = SportMuted, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                run.durationMinutes?.let(::formatMinutesCompact) ?: "—",
                                color = Color.White,
                                fontSize = 11.sp,
                            )
                            Text(
                                run.paceSecondsPerKm?.let(::formatPaceCompact) ?: "—",
                                color = SportMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    if (index < minOf(4, runs.lastIndex)) {
                        HorizontalDivider(color = SportBorder, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SportPanel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .23f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(label, color = SportMuted, fontSize = 8.sp, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
internal fun SportBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF050B0E),
        tonalElevation = 0.dp,
    ) {
        val items =
            listOf(
                Triple(0, Icons.Default.DirectionsRun, "Sport"),
                Triple(1, Icons.Default.ShoppingCart, "Inköp"),
                Triple(2, Icons.Default.CheckCircle, "To-Do"),
                Triple(4, Icons.Default.Settings, "Inställningar"),
                Triple(5, Icons.Default.LocationOn, "Plats"),
            )
        items.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, label) },
                label = { Text(label, maxLines = 1, fontSize = 9.sp) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = SportAccent,
                        selectedTextColor = SportAccent,
                        indicatorColor = SportAccentSoft,
                        unselectedIconColor = SportMuted,
                        unselectedTextColor = SportMuted,
                    ),
            )
        }
    }
}

private fun memberColor(member: SyncMember): Color =
    runCatching { Color(member.colorArgb.toInt()) }.getOrDefault(SportBlue)

private fun isTrainingEvent(event: SyncEvent): Boolean {
    if (isSportNoiseEvent(event)) return false

    val title = event.title.lowercase(Locale("sv", "SE"))
    val source = event.source.lowercase(Locale("sv", "SE"))

    if (
        source.contains("sportadmin") ||
        source.contains("sport_admin") ||
        source.contains("training") ||
        source.contains("workout")
    ) return true

    return listOf(
        "träning",
        "match",
        "innebandy",
        "handboll",
        "fotboll",
        "basket",
        "hockey",
        "tennis",
        "padel",
        "gym",
        "löpning",
        "löprunda",
        "springa",
        "simning",
        "simträning",
        "dans",
        "ridning",
        "karate",
        "judo",
        "taekwondo",
    ).any(title::contains) || title.startsWith("🏃")
}

private fun isSportNoiseEvent(event: SyncEvent): Boolean {
    val title = event.title.trim()
    return title.startsWith("🔔") ||
        title.startsWith("🌈") ||
        title.startsWith("🧺")
}

private fun cleanSportTitle(title: String): String =
    title
        .removePrefix("🌈")
        .removePrefix("🔔")
        .removePrefix("🧺")
        .trim()

private fun safeTimeMinutes(value: String): Int =
    runCatching {
        val parts = value.split(':')
        parts[0].toInt() * 60 + parts.getOrElse(1) { "0" }.take(2).toInt()
    }.getOrDefault(Int.MAX_VALUE)

private fun eventDurationMinutes(event: SyncEvent): Int {
    val start = safeTimeMinutes(event.time)
    val end = event.endTime?.let(::safeTimeMinutes) ?: return 0
    if (start == Int.MAX_VALUE || end == Int.MAX_VALUE) return 0
    return (end - start).coerceAtLeast(0)
}

private fun sportTimeText(event: SyncEvent): String {
    val date = event.date
    val today = LocalDate.now()
    val dateLabel =
        when (date) {
            today -> "Idag"
            today.plusDays(1) -> "Imorgon"
            else -> date.toString()
        }
    val time =
        event.endTime
            ?.takeIf { it.isNotBlank() }
            ?.let { "${event.time}–$it" }
            ?: event.time.ifBlank { "Hela dagen" }
    return "$dateLabel · $time"
}

private fun countdownText(event: SyncEvent): String {
    val today = LocalDate.now()
    if (event.date.isAfter(today.plusDays(1))) {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, event.date)
        return "om $days dagar"
    }
    val time = runCatching { LocalTime.parse(event.time.take(5)) }.getOrNull()
        ?: return if (event.date == today) "idag" else "imorgon"
    val target = LocalDateTime.of(event.date, time)
    val duration = Duration.between(LocalDateTime.now(), target)
    if (duration.isNegative) return "snart"
    val hours = duration.toHours()
    val mins = duration.minusHours(hours).toMinutes()
    return when {
        hours >= 24 -> "imorgon"
        hours > 0 -> "${hours} h ${mins} min"
        else -> "${mins.coerceAtLeast(0)} min"
    }
}

private fun sportRuns(events: List<SyncEvent>): List<SportRunSummary> =
    events
        .mapNotNull { event ->
            when {
                event.title.startsWith("🏃 RUN|") -> {
                    val parts = event.title.removePrefix("🏃 RUN|").split('|')
                    val km = parts.getOrNull(0)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
                    val mins = parts.getOrNull(1)?.toIntOrNull()
                    SportRunSummary(event, km, mins)
                }

                event.title.startsWith("🏃 Löpning · ") -> {
                    val parts = event.title.removePrefix("🏃 Löpning · ").split(" · ")
                    val km =
                        parts.getOrNull(0)
                            ?.removeSuffix(" km")
                            ?.replace(',', '.')
                            ?.toDoubleOrNull()
                            ?: return@mapNotNull null
                    val mins = parts.getOrNull(1)?.removeSuffix(" min")?.trim()?.toIntOrNull()
                    SportRunSummary(event, km, mins)
                }

                else -> null
            }
        }
        .sortedWith(
            compareByDescending<SportRunSummary> { it.event.date }
                .thenByDescending { safeTimeMinutes(it.event.time) }
        )

private fun formatKm(value: Double): String =
    "${"%.2f".format(Locale.US, value).replace('.', ',')} km"

private fun formatSportDuration(minutes: Int): String =
    when {
        minutes <= 0 -> "0 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

private fun formatMinutesCompact(minutes: Int): String =
    if (minutes < 60) "$minutes min"
    else "%d:%02d".format(minutes / 60, minutes % 60)

private fun formatPaceCompact(seconds: Int): String =
    "%d:%02d/km".format(seconds / 60, seconds % 60)
