package se.familjekalender.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val locale = remember { Locale("sv", "SE") }
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var openedEvent by remember { mutableStateOf<SyncEvent?>(null) }
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
            )

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CleanAssistantCard(
                    eventCount = selectedEvents.size,
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
                }
            },
            confirmButton = {
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (selectedEvents.isEmpty()) "Allt ser lugnt ut för den valda dagen."
                        else "Du har ${selectedEvents.size} aktiviteter den valda dagen.",
                        color = Color.White.copy(alpha = .76f),
                        fontSize = 14.sp,
                    )
                    selectedEvents.take(6).forEach { event ->
                        val memberName =
                            if (event.memberId == ALL_FAMILY_MEMBER_ID) {
                                "Hela familjen"
                            } else {
                                memberById[event.memberId]?.name ?: "Övrigt"
                            }
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
                                        .background(CleanPurple.copy(alpha = .20f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(7.dp)
                                            .clip(CircleShape)
                                            .background(CleanPurpleBright)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${cleanEventTime(event)} · ${cleanEventTitle(event)} · $memberName",
                                    color = Color.White.copy(alpha = .90f),
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = .38f),
                                    modifier = Modifier.size(18.dp),
                                )
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
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Surface(
        color = Color(0xB31A1424),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = .17f)),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
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
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        )
                    }
                    SmallGlassIconButton(Icons.Default.ChevronLeft, "Föregående månad", onPrevious)
                    SmallGlassIconButton(Icons.Default.ChevronRight, "Nästa månad", onNext)
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
                        val dayEvents = eventsByDate[date].orEmpty()
                        Box(
                            Modifier.weight(1f)
                                .height(48.dp)
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Surface(
                                    color = Color.White.copy(alpha = .11f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = .34f)),
                                    shadowElevation = 13.dp,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(Modifier.fillMaxSize()) {
                                        Box(
                                            Modifier.align(Alignment.TopCenter)
                                                .padding(top = 4.dp)
                                                .width(24.dp)
                                                .height(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = .13f))
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            Text(
                                                date.dayOfMonth.toString(),
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
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
                                }
                            } else {
                                Surface(
                                    color =
                                        if (inMonth) Color.White.copy(alpha = .035f)
                                        else Color.White.copy(alpha = .015f),
                                    shape = RoundedCornerShape(11.dp),
                                    border =
                                        BorderStroke(
                                            .6.dp,
                                            Color.White.copy(alpha = if (inMonth) .09f else .045f),
                                        ),
                                    shadowElevation = if (inMonth) 1.dp else 0.dp,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            color =
                                                when {
                                                    inMonth -> Color.White.copy(alpha = .92f)
                                                    else -> Color.White.copy(alpha = .30f)
                                                },
                                            fontSize = 13.sp,
                                            fontWeight =
                                                if (date == today) FontWeight.Bold
                                                else FontWeight.Normal,
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
                            }
                        }
                    }
                }
            }
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
) {
    val formatted =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar {
            it.uppercase(locale)
        }
    Surface(
        color = CleanGlassSoft,
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, CleanBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (date == today) "Idag • $formatted" else formatted,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (events.size == 1) "1 aktivitet" else "${events.size} aktiviteter",
                    color = CleanMuted,
                    fontSize = 10.sp,
                )
            }

            if (events.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Inga aktiviteter planerade", color = CleanMuted, fontSize = 13.sp)
            } else {
                Spacer(Modifier.height(8.dp))
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
                            Modifier.width(3.dp)
                                .height(35.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(accent)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            event.time,
                            color = Color.White.copy(alpha = .67f),
                            fontSize = 11.sp,
                            modifier = Modifier.width(42.dp),
                        )
                        Box(
                            Modifier.size(24.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = .20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                cleanEventTitle(event),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                                else member?.name ?: "Övrigt",
                                color = Color.White.copy(alpha = .52f),
                                fontSize = 10.sp,
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
                    if (eventCount == 0) "Allt ser bra ut idag! 🎉"
                    else "Du har $eventCount aktiviteter idag.",
                    color = Color.White.copy(alpha = .67f),
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
