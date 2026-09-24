package se.familjekalender.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val CleanPurple = Color(0xFF8A5CF6)
private val CleanPurpleBright = Color(0xFFAA72FF)
private val CleanGlass = Color(0xB8171422)
private val CleanGlassSoft = Color(0xA61C1828)
private val CleanBorder = Color.White.copy(alpha = .13f)
private val CleanMuted = Color.White.copy(alpha = .66f)

private val EmbeddedTimeRange = Regex("""(?:\s*[·•]\s*)?\b\d{1,2}:\d{2}\s*[–-]\s*\d{1,2}:\d{2}\b""")

private fun cleanSeasonDrawable(month: YearMonth, themeMode: ThemeMode): Int? =
    when (themeMode) {
        ThemeMode.SPRING -> R.drawable.season_spring
        ThemeMode.SUMMER -> R.drawable.season_summer
        ThemeMode.AUTUMN -> R.drawable.season_autumn
        ThemeMode.WINTER -> R.drawable.season_winter
        ThemeMode.CLASSIC -> null
        ThemeMode.AUTO ->
            when (month.monthValue) {
                3, 4, 5 -> R.drawable.season_spring
                6, 7, 8 -> R.drawable.season_summer
                9, 10, 11 -> R.drawable.season_autumn
                else -> R.drawable.season_winter
            }
    }

private fun cleanEventTitle(event: SyncEvent): String =
    event.title
        .replace(EmbeddedTimeRange, "")
        .replace(Regex("""\s*·\s*·\s*"""), " · ")
        .trim()
        .trim('·', '-', ' ')

private fun cleanEventTime(event: SyncEvent): String =
    if (event.time.isBlank()) {
        "Ingen tid"
    } else {
        event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" } ?: event.time
    }

private enum class CleanSummaryKind {
    TODAY,
    WEEK,
    CONFLICTS,
    REMINDERS,
}

private fun isCleanReminderEvent(event: SyncEvent): Boolean {
    val source = event.source.trim().lowercase()
    val title = event.title.trimStart()
    return source.contains("reminder") || title.startsWith("🔔")
}

