package se.familjekalender.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val CleanPurple = Color(0xFF8A5CF6)
private val CleanPurpleBright = Color(0xFFAA72FF)
private val CleanGlass = Color(0xB8171422)
private val CleanGlassSoft = Color(0xA61C1828)
private val CleanBorder = Color.White.copy(alpha = .13f)
private val CleanMuted = Color.White.copy(alpha = .66f)
// Clean calendar reference build marker

private val EmbeddedTimeRange = Regex("""(?:\s*[·•]\s*)?\b\d{1,2}:\d{2}\s*[–-]\s*\d{1,2}:\d{2}\b""")

private fun cleanEventTitle(event: SyncEvent): String =
    event.title
        .replace(EmbeddedTimeRange, "")
        .replace(Regex("""\s*·\s*·\s*"""), " · ")
        .trim()
        .trim('·', '-', ' ')

private fun cleanEventTime(event: SyncEvent): String =
    event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" } ?: event.time

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
) {
    val context = LocalContext.current
    val locale = remember { Locale("sv", "SE") }
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var openedEvent by remember { mutableStateOf<SyncEvent?>(null) }
    var showAllDayActivities by remember { mutableStateOf(false) }
    var showAssistantDetails by remember { mutableStateOf(false) }
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
        Image(
            painter = painterResource(R.drawable.season_autumn),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x26120B16),
                            Color(0x43120C19),
                            Color(0x73110D18),
                        )
                    )
                )
        )

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            CleanHeader(
                onSearch = { showSearch = true },
                onAdd = onAdd,
            )

            Spacer(Modifier.height(12.dp))

            CleanCalendarCard(
                month = month,
                selectedDate = selectedDate,
                today = today,
                locale = locale,
                eventsByDate = eventsByDate,
                memberById = memberById,
                conflictDates = conflictDates,
                onSelect = { date ->
                    month = YearMonth.from(date)
                    onSelect(date)
                },
                onPrevious = {
                    val next = month.minusMonths(1)
                    month = next
                    onSelect(next.atDay(1))
                },
                onNext = {
                    val next = month.plusMonths(1)
                    month = next
                    onSelect(next.atDay(1))
                },
                onToday = {
                    month = YearMonth.from(today)
                    onSelect(today)
                },
            )

            Spacer(Modifier.height(12.dp))

            CleanAgendaCard(
                date = selectedDate,
                today = today,
                events = selectedEvents,
                memberById = memberById,
                locale = locale,
                onEventClick = { openedEvent = it },
                onShowAll = { showAllDayActivities = true },
            )

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CleanAssistantCard(
                    eventCount = selectedEvents.size,
                    conflicts = selectedConflicts,
                    onClick = { showAssistantDetails = true },
                    modifier = Modifier.weight(1.65f),
                )
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
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
        }
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
                                                            .padding(vertical = 10.dp),
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

    if (showAssistantDetails) {
        AlertDialog(
            onDismissRequest = { showAssistantDetails = false },
            containerColor = Color(0xE61A1624),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp)
                            .clip(CircleShape)
                            .background(CleanPurple.copy(alpha = .24f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✦", color = CleanPurpleBright, fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Assistenten",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        when {
                            selectedConflicts.isNotEmpty() ->
                                if (selectedConflicts.size == 1) selectedConflicts.first().message
                                else "${selectedConflicts.size} saker behöver din uppmärksamhet."
                            selectedEvents.isEmpty() -> "Allt ser lugnt ut för den valda dagen."
                            else -> "Du har ${selectedEvents.size} aktiviteter den valda dagen."
                        },
                        color =
                            if (selectedConflicts.isNotEmpty()) Color(0xFFFFA0A8)
                            else Color.White.copy(alpha = .76f),
                        fontSize = 14.sp,
                    )
                    selectedConflicts.take(3).forEach { conflict ->
                        Surface(
                            color = Color(0xFFFF6B78).copy(alpha = .10f),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF8A94).copy(alpha = .22f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    conflict.message,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(5.dp))
                                listOf(conflict.first, conflict.second).distinctBy { it.id }.forEach {
                                    conflictEvent ->
                                    Text(
                                        "${cleanEventTime(conflictEvent)} · ${cleanEventTitle(conflictEvent)}  ›",
                                        color = Color(0xFFFFB1B8),
                                        fontSize = 11.sp,
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .clickable {
                                                    showAssistantDetails = false
                                                    openedEvent = conflictEvent
                                                }
                                                .padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    val assistantGroups =
                        selectedEvents
                            .groupBy { it.memberId ?: ALL_FAMILY_MEMBER_ID }
                            .entries
                            .sortedBy { entry -> entry.value.minOfOrNull { it.time } ?: "99:99" }

                    assistantGroups.forEach { (memberKey, groupEventsRaw) ->
                        val groupEvents =
                            groupEventsRaw.sortedWith(
                                compareBy<SyncEvent> { it.time }.thenBy { it.title }
                            )
                        val member = memberById[memberKey]
                        val memberName =
                            if (memberKey == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                            else member?.name ?: "Övrigt"
                        val accent =
                            if (memberKey == ALL_FAMILY_MEMBER_ID) Color(0xFFFFD75E)
                            else member?.let { Color(it.colorArgb.toInt()) } ?: CleanPurpleBright

                        if (groupEvents.size == 1) {
                            val event = groupEvents.first()
                            Surface(
                                color = Color.White.copy(alpha = .045f),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
                                modifier =
                                    Modifier.fillMaxWidth().clickable {
                                        showAssistantDetails = false
                                        openedEvent = event
                                    },
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
                                            Modifier.size(7.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            cleanEventTitle(event),
                                            color = Color.White.copy(alpha = .92f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
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
                            val firstStart = groupEvents.first().time
                            val lastEvent = groupEvents.last()
                            val lastTime =
                                lastEvent.endTime?.takeIf { it.isNotBlank() } ?: lastEvent.time
                            val span =
                                if (firstStart == lastTime) firstStart else "$firstStart–$lastTime"
                            val hasConflict =
                                selectedConflicts.any { conflict ->
                                    conflict.memberId == memberKey ||
                                            groupEvents.any {
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
                                        if (hasConflict) Color(0xFFFF8A94).copy(alpha = .30f)
                                        else accent.copy(alpha = .22f),
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    Row(
                                        Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    memberName,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                if (hasConflict) {
                                                    Text(
                                                        "Krock",
                                                        color = Color(0xFFFFA0A8),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                            Text(
                                                "${groupEvents.size} aktiviteter · $span",
                                                color = CleanMuted,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = .06f),
                                        thickness = .5.dp,
                                    )

                                    groupEvents.forEachIndexed { index, event ->
                                        val eventHasConflict =
                                            selectedConflicts.any {
                                                it.first.id == event.id || it.second.id == event.id
                                            }
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clickable {
                                                    showAssistantDetails = false
                                                    openedEvent = event
                                                }
                                                .padding(
                                                    start = 13.dp,
                                                    end = 10.dp,
                                                    top = 7.dp,
                                                    bottom = 7.dp,
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                cleanEventTime(event),
                                                color = Color.White.copy(alpha = .68f),
                                                fontSize = 11.sp,
                                                modifier = Modifier.width(82.dp),
                                            )
                                            Box(
                                                Modifier.width(20.dp).height(36.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    Modifier.width(2.dp)
                                                        .fillMaxHeight()
                                                        .background(accent.copy(alpha = .28f))
                                                )
                                                Box(
                                                    Modifier.size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(accent)
                                                )
                                            }
                                            Spacer(Modifier.width(7.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    cleanEventTitle(event),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (eventHasConflict) {
                                                    Text(
                                                        "Dubbelbokning / överlappning",
                                                        color = Color(0xFFFFA0A8),
                                                        fontSize = 9.sp,
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = .32f),
                                                modifier = Modifier.size(17.dp),
                                            )
                                        }
                                        if (index < groupEvents.lastIndex) {
                                            HorizontalDivider(
                                                color = Color.White.copy(alpha = .045f),
                                                thickness = .5.dp,
                                                modifier = Modifier.padding(start = 102.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssistantDetails = false }) {
                    Text("Stäng", color = CleanPurpleBright, fontWeight = FontWeight.SemiBold)
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
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text("♡", color = CleanPurpleBright, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
                Modifier.size(48.dp)
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
                Modifier.size(56.dp)
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
    conflictDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val calendarShape = RoundedCornerShape(30.dp)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .clip(calendarShape)
                .background(Color(0x2414111D))
                .border(1.2.dp, Color.White.copy(alpha = .20f), calendarShape),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
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
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = Color.White.copy(alpha = .075f),
                        shape = RoundedCornerShape(99.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .20f)),
                        shadowElevation = 4.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier.clickable(onClick = onToday),
                    ) {
                        Text(
                            "Idag",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                    SingleGlassArrowButton(
                        glyph = "‹",
                        description = "Föregående månad",
                        onClick = onPrevious,
                    )
                    SingleGlassArrowButton(
                        glyph = "›",
                        description = "Nästa månad",
                        onClick = onNext,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val weekdays = listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN")
            Row(Modifier.fillMaxWidth()) {
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
            repeat(6) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val date = gridStart.plusDays((row * 7 + column).toLong())
                        val inMonth = YearMonth.from(date) == month
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val dayEvents = eventsByDate[date].orEmpty()
                        Box(
                            Modifier.weight(1f)
                                .height(56.dp)
                                .padding(horizontal = 3.dp, vertical = 3.dp)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val dayShape = RoundedCornerShape(12.dp)
                            val dayBrush =
                                when {
                                    isToday ->
                                        Brush.verticalGradient(
                                            listOf(Color(0xFFB65EFF), Color(0xFF7A28F5))
                                        )
                                    isSelected ->
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = .42f),
                                                Color.White.copy(alpha = .18f),
                                            )
                                        )
                                    inMonth ->
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = .135f),
                                                Color(0x36191420),
                                            )
                                        )
                                    else ->
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = .025f),
                                                Color.Transparent,
                                            )
                                        )
                                }
                            Box(
                                modifier =
                                    Modifier.fillMaxSize()
                                        .shadow(
                                            elevation =
                                                when {
                                                    isToday -> 18.dp
                                                    isSelected -> 15.dp
                                                    inMonth -> 7.dp
                                                    else -> 0.dp
                                                },
                                            shape = dayShape,
                                            clip = false,
                                        )
                                        .clip(dayShape)
                                        .background(dayBrush)
                                        .border(
                                            width =
                                                when {
                                                    isToday -> 1.8.dp
                                                    isSelected -> 1.6.dp
                                                    else -> .7.dp
                                                },
                                            color =
                                                when {
                                                    isToday -> Color(0xFFC792FF)
                                                    isSelected -> Color.White.copy(alpha = .88f)
                                                    inMonth -> Color.White.copy(alpha = .15f)
                                                    else -> Color.White.copy(alpha = .05f)
                                                },
                                            shape = dayShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color =
                                            if (inMonth) Color.White
                                            else Color.White.copy(alpha = .28f),
                                        fontSize = 14.sp,
                                        fontWeight =
                                            when {
                                                isToday -> FontWeight.ExtraBold
                                                isSelected -> FontWeight.Bold
                                                else -> FontWeight.SemiBold
                                            },
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.height(5.dp),
                                    ) {
                                        dayEvents.take(3).forEach { event ->
                                            val color =
                                                memberById[event.memberId]?.let {
                                                    Color(it.colorArgb.toInt())
                                                } ?: CleanPurpleBright
                                            Box(
                                                Modifier.size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                            )
                                        }
                                    }
                                }
                            }
                            if (date in conflictDates) {
                                Box(
                                    Modifier.align(Alignment.TopEnd)
                                        .padding(top = 3.dp, end = 3.dp)
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE96A72)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "!",
                                        color = Color.White,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 7.sp,
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
private fun SingleGlassArrowButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier =
            Modifier.size(42.dp)
                .shadow(8.dp, shape, clip = false)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = .16f),
                            Color.White.copy(alpha = .065f),
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = .24f), shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = Color.White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = (-1).dp),
        )
    }
}

@Composable
private fun SmallGlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.White.copy(alpha = .07f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.size(35.dp).clickable(onClick = onClick),
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
    onShowAll: () -> Unit,
) {
    val formatted =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar {
            it.uppercase(locale)
        }
    Surface(
        color = Color(0xB0191622),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.1.dp, Color.White.copy(alpha = .15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatted,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = Color.White.copy(alpha = .055f),
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                    modifier = Modifier.clickable(onClick = onShowAll),
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                            color = Color.White.copy(alpha = .74f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(3.dp))
                        Text("›", color = CleanPurpleBright, fontSize = 13.sp)
                    }
                }
            }

            if (events.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Inga aktiviteter planerade", color = CleanMuted, fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(10.dp))
                events.take(3).forEachIndexed { index, event ->
                    val member = memberById[event.memberId]
                    val accent = member?.let { Color(it.colorArgb.toInt()) } ?: CleanPurpleBright
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onEventClick(event) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(4.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(accent)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            event.time,
                            color = Color.White.copy(alpha = .70f),
                            fontSize = 12.sp,
                            modifier = Modifier.width(58.dp),
                        )
                        Box(
                            Modifier.size(32.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = .20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                cleanEventTitle(event),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                                else member?.name ?: "Övrigt",
                                color = Color.White.copy(alpha = .52f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = .36f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (index < events.take(3).lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = .07f), thickness = .5.dp)
                    }
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
