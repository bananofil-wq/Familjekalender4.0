package se.familjekalender.app

import android.app.Activity
import android.app.DatePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

private data class WorkRuleDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String,
)

private data class WorkRotationWeekDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String,
    val dayTimes: Map<Int, Pair<String, String>> = weekdays.associateWith { startTime to endTime },
)

internal enum class SeriesEditScope {
    THIS,
    THIS_AND_FUTURE,
    WHOLE_SERIES,
}

private data class MaterialTimePickerRequest(
    val initialHour: Int,
    val initialMinute: Int,
    val onPicked: (Int, Int) -> Unit,
)

private fun isLaundryEvent(event: SyncEvent): Boolean {
    val title = event.title.trim()
    return title.startsWith("🧺") || title.equals("Tvätt", ignoreCase = true)
}

private fun displayEventTitle(event: SyncEvent): String =
    event.title.removePrefix("🌈").removePrefix("🧺").trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onPicked: (hour: Int, minute: Int) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPicked(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
        text = {
            TimePicker(state = state)
        },
    )
}

@Composable
internal fun ExactCalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    palette: SeasonPalette,
    themeMode: ThemeMode = palette.mode,
    onAdd: () -> Unit,
    onAddLaundry: () -> Unit,
    addMenuRequest: Int = 0,
) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showWorkMonth by remember { mutableStateOf(false) }
    var showWorkRotation by remember { mutableStateOf(false) }
    var showSchoolSchedule by remember { mutableStateOf(false) }
    var showManageMonth by remember { mutableStateOf(false) }
    var dayPopupDate by remember { mutableStateOf<LocalDate?>(null) }
    var editEvent by remember { mutableStateOf<SyncEvent?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val displayedPalette = paletteFor(themeMode, month.atDay(1))
    val mode = displayedPalette.mode
    val dragOffset = remember { Animatable(0f) }
    // Only react to a NEW + request. ExactCalendarScreen is recreated when the
    // user leaves and returns to the calendar tab; replaying an old non-zero
    // request made the add dialog reopen every time.
    var lastHandledAddMenuRequest by rememberSaveable { mutableIntStateOf(addMenuRequest) }
    LaunchedEffect(addMenuRequest) {
        if (addMenuRequest > lastHandledAddMenuRequest) {
            lastHandledAddMenuRequest = addMenuRequest
            showAddMenu = true
        }
    }

    fun refreshActivity() {
        (context as? Activity)?.recreate()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // The month grid always owns a predictable amount of vertical space.
        // Do not combine weight(), negative offsets and min-height here: that made
        // six equal week rows collapse differently on different screen heights.
        val calendarMinHeight = 390.dp
        val outerVerticalPadding = 20.dp
        val sectionSpacing = 8.dp
        val seasonalHeroMax = 135.dp
        val seasonalHeroMin = 72.dp
        val hasSeasonalHero = mode != ThemeMode.CLASSIC
        val availableForHero = maxHeight - calendarMinHeight - outerVerticalPadding - sectionSpacing
        val heroHeight =
            if (hasSeasonalHero) {
                availableForHero.coerceIn(0.dp, seasonalHeroMax).let {
                    if (it in 1.dp..<seasonalHeroMin) 0.dp else it
                }
            } else 0.dp
        val calendarHeight =
            (maxHeight -
                    outerVerticalPadding -
                    heroHeight -
                    if (heroHeight > 0.dp) sectionSpacing else 0.dp)
                .coerceAtLeast(calendarMinHeight)
        SeasonalPhoto(mode, Modifier.matchParentSize())

        fun settleMonth(delta: Long, widthPx: Float) {
            if (widthPx <= 0f) return
            scope.launch {
                val target = if (delta > 0) -widthPx else widthPx
                dragOffset.animateTo(target, animationSpec = tween(230))
                val next = month.plusMonths(delta)
                month = next
                onSelect(next.atDay(1))
                dragOffset.snapTo(0f)
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (heroHeight > 0.dp) {
                Spacer(Modifier.height(heroHeight))
            }
            BoxWithConstraints(
                modifier =
                    Modifier.fillMaxWidth().height(calendarHeight).clipToBounds().pointerInput(
                        month
                    ) {
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
                                val threshold = widthPx * 0.16f
                                when {
                                    dragOffset.value <= -threshold -> settleMonth(1, widthPx)
                                    dragOffset.value >= threshold -> settleMonth(-1, widthPx)
                                    else ->
                                        scope.launch {
                                            dragOffset.animateTo(0f, animationSpec = tween(180))
                                        }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragOffset.animateTo(0f, animationSpec = tween(180))
                                }
                            },
                        )
                    }
            ) {
                val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val previousMonth = month.minusMonths(1)
                val nextMonth = month.plusMonths(1)

                MonthPanel(
                    month = previousMonth,
                    selected = previousMonth.atDay(1),
                    onSelect = {},
                    events = events,
                    members = members,
                    accent = displayedPalette.accent,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            translationX = dragOffset.value - widthPx
                        },
                )
                MonthPanel(
                    month = nextMonth,
                    selected = nextMonth.atDay(1),
                    onSelect = {},
                    events = events,
                    members = members,
                    accent = displayedPalette.accent,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            translationX = dragOffset.value + widthPx
                        },
                )
                MonthPanel(
                    month = month,
                    selected =
                        if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1),
                    onSelect = { picked ->
                        onSelect(picked)
                        dayPopupDate = picked
                    },
                    events = events,
                    members = members,
                    accent = displayedPalette.accent,
                    onPreviousMonth = { settleMonth(-1, widthPx) },
                    onNextMonth = { settleMonth(1, widthPx) },
                    modifier =
                        Modifier.fillMaxSize().graphicsLayer { translationX = dragOffset.value },
                )
            }
        }
    }

    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("Lägg till") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showAddMenu = false
                            onAdd()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Enstaka aktivitet / egna datum")
                    }
                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkMonth = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Arbetsmånad")
                    }
                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkRotation = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Lägg till arbetsvecka")
                    }
                    Button(
                        onClick = {
                            showAddMenu = false
                            showSchoolSchedule = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Förskola / skola")
                    }
                    Button(
                        onClick = {
                            showAddMenu = false
                            onAddLaundry()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("🧺 Tvätt")
                    }
                    OutlinedButton(
                        onClick = {
                            showAddMenu = false
                            showManageMonth = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text("Hantera / rensa kalender")
                    }
                    Text(
                        "Arbetsmånad och förskola/skola låter dig lägga återkommande tider utan att mata in varje dag för hand.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddMenu = false }) { Text("Avbryt") } },
        )
    }

    if (showWorkMonth) {
        WorkMonthDialog(
            members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
            selectedDate = selectedDate,
            onDismiss = { showWorkMonth = false },
            onChanged = {
                showWorkMonth = false
                refreshActivity()
            },
        )
    }

    if (showWorkRotation) {
        WorkRotationDialog(
            members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
            selectedDate = selectedDate,
            onDismiss = { showWorkRotation = false },
            onChanged = {
                showWorkRotation = false
                refreshActivity()
            },
        )
    }

    if (showSchoolSchedule) {
        SchoolScheduleDialog(
            members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
            selectedDate = selectedDate,
            onDismiss = { showSchoolSchedule = false },
            onChanged = {
                showSchoolSchedule = false
                refreshActivity()
            },
        )
    }

    dayPopupDate?.let { popupDate ->
        DayOverviewPopup(
            date = popupDate,
            events = events.filter { it.date == popupDate }.sortedBy { it.time },
            allEvents = events,
            members = members,
            onDismiss = { dayPopupDate = null },
            onAdd = {
                dayPopupDate = null
                onSelect(popupDate)
                onAdd()
            },
            onEdit = { event ->
                dayPopupDate = null
                editEvent = event
            },
            onDelete = { event ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        runCatching { deleteCalendarEventsDirect(session, listOf(event.id)) }
                            .onSuccess {
                                dayPopupDate = null
                                refreshActivity()
                            }
                    }
                }
            },
            onDeleteMany = { eventsToDelete ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        runCatching {
                            deleteCalendarEventsDirect(session, eventsToDelete.map { it.id })
                        }
                            .onSuccess {
                                dayPopupDate = null
                                refreshActivity()
                            }
                    }
                }
            },
        )
    }

    editEvent?.let { event ->
        val matchingSeries =
            events
                .filter { candidate ->
                    candidate.source != "sportadmin" &&
                            if (event.seriesId != null) {
                                candidate.seriesId == event.seriesId
                            } else {
                                candidate.seriesId == null &&
                                        candidate.source == event.source &&
                                        candidate.memberId == event.memberId &&
                                        candidate.title == event.title &&
                                        candidate.time == event.time &&
                                        candidate.endTime == event.endTime
                            }
                }
                .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
        EditEventDialog(
            event = event,
            members = members,
            hasSeries = matchingSeries.size > 1,
            onDismiss = { editEvent = null },
            onSave = { title, date, time, endTime, memberId, editScope ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        val dayShift = java.time.temporal.ChronoUnit.DAYS.between(event.date, date)
                        val targets =
                            when (editScope) {
                                SeriesEditScope.THIS -> listOf(event)
                                SeriesEditScope.THIS_AND_FUTURE ->
                                    matchingSeries.filter { !it.date.isBefore(event.date) }

                                SeriesEditScope.WHOLE_SERIES -> matchingSeries
                            }
                        runCatching {
                            val splitSeriesId =
                                when {
                                    event.seriesId == null -> null
                                    editScope == SeriesEditScope.THIS_AND_FUTURE ->
                                        java.util.UUID.randomUUID().toString()

                                    else -> event.seriesId
                                }
                            targets.forEach { target ->
                                val targetDate =
                                    if (editScope == SeriesEditScope.THIS) date
                                    else target.date.plusDays(dayShift)
                                SupabaseSync.updateEvent(
                                    session,
                                    target.id,
                                    title,
                                    targetDate,
                                    time,
                                    endTime,
                                    memberId,
                                )
                                when {
                                    event.seriesId == null -> Unit
                                    editScope == SeriesEditScope.THIS ->
                                        SupabaseSync.updateEventSeriesId(session, target.id, null)

                                    editScope == SeriesEditScope.THIS_AND_FUTURE ->
                                        SupabaseSync.updateEventSeriesId(
                                            session,
                                            target.id,
                                            splitSeriesId,
                                        )

                                    editScope == SeriesEditScope.WHOLE_SERIES -> Unit
                                }
                            }
                        }
                            .onSuccess {
                                editEvent = null
                                onSelect(date)
                                refreshActivity()
                            }
                    }
                }
            },
            onDelete = { deleteScope ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        val targets =
                            when (deleteScope) {
                                SeriesEditScope.THIS -> listOf(event)
                                SeriesEditScope.THIS_AND_FUTURE ->
                                    matchingSeries.filter { !it.date.isBefore(event.date) }
                                SeriesEditScope.WHOLE_SERIES -> matchingSeries
                            }
                        val effectiveTargets = if (targets.isEmpty()) listOf(event) else targets
                        runCatching {
                            deleteCalendarEventsDirect(session, effectiveTargets.map { it.id })
                        }.onSuccess {
                            editEvent = null
                            refreshActivity()
                        }
                    }
                }
            },
        )
    }

    if (showManageMonth) {
        ManageMonthEventsDialog(
            month = month,
            events =
                events.filter { YearMonth.from(it.date) == month && it.source != "sportadmin" },
            members = members,
            onDismiss = { showManageMonth = false },
            onDelete = { ids ->
                val session = currentFamilySession(context)
                if (session == null) {
                    showManageMonth = false
                } else {
                    scope.launch {
                        runCatching { deleteCalendarEventsDirect(session, ids) }
                            .onSuccess {
                                showManageMonth = false
                                refreshActivity()
                            }
                    }
                }
            },
        )
    }
}

