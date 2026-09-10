package se.familjekalender.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Bg = Color(0xFF0F0E13)
internal val CardBg = Color(0xFF1B191F)
internal val Purple = Color(0xFFB47CFF)
internal val SoftPurple = Color(0xFF2B2038)
internal val Muted = Color(0xFFAAA4B2)
private val MemberColors = listOf(0xFFB47CFF, 0xFFFF77A8, 0xFF62A9FF, 0xFF6DD6A7, 0xFFFFB86B)

enum class ThemeMode(val label: String, val emoji: String) {
    AUTO("Automatisk", "✨"),
    SPRING("Vår", "🌸"),
    SUMMER("Sommar", "☀️"),
    AUTUMN("Höst", "🍂"),
    WINTER("Vinter", "❄️"),
    CLASSIC("Klassisk", "💜")
}

data class SeasonPalette(
    val mode: ThemeMode,
    val accent: Color,
    val soft: Color,
    val skyTop: Color,
    val skyBottom: Color,
    val sceneDark: Color,
    val sceneLight: Color
)

private fun resolvedTheme(mode: ThemeMode, date: LocalDate = LocalDate.now()) =
    if (mode != ThemeMode.AUTO) mode else when (date.monthValue) {
        3, 4, 5 -> ThemeMode.SPRING
        6, 7, 8 -> ThemeMode.SUMMER
        9, 10, 11 -> ThemeMode.AUTUMN
        else -> ThemeMode.WINTER
    }

internal fun paletteFor(mode: ThemeMode): SeasonPalette = when (resolvedTheme(mode)) {
    ThemeMode.SPRING -> SeasonPalette(ThemeMode.SPRING, Color(0xFFF3A5C8), Color(0xFF352430), Color(0xFF233B39), Color(0xFF5A4055), Color(0xFF173029), Color(0xFFFFC3DD))
    ThemeMode.SUMMER -> SeasonPalette(ThemeMode.SUMMER, Color(0xFFFFC96B), Color(0xFF3A3022), Color(0xFF173C59), Color(0xFF8A6241), Color(0xFF12314A), Color(0xFFFFD983))
    ThemeMode.AUTUMN -> SeasonPalette(ThemeMode.AUTUMN, Color(0xFFFFA45B), Color(0xFF3A281F), Color(0xFF301A23), Color(0xFF8B482D), Color(0xFF251419), Color(0xFFD66B3D))
    ThemeMode.WINTER -> SeasonPalette(ThemeMode.WINTER, Color(0xFF9DBBFF), Color(0xFF222A3A), Color(0xFF111B34), Color(0xFF3C4268), Color(0xFF0D1630), Color(0xFFD9E8FF))
    ThemeMode.CLASSIC -> SeasonPalette(ThemeMode.CLASSIC, Purple, SoftPurple, Color(0xFF17121F), Color(0xFF39264A), Color(0xFF17121F), Purple)
    ThemeMode.AUTO -> error("resolved")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FamilyCalendarApp() }
    }
}

