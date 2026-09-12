package se.familjekalender.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

private data class WorkRuleDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String
)

private data class WorkRotationWeekDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String,
    val dayTimes: Map<Int, Pair<String, String>> = weekdays.associateWith { startTime to endTime }
)

private fun isLaundryEvent(event: SyncEvent): Boolean {
    val title = event.title.trim()
    return title.startsWith("🧺") || title.equals("Tvätt", ignoreCase = true)
}

private fun displayEventTitle(event: SyncEvent): String =
    event.title.removePrefix("🌈").removePrefix("🧺").trim()

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
    addMenuRequest: Int = 0
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
        // Keep enough vertical room for the complete six-week month grid.
        // The photo remains at its natural ratio and simply becomes smaller on
        // short phones instead of squeezing the calendar cells to zero height.
        val calendarMinHeight = 390.dp
        // Keep some of the seasonal artwork visible above the calendar, but never let
        // that hero area steal the height needed by the six week rows.
        val heroHeight = (maxHeight - calendarMinHeight - 8.dp).coerceIn(72.dp, 135.dp)
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
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mode != ThemeMode.CLASSIC) {
                Spacer(Modifier.height(heroHeight))
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-38).dp)
                    .weight(1f)
                    .heightIn(min = calendarMinHeight)
                    .clipToBounds()
                    .pointerInput(month) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                scope.launch {
                                    dragOffset.snapTo((dragOffset.value + amount).coerceIn(-size.width.toFloat(), size.width.toFloat()))
                                }
                            },
                            onDragEnd = {
                                val widthPx = size.width.toFloat().coerceAtLeast(1f)
                                val threshold = widthPx * 0.16f
                                when {
                                    dragOffset.value <= -threshold -> settleMonth(1, widthPx)
                                    dragOffset.value >= threshold -> settleMonth(-1, widthPx)
                                    else -> scope.launch { dragOffset.animateTo(0f, animationSpec = tween(180)) }
                                }
                            },
                            onDragCancel = { scope.launch { dragOffset.animateTo(0f, animationSpec = tween(180)) } }
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
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = dragOffset.value - widthPx }
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
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = dragOffset.value + widthPx }
                )
                MonthPanel(
                    month = month,
                    selected = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1),
                    onSelect = { picked ->
                        onSelect(picked)
                        dayPopupDate = picked
                    },
                    events = events,
                    members = members,
                    accent = displayedPalette.accent,
                    onPreviousMonth = { settleMonth(-1, widthPx) },
                    onNextMonth = { settleMonth(1, widthPx) },
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = dragOffset.value }
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
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Enstaka aktivitet / egna datum") }
                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkMonth = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Arbetsmånad") }
                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkRotation = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lägg till arbetsvecka") }
                    Button(
                        onClick = {
                            showAddMenu = false
                            showSchoolSchedule = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Förskola / skola") }
                    Button(
                        onClick = {
                            showAddMenu = false
                            onAddLaundry()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🧺 Tvätt") }
                    Text(
                        "Arbetsmånad och förskola/skola låter dig lägga återkommande tider utan att mata in varje dag för hand.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddMenu = false }) { Text("Avbryt") } }
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
            }
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
            }
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
            }
        )
    }

    dayPopupDate?.let { popupDate ->
        DayOverviewPopup(
            date = popupDate,
            events = events.filter { it.date == popupDate }.sortedBy { it.time },
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
            }
        )
    }

    editEvent?.let { event ->
        EditEventDialog(
            event = event,
            members = members,
            onDismiss = { editEvent = null },
            onSave = { title, date, time, memberId ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        runCatching { SupabaseSync.updateEvent(session, event.id, title, date, time, memberId) }
                            .onSuccess {
                                editEvent = null
                                onSelect(date)
                                refreshActivity()
                            }
                    }
                }
            }
        )
    }

    if (showManageMonth) {
        ManageMonthEventsDialog(
            month = month,
            events = events.filter { YearMonth.from(it.date) == month && it.source != "sportadmin" },
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
            }
        )
    }
}

