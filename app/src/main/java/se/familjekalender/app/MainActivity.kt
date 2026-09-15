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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
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

internal fun paletteFor(mode: ThemeMode, date: LocalDate = LocalDate.now()): SeasonPalette = when (resolvedTheme(mode, date)) {
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
        setContent { ResponsiveApp { FamilyCalendarApp() } }
    }

    override fun onStart() {
        super.onStart()
        val locationPrefs = getSharedPreferences("family_calendar_location", MODE_PRIVATE)
        if (locationPrefs.getBoolean("sharing_enabled", false) &&
            !locationPrefs.getString("device_member_id", null).isNullOrBlank()
        ) {
            FamilyLocationService.start(this)
        }
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
            secondary = palette.accent,
            surfaceVariant = palette.soft,
            outline = palette.accent.copy(alpha = .55f),
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
                FirstRunIdentityGate(session!!) {
                    SyncedApp(
                        session!!,
                        prefs.getString("sport_url", "") ?: "",
                        prefs.getString("sport_member_id", null),
                        themeMode,
                        { url, memberId ->
                            prefs.edit()
                                .putString("sport_url", url)
                                .putString("sport_member_id", memberId)
                                .apply()
                        },
                        {
                            themeMode = it
                            prefs.edit().putString("theme_mode", it.name).apply()
                        }
                    )
                }
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
    sportMemberId: String?,
    themeMode: ThemeMode,
    onSportSettingsSaved: (String, String?) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val palette = paletteFor(themeMode)
    var members by remember { mutableStateOf(emptyList<SyncMember>()) }
    var shopping by remember { mutableStateOf(emptyList<SyncShoppingItem>()) }
    var events by remember { mutableStateOf(emptyList<SyncEvent>()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddEvent by remember { mutableStateOf(false) }
    var addEventInitialTitle by remember { mutableStateOf("") }
    var assistantAddRequest by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }

    suspend fun refresh() {
        runCatching {
            members = SupabaseSync.loadMembers(session)
            shopping = SupabaseSync.loadShopping(session)
            val loadedEvents = SupabaseSync.loadEvents(session)
            events = RecurringScheduleSync.filterPausedScheduleEvents(session, loadedEvents)
        }.onSuccess {
            if (message.startsWith("Synkfel:")) message = ""
        }.onFailure {
            message = "Synkfel: ${it.message}"
        }
    }

    LaunchedEffect(session.id, sportUrl, sportMemberId) {
        suspend fun syncExternalCalendars() {
            if (sportUrl.isNotBlank()) {
                runCatching { SupabaseSync.importSportAdmin(session, sportUrl, sportMemberId) }
            }
        }
        syncExternalCalendars()
        refresh()
        while (true) {
            delay(30L * 60L * 1000L)
            syncExternalCalendars()
            refresh()
        }
    }

    Scaffold(containerColor = Bg, bottomBar = { BottomNav(selectedTab) { selectedTab = it } }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                // The assistant card can be quite tall on busy days. Let the calendar tab
                // scroll instead of forcing the month grid into whatever height remains.
                // ExactCalendarScreen gets a stable viewport so all six week rows keep
                // their intended proportions on different phone aspect ratios.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }
                    WeekOverviewCard(events, members)
                    FamilyAutopilotCard(events, members)
                    RecurringLifeCard(session = session, events = events) { scope.launch { refresh() } }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(590.dp)
                    ) {
                        ExactCalendarScreen(
                            selectedDate,
                            { selectedDate = it },
                            events,
                            members,
                            palette,
                            themeMode,
                            onAdd = {
                                addEventInitialTitle = ""
                                showAddEvent = true
                            },
                            onAddLaundry = {
                                addEventInitialTitle = "🧺 Tvätt"
                                showAddEvent = true
                            },
                            addMenuRequest = assistantAddRequest
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    when (selectedTab) {
                        1 -> ShoppingScreen(
                            session,
                            shopping,
                            { name -> scope.launch { SupabaseSync.addShopping(session, name); refresh() } },
                            { item -> scope.launch { SupabaseSync.toggleShopping(session, item); refresh() } },
                            { scope.launch { SupabaseSync.clearChecked(session); refresh() } }
                        )
                        2 -> ToDoScreen(session)
                        3 -> {
                            RunningProgressCard(
                                session = session,
                                members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                events = events,
                                onChanged = {
                                    refresh()
                                    FamilyCalendarWidget.enqueueRefresh(context)
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                            EditableFamilyScreen(
                                members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                events,
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
                                },
                                { event, title, date, time ->
                                    scope.launch {
                                        SupabaseSync.updateEvent(session, event.id, title, date, time, event.endTime, event.memberId)
                                        refresh()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                    }
                                },
                                { event ->
                                    scope.launch {
                                        deleteCalendarEventsDirect(session, listOf(event.id))
                                        refresh()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                    }
                                }
                            )
                        }
                        4 -> SettingsScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID }, sportUrl, sportMemberId, themeMode, onThemeModeSaved, onSportSettingsSaved) { url, memberId ->
                            scope.launch {
                                message = "Importerar SportAdmin…"
                                runCatching { SupabaseSync.importSportAdmin(session, url, memberId) }
                                    .onSuccess { message = "$it SportAdmin-aktiviteter synkade" }
                                    .onFailure { message = "SportAdmin-fel: ${it.message}" }
                                refresh()
                            }
                        }
                        5 -> FamilyLocationScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID })
                    }
                }
            }
        }
    }

    AutomaticUpdateNotice()

    if (showAddEvent) {
        AddEventDialog(members, selectedDate, addEventInitialTitle, { showAddEvent = false }) { title, startTime, endTime, memberId, dates, birthday, recurrence ->
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
                    val seriesId = if (recurrence == RecurrenceMode.NONE) null else java.util.UUID.randomUUID().toString()
                    targetDates.forEach { date ->
                        SupabaseSync.addEvent(session, title, date, startTime, endTime, memberId, seriesId)
                    }
                }
                refresh()
                FamilyCalendarWidget.enqueueRefresh(context)
                showAddEvent = false
            }
        }
    }
}

