package se.familjekalender.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val Bg = Color(0xFF111015)
private val CardBg = Color(0xFF1B191F)
private val Purple = Color(0xFFB47CFF)
private val SoftPurple = Color(0xFF2B2038)
private val Muted = Color(0xFFAAA4B2)
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
    val heroStart: Color,
    val heroEnd: Color,
    val title: String,
    val note: String,
    val decoration: String
)

private fun resolvedTheme(mode: ThemeMode, date: LocalDate = LocalDate.now()): ThemeMode {
    if (mode != ThemeMode.AUTO) return mode
    return when (date.monthValue) {
        3, 4, 5 -> ThemeMode.SPRING
        6, 7, 8 -> ThemeMode.SUMMER
        9, 10, 11 -> ThemeMode.AUTUMN
        else -> ThemeMode.WINTER
    }
}

private fun paletteFor(mode: ThemeMode): SeasonPalette = when (resolvedTheme(mode)) {
    ThemeMode.SPRING -> SeasonPalette(
        ThemeMode.SPRING,
        Color(0xFFF19BC2), Color(0xFF342531), Color(0xFF21372E), Color(0xFF56384A),
        "Våren är här 🌸", "Små planer växer till fina dagar.", "🌿  🌸  🌱"
    )
    ThemeMode.SUMMER -> SeasonPalette(
        ThemeMode.SUMMER,
        Color(0xFFFFC96B), Color(0xFF3A3022), Color(0xFF17374A), Color(0xFF5A4631),
        "Sommarkänsla ☀️", "Långa dagar, glass och plats för spontana äventyr.", "☀️  🌊  🏖️"
    )
    ThemeMode.AUTUMN -> SeasonPalette(
        ThemeMode.AUTUMN,
        Color(0xFFFFA45B), Color(0xFF3A281F), Color(0xFF3C211E), Color(0xFF6A3D25),
        "Höstmys 🍂", "Planera vardagen och spara tid för det mjuka.", "🍁  🍂  ☕"
    )
    ThemeMode.WINTER -> SeasonPalette(
        ThemeMode.WINTER,
        Color(0xFF9DBBFF), Color(0xFF222A3A), Color(0xFF142036), Color(0xFF34294A),
        "Vinterlugnet ❄️", "Håll koll på allt och lämna plats för mys.", "❄️  ✨  🏠"
    )
    ThemeMode.CLASSIC -> SeasonPalette(
        ThemeMode.CLASSIC,
        Purple, SoftPurple, Color(0xFF20182A), Color(0xFF38264A),
        "Familjekalendern 💜", "Allt som händer. På ett ställe.", "💜  📅  ✨"
    )
    ThemeMode.AUTO -> error("AUTO resolves before palette creation")
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
            runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name) }
                .getOrDefault(ThemeMode.AUTO)
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
                    session = session!!,
                    sportUrl = prefs.getString("sport_url", "") ?: "",
                    themeMode = themeMode,
                    onSportUrlSaved = { prefs.edit().putString("sport_url", it).apply() },
                    onThemeModeSaved = {
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
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true; error = ""
                    runCatching { SupabaseSync.createFamily(familyName) }
                        .onSuccess(onReady)
                        .onFailure { error = it.message ?: "Kunde inte skapa familjen" }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Skapar…" else "Skapa familj") }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))
        Text("Har familjen redan skapats?", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            joinCode,
            { joinCode = it.uppercase() },
            label = { Text("Familjekod") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    busy = true; error = ""
                    runCatching { SupabaseSync.joinFamily(joinCode) }
                        .onSuccess(onReady)
                        .onFailure { error = it.message ?: "Kunde inte ansluta" }
                    busy = false
                }
            },
            enabled = !busy && joinCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Anslut till familj") }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
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
        }.onFailure { message = "Synkfel: ${it.message ?: "okänt fel"}" }
    }

    LaunchedEffect(session.id) {
        refresh()
        while (true) {
            delay(8000)
            refresh()
        }
    }

    Scaffold(
        containerColor = Bg,
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddEvent = true },
                    containerColor = palette.accent,
                    contentColor = Color(0xFF17131C),
                    shape = CircleShape
                ) { Icon(Icons.Default.Add, contentDescription = "Lägg till aktivitet") }
            }
        },
        bottomBar = { BottomNav(selectedTab) { selectedTab = it } }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 18.dp).fillMaxSize()) {
            Spacer(Modifier.height(14.dp))
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            when (selectedTab) {
                0 -> CalendarScreen(selectedDate, { selectedDate = it }, events, members, palette)
                1 -> ShoppingScreen(
                    items = shopping,
                    onAdd = { name -> scope.launch { SupabaseSync.addShopping(session, name); refresh() } },
                    onToggle = { item -> scope.launch { SupabaseSync.toggleShopping(session, item); refresh() } },
                    onClear = { scope.launch { SupabaseSync.clearChecked(session); refresh() } }
                )
                2 -> FamilyScreen(
                    members = members,
                    onAdd = { name, role ->
                        scope.launch {
                            val color = MemberColors[members.size % MemberColors.size]
                            SupabaseSync.addMember(session, name, role, color)
                            refresh()
                        }
                    }
                )
                3 -> SettingsScreen(
                    session = session,
                    members = members,
                    initialSportUrl = sportUrl,
                    themeMode = themeMode,
                    onThemeChanged = onThemeModeSaved,
                    onSaveUrl = onSportUrlSaved,
                    onImport = { url, memberId ->
                        scope.launch {
                            message = "Importerar SportAdmin…"
                            runCatching { SupabaseSync.importSportAdmin(session, url, memberId) }
                                .onSuccess { count -> message = "$count SportAdmin-aktiviteter synkade" }
                                .onFailure { message = "SportAdmin-fel: ${it.message}" }
                            refresh()
                        }
                    }
                )
            }
        }
    }

    if (showAddEvent) {
        AddEventDialog(
            members = members,
            selectedDate = selectedDate,
            onDismiss = { showAddEvent = false },
            onAdd = { title, time, memberId ->
                scope.launch {
                    SupabaseSync.addEvent(session, title, selectedDate, time, memberId)
                    refresh()
                    showAddEvent = false
                }
            }
        )
    }
}

