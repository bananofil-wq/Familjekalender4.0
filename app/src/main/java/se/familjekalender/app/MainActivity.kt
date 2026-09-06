package se.familjekalender.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

private val Bg = Color(0xFF0F0E13)
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
    val skyTop: Color,
    val skyBottom: Color,
    val sceneDark: Color,
    val sceneLight: Color
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
        ThemeMode.SPRING, Color(0xFFF3A5C8), Color(0xFF352430),
        Color(0xFF233B39), Color(0xFF5A4055), Color(0xFF173029), Color(0xFFFFC3DD)
    )
    ThemeMode.SUMMER -> SeasonPalette(
        ThemeMode.SUMMER, Color(0xFFFFC96B), Color(0xFF3A3022),
        Color(0xFF173C59), Color(0xFF8A6241), Color(0xFF12314A), Color(0xFFFFD983)
    )
    ThemeMode.AUTUMN -> SeasonPalette(
        ThemeMode.AUTUMN, Color(0xFFFFA45B), Color(0xFF3A281F),
        Color(0xFF301A23), Color(0xFF8B482D), Color(0xFF251419), Color(0xFFD66B3D)
    )
    ThemeMode.WINTER -> SeasonPalette(
        ThemeMode.WINTER, Color(0xFF9DBBFF), Color(0xFF222A3A),
        Color(0xFF111B34), Color(0xFF3C4268), Color(0xFF0D1630), Color(0xFFD9E8FF)
    )
    ThemeMode.CLASSIC -> SeasonPalette(
        ThemeMode.CLASSIC, Purple, SoftPurple,
        Color(0xFF17121F), Color(0xFF39264A), Color(0xFF17121F), Color(0xFFB47CFF)
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
                    busy = true
                    error = ""
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
        OutlinedTextField(joinCode, { joinCode = it.uppercase() }, label = { Text("Familjekod") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    busy = true
                    error = ""
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
        bottomBar = { BottomNav(selectedTab, palette.accent) { selectedTab = it } }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                CalendarScreen(selectedDate, { selectedDate = it }, events, members, palette)
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 16.dp).verticalScroll(rememberScrollState())
                ) {
                    if (message.isNotBlank()) {
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    when (selectedTab) {
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
                    Spacer(Modifier.height(24.dp))
                }
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
private fun CalendarScreen(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    palette: SeasonPalette
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(245.dp)) {
            SeasonalScene(palette, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("Familjekalendern", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Allt som händer. På ett ställe.", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    when (palette.mode) {
                        ThemeMode.AUTUMN -> "September i familjen"
                        ThemeMode.WINTER -> "Vinterdagar tillsammans"
                        ThemeMode.SPRING -> "Vårens planer"
                        ThemeMode.SUMMER -> "Sommardagar tillsammans"
                        else -> "Familjens månad"
                    },
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).offset(y = (-38).dp)) {
            MonthCalendar(selectedDate, onSelect, palette.accent, events)
            Spacer(Modifier.height(16.dp))
            DayOverview(selectedDate, events.filter { it.date == selectedDate }, members, palette.accent)
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SeasonalScene(palette: SeasonPalette, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom)))
        val w = size.width
        val h = size.height

        when (palette.mode) {
            ThemeMode.AUTUMN -> {
                drawCircle(Color(0x55FFB36B), radius = w * 0.16f, center = Offset(w * 0.78f, h * 0.32f))
                drawLine(palette.sceneDark, Offset(w * 0.08f, h), Offset(w * 0.28f, h * 0.42f), strokeWidth = 12f)
                drawLine(palette.sceneDark, Offset(w * 0.24f, h * 0.55f), Offset(w * 0.48f, h * 0.24f), strokeWidth = 7f)
                listOf(
                    Offset(w * .17f, h * .56f), Offset(w * .25f, h * .45f), Offset(w * .34f, h * .39f),
                    Offset(w * .42f, h * .31f), Offset(w * .50f, h * .27f), Offset(w * .60f, h * .39f),
                    Offset(w * .69f, h * .48f), Offset(w * .78f, h * .57f), Offset(w * .87f, h * .44f)
                ).forEachIndexed { i, p ->
                    drawCircle(if (i % 2 == 0) palette.sceneLight else Color(0xFFFFA45B), radius = 10f + (i % 3) * 3f, center = p)
                }
                drawPath(Path().apply {
                    moveTo(0f, h)
                    lineTo(w * .22f, h * .78f)
                    lineTo(w * .42f, h * .91f)
                    lineTo(w * .65f, h * .75f)
                    lineTo(w, h * .88f)
                    lineTo(w, h)
                    close()
                }, palette.sceneDark.copy(alpha = .72f))
            }
            ThemeMode.WINTER -> {
                drawCircle(Color(0x88E9F1FF), radius = w * .12f, center = Offset(w * .78f, h * .29f))
                for (i in 0..18) {
                    val x = (i * 53f) % w
                    val y = ((i * 37f) % (h * .72f)) + 18f
                    drawCircle(Color.White.copy(alpha = .68f), radius = 2.5f + (i % 3), center = Offset(x, y))
                }
                fun pine(x: Float, base: Float, scale: Float) {
                    drawPath(Path().apply {
                        moveTo(x, base - 110f * scale)
                        lineTo(x - 45f * scale, base)
                        lineTo(x + 45f * scale, base)
                        close()
                    }, palette.sceneDark)
                    drawPath(Path().apply {
                        moveTo(x, base - 82f * scale)
                        lineTo(x - 58f * scale, base + 24f * scale)
                        lineTo(x + 58f * scale, base + 24f * scale)
                        close()
                    }, palette.sceneDark)
                }
                pine(w * .18f, h * .78f, 1f)
                pine(w * .35f, h * .82f, .75f)
                pine(w * .86f, h * .82f, .9f)
            }
            ThemeMode.SPRING -> {
                drawCircle(Color(0x55FFD6EA), radius = w * .18f, center = Offset(w * .82f, h * .32f))
                drawLine(palette.sceneDark, Offset(0f, h * .72f), Offset(w * .48f, h * .28f), strokeWidth = 9f)
                drawLine(palette.sceneDark, Offset(w * .28f, h * .47f), Offset(w * .58f, h * .20f), strokeWidth = 5f)
                listOf(
                    Offset(w*.16f,h*.57f), Offset(w*.25f,h*.48f), Offset(w*.34f,h*.39f), Offset(w*.43f,h*.31f),
                    Offset(w*.52f,h*.24f), Offset(w*.60f,h*.38f), Offset(w*.72f,h*.49f), Offset(w*.82f,h*.58f)
                ).forEach { p ->
                    drawCircle(palette.sceneLight, 9f, p)
                    drawCircle(Color(0xFFFFE3EF), 4f, p)
                }
                drawPath(Path().apply {
                    moveTo(0f, h)
                    lineTo(w*.3f,h*.82f)
                    lineTo(w*.55f,h*.9f)
                    lineTo(w,h*.78f)
                    lineTo(w,h)
                    close()
                }, Color(0xFF17342B).copy(alpha=.78f))
            }
            ThemeMode.SUMMER -> {
                drawCircle(palette.sceneLight, radius = w * .12f, center = Offset(w * .78f, h * .3f))
                drawRect(Color(0xFF164A66).copy(alpha=.72f), topLeft = Offset(0f, h*.64f), size = androidx.compose.ui.geometry.Size(w, h*.36f))
                for (i in 0..3) {
                    val y = h*.68f + i*18f
                    drawLine(Color.White.copy(alpha=.18f), Offset(0f,y), Offset(w,y+8f), strokeWidth=3f)
                }
                drawPath(Path().apply {
                    moveTo(0f,h)
                    lineTo(w*.28f,h*.76f)
                    lineTo(w*.48f,h*.84f)
                    lineTo(w*.72f,h*.73f)
                    lineTo(w,h*.8f)
                    lineTo(w,h)
                    close()
                }, Color(0xFF7B5739).copy(alpha=.78f))
            }
            ThemeMode.CLASSIC, ThemeMode.AUTO -> {
                drawCircle(palette.sceneLight.copy(alpha=.28f), w*.18f, Offset(w*.78f,h*.32f))
                drawCircle(palette.accent.copy(alpha=.18f), w*.12f, Offset(w*.22f,h*.55f))
            }
        }
    }
}

