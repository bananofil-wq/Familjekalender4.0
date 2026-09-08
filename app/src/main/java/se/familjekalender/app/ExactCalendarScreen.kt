package se.familjekalender.app

import android.app.Activity
import android.app.TimePickerDialog
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

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
    val p = palette
    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)

    fun refreshActivity() {
        (context as? Activity)?.recreate()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF061019))) {
        val heroH = maxWidth * (2f / 3f)
        val panelTop = heroH - 2.dp
        val panelsH = maxHeight * .545f

        SeasonalPhoto(mode, Modifier.fillMaxWidth().height(heroH))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).offset(y = panelTop).height(panelsH),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonthPanel(month, date, onSelect, events, members, p.accent, Modifier.weight(1.78f))
            DayPanel(
                date = date,
                events = events.filter { it.date == date },
                members = members,
                p = p,
                modifier = Modifier.weight(1f),
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
                    scope.launch {
                        runCatching { updateCalendarEventMemberDirect(session, event.id, memberId) }
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
            members = members,
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

    if (imageRes == null) {
        Box(modifier.background(Color(0xFF061019)))
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = false
                setImageResource(imageRes)
            }
        },
        update = {
            it.scaleType = ImageView.ScaleType.CENTER_CROP
            it.adjustViewBounds = false
            it.setImageResource(imageRes)
        }
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
    modifier: Modifier
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF3131820))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("MÅN", "TIS", "ONS", "TOR", "FRE", "LÖR", "SÖN").forEach {
                    Text(it, color = Color(0xFFD5D4DB), fontSize = 7.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { column ->
                        val number = week * 7 + column - offset + 1
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            if (number in 1..month.lengthOfMonth()) {
                                val day = month.atDay(number)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(day) }) {
                                    Box(
                                        Modifier.size(27.dp).clip(CircleShape).background(if (day == selected) accent else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$number", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        events.filter { it.date == day }.take(3).forEach { event ->
                                            val member = members.find { it.id == event.memberId }
                                            val dotColor = member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                                            Box(Modifier.size(3.5.dp).clip(CircleShape).background(dotColor))
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

    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xF3131820))) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
                val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$dayName ${date.dayOfMonth} $monthName", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (events.any { it.source != "sportadmin" }) {
                        TextButton(onClick = onManageMany, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                            Text("Hantera", fontSize = 8.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (events.isEmpty()) Text("Inget planerat", color = Color.White.copy(alpha = .55f), fontSize = 9.sp)
                events.take(5).forEach { event ->
                    val member = members.find { it.id == event.memberId }
                    val eventColor = member?.let { Color(it.colorArgb.toInt()) } ?: Color(0xFF8D95A5)
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedEvent = event }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(eventColor))
                        Spacer(Modifier.width(7.dp))
                        Text(event.time, color = Color.White.copy(alpha = .9f), fontSize = 9.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(event.title, color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    quote(p.mode), color = p.accent, fontSize = 14.sp, lineHeight = 16.sp,
                    fontStyle = FontStyle.Italic, fontFamily = FontFamily.Cursive,
                    modifier = Modifier.padding(bottom = 43.dp)
                )
            }
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color(0xFF9C35FF),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(48.dp)
            ) {
                Icon(Icons.Default.Add, "Lägg till", modifier = Modifier.size(29.dp))
            }
        }
    }

    selectedEvent?.let { event ->
        val currentMember = members.find { it.id == event.memberId }
        var editPerson by remember(event.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { selectedEvent = null },
            title = { Text(event.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tid: ${event.time}")
                    Text("Datum: ${event.date}")
                    Text("Berör: ${currentMember?.name ?: "Ingen särskild person"}")
                    if (!currentMember?.role.isNullOrBlank()) Text("Roll: ${currentMember?.role}")
                    Text(
                        "Källa: ${when (event.source) {
                            "sportadmin" -> "SportAdmin"
                            "work_schedule" -> "Arbetsmånad"
                            else -> "Manuellt tillagd"
                        }}"
                    )
                    OutlinedButton(
                        onClick = { editPerson = !editPerson },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (editPerson) "Dölj personer" else "Byt person") }

                    if (editPerson) {
                        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedEvent = null
                                    onChangePerson(event, null)
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = event.memberId == null, onClick = null)
                                Text("Ingen särskild person")
                            }
                            members.forEach { member ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selectedEvent = null
                                        onChangePerson(event, member.id)
                                    }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = event.memberId == member.id, onClick = null)
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                                    Spacer(Modifier.width(8.dp))
                                    Text(member.name)
                                }
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
                            selectedEvent = null
                            onDelete(event)
                        }
                    ) { Text("Ta bort", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}

@Composable
private fun ManageMonthEventsDialog(
    month: YearMonth,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onDelete: (List<String>) -> Unit
) {
    val selectedIds = remember(month, events) { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Ta bort ${selectedIds.size} inlägg?") },
            text = { Text("De markerade kalenderinläggen tas bort permanent.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete(selectedIds.toList())
                    }
                ) { Text("Ta bort") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Avbryt") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hantera $monthName") },
        text = {
            Column {
                if (events.isEmpty()) {
                    Text("Inga manuella kalenderinlägg att hantera den här månaden.")
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            selectedIds.clear()
                            selectedIds.addAll(events.map { it.id })
                        }) { Text("Markera alla") }
                        TextButton(onClick = { selectedIds.clear() }) { Text("Avmarkera") }
                    }
                    Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                        events.sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time }).forEach { event ->
                            val member = members.find { it.id == event.memberId }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (event.id in selectedIds) selectedIds.remove(event.id) else selectedIds.add(event.id)
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = event.id in selectedIds,
                                    onCheckedChange = {
                                        if (it) {
                                            if (event.id !in selectedIds) selectedIds.add(event.id)
                                        } else selectedIds.remove(event.id)
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${event.date.dayOfMonth} ${event.date.month.getDisplayName(TextStyle.SHORT, Locale("sv", "SE"))}  ${event.time}", fontSize = 12.sp)
                                    Text(event.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(member?.name ?: "Ingen särskild person", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { confirmDelete = true },
                enabled = selectedIds.isNotEmpty()
            ) { Text("Ta bort valda (${selectedIds.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
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
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var title by remember { mutableStateOf("Jobb") }
    var memberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
    val rules = remember {
        mutableStateListOf(
            WorkRuleDraft(setOf(1), "07:00", "15:00"),
            WorkRuleDraft(setOf(2, 3, 4, 5), "08:00", "16:00")
        )
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val weekdayLabels = listOf("M", "Ti", "O", "To", "F", "L", "S")

    fun openTime(current: String, onPicked: (String) -> Unit) {
        val parsed = runCatching { LocalTime.parse(current) }.getOrElse { LocalTime.of(8, 0) }
        TimePickerDialog(
            context,
            { _, h, m -> onPicked("%02d:%02d".format(h, m)) },
            parsed.hour,
            parsed.minute,
            true
        ).show()
    }

    fun toggleWeekday(ruleIndex: Int, weekday: Int) {
        val current = rules[ruleIndex]
        if (weekday in current.weekdays) {
            rules[ruleIndex] = current.copy(weekdays = current.weekdays - weekday)
        } else {
            rules.indices.filter { it != ruleIndex }.forEach { i ->
                val other = rules[i]
                if (weekday in other.weekdays) rules[i] = other.copy(weekdays = other.weekdays - weekday)
            }
            rules[ruleIndex] = current.copy(weekdays = current.weekdays + weekday)
        }
    }

    val shiftCount = remember(month, rules.toList()) {
        (1..month.lengthOfMonth()).count { day ->
            val weekday = month.atDay(day).dayOfWeek.value
            rules.any { weekday in it.weekdays }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Arbetsmånad") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                    Text(
                        "${month.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }} ${month.year}",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Namn, t.ex. Jobb") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Vem gäller det?", fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().clickable { memberId = member.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = memberId == member.id, onClick = { memberId = member.id })
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                        Spacer(Modifier.width(8.dp))
                        Text(member.name)
                    }
                }

                Text("Veckotider", fontWeight = FontWeight.Bold)
                Text(
                    "Välj dagarna som ska ha samma tid. En dag kan bara ligga i en tidsgrupp.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    fontSize = 12.sp
                )

                rules.forEachIndexed { index, rule ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF24212A))) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                weekdayLabels.forEachIndexed { dayIndex, label ->
                                    val weekday = dayIndex + 1
                                    FilterChip(
                                        selected = weekday in rule.weekdays,
                                        onClick = { toggleWeekday(index, weekday) },
                                        label = { Text(label, fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { openTime(rule.startTime) { rules[index] = rule.copy(startTime = it) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Start ${rule.startTime}") }
                                OutlinedButton(
                                    onClick = { openTime(rule.endTime) { rules[index] = rule.copy(endTime = it) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Slut ${rule.endTime}") }
                            }
                            if (rules.size > 1) {
                                TextButton(onClick = { rules.removeAt(index) }) {
                                    Text("Ta bort tidsgrupp")
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { rules.add(WorkRuleDraft(emptySet(), "08:00", "16:00")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Lägg till en tid till") }

                Text(
                    "$shiftCount arbetspass kommer att läggas in för månaden.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Sparar du månaden igen ersätts tidigare pass som skapats via Arbetsmånad för samma person och månad.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                    fontSize = 11.sp
                )

                if (message.isNotBlank()) {
                    Text(message, color = if (message.startsWith("Fel")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }

                OutlinedButton(
                    onClick = {
                        val session = currentFamilySession(context)
                        if (session == null) {
                            message = "Fel: familjeanslutningen saknas"
                        } else {
                            busy = true
                            scope.launch {
                                runCatching { deleteWorkMonth(session, month, memberId) }
                                    .onSuccess {
                                        message = "$it arbetspass borttagna"
                                        onChanged()
                                    }
                                    .onFailure { message = "Fel: ${it.message}" }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && memberId != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ta bort månadens jobbtider", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val session = currentFamilySession(context)
                    val validRules = rules.filter { it.weekdays.isNotEmpty() }
                    if (session == null) {
                        message = "Fel: familjeanslutningen saknas"
                    } else if (validRules.isEmpty()) {
                        message = "Fel: välj minst en veckodag"
                    } else {
                        busy = true
                        scope.launch {
                            runCatching {
                                saveWorkMonth(
                                    session = session,
                                    month = month,
                                    title = title,
                                    memberId = memberId,
                                    rules = validRules.map { WorkRule(it.weekdays, it.startTime, it.endTime) }
                                )
                            }.onSuccess {
                                message = "$it arbetspass sparade"
                                onChanged()
                            }.onFailure { message = "Fel: ${it.message}" }
                            busy = false
                        }
                    }
                },
                enabled = !busy && title.isNotBlank() && memberId != null && shiftCount > 0
            ) { Text(if (busy) "Sparar…" else "Spara hela månaden") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Avbryt") } }
    )
}

private fun quote(mode: ThemeMode) = when (mode) {
    ThemeMode.WINTER -> "Kalla dagar,\nvarma stunder ♡"
    ThemeMode.SPRING -> "Nya dagar,\nnya möjligheter ♡"
    ThemeMode.SUMMER -> "Sommar,\nmer tillsammans ♡"
    ThemeMode.AUTUMN -> "Hösten\nsamlar oss ♡"
    else -> "Tillsammans ♡"
}