@Composable
private fun DayOverviewPopup(
    date: LocalDate,
    events: List<SyncEvent>,
    allEvents: List<SyncEvent>,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (SyncEvent) -> Unit,
    onDelete: (SyncEvent) -> Unit,
    onDeleteMany: (List<SyncEvent>) -> Unit,
) {
    val dayName =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar {
            it.uppercase()
        }
    val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))
    var deleteChoiceEvent by remember { mutableStateOf<SyncEvent?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF171A20),
        title = {
            Column {
                Text("$dayName ${date.dayOfMonth} $monthName", fontWeight = FontWeight.Bold)
                Text(
                    "${events.size} ${if (events.size == 1) "aktivitet" else "aktiviteter"}",
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (events.isEmpty()) {
                    Text("Inget inlagt den här dagen.", color = Color.White.copy(alpha = .62f))
                } else {
                    var expandedGroupKeys by remember(date) { mutableStateOf(emptySet<String>()) }
                    val groupedEvents = events.groupBy { it.memberId }
                    groupedEvents.forEach { (memberId, personEvents) ->
                        val groupMember = members.find { it.id == memberId }
                        val groupName =
                            groupMember?.name
                                ?: if (memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                                else "Familjen"
                        val groupColor =
                            if (memberId == ALL_FAMILY_MEMBER_ID) {
                                Color(0xFFFFD75E)
                            } else {
                                groupMember?.let { Color(it.colorArgb.toInt()) }
                                    ?: Color(0xFF8D95A5)
                            }
                        val groupKey = "member:${memberId ?: "unassigned"}"
                        val expanded = groupKey in expandedGroupKeys

                        Surface(
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    expandedGroupKeys =
                                        if (groupKey in expandedGroupKeys) {
                                            expandedGroupKeys - groupKey
                                        } else {
                                            expandedGroupKeys + groupKey
                                        }
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF20242B),
                            border = BorderStroke(1.dp, groupColor.copy(alpha = .34f)),
                        ) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(13.dp).clip(CircleShape).background(groupColor))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        groupName,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                    )
                                    Text(
                                        "${personEvents.size} aktiviteter",
                                        color = Color.White.copy(alpha = .62f),
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    if (expanded) "Dölj" else "Visa",
                                    color = groupColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        if (expanded) {
                            personEvents
                                .sortedBy { it.time }
                                .forEach { event ->
                                    val member = members.find { it.id == event.memberId }
                                    val allFamily = event.memberId == ALL_FAMILY_MEMBER_ID
                                    val birthday = isBirthdayEvent(event)
                                    val laundry = isLaundryEvent(event)
                                    val dotColor =
                                        if (allFamily) Color(0xFFFFD75E)
                                        else
                                            member?.let { Color(it.colorArgb.toInt()) }
                                                ?: Color(0xFF8D95A5)

                                    Surface(
                                        color = Color(0xFF20242B),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            when {
                                                birthday ->
                                                    BirthdayRainbowIcon(
                                                        Modifier.size(width = 26.dp, height = 20.dp)
                                                    )

                                                laundry ->
                                                    Text("🧺", fontSize = 20.sp, lineHeight = 22.sp)

                                                allFamily ->
                                                    Text(
                                                        "★",
                                                        color = Color(0xFFFFD75E),
                                                        fontSize = 22.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )

                                                else ->
                                                    Box(
                                                        Modifier.size(13.dp)
                                                            .clip(CircleShape)
                                                            .background(dotColor)
                                                    )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    displayEventTitle(event),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                )
                                                if (event.time.isNotBlank()) {
                                                    Text(
                                                        event.time,
                                                        color = Color.White.copy(alpha = .62f),
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    if (allFamily) "Hela familjen"
                                                    else member?.name ?: "Familjen",
                                                    color = Color.White.copy(alpha = .64f),
                                                    fontSize = 11.sp,
                                                )
                                                if (event.source != "sportadmin") {
                                                    TextButton(
                                                        onClick = { onEdit(event) },
                                                        contentPadding =
                                                            PaddingValues(
                                                                horizontal = 6.dp,
                                                                vertical = 0.dp,
                                                            ),
                                                    ) {
                                                        Text("Redigera", fontSize = 10.sp)
                                                    }
                                                    TextButton(
                                                        onClick = { deleteChoiceEvent = event },
                                                        contentPadding =
                                                            PaddingValues(
                                                                horizontal = 6.dp,
                                                                vertical = 0.dp,
                                                            ),
                                                    ) {
                                                        Text(
                                                            "Ta bort",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.error,
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
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Lägg till")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stäng") } },
    )

    deleteChoiceEvent?.let { event ->
        val series =
            allEvents
                .filter { candidate ->
                    candidate.source != "sportadmin" &&
                            if (event.seriesId != null) {
                                candidate.seriesId == event.seriesId
                            } else {
                                candidate.seriesId == null &&
                                        candidate.source == event.source &&
                                        candidate.memberId == event.memberId &&
                                        candidate.title == event.title &&
                                        candidate.time == event.time &&
                                        candidate.endTime == event.endTime
                            }
                }
                .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
        val futureSeries = series.filter { candidate ->
            candidate.date.isAfter(event.date) ||
                    (candidate.date == event.date && candidate.time >= event.time)
        }

        AlertDialog(
            onDismissRequest = { deleteChoiceEvent = null },
            title = { Text("Ta bort aktivitet") },
            text = {
                Text(
                    if (series.size > 1)
                        "Välj om bara denna förekomst, denna och framåt eller hela serien ska tas bort."
                    else "Ta bort denna aktivitet?"
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (series.size > 1) {
                        TextButton(
                            onClick = {
                                onDeleteMany(series)
                                deleteChoiceEvent = null
                            }
                        ) {
                            Text("Hela serien", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                onDeleteMany(futureSeries)
                                deleteChoiceEvent = null
                            }
                        ) {
                            Text("Denna och framåt", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Button(
                        onClick = {
                            onDelete(event)
                            deleteChoiceEvent = null
                        }
                    ) {
                        Text("Bara denna")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteChoiceEvent = null }) { Text("Avbryt") }
            },
        )
    }
}

@Composable
internal fun EditEventDialog(
    event: SyncEvent,
    members: List<SyncMember>,
    hasSeries: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, String?, String?, SeriesEditScope) -> Unit,
    onDelete: (SeriesEditScope) -> Unit,
) {
    val context = LocalContext.current
    val birthday = isBirthdayEvent(event)
    val canModify = event.source != "sportadmin"
    var title by remember(event.id) { mutableStateOf(event.title.removePrefix("🌈").trim()) }
    var date by remember(event.id) { mutableStateOf(event.date) }
    var time by remember(event.id) { mutableStateOf(event.time.ifBlank { "18:00" }) }
    var endTime by remember(event.id) { mutableStateOf(event.endTime ?: "") }
    var memberId by remember(event.id) { mutableStateOf(event.memberId ?: ALL_FAMILY_MEMBER_ID) }
    var editScope by remember(event.id) { mutableStateOf(SeriesEditScope.THIS) }
    var timePickerRequest by remember(event.id) { mutableStateOf<MaterialTimePickerRequest?>(null) }
    var showDeleteConfirm by remember(event.id) { mutableStateOf(false) }

    fun chooseDate() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                date = LocalDate.of(year, month + 1, day)
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
    }

    fun chooseTime() {
        val parsed = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        timePickerRequest =
            MaterialTimePickerRequest(parsed.hour, parsed.minute) { hour, minute ->
                time = "%02d:%02d".format(hour, minute)
            }
    }

    fun chooseEndTime() {
        val fallback =
            runCatching { LocalTime.parse(time).plusHours(1) }.getOrElse { LocalTime.of(19, 0) }
        val parsed = runCatching { LocalTime.parse(endTime) }.getOrDefault(fallback)
        timePickerRequest =
            MaterialTimePickerRequest(parsed.hour, parsed.minute) { hour, minute ->
                endTime = "%02d:%02d".format(hour, minute)
            }
    }

    val panelColor = Color(0xE61A1624)
    val innerColor = Color.White.copy(alpha = .045f)
    val outline = Color.White.copy(alpha = .10f)
    val muted = Color.White.copy(alpha = .62f)
    val accent = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = panelColor,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        title = {
            Column {
                Text(
                    "Redigera aktivitet",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (canModify) "Ändra detaljer och spara när du är klar."
                    else "Den här aktiviteten hanteras av SportAdmin.",
                    color = muted,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!canModify) {
                    Surface(
                        color = Color(0xFFFFB86B).copy(alpha = .10f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB86B).copy(alpha = .22f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Den här aktiviteten kommer från SportAdmin. Ändringar och borttagning görs där för att inte skrivas över vid nästa synk.",
                            color = Color.White.copy(alpha = .88f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (birthday) "Namn" else "Aktivitet") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Datum och tid", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = innerColor,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, outline),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = ::chooseDate),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Datum", color = muted, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                            Text(
                                "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text("›", color = accent, fontSize = 20.sp)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            color = innerColor,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, outline),
                            modifier = Modifier.weight(1f).clickable(onClick = ::chooseTime),
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text("Starttid", color = muted, fontSize = 11.sp)
                                Text(time, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            color = innerColor,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, outline),
                            modifier = Modifier.weight(1f).clickable(onClick = ::chooseEndTime),
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text("Sluttid", color = muted, fontSize = 11.sp)
                                Text(
                                    endTime.ifBlank { "Ingen" },
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    if (endTime.isNotBlank()) {
                        TextButton(
                            onClick = { endTime = "" },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Ingen sluttid")
                        }
                    }

                    Text("Gäller för", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    members.forEach { member ->
                        val selected = memberId == member.id
                        val memberColor =
                            if (member.id == ALL_FAMILY_MEMBER_ID) Color(0xFFFFD75E)
                            else Color(member.colorArgb.toInt())
                        Surface(
                            color =
                                if (selected) memberColor.copy(alpha = .16f)
                                else innerColor,
                            shape = RoundedCornerShape(16.dp),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (selected) memberColor.copy(alpha = .62f) else outline,
                                ),
                            modifier =
                                Modifier.fillMaxWidth().clickable { memberId = member.id },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(10.dp).clip(CircleShape).background(memberColor)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (member.id == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                                    else member.name,
                                    color = Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Text("✓", color = memberColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (hasSeries) {
                        Text(
                            "Vad vill du ändra?",
                            color = muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        listOf(
                            SeriesEditScope.THIS to "Bara denna",
                            SeriesEditScope.THIS_AND_FUTURE to "Denna och framåt",
                            SeriesEditScope.WHOLE_SERIES to "Hela serien",
                        ).forEach { (scopeOption, label) ->
                            val selected = editScope == scopeOption
                            Surface(
                                color =
                                    if (selected) accent.copy(alpha = .14f)
                                    else innerColor,
                                shape = RoundedCornerShape(15.dp),
                                border =
                                    BorderStroke(
                                        1.dp,
                                        if (selected) accent.copy(alpha = .55f) else outline,
                                    ),
                                modifier =
                                    Modifier.fillMaxWidth().clickable { editScope = scopeOption },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        label,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                    if (selected) Text("✓", color = accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        border =
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = .45f),
                            ),
                    ) {
                        Text("Ta bort aktivitet", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            if (canModify) {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        onSave(
                            (if (birthday) "🌈 " else "") + title.trim(),
                            date,
                            time,
                            endTime.ifBlank { null },
                            memberId,
                            editScope,
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Spara ändringar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (canModify) "Avbryt" else "Stäng")
            }
        },
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = panelColor,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            title = {
                Text(
                    if (hasSeries) "Vad vill du ta bort?" else "Ta bort aktivitet?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (hasSeries) {
                        Text(
                            "Välj hur stor del av den återkommande aktiviteten som ska tas bort.",
                            color = muted,
                            fontSize = 12.sp,
                        )
                        listOf(
                            SeriesEditScope.THIS to "Bara denna",
                            SeriesEditScope.THIS_AND_FUTURE to "Denna och framåt",
                            SeriesEditScope.WHOLE_SERIES to "Hela serien",
                        ).forEach { (scopeOption, label) ->
                            OutlinedButton(
                                onClick = {
                                    showDeleteConfirm = false
                                    onDelete(scopeOption)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(15.dp),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                            ) {
                                Text(label)
                            }
                        }
                    } else {
                        Text(
                            "Är du säker på att du vill ta bort \"${displayEventTitle(event)}\"?",
                            color = Color.White.copy(alpha = .82f),
                            fontSize = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                if (!hasSeries) {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete(SeriesEditScope.THIS)
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text("Ta bort")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Avbryt")
                }
            },
        )
    }

    timePickerRequest?.let { request ->
        MyTimePickerDialog(
            initialHour = request.initialHour,
            initialMinute = request.initialMinute,
            onDismiss = { timePickerRequest = null },
            onPicked = { hour, minute ->
                request.onPicked(hour, minute)
                timePickerRequest = null
            },
        )
    }
}

@Composable
private fun SeasonalPhoto(mode: ThemeMode, modifier: Modifier) {
    val imageRes =
        when (mode) {
            ThemeMode.WINTER -> R.drawable.season_winter
            ThemeMode.SPRING -> R.drawable.season_spring
            ThemeMode.SUMMER -> R.drawable.season_summer
            ThemeMode.AUTUMN -> R.drawable.season_autumn
            ThemeMode.CLASSIC,
            ThemeMode.AUTO -> null
        }

    if (imageRes == null) return

    Image(
        painter = painterResource(imageRes),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
    )
}

@Composable
private fun MonthPanel(
    month: YearMonth,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    accent: Color,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier,
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val monthName =
        month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar {
            it.uppercase()
        }
    val weekFields = WeekFields.of(Locale("sv", "SE"))

    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBF131820)),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$monthName ${month.year}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = 8.dp),
                )
                TextButton(
                    onClick = onPreviousMonth,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onNextMonth,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("›", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "v",
                    color = Color.White.copy(alpha = .55f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(18.dp),
                )
                listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach {
                    Text(
                        it,
                        color = Color(0xFFBBBAC2),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            repeat(6) { week ->
                val rowMonday = month.atDay(1).minusDays(offset.toLong()).plusWeeks(week.toLong())
                val weekNumber = rowMonday.get(weekFields.weekOfWeekBasedYear())
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        "$weekNumber",
                        color = Color.White.copy(alpha = .58f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(18.dp).align(Alignment.CenterVertically),
                    )
                    repeat(7) { column ->
                        val number = week * 7 + column - offset + 1
                        val validDay = number in 1..month.lengthOfMonth()
                        val day = if (validDay) month.atDay(number) else null
                        val selectedDay = day == selected
                        val todayDay = day == LocalDate.now()
                        val dayEvents =
                            if (day == null) {
                                emptyList()
                            } else {
                                val allDayEvents = events.filter { it.date == day }
                                val firstBirthday = allDayEvents.firstOrNull { isBirthdayEvent(it) }
                                val firstLaundry = allDayEvents.firstOrNull { isLaundryEvent(it) }
                                val firstAllFamily = allDayEvents.firstOrNull {
                                    !isBirthdayEvent(it) &&
                                            !isLaundryEvent(it) &&
                                            it.memberId == ALL_FAMILY_MEMBER_ID
                                }
                                val uniqueMembers =
                                    allDayEvents
                                        .filter {
                                            !isBirthdayEvent(it) &&
                                                    !isLaundryEvent(it) &&
                                                    it.memberId != ALL_FAMILY_MEMBER_ID
                                        }
                                        .distinctBy { it.memberId }
                                buildList {
                                    if (firstBirthday != null) add(firstBirthday)
                                    if (firstLaundry != null) add(firstLaundry)
                                    if (firstAllFamily != null) add(firstAllFamily)
                                    addAll(uniqueMembers)
                                }
                                    .take(3)
                            }

                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        when {
                                            day == null -> Color.Transparent
                                            selectedDay && todayDay -> accent.copy(alpha = .92f)
                                            todayDay -> accent.copy(alpha = .82f)
                                            selectedDay -> Color.White.copy(alpha = .26f)
                                            else -> Color(0x661B2028)
                                        }
                                ),
                            border =
                                if (day == null) null
                                else
                                    BorderStroke(
                                        if (todayDay) 2.dp else 1.dp,
                                        when {
                                            selectedDay && todayDay -> Color.White.copy(alpha = .98f)
                                            todayDay -> accent.copy(alpha = .98f)
                                            selectedDay -> Color.White.copy(alpha = .82f)
                                            else -> Color.White.copy(alpha = .24f)
                                        },
                                    ),
                            elevation =
                                CardDefaults.cardElevation(
                                    defaultElevation =
                                        if (day == null) 0.dp else if (todayDay) 12.dp else if (selectedDay) 9.dp else 6.dp
                                ),
                            shape = RoundedCornerShape(10.dp),
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 1.dp, vertical = 2.dp)
                                    .then(
                                        if (day != null) Modifier.clickable { onSelect(day) }
                                        else Modifier
                                    ),
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (day != null) {
                                    Text(
                                        "$number",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 15.sp,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        dayEvents.forEach { event ->
                                            when {
                                                isBirthdayEvent(event) ->
                                                    BirthdayRainbowIcon(
                                                        Modifier.size(width = 20.dp, height = 16.dp)
                                                    )

                                                isLaundryEvent(event) ->
                                                    Text("🧺", fontSize = 14.sp, lineHeight = 16.sp)

                                                event.memberId == ALL_FAMILY_MEMBER_ID ->
                                                    Text(
                                                        "★",
                                                        color = Color(0xFFFFD75E),
                                                        fontSize = 15.sp,
                                                        lineHeight = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )

                                                else -> {
                                                    val member = members.find {
                                                        it.id == event.memberId
                                                    }
                                                    val dotColor =
                                                        member?.let { Color(it.colorArgb.toInt()) }
                                                            ?: Color(0xFF8D95A5)
                                                    Box(
                                                        Modifier.size(9.dp)
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
    }
}

@Composable
private fun DayPanel(
    date: LocalDate,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    p: SeasonPalette,
    modifier: Modifier,
    onAdd: () -> Unit,
    onManageMany: () -> Unit,
    onDelete: (SyncEvent) -> Unit,
    onChangePerson: (SyncEvent, String?) -> Unit,
) {
    var selectedEvent by remember { mutableStateOf<SyncEvent?>(null) }

    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xBF131820)),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp)) {
            val dayName =
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar {
                    it.uppercase()
                }
            val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$dayName ${date.dayOfMonth} $monthName",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${events.size} ${if (events.size == 1) "aktivitet" else "aktiviteter"}",
                    color = Color.White.copy(alpha = .68f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
                if (events.any { it.source != "sportadmin" }) {
                    TextButton(
                        onClick = onManageMany,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text("Hantera", fontSize = 9.sp)
                    }
                }
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = Color(0xFF8B21FF),
                    contentColor = Color.White,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Default.Add, "Lägg till", modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                events.forEach { event ->
                    val member = members.find { it.id == event.memberId }
                    val allFamily = event.memberId == ALL_FAMILY_MEMBER_ID
                    val birthday = isBirthdayEvent(event)
                    val laundry = isLaundryEvent(event)
                    val dotColor =
                        if (allFamily) Color(0xFFFFD75E)
                        else member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                    Surface(
                        color = Color(0xD9191D24),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedEvent = event },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (birthday) {
                                Text(
                                    "🌈",
                                    fontSize = 20.sp,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.width(32.dp),
                                )
                            } else if (laundry) {
                                Text(
                                    "🧺",
                                    fontSize = 20.sp,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.width(32.dp),
                                )
                            } else if (allFamily) {
                                Text(
                                    "★",
                                    color = Color(0xFFFFD75E),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(32.dp),
                                )
                            } else {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(dotColor))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayEventTitle(event),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (event.time.isNotBlank())
                                    Text(
                                        event.time,
                                        color = Color.White.copy(alpha = .62f),
                                        fontSize = 11.sp,
                                    )
                            }
                            Text(
                                if (allFamily) "Hela familjen" else member?.name ?: "Familjen",
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedEvent?.let { event ->
        val currentMember = members.find { it.id == event.memberId }
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text(displayEventTitle(event)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tid: ${event.time.ifBlank { "Ingen tid" }}")
                    Text(
                        "Gäller: ${if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else currentMember?.name ?: "Familjen"}"
                    )
                    if (event.source != "sportadmin") {
                        Text("Ändra person", fontWeight = FontWeight.SemiBold)
                        members.forEach { member ->
                            TextButton(
                                onClick = {
                                    onChangePerson(event, member.id)
                                    selectedEvent = null
                                }
                            ) {
                                Text(member.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text("Stäng") } },
            dismissButton = {
                if (event.source != "sportadmin") {
                    TextButton(
                        onClick = {
                            onDelete(event)
                            selectedEvent = null
                        }
                    ) {
                        Text("Ta bort")
                    }
                }
            },
        )
    }
}

@Composable
private fun WorkRotationDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = currentFamilySession(context)
    var rotating by remember { mutableStateOf(true) }
    var rotationWeeks by remember { mutableStateOf(4) }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var startDate by remember {
        mutableStateOf(
            selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong()).plusWeeks(1)
        )
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingWeek by remember { mutableStateOf<Int?>(null) }
    var timePickerRequest by remember { mutableStateOf<MaterialTimePickerRequest?>(null) }
    var weeks by remember {
        mutableStateOf(
            listOf(
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "06:00", "14:00"),
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "14:00", "22:00"),
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "08:00", "16:00"),
                WorkRotationWeekDraft(emptySet(), "06:00", "14:00"),
            )
        )
    }

    fun pickTime(index: Int, day: Int, start: Boolean) {
        val week = weeks[index]
        val currentTimes = week.dayTimes[day] ?: (week.startTime to week.endTime)
        val current = if (start) currentTimes.first else currentTimes.second
        val parsed = runCatching { LocalTime.parse(current) }.getOrDefault(LocalTime.of(6, 0))
        timePickerRequest =
            MaterialTimePickerRequest(parsed.hour, parsed.minute) { h, m ->
                val value = "%02d:%02d".format(h, m)
                weeks =
                    weeks.toMutableList().also { list ->
                        val old = list[index]
                        val oldTimes = old.dayTimes[day] ?: (old.startTime to old.endTime)
                        val updatedTimes =
                            if (start) value to oldTimes.second else oldTimes.first to value
                        list[index] = old.copy(dayTimes = old.dayTimes + (day to updatedTimes))
                    }
            }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF17131D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (!saving) onDismiss() },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("‹", fontSize = 28.sp)
                }
                Text(
                    "Lägg till arbetsvecka",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF21182B))
                ) {
                    TextButton(
                        onClick = {
                            rotating = false
                            rotationWeeks = 1
                        },
                        modifier =
                            Modifier.weight(1f)
                                .background(
                                    if (!rotating) Color(0xFF9C4DFF) else Color.Transparent
                                ),
                    ) {
                        Text(
                            "Fast schema",
                            color =
                                if (!rotating) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TextButton(
                        onClick = {
                            rotating = true
                            rotationWeeks = 4
                        },
                        modifier =
                            Modifier.weight(1f)
                                .background(if (rotating) Color(0xFF9C4DFF) else Color.Transparent),
                    ) {
                        Text(
                            "Roterande schema",
                            color =
                                if (rotating) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (members.isNotEmpty()) {
                    Text(
                        "Person",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        members.take(4).forEach { member ->
                            FilterChip(
                                selected = selectedMemberId == member.id,
                                onClick = { selectedMemberId = member.id },
                                label = { Text(member.name, maxLines = 1) },
                            )
                        }
                    }
                }

                if (rotating) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Antal veckor i rotation",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                            )
                            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                Text("4 veckor")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Startdatum för rotation",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                            )
                            OutlinedButton(
                                onClick = {
                                    android.app
                                        .DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val picked = LocalDate.of(y, m + 1, d)
                                                startDate =
                                                    picked.minusDays(
                                                        (picked.dayOfWeek.value - 1).toLong()
                                                    )
                                            },
                                            startDate.year,
                                            startDate.monthValue - 1,
                                            startDate.dayOfMonth,
                                        )
                                        .show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "${startDate.dayOfMonth}/${startDate.monthValue} ${startDate.year}",
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val visibleWeeks = if (rotating) 4 else 1
                    repeat(visibleWeeks) { index ->
                        val week = weeks[index]
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF201925),
                            border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .38f)),
                        ) {
                            Column(
                                Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    "Vecka ${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    when {
                                        week.weekdays.isEmpty() -> "Ledig"
                                        week.dayTimes
                                            .filterKeys { it in week.weekdays }
                                            .values
                                            .distinct()
                                            .size <= 1 -> {
                                            val t =
                                                week.dayTimes[week.weekdays.first()]
                                                    ?: (week.startTime to week.endTime)
                                            "${t.first} – ${t.second}"
                                        }

                                        else -> "${week.weekdays.size} pass · olika tider"
                                    },
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                )
                                Button(
                                    onClick = { editingWeek = index },
                                    contentPadding =
                                        PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF9C4DFF),
                                            contentColor = Color.White,
                                        ),
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                ) {
                                    Text("Redigera", fontSize = 9.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (rotating) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = Color(0xFF21182B),
                        border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .38f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "↻   Schemat upprepas automatiskt: Vecka 1 → 2 → 3 → 4 → 1 …",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled =
                    !saving &&
                            session != null &&
                            selectedMemberId.isNotBlank() &&
                            weeks.take(rotationWeeks).any { it.weekdays.isNotEmpty() },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C4DFF),
                        contentColor = Color.White,
                    ),
                onClick = {
                    val activeSession = session ?: return@Button
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            val seriesId = java.util.UUID.randomUUID().toString()
                            repeat(52) { weekIndex ->
                                val template = weeks[weekIndex % rotationWeeks]
                                val monday = startDate.plusWeeks(weekIndex.toLong())
                                template.weekdays.sorted().forEach { day ->
                                    val date = monday.plusDays((day - 1).toLong())
                                    val times =
                                        template.dayTimes[day]
                                            ?: (template.startTime to template.endTime)
                                    val eventTitle = "Jobb · ${times.first}–${times.second}"
                                    SupabaseSync.addEvent(
                                        activeSession,
                                        eventTitle,
                                        date,
                                        times.first,
                                        times.second,
                                        selectedMemberId,
                                        seriesId,
                                    )
                                }
                            }
                        }
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Kunde inte spara arbetsveckan" }
                        saving = false
                    }
                },
            ) {
                Text(if (saving) "Sparar…" else "Spara")
            }
        },
        dismissButton = {},
    )

    editingWeek?.let { index ->
        val week = weeks[index]
        AlertDialog(
            onDismissRequest = { editingWeek = null },
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(22.dp),
            containerColor = Color(0xFF17131D),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Redigera vecka ${index + 1}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Arbetsdagar och tider", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Varje dag kan ha sin egen arbetstid.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    )
                    val dayLabels = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")
                    dayLabels.forEachIndexed { dayIndex, label ->
                        val day = dayIndex + 1
                        val enabled = day in week.weekdays
                        val times = week.dayTimes[day] ?: (week.startTime to week.endTime)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF21182B),
                            border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .28f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = enabled,
                                        onCheckedChange = { checked ->
                                            weeks =
                                                weeks.toMutableList().also { list ->
                                                    val old = list[index]
                                                    val newDays =
                                                        if (checked) old.weekdays + day
                                                        else old.weekdays - day
                                                    val newTimes =
                                                        if (checked && day !in old.dayTimes) {
                                                            old.dayTimes +
                                                                    (day to
                                                                            (old.startTime to old.endTime))
                                                        } else old.dayTimes
                                                    list[index] =
                                                        old.copy(
                                                            weekdays = newDays,
                                                            dayTimes = newTimes,
                                                        )
                                                }
                                        },
                                    )
                                    Text(
                                        label,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!enabled)
                                        Text(
                                            "Ledig",
                                            color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = .55f
                                                ),
                                            fontSize = 12.sp,
                                        )
                                }
                                if (enabled) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = { pickTime(index, day, true) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Från ${times.first}", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { pickTime(index, day, false) },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Till ${times.second}", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { editingWeek = null },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C4DFF),
                            contentColor = Color.White,
                        ),
                ) {
                    Text("Klar")
                }
            },
            dismissButton = {},
        )
    }

    timePickerRequest?.let { request ->
        MyTimePickerDialog(
            initialHour = request.initialHour,
            initialMinute = request.initialMinute,
            onDismiss = { timePickerRequest = null },
            onPicked = { hour, minute ->
                request.onPicked(hour, minute)
                timePickerRequest = null
            },
        )
    }
}

@Composable
private fun WorkMonthDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = currentFamilySession(context)
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var title by remember { mutableStateOf("Jobb") }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var replaceExisting by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var timePickerRequest by remember { mutableStateOf<MaterialTimePickerRequest?>(null) }
    var rules by remember {
        mutableStateOf(listOf(WorkRuleDraft(setOf(1, 2, 3, 4, 5), "06:00", "14:18")))
    }

    fun pickTime(current: String, onPicked: (String) -> Unit) {
        val parsed = runCatching { LocalTime.parse(current) }.getOrDefault(LocalTime.of(6, 0))
        timePickerRequest =
            MaterialTimePickerRequest(parsed.hour, parsed.minute) { h, m ->
                onPicked("%02d:%02d".format(h, m))
            }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF17131D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Arbetsmånad") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                    Text(
                        "${
                            month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))
                                .replaceFirstChar { it.uppercase() }
                        } ${month.year}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
                }
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Rubrik") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Person", fontWeight = FontWeight.SemiBold)
                members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMemberId == member.id,
                            onClick = { selectedMemberId = member.id },
                        )
                        Text(member.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(replaceExisting, { replaceExisting = it })
                    Text("Ersätt befintliga '$title'-pass för personen denna månad")
                }
                rules.forEachIndexed { index, rule ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Regel ${index + 1}", fontWeight = FontWeight.SemiBold)
                            Row {
                                listOf(
                                    "M" to 1,
                                    "T" to 2,
                                    "O" to 3,
                                    "T" to 4,
                                    "F" to 5,
                                    "L" to 6,
                                    "S" to 7,
                                )
                                    .forEach { (label, day) ->
                                        FilterChip(
                                            selected = day in rule.weekdays,
                                            onClick = {
                                                val nextDays =
                                                    if (day in rule.weekdays) rule.weekdays - day
                                                    else rule.weekdays + day
                                                rules =
                                                    rules.toMutableList().also {
                                                        it[index] = rule.copy(weekdays = nextDays)
                                                    }
                                            },
                                            label = { Text(label, fontSize = 10.sp) },
                                            modifier = Modifier.padding(end = 2.dp),
                                        )
                                    }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        pickTime(rule.startTime) { value ->
                                            rules =
                                                rules.toMutableList().also {
                                                    it[index] = rule.copy(startTime = value)
                                                }
                                        }
                                    }
                                ) {
                                    Text("Från ${rule.startTime}")
                                }
                                OutlinedButton(
                                    onClick = {
                                        pickTime(rule.endTime) { value ->
                                            rules =
                                                rules.toMutableList().also {
                                                    it[index] = rule.copy(endTime = value)
                                                }
                                        }
                                    }
                                ) {
                                    Text("Till ${rule.endTime}")
                                }
                            }
                            if (rules.size > 1)
                                TextButton(
                                    onClick = {
                                        rules = rules.toMutableList().also { it.removeAt(index) }
                                    }
                                ) {
                                    Text("Ta bort regel")
                                }
                        }
                    }
                }
                TextButton(
                    onClick = { rules = rules + WorkRuleDraft(emptySet(), "14:00", "22:00") }
                ) {
                    Text("+ Lägg till regel")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled =
                    !saving &&
                            session != null &&
                            selectedMemberId.isNotBlank() &&
                            rules.any { it.weekdays.isNotEmpty() },
                onClick = {
                    val activeSession = session ?: return@Button
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            val rows = mutableListOf<WorkMonthEventInput>()
                            for (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                rules
                                    .filter { date.dayOfWeek.value in it.weekdays }
                                    .forEach { rule ->
                                        rows +=
                                            WorkMonthEventInput(
                                                title.trim().ifBlank { "Jobb" },
                                                date,
                                                rule.startTime,
                                                rule.endTime,
                                                selectedMemberId,
                                            )
                                    }
                            }
                            saveWorkMonthDirect(
                                activeSession,
                                month,
                                title.trim().ifBlank { "Jobb" },
                                selectedMemberId,
                                rows,
                                replaceExisting,
                            )
                        }
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Kunde inte spara arbetsmånaden" }
                        saving = false
                    }
                },
            ) {
                Text(if (saving) "Sparar…" else "Spara månaden")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Avbryt") } },
    )

    timePickerRequest?.let { request ->
        MyTimePickerDialog(
            initialHour = request.initialHour,
            initialMinute = request.initialMinute,
            onDismiss = { timePickerRequest = null },
            onPicked = { hour, minute ->
                request.onPicked(hour, minute)
                timePickerRequest = null
            },
        )
    }
}

@Composable
private fun ManageMonthEventsDialog(
    month: YearMonth,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onDelete: (List<String>) -> Unit,
) {
    var selectedIds by remember(events) { mutableStateOf(events.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hantera månadens aktiviteter") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Markera aktiviteterna du vill ta bort. SportAdmin-poster lämnas orörda.")
                Row {
                    TextButton(onClick = { selectedIds = events.map { it.id }.toSet() }) {
                        Text("Markera alla")
                    }
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Avmarkera") }
                }
                events.forEach { event ->
                    val memberName =
                        members.find { it.id == event.memberId }?.name
                            ?: if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen"
                            else "Familjen"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = event.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds =
                                    if (checked) selectedIds + event.id else selectedIds - event.id
                            },
                        )
                        Column {
                            Text(
                                "${event.date.dayOfMonth}/${event.date.monthValue} ${event.time} ${event.title}"
                            )
                            Text(
                                memberName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedIds.isNotEmpty(),
                onClick = { onDelete(selectedIds.toList()) },
            ) {
                Text("Ta bort ${selectedIds.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stäng") } },
    )
}