@Composable
private fun SeasonHeader(palette: SeasonPalette) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd)))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(palette.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(palette.note, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                }
                Text(palette.decoration, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun CalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    palette: SeasonPalette
) {
    Text("Familjekalendern", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Allt som händer. På ett ställe.", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(14.dp))
    SeasonHeader(palette)
    Spacer(Modifier.height(16.dp))
    MonthCalendar(selectedDate, onSelect, palette.accent)
    Spacer(Modifier.height(18.dp))
    DayOverview(selectedDate, events.filter { it.date == selectedDate }, members, palette.accent)
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
    Text("Synkas mellan era telefoner", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }) {
            Icon(Icons.Default.Add, contentDescription = "Lägg till")
        }
    }
    Spacer(Modifier.height(14.dp))
    if (items.isEmpty()) Text("Listan är tom.", color = Muted)
    items.forEach { item ->
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onToggle(item) }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.checked, { onToggle(item) })
                Spacer(Modifier.width(8.dp))
                Text(item.name, color = if (item.checked) Muted else Color.White, modifier = Modifier.weight(1f))
            }
        }
    }
    if (items.any { it.checked }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClear) { Text("Rensa avbockade") }
        }
    }
}

@Composable
private fun FamilyScreen(members: List<SyncMember>, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    Text("Familjen", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Lägg till personer och valfria roller", color = Muted)
    Spacer(Modifier.height(18.dp))
    members.forEach { member ->
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(member.name, fontWeight = FontWeight.SemiBold)
                    if (member.role.isNotBlank()) Text(member.role, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(name, { name = it }, label = { Text("Namn") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(role, { role = it }, label = { Text("Valfri roll") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { if (name.isNotBlank()) { onAdd(name.trim(), role.trim()); name = ""; role = "" } },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Lägg till person") }
}

private fun shareFamilyInvite(context: Context, session: FamilySession) {
    val text = "Du är inbjuden till ${session.name} i Familjekalendern 💜\n\nFamiljekod: ${session.code}\n\nÖppna Familjekalendern och välj Anslut till familj."
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Inbjudan till ${session.name}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Bjud in till familjen"))
}

private fun copyFamilyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Familjekod", code))
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
    var selectedMemberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
    var copied by remember { mutableStateOf(false) }

    Text("Inställningar", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))

    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Familjen", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(session.name, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text("Familjekod", color = Muted, fontSize = 12.sp)
            Text(session.code, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { shareFamilyInvite(context, session) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Bjud in till familjen")
            }
            OutlinedButton(
                onClick = { copyFamilyCode(context, session.code); copied = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Familjekod kopierad" else "Kopiera familjekod")
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    Text("Tema", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text("Automatisk följer årstiden. Du kan också låsa ett tema.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    ThemeSelector(themeMode, onThemeChanged)

    Spacer(Modifier.height(22.dp))
    Text("SportAdmin", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text("Klistra in kalenderlänken från SportAdmin en gång.", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(url, { url = it }, label = { Text("SportAdmin webcal-länk") }, modifier = Modifier.fillMaxWidth())
    if (members.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Koppla aktiviteterna till:", color = Muted, fontSize = 12.sp)
        members.forEach { member ->
            Row(
                Modifier.fillMaxWidth().clickable { selectedMemberId = member.id }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedMemberId == member.id, onClick = { selectedMemberId = member.id })
                Text(member.name)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = { if (url.isNotBlank()) { onSaveUrl(url.trim()); onImport(url.trim(), selectedMemberId) } },
        enabled = url.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Spara och importera SportAdmin") }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column {
        ThemeMode.entries.chunked(2).forEach { rowThemes ->
            Row(Modifier.fillMaxWidth()) {
                rowThemes.forEach { mode ->
                    val active = selected == mode
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (active) paletteFor(mode).soft else CardBg),
                        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, paletteFor(mode).accent) else null,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).padding(4.dp).clickable { onSelect(mode) }
                    ) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(mode.emoji, fontSize = 22.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(mode.label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MonthCalendar(selected: LocalDate, onSelect: (LocalDate) -> Unit, accent: Color = Purple) {
    var visibleMonth by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    val offset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val monthName = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                Text("$monthName ${visibleMonth.year}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "O", "T", "F", "L", "S").forEach {
                    Text(it, color = Muted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { dayOfWeek ->
                        val day = week * 7 + dayOfWeek - offset + 1
                        if (day in 1..visibleMonth.lengthOfMonth()) {
                            val date = visibleMonth.atDay(day)
                            val active = date == selected
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(if (active) accent else Color.Transparent).clickable { onSelect(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day.toString(), color = if (active) Color(0xFF17131C) else Color.White, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            }
                        } else Spacer(Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            TextButton(onClick = { val today = LocalDate.now(); visibleMonth = YearMonth.from(today); onSelect(today) }) { Text("Idag") }
        }
    }
}

@Composable
private fun DayOverview(date: LocalDate, events: List<SyncEvent>, members: List<SyncMember>, accent: Color = Purple) {
    Text("Dagens aktiviteter", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    if (events.isEmpty()) Text("Inget planerat ännu", color = Muted)
    events.forEach { event ->
        val member = members.find { it.id == event.memberId }
        val color = member?.let { Color(it.colorArgb.toInt()) } ?: accent
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(42.dp).clip(RoundedCornerShape(4.dp)).background(color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.title, fontWeight = FontWeight.SemiBold)
                    Text(member?.name ?: if (event.source == "sportadmin") "SportAdmin" else "Familjen", color = color, fontSize = 12.sp)
                }
                Text(event.time, color = Muted)
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFF17151A)) {
        NavigationBarItem(selected == 0, { onSelect(0) }, { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Kalender") })
        NavigationBarItem(selected == 1, { onSelect(1) }, { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Inköp") })
        NavigationBarItem(selected == 2, { onSelect(2) }, { Icon(Icons.Default.People, null) }, label = { Text("Familj") })
        NavigationBarItem(selected == 3, { onSelect(3) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Inställningar") })
    }
}

@Composable
private fun AddEventDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("18:00") }
    var memberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ny aktivitet") },
        text = {
            Column {
                Text("${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}", color = Muted)
                OutlinedTextField(title, { title = it }, label = { Text("Aktivitet") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(time, { time = it }, label = { Text("Tid, t.ex. 18:00") })
                members.forEach { member ->
                    Row(Modifier.fillMaxWidth().clickable { memberId = member.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(memberId == member.id, { memberId = member.id })
                        Text(member.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onAdd(title.trim(), time.trim(), memberId) }) { Text("Lägg till") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}