@Composable
private fun DayOverviewPopup(
    date: LocalDate,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (SyncEvent) -> Unit,
    onDelete: (SyncEvent) -> Unit
) {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))

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
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (events.isEmpty()) {
                    Text("Inget inlagt den här dagen.", color = Color.White.copy(alpha = .62f))
                } else {
                    events.forEach { event ->
                        val member = members.find { it.id == event.memberId }
                        val allFamily = event.memberId == ALL_FAMILY_MEMBER_ID
                        val birthday = isBirthdayEvent(event)
                        val laundry = isLaundryEvent(event)
                        val dotColor = if (allFamily) Color(0xFFFFD75E) else member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)

                        Surface(
                            color = Color(0xFF20242B),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when {
                                    birthday -> BirthdayRainbowIcon(Modifier.size(width = 26.dp, height = 20.dp))
                                    laundry -> Text("🧺", fontSize = 20.sp, lineHeight = 22.sp)
                                    allFamily -> Text("★", color = Color(0xFFFFD75E), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    else -> Box(Modifier.size(13.dp).clip(CircleShape).background(dotColor))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        displayEventTitle(event),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (event.time.isNotBlank()) {
                                        Text(event.time, color = Color.White.copy(alpha = .62f), fontSize = 12.sp)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (allFamily) "Hela familjen" else member?.name ?: "Familjen",
                                        color = Color.White.copy(alpha = .64f),
                                        fontSize = 11.sp
                                    )
                                    if (event.source != "sportadmin") {
                                        TextButton(
                                            onClick = { onEdit(event) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Redigera", fontSize = 10.sp)
                                        }
                                        TextButton(
                                            onClick = { onDelete(event) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Ta bort", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}

@Composable
private fun EditEventDialog(
    event: SyncEvent,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, String?) -> Unit
) {
    val context = LocalContext.current
    val birthday = isBirthdayEvent(event)
    var title by remember(event.id) { mutableStateOf(event.title.removePrefix("🌈").trim()) }
    var date by remember(event.id) { mutableStateOf(event.date) }
    var time by remember(event.id) { mutableStateOf(event.time.ifBlank { "18:00" }) }
    var memberId by remember(event.id) { mutableStateOf(event.memberId ?: ALL_FAMILY_MEMBER_ID) }

    fun chooseDate() {
        DatePickerDialog(context, { _, year, month, day ->
            date = LocalDate.of(year, month + 1, day)
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    fun chooseTime() {
        val parsed = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        TimePickerDialog(context, { _, hour, minute ->
            time = "%02d:%02d".format(hour, minute)
        }, parsed.hour, parsed.minute, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Redigera aktivitet") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(if (birthday) "Namn" else "Aktivitet") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = ::chooseDate, modifier = Modifier.fillMaxWidth()) {
                    Text("Datum: ${date.dayOfMonth}/${date.monthValue} ${date.year}")
                }
                OutlinedButton(onClick = ::chooseTime, modifier = Modifier.fillMaxWidth()) { Text("Tid: $time") }
                Text("Gäller", fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().clickable { memberId = member.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = memberId == member.id, onClick = { memberId = member.id })
                        Text(if (member.id == ALL_FAMILY_MEMBER_ID) "Hela familjen" else member.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onSave((if (birthday) "🌈 " else "") + title.trim(), date, time, memberId) }
            ) { Text("Spara") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}

@Composable
private fun SeasonalPhoto(mode: ThemeMode, modifier: Modifier) {
    val imageRes = when (mode) {
        ThemeMode.WINTER -> R.drawable.season_winter
        ThemeMode.SPRING -> R.drawable.season_spring
        ThemeMode.SUMMER -> R.drawable.season_summer
        ThemeMode.AUTUMN -> R.drawable.season_autumn
        ThemeMode.CLASSIC, ThemeMode.AUTO -> null
    }

    if (imageRes == null) return

    Image(
        painter = painterResource(imageRes),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter
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
    modifier: Modifier
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    val weekFields = WeekFields.of(Locale("sv", "SE"))

    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xBF131820))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$monthName ${month.year}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = 8.dp)
                )
                TextButton(onClick = onPreviousMonth, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("‹", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onNextMonth, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("›", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("v", color = Color.White.copy(alpha = .55f), fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(18.dp))
                listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach {
                    Text(
                        it,
                        color = Color(0xFFBBBAC2),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            repeat(6) { week ->
                val rowMonday = month.atDay(1).minusDays(offset.toLong()).plusWeeks(week.toLong())
                val weekNumber = rowMonday.get(weekFields.weekOfWeekBasedYear())
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Text("$weekNumber", color = Color.White.copy(alpha = .58f), fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(18.dp).align(Alignment.CenterVertically))
                    repeat(7) { column ->
                        val number = week * 7 + column - offset + 1
                        val validDay = number in 1..month.lengthOfMonth()
                        val day = if (validDay) month.atDay(number) else null
                        val selectedDay = day == selected
                        val todayDay = day == LocalDate.now()
                        val dayEvents = if (day == null) {
                            emptyList()
                        } else {
                            val allDayEvents = events.filter { it.date == day }
                            val firstBirthday = allDayEvents.firstOrNull { isBirthdayEvent(it) }
                            val firstLaundry = allDayEvents.firstOrNull { isLaundryEvent(it) }
                            val firstAllFamily = allDayEvents.firstOrNull { !isBirthdayEvent(it) && !isLaundryEvent(it) && it.memberId == ALL_FAMILY_MEMBER_ID }
                            val uniqueMembers = allDayEvents
                                .filter { !isBirthdayEvent(it) && !isLaundryEvent(it) && it.memberId != ALL_FAMILY_MEMBER_ID }
                                .distinctBy { it.memberId }
                            buildList {
                                if (firstBirthday != null) add(firstBirthday)
                                if (firstLaundry != null) add(firstLaundry)
                                if (firstAllFamily != null) add(firstAllFamily)
                                addAll(uniqueMembers)
                            }.take(3)
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when { day == null -> Color.Transparent; selectedDay -> accent.copy(alpha = .88f); todayDay -> accent.copy(alpha = .42f); else -> Color(0x991B2028) }
                            ),
                            border = if (day == null) null else BorderStroke(
                                if (todayDay && !selectedDay) 2.dp else 1.dp,
                                when { selectedDay -> accent.copy(alpha = .95f); todayDay -> accent; else -> Color.White.copy(alpha = .18f) }
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (day == null) 0.dp else if (selectedDay) 6.dp else 3.dp
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp, vertical = 2.dp)
                                .then(if (day != null) Modifier.clickable { onSelect(day) } else Modifier)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (day != null) {
                                    Text(
                                        "$number",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 15.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        dayEvents.forEach { event ->
                                            when {
                                                isBirthdayEvent(event) -> BirthdayRainbowIcon(Modifier.size(width = 20.dp, height = 16.dp))
                                                isLaundryEvent(event) -> Text("🧺", fontSize = 14.sp, lineHeight = 16.sp)
                                                event.memberId == ALL_FAMILY_MEMBER_ID -> Text(
                                                    "★",
                                                    color = Color(0xFFFFD75E),
                                                    fontSize = 15.sp,
                                                    lineHeight = 18.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                else -> {
                                                    val member = members.find { it.id == event.memberId }
                                                    val dotColor = member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                                                    Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
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
    onChangePerson: (SyncEvent, String?) -> Unit
) {
    var selectedEvent by remember { mutableStateOf<SyncEvent?>(null) }

    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xBF131820))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp)) {
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
            val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$dayName ${date.dayOfMonth} $monthName",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${events.size} ${if (events.size == 1) "aktivitet" else "aktiviteter"}",
                    color = Color.White.copy(alpha = .68f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                if (events.any { it.source != "sportadmin" }) {
                    TextButton(onClick = onManageMany, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Text("Hantera", fontSize = 9.sp)
                    }
                }
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = Color(0xFF8B21FF),
                    contentColor = Color.White,
                    modifier = Modifier.size(44.dp)
                ) { Icon(Icons.Default.Add, "Lägg till", modifier = Modifier.size(26.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                events.forEach { event ->
                    val member = members.find { it.id == event.memberId }
                    val allFamily = event.memberId == ALL_FAMILY_MEMBER_ID
                    val birthday = isBirthdayEvent(event)
                    val laundry = isLaundryEvent(event)
                    val dotColor = if (allFamily) Color(0xFFFFD75E) else member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                    Surface(
                        color = Color(0xD9191D24),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedEvent = event }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (birthday) {
                                Text("🌈", fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.width(32.dp))
                            } else if (laundry) {
                                Text("🧺", fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.width(32.dp))
                            } else if (allFamily) {
                                Text("★", color = Color(0xFFFFD75E), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
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
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (event.time.isNotBlank()) Text(event.time, color = Color.White.copy(alpha = .62f), fontSize = 11.sp)
                            }
                            Text(
                                if (allFamily) "Hela familjen" else member?.name ?: "Familjen",
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 10.sp
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
                    Text("Gäller: ${if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else currentMember?.name ?: "Familjen"}")
                    if (event.source != "sportadmin") {
                        Text("Ändra person", fontWeight = FontWeight.SemiBold)
                        members.forEach { member ->
                            TextButton(onClick = {
                                onChangePerson(event, member.id)
                                selectedEvent = null
                            }) { Text(member.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text("Stäng") } },
            dismissButton = {
                if (event.source != "sportadmin") {
                    TextButton(onClick = {
                        onDelete(event)
                        selectedEvent = null
                    }) { Text("Ta bort") }
                }
            }
        )
    }
}


@Composable
private fun WorkRotationDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = currentFamilySession(context)
    var rotating by remember { mutableStateOf(true) }
    var rotationWeeks by remember { mutableStateOf(4) }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var startDate by remember {
        mutableStateOf(selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong()).plusWeeks(1))
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingWeek by remember { mutableStateOf<Int?>(null) }
    var weeks by remember {
        mutableStateOf(
            listOf(
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "06:00", "14:00"),
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "14:00", "22:00"),
                WorkRotationWeekDraft(setOf(1, 2, 3, 4, 5), "08:00", "16:00"),
                WorkRotationWeekDraft(emptySet(), "06:00", "14:00")
            )
        )
    }

    fun pickTime(index: Int, day: Int, start: Boolean) {
        val week = weeks[index]
        val currentTimes = week.dayTimes[day] ?: (week.startTime to week.endTime)
        val current = if (start) currentTimes.first else currentTimes.second
        val parsed = runCatching { LocalTime.parse(current) }.getOrDefault(LocalTime.of(6, 0))
        TimePickerDialog(context, { _, h, m ->
            val value = "%02d:%02d".format(h, m)
            weeks = weeks.toMutableList().also { list ->
                val old = list[index]
                val oldTimes = old.dayTimes[day] ?: (old.startTime to old.endTime)
                val updatedTimes = if (start) value to oldTimes.second else oldTimes.first to value
                list[index] = old.copy(dayTimes = old.dayTimes + (day to updatedTimes))
            }
        }, parsed.hour, parsed.minute, true).show()
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
                TextButton(onClick = { if (!saving) onDismiss() }, contentPadding = PaddingValues(0.dp)) {
                    Text("‹", fontSize = 28.sp)
                }
                Text(
                    "Lägg till arbetsvecka",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF21182B))
                ) {
                    TextButton(
                        onClick = { rotating = false; rotationWeeks = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .background(if (!rotating) Color(0xFF9C4DFF) else Color.Transparent)
                    ) {
                        Text("Fast schema", color = if (!rotating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(
                        onClick = { rotating = true; rotationWeeks = 4 },
                        modifier = Modifier
                            .weight(1f)
                            .background(if (rotating) Color(0xFF9C4DFF) else Color.Transparent)
                    ) {
                        Text("Roterande schema", color = if (rotating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (members.isNotEmpty()) {
                    Text("Person", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        members.take(4).forEach { member ->
                            FilterChip(
                                selected = selectedMemberId == member.id,
                                onClick = { selectedMemberId = member.id },
                                label = { Text(member.name, maxLines = 1) }
                            )
                        }
                    }
                }

                if (rotating) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Antal veckor i rotation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                            OutlinedButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                                Text("4 veckor")
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Startdatum för rotation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                            OutlinedButton(
                                onClick = {
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val picked = LocalDate.of(y, m + 1, d)
                                            startDate = picked.minusDays((picked.dayOfWeek.value - 1).toLong())
                                        },
                                        startDate.year,
                                        startDate.monthValue - 1,
                                        startDate.dayOfMonth
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${startDate.dayOfMonth}/${startDate.monthValue} ${startDate.year}", fontSize = 12.sp)
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
                            border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .38f))
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Vecka ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    when {
                                        week.weekdays.isEmpty() -> "Ledig"
                                        week.dayTimes.filterKeys { it in week.weekdays }.values.distinct().size <= 1 -> {
                                            val t = week.dayTimes[week.weekdays.first()] ?: (week.startTime to week.endTime)
                                            "${t.first} – ${t.second}"
                                        }
                                        else -> "${week.weekdays.size} pass · olika tider"
                                    },
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                                Button(
                                    onClick = { editingWeek = index },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "↻   Schemat upprepas automatiskt: Vecka 1 → 2 → 3 → 4 → 1 …",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && session != null && selectedMemberId.isNotBlank() && weeks.take(rotationWeeks).any { it.weekdays.isNotEmpty() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White),
                onClick = {
                    val activeSession = session ?: return@Button
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            repeat(52) { weekIndex ->
                                val template = weeks[weekIndex % rotationWeeks]
                                val monday = startDate.plusWeeks(weekIndex.toLong())
                                template.weekdays.sorted().forEach { day ->
                                    val date = monday.plusDays((day - 1).toLong())
                                    val times = template.dayTimes[day] ?: (template.startTime to template.endTime)
                                    val eventTitle = "Jobb · ${times.first}–${times.second}"
                                    SupabaseSync.addEvent(activeSession, eventTitle, date, times.first, selectedMemberId)
                                }
                            }
                        }.onSuccess { onChanged() }
                            .onFailure { error = it.message ?: "Kunde inte spara arbetsveckan" }
                        saving = false
                    }
                }
            ) {
                Text(if (saving) "Sparar…" else "Spara")
            }
        },
        dismissButton = {}
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
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Arbetsdagar och tider", fontWeight = FontWeight.SemiBold)
                    Text("Varje dag kan ha sin egen arbetstid.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    val dayLabels = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")
                    dayLabels.forEachIndexed { dayIndex, label ->
                        val day = dayIndex + 1
                        val enabled = day in week.weekdays
                        val times = week.dayTimes[day] ?: (week.startTime to week.endTime)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF21182B),
                            border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = enabled,
                                        onCheckedChange = { checked ->
                                            weeks = weeks.toMutableList().also { list ->
                                                val old = list[index]
                                                val newDays = if (checked) old.weekdays + day else old.weekdays - day
                                                val newTimes = if (checked && day !in old.dayTimes) {
                                                    old.dayTimes + (day to (old.startTime to old.endTime))
                                                } else old.dayTimes
                                                list[index] = old.copy(weekdays = newDays, dayTimes = newTimes)
                                            }
                                        }
                                    )
                                    Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (!enabled) Text("Ledig", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
                                }
                                if (enabled) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { pickTime(index, day, true) }, modifier = Modifier.weight(1f)) {
                                            Text("Från ${times.first}", fontSize = 12.sp)
                                        }
                                        OutlinedButton(onClick = { pickTime(index, day, false) }, modifier = Modifier.weight(1f)) {
                                            Text("Till ${times.second}", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { editingWeek = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White)) { Text("Klar") } },
            dismissButton = {}
        )
    }
}

@Composable
private fun WorkMonthDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
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
    var rules by remember {
        mutableStateOf(
            listOf(
                WorkRuleDraft(setOf(1, 2, 3, 4, 5), "06:00", "14:18")
            )
        )
    }

    fun pickTime(current: String, onPicked: (String) -> Unit) {
        val parsed = runCatching { LocalTime.parse(current) }.getOrDefault(LocalTime.of(6, 0))
        TimePickerDialog(context, { _, h, m -> onPicked("%02d:%02d".format(h, m)) }, parsed.hour, parsed.minute, true).show()
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
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                    Text("${month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }} ${month.year}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Rubrik") }, modifier = Modifier.fillMaxWidth())
                Text("Person", fontWeight = FontWeight.SemiBold)
                members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMemberId == member.id, onClick = { selectedMemberId = member.id })
                        Text(member.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(replaceExisting, { replaceExisting = it })
                    Text("Ersätt befintliga '$title'-pass för personen denna månad")
                }
                rules.forEachIndexed { index, rule ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Regel ${index + 1}", fontWeight = FontWeight.SemiBold)
                            Row {
                                listOf("M" to 1, "T" to 2, "O" to 3, "T" to 4, "F" to 5, "L" to 6, "S" to 7).forEach { (label, day) ->
                                    FilterChip(
                                        selected = day in rule.weekdays,
                                        onClick = {
                                            val nextDays = if (day in rule.weekdays) rule.weekdays - day else rule.weekdays + day
                                            rules = rules.toMutableList().also { it[index] = rule.copy(weekdays = nextDays) }
                                        },
                                        label = { Text(label, fontSize = 10.sp) },
                                        modifier = Modifier.padding(end = 2.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pickTime(rule.startTime) { value -> rules = rules.toMutableList().also { it[index] = rule.copy(startTime = value) } } }) { Text("Från ${rule.startTime}") }
                                OutlinedButton(onClick = { pickTime(rule.endTime) { value -> rules = rules.toMutableList().also { it[index] = rule.copy(endTime = value) } } }) { Text("Till ${rule.endTime}") }
                            }
                            if (rules.size > 1) TextButton(onClick = { rules = rules.toMutableList().also { it.removeAt(index) } }) { Text("Ta bort regel") }
                        }
                    }
                }
                TextButton(onClick = { rules = rules + WorkRuleDraft(emptySet(), "14:00", "22:00") }) { Text("+ Lägg till regel") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && session != null && selectedMemberId.isNotBlank() && rules.any { it.weekdays.isNotEmpty() },
                onClick = {
                    val activeSession = session ?: return@Button
                    saving = true
                    error = null
                    scope.launch {
                        runCatching {
                            val rows = mutableListOf<WorkMonthEventInput>()
                            for (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                rules.filter { date.dayOfWeek.value in it.weekdays }.forEach { rule ->
                                    rows += WorkMonthEventInput(title.trim().ifBlank { "Jobb" }, date, rule.startTime, selectedMemberId)
                                }
                            }
                            saveWorkMonthDirect(activeSession, month, title.trim().ifBlank { "Jobb" }, selectedMemberId, rows, replaceExisting)
                        }.onSuccess { onChanged() }.onFailure { error = it.message ?: "Kunde inte spara arbetsmånaden" }
                        saving = false
                    }
                }
            ) { Text(if (saving) "Sparar…" else "Spara månaden") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Avbryt") } }
    )
}

@Composable
private fun ManageMonthEventsDialog(
    month: YearMonth,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onDelete: (List<String>) -> Unit
) {
    var selectedIds by remember(events) { mutableStateOf(events.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hantera månadens aktiviteter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Markera aktiviteterna du vill ta bort. SportAdmin-poster lämnas orörda.")
                Row {
                    TextButton(onClick = { selectedIds = events.map { it.id }.toSet() }) { Text("Markera alla") }
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Avmarkera") }
                }
                events.forEach { event ->
                    val memberName = members.find { it.id == event.memberId }?.name ?: if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else "Familjen"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = event.id in selectedIds,
                            onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + event.id else selectedIds - event.id }
                        )
                        Column {
                            Text("${event.date.dayOfMonth}/${event.date.monthValue} ${event.time} ${event.title}")
                            Text(memberName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = selectedIds.isNotEmpty(), onClick = { onDelete(selectedIds.toList()) }) {
                Text("Ta bort ${selectedIds.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}
