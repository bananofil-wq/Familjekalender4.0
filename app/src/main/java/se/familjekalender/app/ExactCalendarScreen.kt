package se.familjekalender.app

import android.app.Activity
import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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

@Composable
internal fun ExactCalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    palette: SeasonPalette,
    onAdd: () -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showWorkMonth by remember { mutableStateOf(false) }
    var showManageMonth by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode = palette.mode
    val monthDrag = remember { Animatable(0f) }
    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)

    fun refreshActivity() {
        (context as? Activity)?.recreate()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        SeasonalPhoto(mode, Modifier.align(Alignment.TopCenter))

        fun moveMonth(delta: Long) {
            val next = month.plusMonths(delta)
            month = next
            onSelect(next.atDay(1))
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonthPanel(
                month = month,
                selected = date,
                onSelect = onSelect,
                events = events,
                members = members,
                accent = palette.accent,
                onPreviousMonth = { moveMonth(-1) },
                onNextMonth = { moveMonth(1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.72f)
          .graphicsLayer { translationX = monthDrag.value }
          .pointerInput(month) {
              detectHorizontalDragGestures(
                  onDragStart = { launch { monthDrag.stop() } },
                  onHorizontalDrag = { change, amount ->
                      change.consume()
                      launch { monthDrag.snapTo(monthDrag.value + amount) }
                  },
                  onDragEnd = {
                      launch {
                          val width = size.width.toFloat().coerceAtLeast(1f)
                          if (abs(monthDrag.value) >= width * 0.18f) {
                              val direction = if (monthDrag.value < 0f) -1f else 1f
                              monthDrag.animateTo(direction * width, tween(130))
                              moveMonth(if (direction < 0f) 1 else -1)
                              monthDrag.snapTo(-direction * width)
                              monthDrag.animateTo(0f, tween(190))
                          } else {
                              monthDrag.animateTo(0f, tween(160))
                          }
                      }
                  },
                  onDragCancel = { launch { monthDrag.animateTo(0f, tween(160)) } }
              )
          }
            )
            DayPanel(
                date = date,
                events = events.filter { it.date == date },
                members = members,
                p = palette,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onAdd = { showAddMenu = true },
                onManageMany = { showManageMonth = true },
                onDelete = { event ->
                    val session = currentFamilySession(context) ?: return@DayPanel
                    scope.launch {
                        runCatching { deleteCalendarEventsDirect(session, listOf(event.id)) }
                            .onSuccess { refreshActivity() }
                    }
                },
                onChangePerson = { event, memberId ->
                    val session = currentFamilySession(context) ?: return@DayPanel
                    val persistedMemberId = memberId?.takeUnless { it == ALL_FAMILY_MEMBER_ID }
                    scope.launch {
                        runCatching { updateCalendarEventMemberDirect(session, event.id, persistedMemberId) }
                            .onSuccess { refreshActivity() }
                    }
                }
            )
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
                    Text(
                        "Arbetsmånad låter dig ange olika tider för olika veckodagar och fyller hela månaden åt dig.",
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
        modifier = modifier.fillMaxWidth().aspectRatio(1.52f),
        contentScale = ContentScale.Fit,
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
                        val dayEvents = if (day == null) emptyList() else events.filter { it.date == day }.take(3)

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
                                                isBirthdayEvent(event) -> Text("🌈", fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
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
                    fontSize = 11.sp
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
                            } else if (allFamily) {
                                Text("★", color = Color(0xFFFFD75E), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                            } else {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(dotColor))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.title.removePrefix("🌈").trim(),
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
            title = { Text(event.title.removePrefix("🌈").trim()) },
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