@Composable
private fun MonthCalendar(selected: LocalDate, onSelect: (LocalDate) -> Unit, accent: Color, events: List<SyncEvent>) {
    var visibleMonth by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    val offset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val monthName = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xF21A181F)),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                Text("$monthName ${visibleMonth.year}", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "O", "T", "F", "L", "S").forEach {
                    Text(it, color = Muted, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(7.dp))
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { dayOfWeek ->
                        val day = week * 7 + dayOfWeek - offset + 1
                        if (day in 1..visibleMonth.lengthOfMonth()) {
                            val date = visibleMonth.atDay(day)
                            val active = date == selected
                            val hasEvents = events.any { it.date == date }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(if (active) accent else Color.Transparent).clickable { onSelect(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(day.toString(), color = if (active) Color(0xFF17131C) else Color.White, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                                }
                                Box(Modifier.size(4.dp).clip(CircleShape).background(if (hasEvents) accent else Color.Transparent))
                            }
                        } else Spacer(Modifier.width(36.dp).height(38.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val today = LocalDate.now()
                    visibleMonth = YearMonth.from(today)
                    onSelect(today)
                }) { Text("Idag") }
            }
        }
    }
}

@Composable
private fun DayOverview(date: LocalDate, events: List<SyncEvent>, members: List<SyncMember>, accent: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Dagens aktiviteter", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}", color = Muted, fontSize = 13.sp)
        }
        Text("${events.size} st", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(10.dp))
    if (events.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xAA1B191F)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Inget planerat ännu", color = Muted, modifier = Modifier.padding(18.dp))
        }
    }
    events.forEach { event ->
        val member = members.find { it.id == event.memberId }
        val color = member?.let { Color(it.colorArgb.toInt()) } ?: accent
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(5.dp).height(44.dp).clip(RoundedCornerShape(5.dp)).background(color))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(member?.name ?: if (event.source == "sportadmin") "SportAdmin" else "Familjen", color = color, fontSize = 12.sp)
                }
                Text(event.time, color = Muted, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ShoppingScreen(items: List<SyncShoppingItem>, onAdd: (String) -> Unit, onToggle: (SyncShoppingItem) -> Unit, onClear: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Text("Inköpslista", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Synkas mellan era telefoner", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }
    }
    Spacer(Modifier.height(14.dp))
    if (items.isEmpty()) Text("Listan är tom.", color = Muted)
    items.forEach { item ->
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onToggle(item) }) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.checked, { onToggle(item) })
                Spacer(Modifier.width(8.dp))
                Text(item.name, color = if (item.checked) Muted else Color.White, modifier = Modifier.weight(1f))
            }
        }
    }
    if (items.any { it.checked }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onClear) { Text("Rensa avbockade") } }
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
    Button(onClick = { if (name.isNotBlank()) { onAdd(name.trim(), role.trim()); name = ""; role = "" } }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Lägg till person") }
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
            OutlinedButton(onClick = { copyFamilyCode(context, session.code); copied = true }, modifier = Modifier.fillMaxWidth()) {
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
            Row(Modifier.fillMaxWidth().clickable { selectedMemberId = member.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedMemberId == member.id, onClick = { selectedMemberId = member.id })
                Text(member.name)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { if (url.isNotBlank()) { onSaveUrl(url.trim()); onImport(url.trim(), selectedMemberId) } }, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Spara och importera SportAdmin")
    }
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
private fun BottomNav(selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xF518161C)) {
        NavigationBarItem(selected == 0, { onSelect(0) }, { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Kalender") }, colors = NavigationBarItemDefaults.colors(indicatorColor = accent.copy(alpha=.22f), selectedIconColor = accent))
        NavigationBarItem(selected == 1, { onSelect(1) }, { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Inköp") }, colors = NavigationBarItemDefaults.colors(indicatorColor = accent.copy(alpha=.22f), selectedIconColor = accent))
        NavigationBarItem(selected == 2, { onSelect(2) }, { Icon(Icons.Default.People, null) }, label = { Text("Familj") }, colors = NavigationBarItemDefaults.colors(indicatorColor = accent.copy(alpha=.22f), selectedIconColor = accent))
        NavigationBarItem(selected == 3, { onSelect(3) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Inställningar") }, colors = NavigationBarItemDefaults.colors(indicatorColor = accent.copy(alpha=.22f), selectedIconColor = accent))
    }
}

@Composable
private fun AddEventDialog(members: List<SyncMember>, selectedDate: LocalDate, onDismiss: () -> Unit, onAdd: (String, String, String?) -> Unit) {
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