@Composable
fun FamilyCalendarApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("family_calendar", 0) }
    var session by remember {
        mutableStateOf(
            prefs.getString("family_id", null)?.let {
                FamilySession(
                    it,
                    prefs.getString("family_name", "Min familj") ?: "Min familj",
                    prefs.getString("family_code", "") ?: ""
                )
            }
        )
    }
    var themeMode by remember {
        mutableStateOf(
            runCatching {
                ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name)
            }.getOrDefault(ThemeMode.AUTO)
        )
    }
    val palette = paletteFor(themeMode)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = palette.accent,
            background = Bg,
            surface = CardBg,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            if (session == null) {
                FamilySetupScreen { created ->
                    prefs.edit()
                        .putString("family_id", created.id)
                        .putString("family_name", created.name)
                        .putString("family_code", created.code)
                        .apply()
                    session = created
                }
            } else {
                SyncedApp(
                    session!!,
                    prefs.getString("sport_url", "") ?: "",
                    themeMode,
                    { prefs.edit().putString("sport_url", it).apply() },
                    {
                        themeMode = it
                        prefs.edit().putString("theme_mode", it.name).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun FamilySetupScreen(onReady: (FamilySession) -> Unit) {
    val scope = rememberCoroutineScope()
    var familyName by remember { mutableStateOf("Min familj") }
    var joinCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Familjekalendern", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Skapa familjen på första telefonen, anslut med koden på nästa.", color = Muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(familyName, { familyName = it }, label = { Text("Familjens namn") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    runCatching { SupabaseSync.createFamily(familyName) }
                        .onSuccess(onReady)
                        .onFailure { error = it.message ?: "Fel" }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Skapa familj") }
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(joinCode, { joinCode = it.uppercase() }, label = { Text("Familjekod") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = {
                scope.launch {
                    busy = true
                    runCatching { SupabaseSync.joinFamily(joinCode) }
                        .onSuccess(onReady)
                        .onFailure { error = it.message ?: "Fel" }
                    busy = false
                }
            },
            enabled = !busy && joinCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Anslut till familj") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SyncedApp(
    session: FamilySession,
    sportUrl: String,
    themeMode: ThemeMode,
    onSportUrlSaved: (String) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit
) {
    val scope = rememberCoroutineScope()
    val palette = paletteFor(themeMode)
    var members by remember { mutableStateOf(emptyList<SyncMember>()) }
    var shopping by remember { mutableStateOf(emptyList<SyncShoppingItem>()) }
    var events by remember { mutableStateOf(emptyList<SyncEvent>()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddEvent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun refresh() {
        runCatching {
            members = SupabaseSync.loadMembers(session)
            shopping = SupabaseSync.loadShopping(session)
            events = SupabaseSync.loadEvents(session)
        }.onSuccess {
            if (message.startsWith("Synkfel:")) message = ""
        }.onFailure {
            message = "Synkfel: ${it.message}"
        }
    }

    LaunchedEffect(session.id) {
        refresh()
        while (true) {
            delay(30L * 60L * 1000L)
            refresh()
        }
    }

    Scaffold(containerColor = Bg, bottomBar = { BottomNav(selectedTab) { selectedTab = it } }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                ExactCalendarScreen(selectedDate, { selectedDate = it }, events, members, palette) { showAddEvent = true }
            } else {
                Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    when (selectedTab) {
                        1 -> ShoppingScreen(
                            shopping,
                            { name -> scope.launch { SupabaseSync.addShopping(session, name); refresh() } },
                            { item -> scope.launch { SupabaseSync.toggleShopping(session, item); refresh() } },
                            { scope.launch { SupabaseSync.clearChecked(session); refresh() } }
                        )
                        2 -> ToDoScreen(session)
                        3 -> EditableFamilyScreen(
                            members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                            { name, role ->
                                scope.launch {
                                    val realMemberCount = members.count { it.id != ALL_FAMILY_MEMBER_ID }
                                    SupabaseSync.addMember(session, name, role, MemberColors[realMemberCount % MemberColors.size])
                                    refresh()
                                }
                            },
                            { member, color ->
                                scope.launch {
                                    SupabaseSync.updateMemberColor(session, member.id, color)
                                    refresh()
                                }
                            }
                        )
                        4 -> SettingsScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID }, sportUrl, themeMode, onThemeModeSaved, onSportUrlSaved) { url, memberId ->
                            scope.launch {
                                message = "Importerar SportAdmin…"
                                runCatching { SupabaseSync.importSportAdmin(session, url, memberId) }
                                    .onSuccess { message = "$it SportAdmin-aktiviteter synkade" }
                                    .onFailure { message = "SportAdmin-fel: ${it.message}" }
                                refresh()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEvent) {
        AddEventDialog(members, selectedDate, { showAddEvent = false }) { title, startTime, endTime, memberId, dates, birthday, recurrence ->
            scope.launch {
                if (birthday) {
                    val today = LocalDate.now()
                    val month = dates.first().monthValue
                    val day = dates.first().dayOfMonth
                    for (year in today.year..(today.year + 20)) {
                        val birthdayDate = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
                        SupabaseSync.addEvent(session, "🌈 $title", birthdayDate, "09:00", null, memberId)
                    }
                } else {
                    val targetDates = if (recurrence == RecurrenceMode.NONE) {
                        dates.sorted()
                    } else {
                        dates.sorted().flatMap { recurringDates(it, recurrence) }.distinct().sorted()
                    }
                    targetDates.forEach { date ->
                        SupabaseSync.addEvent(session, title, date, startTime, endTime, memberId)
                    }
                }
                refresh()
                showAddEvent = false
            }
        }
    }
}

@Composable
private fun ShoppingScreen(
    items: List<SyncShoppingItem>,
    onAdd: (String) -> Unit,
    onToggle: (SyncShoppingItem) -> Unit,
    onClear: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    Text("Inköpslista", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Synkas mellan era telefoner", color = Muted)
    Spacer(Modifier.height(18.dp))
    Row {
        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, modifier = Modifier.weight(1f))
        FilledIconButton(onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }) { Icon(Icons.Default.Add, null) }
    }
    items.forEach { item ->
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onToggle(item) }
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.checked, { onToggle(item) })
                Text(item.name, color = if (item.checked) Muted else Color.White)
            }
        }
    }
    if (items.any { it.checked }) TextButton(onClick = onClear) { Text("Rensa avbockade") }
}

private fun shareFamilyInvite(context: Context, session: FamilySession) {
    val encodedCode = Uri.encode(session.code)
    val appLink = "familjekalendern://join?code=$encodedCode"
    val text = "Du är inbjuden till ${session.name} i Familjekalendern 💜\n\nFamiljekod: ${session.code}\n\nHar du Familjekalendern installerad kan du öppna den här direktlänken:\n$appLink\n\nOm telefonen inte gör länken klickbar: öppna Familjekalendern och skriv familjekoden ovan under 'Anslut till familj'."
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Bjud in till familjen"
        )
    )
}

private fun copyFamilyCode(context: Context, code: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("Familjekod", code))
}

@Composable
private fun SettingsScreen(
    session: FamilySession,
    members: List<SyncMember>,
    initialSportUrl: String,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onSaveUrl: (String) -> Unit,
    onImport: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var url by remember(initialSportUrl) { mutableStateOf(initialSportUrl) }
    var memberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
    Text("Inställningar", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(session.name, fontSize = 18.sp)
            Text("Familjekod", color = Muted)
            Text(session.code, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { shareFamilyInvite(context, session) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, null)
                Text(" Bjud in till familjen")
            }
            OutlinedButton(onClick = { copyFamilyCode(context, session.code) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentCopy, null)
                Text(" Kopiera familjekod")
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Tema", fontWeight = FontWeight.Bold)
    ThemeMode.values().forEach { mode ->
        Row(Modifier.fillMaxWidth().clickable { onThemeChanged(mode) }.padding(8.dp)) {
            RadioButton(themeMode == mode, { onThemeChanged(mode) })
            Text("${mode.emoji} ${mode.label}")
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("SportAdmin", fontWeight = FontWeight.Bold)
    OutlinedTextField(url, { url = it }, label = { Text("Kalenderlänk") }, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = { onSaveUrl(url); onImport(url, memberId) },
        enabled = url.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Spara och importera") }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val accent = Color(0xFF9C4DFF)
    NavigationBar(containerColor = Color(0xF20F0E13)) {
        listOf(
            Icons.Default.CalendarMonth to "Kalender",
            Icons.Default.ShoppingCart to "Inköp",
            Icons.Default.CheckCircle to "To-Do",
            Icons.Default.People to "Familj",
            Icons.Default.Settings to "Inställningar"
        ).forEachIndexed { i, (icon, label) ->
            NavigationBarItem(
                selected == i,
                { onSelect(i) },
                { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = .15f)
                )
            )
        }
    }
}

@Composable
private fun AddEventDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String?, List<LocalDate>, Boolean, RecurrenceMode) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("sv", "SE")) }
    var title by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("18:00") }
    var endTime by remember { mutableStateOf("19:00") }
    var memberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
    var isBirthday by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf(RecurrenceMode.NONE) }
    val dates = remember { mutableStateListOf(selectedDate) }

    fun openSingleDatePicker() {
        val base = dates.lastOrNull() ?: selectedDate
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day)
                if (isBirthday) {
                    dates.clear()
                    dates.add(picked)
                } else if (picked !in dates) {
                    dates.add(picked)
                }
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth
        ).show()
    }

    fun openRangePicker() {
        val base = dates.minOrNull() ?: selectedDate
        DatePickerDialog(
            context,
            { _, startYear, startMonth, startDay ->
                val rangeStart = LocalDate.of(startYear, startMonth + 1, startDay)
                DatePickerDialog(
                    context,
                    { _, endYear, endMonth, endDay ->
                        val pickedEnd = LocalDate.of(endYear, endMonth + 1, endDay)
                        val rangeEnd = if (pickedEnd < rangeStart) rangeStart else pickedEnd
                        dates.clear()
                        var cursor = rangeStart
                        while (!cursor.isAfter(rangeEnd)) {
                            dates.add(cursor)
                            cursor = cursor.plusDays(1)
                        }
                    },
                    rangeStart.year,
                    rangeStart.monthValue - 1,
                    rangeStart.dayOfMonth
                ).show()
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth
        ).show()
    }

    fun openTimePicker(current: String, onPicked: (String) -> Unit) {
        val parsed = runCatching { LocalTime.parse(current) }.getOrElse { LocalTime.of(18, 0) }
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked("%02d:%02d".format(hour, minute)) },
            parsed.hour,
            parsed.minute,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBirthday) "Ny födelsedag" else "Ny aktivitet") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        isBirthday = !isBirthday
                        if (isBirthday) recurrence = RecurrenceMode.NONE
                        if (isBirthday && dates.size > 1) {
                            val first = dates.first()
                            dates.clear()
                            dates.add(first)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(isBirthday, { checked ->
                        isBirthday = checked
                        if (checked) recurrence = RecurrenceMode.NONE
                        if (checked && dates.size > 1) {
                            val first = dates.first()
                            dates.clear()
                            dates.add(first)
                        }
                    })
                    Text("Födelsedag – upprepas varje år")
                }

                if (!isBirthday) {
                    Spacer(Modifier.height(6.dp))
                    Text("Upprepning", fontWeight = FontWeight.Bold)
                    RecurrenceMode.values().forEach { mode ->
                        Row(Modifier.fillMaxWidth().clickable { recurrence = mode }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = recurrence == mode, onClick = { recurrence = mode })
                            Text(mode.label)
                        }
                    }
                }

                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(if (isBirthday) "Namn" else "Aktivitet") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isBirthday) {
                    Spacer(Modifier.height(8.dp))
                    Text("Tid", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { openTimePicker(startTime) { startTime = it } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Start $startTime") }
                        OutlinedButton(
                            onClick = { openTimePicker(endTime) { endTime = it } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Slut $endTime") }
                    }
                    if (endTime <= startTime) {
                        Text("Sluttiden räknas som nästa dag.", color = Muted, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(if (isBirthday) "Födelsedatum" else "Valda dagar", fontWeight = FontWeight.Bold)
                dates.sorted().forEach { date ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(date.format(dateFormatter), modifier = Modifier.weight(1f))
                        if (!isBirthday && dates.size > 1) {
                            TextButton(onClick = { dates.remove(date) }) { Text("Ta bort") }
                        }
                    }
                }

                if (isBirthday) {
                    OutlinedButton(onClick = { openSingleDatePicker() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Välj födelsedatum")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openSingleDatePicker() }, modifier = Modifier.weight(1f)) {
                            Text("+ En dag")
                        }
                        Button(onClick = { openRangePicker() }, modifier = Modifier.weight(1f)) {
                            Text("Flera dagar")
                        }
                    }
                }

                if (!isBirthday && dates.size > 1) {
                    Text("Samma aktivitet sparas på ${dates.size} dagar samtidigt.", color = Muted, fontSize = 12.sp)
                }
                if (isBirthday) {
                    Text("Födelsedagen läggs in årligen i kalendern.", color = Muted, fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))
                members.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().clickable { memberId = member.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(memberId == member.id, { memberId = member.id })
                        if (member.id == ALL_FAMILY_MEMBER_ID) {
                            Text("★", color = Color(0xFFFFD75E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(member.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title.trim(), startTime, endTime, memberId, dates.toList(), isBirthday, recurrence) },
                enabled = title.isNotBlank() && dates.isNotEmpty()
            ) {
                Text(if (isBirthday) "Lägg till födelsedag" else if (dates.size > 1) "Lägg till ${dates.size} dagar" else "Lägg till")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}