@Composable
internal fun MinimalCalendarScreen(
    session: FamilySession,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onAdd: () -> Unit,
    onEdit: (SyncEvent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocation: () -> Unit,
    themeMode: ThemeMode = ThemeMode.AUTO,
) {
    val context = LocalContext.current
    val locale = remember { Locale("sv", "SE") }
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var openedEvent by remember { mutableStateOf<SyncEvent?>(null) }
    var showAllDayActivities by remember { mutableStateOf(false) }
    var summaryDetail by remember { mutableStateOf<CleanSummaryKind?>(null) }
    var showWeatherDetails by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<CleanWeatherSnapshot?>(null) }
    var weatherLoading by remember { mutableStateOf(false) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var weatherRefreshRequest by remember { mutableIntStateOf(0) }
    var forceWeatherRefresh by remember { mutableStateOf(false) }

    fun hasWeatherLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED

    val weatherPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            showWeatherDetails = true
            if (grants.values.any { it }) {
                weatherError = null
                forceWeatherRefresh = true
                weatherRefreshRequest++
            } else {
                weatherError = "Platsbehörighet behövs för att visa väder där du befinner dig."
            }
        }

    val memberById = remember(members) { members.associateBy { it.id } }
    val eventsByDate = remember(events) { events.groupBy { it.date } }
    val selectedEvents =
        remember(events, selectedDate) {
            events
                .filter { it.date == selectedDate }
                .sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
        }
    val selectedConflicts =
        remember(events, members, selectedDate) {
            analyzeCalendarConflicts(events, members, selectedDate)
        }
    val conflictDates =
        remember(events, members) {
            analyzeCalendarConflicts(events, members).map { it.date }.toSet()
        }

    val weekStart =
        remember(selectedDate) {
            selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
        }
    val weekEnd = remember(weekStart) { weekStart.plusDays(6) }
    val selectedDayActivities =
        remember(events, selectedDate) {
            events
                .filter { it.date == selectedDate && !isCleanReminderEvent(it) }
                .sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
        }
    val weekActivities =
        remember(events, weekStart, weekEnd) {
            events
                .filter {
                    !it.date.isBefore(weekStart) &&
                        !it.date.isAfter(weekEnd) &&
                        !isCleanReminderEvent(it)
                }
                .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time }.thenBy { it.title })
        }
    val weekConflicts =
        remember(events, members, weekStart, weekEnd) {
            analyzeCalendarConflicts(events, members)
                .filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
        }
    val weekReminders =
        remember(events, weekStart, weekEnd) {
            events
                .filter {
                    !it.date.isBefore(weekStart) &&
                        !it.date.isAfter(weekEnd) &&
                        isCleanReminderEvent(it)
                }
                .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time }.thenBy { it.title })
        }

    LaunchedEffect(selectedDate) {
        val target = YearMonth.from(selectedDate)
        if (target != month) month = target
    }

    LaunchedEffect(weatherRefreshRequest) {
        if (!hasWeatherLocationPermission()) return@LaunchedEffect
        weatherLoading = true
        weatherError = null
        val force = forceWeatherRefresh
        forceWeatherRefresh = false
        runCatching { CleanWeatherService.loadCurrent(context, force = force) }
            .onSuccess { weather = it }
            .onFailure { weatherError = it.message ?: "Kunde inte hämta vädret." }
        weatherLoading = false
    }

    Box(Modifier.fillMaxSize()) {
        val customBackground = rememberCustomBackgroundBitmap()
        if (customBackground != null) {
            Image(
                bitmap = customBackground,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            cleanSeasonDrawable(month, themeMode)?.let { backgroundRes ->
                Image(
                    painter = painterResource(backgroundRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x44120B16),
                            Color(0x66120C19),
                            Color(0x99110D18),
                        )
                    )
                )
        )

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            CleanHeader(
                onSearch = { showSearch = true },
                onAdd = onAdd,
            )

            Spacer(Modifier.height(10.dp))

            CleanSummaryStrip(
                selectedDate = selectedDate,
                today = today,
                dayCount = selectedDayActivities.size,
                weekCount = weekActivities.size,
                conflictCount = weekConflicts.size,
                reminderCount = weekReminders.size,
                onToday = {
                    summaryDetail = CleanSummaryKind.TODAY
                },
                onWeek = { summaryDetail = CleanSummaryKind.WEEK },
                onConflicts = { summaryDetail = CleanSummaryKind.CONFLICTS },
                onReminders = { summaryDetail = CleanSummaryKind.REMINDERS },
            )

            Spacer(Modifier.height(12.dp))

            CleanCalendarCard(
                month = month,
                selectedDate = selectedDate,
                today = today,
                locale = locale,
                eventsByDate = eventsByDate,
                memberById = memberById,
                onSelect = { date ->
                    month = YearMonth.from(date)
                    onSelect(date)
                },
                onMonthChange = { delta ->
                    val next = month.plusMonths(delta)
                    month = next
                    onSelect(next.atDay(1))
                },
            )

            Spacer(Modifier.height(12.dp))

            CleanWeatherCard(
                weather = weather,
                loading = weatherLoading,
                hasLocationPermission = hasWeatherLocationPermission(),
                onClick = {
                    showWeatherDetails = true
                    if (hasWeatherLocationPermission()) {
                        forceWeatherRefresh = true
                        weatherRefreshRequest++
                    } else {
                        weatherPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
        }
    }

    summaryDetail?.let { detail ->
        CleanSummaryDialog(
            kind = detail,
            selectedDate = selectedDate,
            weekStart = weekStart,
            weekEnd = weekEnd,
            dayActivities = selectedDayActivities,
            weekActivities = weekActivities,
            conflicts = weekConflicts,
            reminders = weekReminders,
            memberById = memberById,
            locale = locale,
            onDismiss = { summaryDetail = null },
            onSelectDate = { date ->
                month = YearMonth.from(date)
                onSelect(date)
            },
            onOpenEvent = { event ->
                summaryDetail = null
                if (event.source == "sportadmin") {
                    openedEvent = event
                } else {
                    onEdit(event)
                }
            },
        )
    }

    if (showSearch) {
        val normalized = searchQuery.trim()
        val matches =
            remember(events, normalized) {
                if (normalized.isBlank()) emptyList()
                else
                    events
                        .filter { it.title.contains(normalized, ignoreCase = true) }
                        .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
                        .take(6)
            }
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Sök i kalendern") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Aktivitet") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    matches.forEach { event ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    onSelect(event.date)
                                    month = YearMonth.from(event.date)
                                    showSearch = false
                                    searchQuery = ""
                                },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    event.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${event.date} • ${event.time}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    if (normalized.isNotBlank() && matches.isEmpty()) {
                        Text("Inga träffar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearch = false }) { Text("Stäng") }
            },
        )
    }

    if (showAllDayActivities) {
        val dateText =
            selectedDate
                .format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                .replaceFirstChar { it.uppercase(locale) }
        val groupedEvents =
            remember(selectedEvents) {
                selectedEvents
                    .groupBy { it.memberId ?: ALL_FAMILY_MEMBER_ID }
                    .entries
                    .sortedBy { entry -> entry.value.minOfOrNull { it.time } ?: "99:99" }
            }
        var collapsedMemberKeys by
            remember(selectedDate) { mutableStateOf(emptySet<String>()) }

        AlertDialog(
            onDismissRequest = { showAllDayActivities = false },
            containerColor = Color(0xE61A1624),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp,
            title = {
                Column {
                    Text(
                        dateText,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (selectedEvents.size == 1) "1 aktivitet"
                        else "${selectedEvents.size} aktiviteter",
                        color = CleanMuted,
                        fontSize = 12.sp,
                    )
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (selectedEvents.isEmpty()) {
                        Text("Inga aktiviteter planerade", color = CleanMuted)
                    } else {
                        groupedEvents.forEach { (memberKey, personEventsRaw) ->
                            val personEvents =
                                personEventsRaw.sortedWith(
                                    compareBy<SyncEvent> { it.time }.thenBy { it.title }
                                )
                            val member = memberById[memberKey]
                            val memberName =
                                if (memberKey == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                                else member?.name ?: "Övrigt"
                            val accent =
                                if (memberKey == ALL_FAMILY_MEMBER_ID) Color(0xFFFFD75E)
                                else member?.let { Color(it.colorArgb.toInt()) }
                                    ?: CleanPurpleBright

                            if (personEvents.size == 1) {
                                val event = personEvents.first()
                                val hasConflict =
                                    selectedConflicts.any {
                                        it.first.id == event.id || it.second.id == event.id
                                    }
                                Surface(
                                    color = Color.White.copy(alpha = .045f),
                                    shape = RoundedCornerShape(18.dp),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            if (hasConflict) Color(0xFFFF8A94).copy(alpha = .35f)
                                            else Color.White.copy(alpha = .08f),
                                        ),
                                    modifier =
                                        Modifier.fillMaxWidth().clickable {
                                            showAllDayActivities = false
                                            openedEvent = event
                                        },
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier.width(3.dp)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(accent)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            cleanEventTime(event),
                                            color = Color.White.copy(alpha = .70f),
                                            fontSize = 12.sp,
                                            modifier = Modifier.width(78.dp),
                                        )
                                        Box(
                                            Modifier.size(30.dp)
                                                .clip(CircleShape)
                                                .background(accent.copy(alpha = .18f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier.size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(accent)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                cleanEventTitle(event),
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    memberName,
                                                    color = CleanMuted,
                                                    fontSize = 11.sp,
                                                )
                                                if (hasConflict) {
                                                    Text(
                                                        "Krock",
                                                        color = Color(0xFFFFA0A8),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = "Öppna aktivitet",
                                            tint = Color.White.copy(alpha = .38f),
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                }
                            } else {
                                val collapsed = memberKey in collapsedMemberKeys
                                val firstStart = personEvents.first().time
                                val lastEvent = personEvents.last()
                                val lastTime =
                                    lastEvent.endTime?.takeIf { it.isNotBlank() }
                                        ?: lastEvent.time
                                val daySpan =
                                    if (firstStart == lastTime) firstStart else "$firstStart–$lastTime"
                                val personHasConflict =
                                    selectedConflicts.any { conflict ->
                                        conflict.memberId == memberKey ||
                                                personEvents.any {
                                                    it.id == conflict.first.id ||
                                                            it.id == conflict.second.id
                                                }
                                    }

                                Surface(
                                    color = Color.White.copy(alpha = .045f),
                                    shape = RoundedCornerShape(20.dp),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            if (personHasConflict)
                                                Color(0xFFFF8A94).copy(alpha = .30f)
                                            else accent.copy(alpha = .22f),
                                        ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clickable {
                                                    collapsedMemberKeys =
                                                        if (collapsed) {
                                                            collapsedMemberKeys - memberKey
                                                        } else {
                                                            collapsedMemberKeys + memberKey
                                                        }
                                                }
                                                .padding(horizontal = 13.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier.size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(accent.copy(alpha = .18f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    Modifier.size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(accent)
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                ) {
                                                    Text(
                                                        memberName,
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    if (personHasConflict) {
                                                        Text(
                                                            "Krock",
                                                            color = Color(0xFFFFA0A8),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "${personEvents.size} aktiviteter · $daySpan",
                                                    color = CleanMuted,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                            Icon(
                                                if (collapsed) Icons.Default.KeyboardArrowDown
                                                else Icons.Default.KeyboardArrowUp,
                                                contentDescription =
                                                    if (collapsed) "Visa aktiviteter"
                                                    else "Dölj aktiviteter",
                                                tint = Color.White.copy(alpha = .48f),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }

                                        if (!collapsed) {
                                            HorizontalDivider(
                                                color = Color.White.copy(alpha = .07f),
                                                thickness = .5.dp,
                                            )
                                            Column(
                                                Modifier.padding(
                                                    start = 13.dp,
                                                    end = 10.dp,
                                                    bottom = 8.dp,
                                                )
                                            ) {
                                                personEvents.forEachIndexed { index, event ->
                                                    val hasConflict =
                                                        selectedConflicts.any {
                                                            it.first.id == event.id ||
                                                                    it.second.id == event.id
                                                        }
                                                    Row(
                                                        Modifier.fillMaxWidth()
                                                            .clickable {
                                                                showAllDayActivities = false
                                                                openedEvent = event
                                                            }
                                                            .padding(vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            cleanEventTime(event),
                                                            color = Color.White.copy(alpha = .68f),
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.width(82.dp),
                                                        )
                                                        Box(
                                                            Modifier.width(20.dp).height(42.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            if (personEvents.size > 1) {
                                                                Box(
                                                                    Modifier.width(2.dp)
                                                                        .fillMaxHeight()
                                                                        .background(
                                                                            accent.copy(alpha = .28f)
                                                                        )
                                                                )
                                                            }
                                                            Box(
                                                                Modifier.size(10.dp)
                                                                    .clip(CircleShape)
                                                                    .background(accent)
                                                            )
                                                        }
                                                        Spacer(Modifier.width(7.dp))
                                                        Column(Modifier.weight(1f)) {
                                                            Text(
                                                                cleanEventTitle(event),
                                                                color = Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                            if (hasConflict) {
                                                                Text(
                                                                    "Överlappning / dubbelbokning",
                                                                    color = Color(0xFFFFA0A8),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                )
                                                            }
                                                        }
                                                        Icon(
                                                            Icons.Default.ChevronRight,
                                                            contentDescription = "Öppna aktivitet",
                                                            tint = Color.White.copy(alpha = .32f),
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                    if (index < personEvents.lastIndex) {
                                                        HorizontalDivider(
                                                            color = Color.White.copy(alpha = .05f),
                                                            thickness = .5.dp,
                                                            modifier = Modifier.padding(start = 89.dp),
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
            },
            confirmButton = {
                TextButton(onClick = { showAllDayActivities = false }) {
                    Text("Stäng", color = CleanPurpleBright, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    openedEvent?.let { event ->
        val memberName =
            if (event.memberId == ALL_FAMILY_MEMBER_ID) {
                "Hela familjen"
            } else {
                memberById[event.memberId]?.name ?: "Övrigt"
            }
        val dateText =
            event.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar {
                it.uppercase(locale)
            }
        val timeText = event.endTime?.let { "${event.time}–$it" } ?: event.time

        AlertDialog(
            onDismissRequest = { openedEvent = null },
            title = {
                Text(
                    cleanEventTitle(event),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dateText)
                    Text("Tid: $timeText")
                    Text("Gäller för: $memberName")
                    if (event.source == "sportadmin") {
                        Text(
                            "Den här aktiviteten kommer från SportAdmin och hanteras där.",
                            color = CleanMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                if (event.source != "sportadmin") {
                    Button(
                        onClick = {
                            openedEvent = null
                            onEdit(event)
                        },
                    ) {
                        Text("Redigera")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { openedEvent = null }) {
                    Text("Stäng")
                }
            },
        )
    }

    if (showWeatherDetails) {
        AlertDialog(
            onDismissRequest = { showWeatherDetails = false },
            containerColor = Color(0xE61A1624),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .07f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = .88f),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Väder",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            text = {
                when {
                    weatherLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Hämtar aktuellt väder…", color = Color.White.copy(alpha = .76f))
                        }
                    }

                    weather != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "${weather!!.temperatureC}°",
                                color = Color.White,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                weather!!.description,
                                color = Color.White.copy(alpha = .76f),
                                fontSize = 16.sp,
                            )
                            Text(
                                "Aktuellt väder för din nuvarande plats.",
                                color = Color.White.copy(alpha = .52f),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    else -> {
                        Text(
                            weatherError
                                ?: if (hasWeatherLocationPermission()) {
                                    "Kunde inte hämta vädret just nu."
                                } else {
                                    "Tillåt platsåtkomst för att visa aktuellt väder."
                                },
                            color = Color.White.copy(alpha = .76f),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherDetails = false }) {
                    Text("Stäng", color = CleanPurpleBright, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                if (hasWeatherLocationPermission()) {
                    TextButton(
                        enabled = !weatherLoading,
                        onClick = {
                            forceWeatherRefresh = true
                            weatherRefreshRequest++
                        },
                    ) {
                        Text("Uppdatera", color = CleanPurpleBright)
                    }
                }
            },
        )
    }
}

@Composable
private fun CleanSummaryStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    dayCount: Int,
    weekCount: Int,
    conflictCount: Int,
    reminderCount: Int,
    onToday: () -> Unit,
    onWeek: () -> Unit,
    onConflicts: () -> Unit,
    onReminders: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CleanSummaryTile(
            label =
                if (selectedDate == today) "IDAG"
                else selectedDate.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))
                    .uppercase(Locale("sv", "SE"))
                    .take(3),
            value = dayCount.toString(),
            helper =
                selectedDate.format(DateTimeFormatter.ofPattern("d/M")) +
                    if (dayCount == 1) " · aktivitet" else " · aktiviteter",
            onClick = onToday,
            modifier = Modifier.weight(1f),
        )
        CleanSummaryTile(
            label = "VECKAN",
            value = weekCount.toString(),
            helper = if (weekCount == 1) "aktivitet" else "aktiviteter",
            onClick = onWeek,
            modifier = Modifier.weight(1f),
        )
        CleanSummaryTile(
            label = "KROCKAR",
            value = conflictCount.toString(),
            helper = if (conflictCount == 0) "lugnt" else "att se över",
            accent = if (conflictCount > 0) Color(0xFFFFA56A) else CleanPurpleBright,
            onClick = onConflicts,
            modifier = Modifier.weight(1f),
        )
        CleanSummaryTile(
            label = "PÅMINN.",
            value = reminderCount.toString(),
            helper = if (reminderCount == 1) "påminnelse" else "påminnelser",
            accent = Color(0xFF8FB8FF),
            onClick = onReminders,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CleanSummaryTile(
    label: String,
    value: String,
    helper: String,
    accent: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CleanGlassSoft,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CleanBorder),
        modifier = modifier.height(112.dp).clickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                color = Color.White.copy(alpha = .62f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .7.sp,
                maxLines = 1,
            )
            Text(
                value,
                color = accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                helper,
                color = Color.White.copy(alpha = .56f),
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CleanSummaryDialog(
    kind: CleanSummaryKind,
    selectedDate: LocalDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    dayActivities: List<SyncEvent>,
    weekActivities: List<SyncEvent>,
    conflicts: List<CalendarConflict>,
    reminders: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    locale: Locale,
    onDismiss: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenEvent: (SyncEvent) -> Unit,
) {
    val title =
        when (kind) {
            CleanSummaryKind.TODAY -> "Idag"
            CleanSummaryKind.WEEK -> "Veckan"
            CleanSummaryKind.CONFLICTS -> "Krockar"
            CleanSummaryKind.REMINDERS -> "Påminnelser"
        }
    val subtitle =
        when (kind) {
            CleanSummaryKind.TODAY ->
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                    .replaceFirstChar { it.uppercase(locale) }

            else ->
                weekStart.format(DateTimeFormatter.ofPattern("d MMM", locale)) +
                    " – " +
                    weekEnd.format(DateTimeFormatter.ofPattern("d MMM", locale))
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xE61A1624),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = CleanMuted, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (kind) {
                    CleanSummaryKind.TODAY -> {
                        if (dayActivities.isEmpty()) {
                            CleanSummaryEmpty("Inga aktiviteter den här dagen.")
                        } else {
                            CleanGroupedActivityList(
                                events = dayActivities,
                                memberById = memberById,
                                onOpenEvent = onOpenEvent,
                            )
                        }
                    }

                    CleanSummaryKind.WEEK -> {
                        if (weekActivities.isEmpty()) {
                            CleanSummaryEmpty("Inga aktiviteter den här veckan.")
                        } else {
                            weekActivities.groupBy { it.date }.toSortedMap().forEach { (date, dayEvents) ->
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                                        .replaceFirstChar { it.uppercase(locale) },
                                    color = CleanPurpleBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectDate(date)
                                            onDismiss()
                                        }
                                        .padding(top = 6.dp, bottom = 3.dp),
                                )
                                CleanGroupedActivityList(
                                    events = dayEvents,
                                    memberById = memberById,
                                    onOpenEvent = onOpenEvent,
                                )
                            }
                        }
                    }

                    CleanSummaryKind.CONFLICTS -> {
                        if (conflicts.isEmpty()) {
                            CleanSummaryEmpty("Inga krockar den här veckan.")
                        } else {
                            conflicts.forEach { conflict ->
                                Surface(
                                    color = Color(0xFFFF6B78).copy(alpha = .09f),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF8A94).copy(alpha = .24f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            conflict.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
                                                .replaceFirstChar { it.uppercase(locale) },
                                            color = Color(0xFFFFB1B8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            conflict.message,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.height(5.dp))
                                        listOf(conflict.first, conflict.second)
                                            .distinctBy { it.id }
                                            .forEach { event ->
                                                Text(
                                                    "${cleanEventTime(event)} · ${cleanEventTitle(event)}  ›",
                                                    color = Color(0xFFFFB1B8),
                                                    fontSize = 11.sp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onOpenEvent(event) }
                                                        .padding(vertical = 4.dp),
                                                )
                                            }
                                    }
                                }
                            }
                        }
                    }

                    CleanSummaryKind.REMINDERS -> {
                        if (reminders.isEmpty()) {
                            CleanSummaryEmpty("Inga påminnelser den här veckan.")
                        } else {
                            reminders.forEach { event ->
                                CleanSummaryEventRow(
                                    event = event,
                                    member = event.memberId?.let(memberById::get),
                                    locale = locale,
                                    showDate = true,
                                    reminder = true,
                                    onClick = { onOpenEvent(event) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Stäng", color = CleanPurpleBright) }
        },
    )
}

@Composable
private fun CleanSummaryEmpty(text: String) {
    Surface(
        color = Color.White.copy(alpha = .045f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            color = CleanMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun CleanGroupedActivityList(
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    onOpenEvent: (SyncEvent) -> Unit,
) {
    val grouped =
        events
            .groupBy { it.memberId ?: ALL_FAMILY_MEMBER_ID }
            .entries
            .sortedBy { entry -> entry.value.minOfOrNull { it.time } ?: "99:99" }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        grouped.forEach { (memberKey, rawEvents) ->
            val personEvents =
                rawEvents.sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
            val member = memberById[memberKey]
            val memberName =
                if (memberKey == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                else member?.name ?: "Övrigt"
            val accent =
                if (memberKey == ALL_FAMILY_MEMBER_ID) Color(0xFFFFD75E)
                else member?.let { Color(it.colorArgb.toInt()) } ?: CleanPurpleBright

            if (personEvents.size == 1) {
                val event = personEvents.first()
                Surface(
                    color = Color.White.copy(alpha = .045f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenEvent(event) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = .20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(8.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                cleanEventTitle(event),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${cleanEventTime(event)} · $memberName",
                                color = CleanMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = .38f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                val firstStart = personEvents.first().time.ifBlank { "Hela dagen" }
                val lastEvent = personEvents.last()
                val lastTime =
                    lastEvent.endTime?.takeIf { it.isNotBlank() }
                        ?: lastEvent.time.ifBlank { "Hela dagen" }
                val span =
                    if (firstStart == lastTime) firstStart else "$firstStart–$lastTime"

                Surface(
                    color = Color.White.copy(alpha = .045f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(36.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = .20f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(9.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    memberName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${personEvents.size} aktiviteter · $span",
                                    color = CleanMuted,
                                    fontSize = 10.sp,
                                )
                            }
                        }

                        personEvents.forEachIndexed { index, event ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = .055f),
                                    thickness = .5.dp,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onOpenEvent(event) }
                                    .padding(horizontal = 13.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    cleanEventTime(event),
                                    color = Color.White.copy(alpha = .72f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(88.dp),
                                )
                                Box(
                                    Modifier.width(3.dp)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(accent.copy(alpha = .70f))
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    cleanEventTitle(event),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = .34f),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanSummaryEventRow(
    event: SyncEvent,
    member: SyncMember?,
    locale: Locale,
    showDate: Boolean,
    reminder: Boolean = false,
    onClick: () -> Unit,
) {
    val accent =
        when {
            reminder -> Color(0xFF8FB8FF)
            member != null -> Color(member.colorArgb.toInt())
            event.memberId == ALL_FAMILY_MEMBER_ID -> Color(0xFFFFD75E)
            else -> CleanPurpleBright
        }
    val memberName =
        when {
            event.memberId == ALL_FAMILY_MEMBER_ID -> "Hela familjen"
            member != null -> member.name
            else -> "Familjen"
        }
    Surface(
        color = Color.White.copy(alpha = .045f),
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .18f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(3.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.width(if (showDate) 88.dp else 64.dp)) {
                if (showDate) {
                    Text(
                        event.date.format(DateTimeFormatter.ofPattern("EEE d/M", locale))
                            .replaceFirstChar { it.uppercase(locale) },
                        color = CleanMuted,
                        fontSize = 9.sp,
                    )
                }
                Text(
                    event.time.ifBlank { "Hela dagen" },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    cleanEventTitle(event).removePrefix("🔔").trim(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(memberName, color = CleanMuted, fontSize = 9.sp)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = .38f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun CleanHeader(onSearch: () -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Familjekalender",
                    color = Color.White,
                    fontFamily = FontFamily.Cursive,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text("♡", color = CleanPurpleBright, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "TILLSAMMANS VARJE DAG",
                color = Color.White.copy(alpha = .67f),
                fontSize = 9.sp,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(43.dp)
                    .clip(CircleShape)
                    .background(Color(0x661C1726))
                    .clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Sök",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
            Box(
                Modifier.size(50.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(CleanPurpleBright, CleanPurple)))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Lägg till aktivitet",
                    tint = Color.White,
                    modifier = Modifier.size(27.dp),
                )
            }
        }
    }
}

@Composable
private fun CleanCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    eventsByDate: Map<LocalDate, List<SyncEvent>>,
    memberById: Map<String, SyncMember>,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
) {
    val calendarShape = RoundedCornerShape(30.dp)
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    BoxWithConstraints(
        modifier =
            Modifier.fillMaxWidth()
                .clip(calendarShape)
                .clipToBounds()
                .border(1.2.dp, Color.White.copy(alpha = .17f), calendarShape)
                .pointerInput(month) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                dragOffset.snapTo(
                                    (dragOffset.value + amount).coerceIn(
                                        -size.width.toFloat(),
                                        size.width.toFloat(),
                                    )
                                )
                            }
                        },
                        onDragEnd = {
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val threshold = widthPx * 0.14f
                            val delta =
                                when {
                                    dragOffset.value <= -threshold -> 1L
                                    dragOffset.value >= threshold -> -1L
                                    else -> 0L
                                }
                            scope.launch {
                                if (delta == 0L) {
                                    dragOffset.animateTo(0f, tween(180))
                                } else {
                                    val target = if (delta > 0) -widthPx else widthPx
                                    dragOffset.animateTo(target, tween(260))
                                    onMonthChange(delta)
                                    dragOffset.snapTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragOffset.animateTo(0f, tween(180)) }
                        },
                    )
                },
    ) {
        Box(
            Modifier.fillMaxWidth().graphicsLayer {
                translationX = dragOffset.value
                alpha = 1f - (kotlin.math.abs(dragOffset.value) / constraints.maxWidth.coerceAtLeast(1)) * 0.12f
            }
        ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar {
                        it.uppercase(locale)
                    } + " ${month.year}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )

            }

            Spacer(Modifier.height(12.dp))

            val weekdays = listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN")
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "V",
                    color = Color.White.copy(alpha = .38f),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(22.dp),
                )
                weekdays.forEach { day ->
                    Text(
                        day,
                        color = Color.White.copy(alpha = .52f),
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            val first = month.atDay(1)
            val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
            val weekFields = java.time.temporal.WeekFields.ISO
            repeat(6) { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val weekDate = gridStart.plusDays((row * 7).toLong())
                    Text(
                        weekDate.get(weekFields.weekOfWeekBasedYear()).toString(),
                        color = Color.White.copy(alpha = .38f),
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(22.dp),
                    )
                    repeat(7) { column ->
                        val date = gridStart.plusDays((row * 7 + column).toLong())
                        val inMonth = YearMonth.from(date) == month
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val dayEvents = eventsByDate[date].orEmpty()
                        val dayShape = RoundedCornerShape(12.dp)

                        val glassBrush =
                            when {
                                isToday ->
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFFC06BFF),
                                            Color(0xFF8E3CFF),
                                            Color(0xFF6D22E8),
                                        )
                                    )
                                isSelected ->
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = .43f),
                                            Color.White.copy(alpha = .24f),
                                            Color.White.copy(alpha = .12f),
                                        )
                                    )
                                inMonth ->
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = .085f),
                                            Color.White.copy(alpha = .035f),
                                            Color.White.copy(alpha = .012f),
                                        )
                                    )
                                else ->
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = .025f),
                                            Color.White.copy(alpha = .008f),
                                            Color.Transparent,
                                        )
                                    )
                            }

                        val glassBorder =
                            when {
                                isToday -> Color(0xFFD9B2FF)
                                isSelected -> Color.White.copy(alpha = .82f)
                                inMonth -> Color.White.copy(alpha = .14f)
                                else -> Color.White.copy(alpha = .075f)
                            }

                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .height(56.dp)
                                    .padding(horizontal = 3.dp, vertical = 3.dp)
                                    .shadow(
                                        elevation = 0.dp,
                                        shape = dayShape,
                                        clip = false,
                                    )
                                    .clip(dayShape)
                                    .background(glassBrush, dayShape)
                                    .border(
                                        width =
                                            when {
                                                isToday -> 1.5.dp
                                                isSelected -> 1.25.dp
                                                else -> .8.dp
                                            },
                                        color = glassBorder,
                                        shape = dayShape,
                                    )
                                    .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val hasBirthday = dayEvents.any { it.title.trimStart().startsWith("🌈") }
                            if (hasBirthday) {
                                Canvas(
                                    modifier =
                                        Modifier.align(Alignment.TopCenter)
                                            .padding(top = 2.dp)
                                            .width(24.dp)
                                            .height(12.dp)
                                ) {
                                    val stroke = 2.dp.toPx()
                                    val inset = stroke / 2f
                                    val arcBox = androidx.compose.ui.geometry.Rect(
                                        inset,
                                        inset,
                                        size.width - inset,
                                        size.height * 1.85f,
                                    )
                                    val rainbowColors = listOf(
                                        Color(0xFFFF5A67),
                                        Color(0xFFFFA63D),
                                        Color(0xFFFFE45C),
                                        Color(0xFF55D98B),
                                        Color(0xFF55B8FF),
                                        Color(0xFFA66CFF),
                                    )
                                    rainbowColors.forEachIndexed { index, color ->
                                        val offset = index * stroke * .72f
                                        drawArc(
                                            color = color,
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(arcBox.left + offset, arcBox.top + offset),
                                            size = androidx.compose.ui.geometry.Size(
                                                arcBox.width - offset * 2f,
                                                arcBox.height - offset * 2f,
                                            ),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
                                        )
                                    }
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color =
                                        if (inMonth) Color.White
                                        else Color.White.copy(alpha = .32f),
                                    fontSize = 14.sp,
                                    fontWeight =
                                        when {
                                            isToday -> FontWeight.ExtraBold
                                            isSelected -> FontWeight.Bold
                                            inMonth -> FontWeight.SemiBold
                                            else -> FontWeight.Normal
                                        },
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.height(5.dp),
                                ) {
                                    dayEvents
                                        .filterNot { it.title.trimStart().startsWith("🌈") }
                                        .take(3)
                                        .forEach { event ->
                                            val dotColor =
                                                memberById[event.memberId]?.let {
                                                    Color(it.colorArgb.toInt())
                                                } ?: CleanPurpleBright
                                            Box(
                                                Modifier.size(4.dp)
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
private fun SingleGlassArrowButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier.size(42.dp)
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .15f), Color.White.copy(alpha = .055f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = .23f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.6.dp.toPx()
            val xLeft = if (glyph == "‹") size.width * .66f else size.width * .34f
            val xRight = if (glyph == "‹") size.width * .34f else size.width * .66f
            drawLine(Color.White, Offset(xLeft, size.height * .18f), Offset(xRight, size.height * .50f), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(xRight, size.height * .50f), Offset(xLeft, size.height * .82f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun SmallGlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.White.copy(alpha = .10f),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .23f)),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = Color.White.copy(alpha = .90f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun CleanAgendaCard(
    date: LocalDate,
    today: LocalDate,
    events: List<SyncEvent>,
    memberById: Map<String, SyncMember>,
    locale: Locale,
    onEventClick: (SyncEvent) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onShowAll: () -> Unit,
) {
    val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val weekEnd = weekStart.plusDays(6)
    val weekEvents = remember(events, weekStart) {
        events.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
    }
    val eventsByDay = remember(weekEvents) { weekEvents.groupBy { it.date } }
    val weekLabel = weekStart.format(DateTimeFormatter.ofPattern("d MMM", locale)) + " – " +
        weekEnd.format(DateTimeFormatter.ofPattern("d MMM", locale))

    Surface(
        color = CleanGlassSoft,
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, CleanBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Veckan", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(weekLabel, color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                }
                Text(
                    if (weekEvents.size == 1) "1 aktivitet" else "${weekEvents.size} aktiviteter",
                    color = Color.White.copy(alpha = .58f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            (0L..6L).forEach { offset ->
                val day = weekStart.plusDays(offset)
                val dayEvents = eventsByDay[day].orEmpty()
                val isToday = day == today
                val dayName = day.format(DateTimeFormatter.ofPattern("EEEE", locale))
                    .replaceFirstChar { it.uppercase(locale) }
                val conflicts = analyzeCalendarConflicts(events, memberById.values.toList(), day)
                val hasConflict = conflicts.isNotEmpty()

                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { onDayClick(day) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isToday) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(CleanPurpleBright))
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        dayName,
                        color = if (isToday) Color.White else Color.White.copy(alpha = .76f),
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier.width(74.dp),
                    )
                    Text(
                        day.dayOfMonth.toString(),
                        color = Color.White.copy(alpha = .50f),
                        fontSize = 11.sp,
                        modifier = Modifier.width(28.dp),
                    )
                    if (hasConflict) {
                        Text("!", color = Color(0xFFFF8A94), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (dayEvents.size) {
                            0 -> "Ledigt"
                            1 -> "1 aktivitet"
                            else -> "${dayEvents.size} aktiviteter"
                        },
                        color = if (hasConflict) Color(0xFFFFA0A8) else Color.White.copy(alpha = .52f),
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("›", color = CleanPurpleBright, fontSize = 15.sp)
                }
                if (offset < 6L) {
                    HorizontalDivider(color = Color.White.copy(alpha = .045f), thickness = .5.dp)
                }
            }
        }
    }
}

@Composable
private fun CleanAssistantCard(
    eventCount: Int,
    conflicts: List<CalendarConflict>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CleanGlassSoft,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CleanBorder),
        modifier = modifier.heightIn(min = 100.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(CleanPurple.copy(alpha = .22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", color = CleanPurpleBright, fontSize = 20.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Assistenten",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        conflicts.isNotEmpty() ->
                            if (conflicts.size == 1) conflicts.first().message
                            else "${conflicts.size} saker behöver din uppmärksamhet."
                        eventCount == 0 -> "Allt ser bra ut idag! 🎉"
                        else -> "Du har $eventCount aktiviteter idag."
                    },
                    color =
                        if (conflicts.isNotEmpty()) Color(0xFFFFA0A8)
                        else Color.White.copy(alpha = .67f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = .32f),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun CleanWeatherCard(
    weather: CleanWeatherSnapshot?,
    loading: Boolean,
    hasLocationPermission: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CleanGlassSoft,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CleanBorder),
        modifier = modifier.heightIn(min = 100.dp).clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = Color.White.copy(alpha = .80f),
                modifier = Modifier.size(25.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    weather != null -> "${weather.temperatureC}°"
                    loading -> "…"
                    else -> "—°"
                },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Väder", color = Color.White.copy(alpha = .70f), fontSize = 10.sp)
            Text(
                when {
                    weather != null -> weather.description
                    loading -> "Hämtar…"
                    hasLocationPermission -> "Tryck för väder"
                    else -> "Aktivera plats"
                },
                color = Color.White.copy(alpha = .48f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