@Composable
private fun ShoppingScreen(
    session: FamilySession,
    items: List<SyncShoppingItem>,
    onAdd: (String) -> Unit,
    onToggle: (SyncShoppingItem) -> Unit,
    onClear: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val openItems = items.filterNot { it.checked }
    val checkedItems = items.filter { it.checked }
    val total = items.size
    val done = checkedItems.size

    fun categoryFor(name: String): String {
        val value = name.lowercase(Locale("sv", "SE"))
        return when {
            listOf("äpp", "banan", "päron", "apels", "citron", "gurk", "tomat", "sallad", "lök", "potatis", "morot", "paprika", "avokado", "frukt", "grönsak").any { it in value } -> "Frukt & grönt"
            listOf("mjölk", "fil", "yoghurt", "ost", "smör", "grädde", "ägg", "kvarg").any { it in value } -> "Mejeri & ägg"
            listOf("kött", "kyckling", "färs", "korv", "bacon", "fisk", "lax", "skinka").any { it in value } -> "Kött & fisk"
            listOf("fryst", "glass", "pizza", "pommes").any { it in value } -> "Frys"
            listOf("schampo", "tvål", "tand", "deo", "blöj", "toalett", "hygien").any { it in value } -> "Hygien"
            listOf("disk", "tvätt", "soppås", "hushåll", "folie", "bakplåt", "rengör").any { it in value } -> "Hushåll"
            listOf("bröd", "kaffe", "te", "pasta", "ris", "mjöl", "socker", "fling", "konserv", "sås", "krydd").any { it in value } -> "Skafferi"
            else -> "Övrigt"
        }
    }

    val categoryOrder = listOf("Frukt & grönt", "Mejeri & ägg", "Kött & fisk", "Skafferi", "Frys", "Hygien", "Hushåll", "Övrigt")
    val grouped = openItems.groupBy { categoryFor(it.name) }

    Text("Inköp", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("Familjens gemensamma inköpslista", color = Muted)
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = SoftPurple), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (total == 0) "Listan är tom" else "$done av $total klara", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (openItems.isEmpty() && total > 0) "Allt är fixat" else "${openItems.size} kvar att handla", color = Muted, fontSize = 13.sp)
                }
                if (total > 0) Text("${done * 100 / total}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Lägg till vara") }, placeholder = { Text("t.ex. mjölk, bananer, kaffe") }, singleLine = true, modifier = Modifier.weight(1f))
        FilledIconButton(onClick = {
            text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }.forEach(onAdd)
            text = ""
        }, enabled = text.isNotBlank(), modifier = Modifier.size(56.dp), shape = RoundedCornerShape(14.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Add, contentDescription = "Lägg till", tint = Color.Black)
        }
    }
    Text("Tips: skriv flera varor separerade med kommatecken", color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(14.dp))

    if (openItems.isEmpty() && checkedItems.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(8.dp))
                Text("Dags att fylla listan", fontWeight = FontWeight.SemiBold)
                Text("Lägg till det ni behöver ovan", color = Muted, fontSize = 13.sp)
            }
        }
    }

    categoryOrder.forEach { category ->
        val categoryItems = grouped[category].orEmpty()
        if (categoryItems.isNotEmpty()) {
            Text(category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
            categoryItems.forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = false, onCheckedChange = { onToggle(item) })
                        Text(item.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (checkedItems.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Klara (${checkedItems.size})", color = Muted, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { showClearConfirmation = true }) { Text("Rensa avbockade") }
        }
        checkedItems.forEach { item ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = .62f)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = true, onCheckedChange = { onToggle(item) })
                    Text(item.name, color = Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(onDismissRequest = { showClearConfirmation = false }, title = { Text("Rensa avbockade?") }, text = { Text("${checkedItems.size} ${if (checkedItems.size == 1) "vara" else "varor"} tas bort från listan.") }, confirmButton = { TextButton(onClick = { showClearConfirmation = false; onClear() }) { Text("Ta bort") } }, dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Avbryt") } })
    }
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
    initialSportMemberId: String?,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
    onImport: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var url by remember(initialSportUrl) { mutableStateOf(initialSportUrl) }
    var memberId by remember(initialSportMemberId, members) {
        mutableStateOf(initialSportMemberId?.takeIf { id -> members.any { it.id == id } } ?: members.firstOrNull()?.id)
    }
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
    Text("Koppla lagets kalender till rätt familjemedlem. Den uppdateras automatiskt var 30:e minut.", color = Muted, fontSize = 12.sp)
    OutlinedTextField(url, { url = it }, label = { Text("Kalenderlänk") }, modifier = Modifier.fillMaxWidth())
    if (members.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Gäller", color = Muted, fontSize = 12.sp)
        members.forEach { member ->
            Row(
                Modifier.fillMaxWidth().clickable { memberId = member.id }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = memberId == member.id, onClick = { memberId = member.id })
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                Spacer(Modifier.width(8.dp))
                Text(member.name)
            }
        }
    }
    Button(
        onClick = { onSaveSportSettings(url.trim(), memberId); onImport(url.trim(), memberId) },
        enabled = url.isNotBlank() && memberId != null,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Spara och synka nu") }

    Spacer(Modifier.height(20.dp))
    AppUpdateSettingsCard()
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
            Icons.Default.Settings to "Inställningar",
            Icons.Default.LocationOn to "Plats"
        ).forEachIndexed { i, (icon, label) ->
            NavigationBarItem(
                selected == i,
                { onSelect(i) },
                { Icon(icon, label) },
                label = { Text(label, maxLines = 1, softWrap = false, fontSize = 9.sp) },
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
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String?, List<LocalDate>, Boolean, RecurrenceMode) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("sv", "SE")) }
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
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
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF17131D),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(if (isBirthday) "Ny födelsedag" else "Ny aktivitet", fontWeight = FontWeight.Bold) },
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
                enabled = title.isNotBlank() && dates.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White)
            ) {
                Text(if (isBirthday) "Lägg till födelsedag" else if (dates.size > 1) "Lägg till ${dates.size} dagar" else "Lägg till")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}
