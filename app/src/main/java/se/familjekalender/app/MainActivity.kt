package se.familjekalender.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal val Bg = LuxuryBackground
internal val CardBg = PremiumGlass
internal val Purple = PremiumPurpleBright
internal val SoftPurple = PremiumGlassRaised
internal val Muted = PremiumMuted
private val MemberColors = listOf(0xFFB47CFF, 0xFFFF77A8, 0xFF62A9FF, 0xFF6DD6A7, 0xFFFFB86B)

enum class ThemeMode(val label: String, val emoji: String) {
    AUTO("Automatisk", "✨"),
    SPRING("Vår", "🌸"),
    SUMMER("Sommar", "☀️"),
    AUTUMN("Höst", "🍂"),
    WINTER("Vinter", "❄️"),
    CLASSIC("Klassisk", "💜"),
}

enum class UiLayoutMode(val label: String, val description: String) {
    MINIMAL("Clean", "Ren månadskalender med en diskret markering per dag"),
    FULL("Fullständigt", "Alla översikter, familjeverktyg och den fulla kalendern"),
    RUNNING("Sportläge", "Löpning i fokus med träningspass, progression, schema och återhämtning"),
    PERSONAL(
        "Personligt",
        "Helt anpassningsbar vy där du lägger till, tar bort, flyttar och ändrar storlek på alla delar.",
    ),
}

data class SeasonPalette(
    val mode: ThemeMode,
    val accent: Color,
    val soft: Color,
    val skyTop: Color,
    val skyBottom: Color,
    val sceneDark: Color,
    val sceneLight: Color,
)

private fun resolvedTheme(mode: ThemeMode, date: LocalDate = LocalDate.now()) =
    if (mode != ThemeMode.AUTO) mode
    else
        when (date.monthValue) {
            3,
            4,
            5 -> ThemeMode.SPRING

            6,
            7,
            8 -> ThemeMode.SUMMER

            9,
            10,
            11 -> ThemeMode.AUTUMN

            else -> ThemeMode.WINTER
        }

internal fun paletteFor(mode: ThemeMode, date: LocalDate = LocalDate.now()): SeasonPalette =
    when (resolvedTheme(mode, date)) {
        ThemeMode.SPRING ->
            SeasonPalette(
                ThemeMode.SPRING,
                Color(0xFFF3A5C8),
                Color(0xFF352430),
                Color(0xFF233B39),
                Color(0xFF5A4055),
                Color(0xFF173029),
                Color(0xFFFFC3DD),
            )

        ThemeMode.SUMMER ->
            SeasonPalette(
                ThemeMode.SUMMER,
                Color(0xFFFFC96B),
                Color(0xFF3A3022),
                Color(0xFF173C59),
                Color(0xFF8A6241),
                Color(0xFF12314A),
                Color(0xFFFFD983),
            )

        ThemeMode.AUTUMN ->
            SeasonPalette(
                ThemeMode.AUTUMN,
                Color(0xFFFFA45B),
                Color(0xFF3A281F),
                Color(0xFF301A23),
                Color(0xFF8B482D),
                Color(0xFF251419),
                Color(0xFFD66B3D),
            )

        ThemeMode.WINTER ->
            SeasonPalette(
                ThemeMode.WINTER,
                Color(0xFF9DBBFF),
                Color(0xFF222A3A),
                Color(0xFF111B34),
                Color(0xFF3C4268),
                Color(0xFF0D1630),
                Color(0xFFD9E8FF),
            )

        ThemeMode.CLASSIC ->
            SeasonPalette(
                ThemeMode.CLASSIC,
                Purple,
                SoftPurple,
                Color(0xFF17121F),
                Color(0xFF39264A),
                Color(0xFF17121F),
                Purple,
            )

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
        if (
            locationPrefs.getBoolean("sharing_enabled", false) &&
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
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { granted ->
            prefs
                .edit()
                .putBoolean("notification_permission_requested", true)
                .putBoolean("notification_permission_granted", granted)
                .apply()
        }

    fun requestNotificationPermissionAfterFamilySetup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            prefs
                .edit()
                .putBoolean("notification_permission_requested", true)
                .putBoolean("notification_permission_granted", true)
                .apply()
            return
        }

        if (!prefs.getBoolean("notification_permission_requested", false)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var session by remember {
        mutableStateOf(
            prefs.getString("family_id", null)?.let {
                FamilySession(
                    it,
                    prefs.getString("family_name", "Min familj") ?: "Min familj",
                    prefs.getString("family_code", "") ?: "",
                )
            }
        )
    }
    var themeMode by remember {
        mutableStateOf(
            runCatching {
                ThemeMode.valueOf(
                    prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
                )
            }
                .getOrDefault(ThemeMode.AUTO)
        )
    }
    var uiLayoutMode by remember {
        mutableStateOf(
            runCatching {
                UiLayoutMode.valueOf(
                    prefs.getString("ui_layout_mode", UiLayoutMode.MINIMAL.name)
                        ?: UiLayoutMode.MINIMAL.name
                )
            }
                .getOrDefault(UiLayoutMode.MINIMAL)
        )
    }
    val palette = paletteFor(themeMode)
    FamiljekalenderLuxuryTheme(palette) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (session == null) {
                FamilySetupScreen { created ->
                    prefs
                        .edit()
                        .putString("family_id", created.id)
                        .putString("family_name", created.name)
                        .putString("family_code", created.code)
                        .apply()
                    session = created
                    requestNotificationPermissionAfterFamilySetup()
                }
            } else {
                FirstRunIdentityGate(session!!) {
                    SyncedApp(
                        session!!,
                        prefs.getString("sport_url", "") ?: "",
                        prefs.getString("sport_member_id", null),
                        themeMode,
                        uiLayoutMode,
                        { url, memberId ->
                            prefs
                                .edit()
                                .putString("sport_url", url)
                                .putString("sport_member_id", memberId)
                                .apply()
                        },
                        {
                            themeMode = it
                            prefs.edit().putString("theme_mode", it.name).apply()
                        },
                        {
                            uiLayoutMode = it
                            prefs.edit().putString("ui_layout_mode", it.name).apply()
                        },
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
        OutlinedTextField(
            familyName,
            { familyName = it },
            label = { Text("Familjens namn") },
            modifier = Modifier.fillMaxWidth(),
        )
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Skapa familj")
        }
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            joinCode,
            { joinCode = it.uppercase() },
            label = { Text("Familjekod") },
            modifier = Modifier.fillMaxWidth(),
        )
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Anslut till familj")
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SyncedApp(
    session: FamilySession,
    sportUrl: String,
    sportMemberId: String?,
    themeMode: ThemeMode,
    uiLayoutMode: UiLayoutMode,
    onSportSettingsSaved: (String, String?) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit,
    onUiLayoutModeSaved: (UiLayoutMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val motionEnabled = appMotionEnabled()
    val appPrefs = remember { context.getSharedPreferences("family_calendar", 0) }
    val palette = paletteFor(themeMode)
    var personalLayoutRevision by remember { mutableIntStateOf(0) }
    val personalProfile =
        remember(personalLayoutRevision) { PersonalLayoutStore.activeProfile(appPrefs) }
    var members by remember { mutableStateOf(emptyList<SyncMember>()) }
    var shopping by remember { mutableStateOf(emptyList<SyncShoppingItem>()) }
    var mailOffers by remember { mutableStateOf(emptyList<MailOffer>()) }
    var events by remember { mutableStateOf(emptyList<SyncEvent>()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddEvent by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<SyncEvent?>(null) }
    var addEventInitialTitle by remember { mutableStateOf("") }
    var assistantAddRequest by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }

    suspend fun refresh() {
        runCatching {
            members = SupabaseSync.loadMembers(session)
            shopping = SupabaseSync.loadShopping(session)
            mailOffers = SupabaseSync.loadMailOffers(session)
            val loadedEvents = SupabaseSync.loadEvents(session)
            events = RecurringScheduleSync.filterPausedScheduleEvents(session, loadedEvents)
        }
            .onSuccess {
                if (message.startsWith("Synkfel:")) message = ""
            }
            .onFailure {
                message = "Synkfel: ${it.message}"
            }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session.id, sportUrl, sportMemberId, lifecycle) {
        MailSyncScheduler.schedule(context)

        suspend fun syncExternalCalendars() {
            if (sportUrl.isNotBlank()) {
                runCatching { SupabaseSync.importSportAdmin(session, sportUrl, sportMemberId) }
            }
        }

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                syncExternalCalendars()
                refresh()
                delay(30L * 60L * 1000L)
            }
        }
    }

    // Shopping is shared family data and should feel live between phones.
    // Keep the general app sync at 30 minutes, but refresh only the shopping
    // list frequently while the Shopping tab is visible. Opening the tab also
    // causes an immediate refresh.
    LaunchedEffect(session.id, selectedTab, lifecycle) {
        if (selectedTab != 1) return@LaunchedEffect

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                runCatching { SupabaseSync.loadShopping(session) }
                    .onSuccess { latest ->
                        if (latest != shopping) shopping = latest
                        if (message.startsWith("Inköpssynkfel:")) message = ""
                    }
                    .onFailure {
                        message = "Inköpssynkfel: ${it.message}"
                    }
                delay(3_000L)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (uiLayoutMode == UiLayoutMode.MINIMAL) {
                MinimalBottomNav(selectedTab) { selectedTab = it }
            } else {
                BottomNav(selectedTab) { selectedTab = it }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (selectedTab == 0) {
                AnimatedContent(
                    targetState = uiLayoutMode,
                    transitionSpec = {
                        (fadeIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled))) +
                                scaleIn(
                                    tween(motionDuration(LuxuryMotion.Standard, motionEnabled)),
                                    initialScale = .992f,
                                )) togetherWith
                                (fadeOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled))) +
                                        scaleOut(
                                            tween(motionDuration(LuxuryMotion.Fast, motionEnabled)),
                                            targetScale = .996f,
                                        ))
                    },
                    label = "calendar-layout-mode",
                ) { mode ->
                    when (mode) {
                        UiLayoutMode.MINIMAL ->
                            MinimalCalendarScreen(
                                selectedDate = selectedDate,
                                onSelect = { selectedDate = it },
                                events = events,
                                members = members,
                                onAdd = {
                                    addEventInitialTitle = ""
                                    showAddEvent = true
                                },
                                onEdit = { editEvent = it },
                                onOpenSettings = { selectedTab = 4 },
                            )

                        UiLayoutMode.PERSONAL ->
                            PremiumModeBackground {
                                PersonalCalendarScreen(
                                    session = session,
                                    prefs = appPrefs,
                                    profile = personalProfile,
                                    revision = personalLayoutRevision,
                                    selectedDate = selectedDate,
                                    onSelectDate = { selectedDate = it },
                                    events = events,
                                    members = members,
                                    shopping = shopping,
                                    palette = palette,
                                    themeMode = themeMode,
                                    onAdd = {
                                        addEventInitialTitle = ""
                                        showAddEvent = true
                                    },
                                    onRefresh = {
                                        refresh()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                    },
                                )
                            }

                        UiLayoutMode.FULL ->
                            PremiumModeBackground {
                                Column(
                                    Modifier.fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    PremiumModeHeader(
                                        title = "Familjekalender",
                                        subtitle = "Fullständig familjeöversikt",
                                        onSettings = { selectedTab = 4 },
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    FamilyAssistantCard(session, events, members, shopping) {
                                        assistantAddRequest++
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    WeekOverviewCard(events, members)
                                    Spacer(Modifier.height(10.dp))
                                    FamilyAutopilotCard(events, members)
                                    Spacer(Modifier.height(10.dp))
                                    RecurringLifeCard(session = session, events = events) {
                                        scope.launch { refresh() }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    PremiumGlassPanel(Modifier.fillMaxWidth()) {
                                        Box(Modifier.fillMaxWidth().height(590.dp)) {
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
                                                addMenuRequest = assistantAddRequest,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                            }

                        UiLayoutMode.RUNNING ->
                            PremiumModeBackground {
                                RunningLifeDashboard(
                                    session = session,
                                    selectedDate = selectedDate,
                                    onSelectDate = { selectedDate = it },
                                    events = events,
                                    members = members,
                                    onAdd = {
                                        addEventInitialTitle = ""
                                        showAddEvent = true
                                    },
                                    onOpenSettings = { selectedTab = 4 },
                                    onRefresh = {
                                        refresh()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                    },
                                )
                            }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled))) +
                                slideInVertically(
                                    tween(motionDuration(LuxuryMotion.Standard, motionEnabled))
                                ) {
                                    it / 28
                                }) togetherWith
                                (fadeOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled))) +
                                        slideOutVertically(
                                            tween(motionDuration(LuxuryMotion.Fast, motionEnabled))
                                        ) {
                                            -it / 30
                                        })
                    },
                    label = "main-tab-content",
                ) { tab ->
                    Column(
                        Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())
                    ) {
                        if (message.isNotBlank())
                            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        when (tab) {
                            1 ->
                                ShoppingScreen(
                                    session,
                                    shopping,
                                    mailOffers,
                                    { name ->
                                        scope.launch {
                                            SupabaseSync.addShopping(session, name)
                                            refresh()
                                        }
                                    },
                                    { item ->
                                        scope.launch {
                                            SupabaseSync.toggleShopping(session, item)
                                            refresh()
                                        }
                                    },
                                    {
                                        scope.launch {
                                            SupabaseSync.clearChecked(session)
                                            refresh()
                                        }
                                    },
                                )

                            2 -> ToDoScreen(session)
                            3 -> {
                                if (uiLayoutMode == UiLayoutMode.MINIMAL) {
                                    OutlinedButton(
                                        onClick = { selectedTab = 5 },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null)
                                        Text(" Familjens plats")
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                                EditableFamilyScreen(
                                    members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                    events,
                                    { name, role ->
                                        scope.launch {
                                            val realMemberCount = members.count {
                                                it.id != ALL_FAMILY_MEMBER_ID
                                            }
                                            SupabaseSync.addMember(
                                                session,
                                                name,
                                                role,
                                                MemberColors[realMemberCount % MemberColors.size],
                                            )
                                            refresh()
                                        }
                                    },
                                    { member, color ->
                                        scope.launch {
                                            SupabaseSync.updateMemberColor(
                                                session,
                                                member.id,
                                                color,
                                            )
                                            refresh()
                                        }
                                    },
                                    { event, title, date, time ->
                                        scope.launch {
                                            SupabaseSync.updateEvent(
                                                session,
                                                event.id,
                                                title,
                                                date,
                                                time,
                                                event.endTime,
                                                event.memberId,
                                            )
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
                                    },
                                )
                            }

                            4 ->
                                SettingsScreen(
                                    session,
                                    members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                    sportUrl,
                                    sportMemberId,
                                    themeMode,
                                    uiLayoutMode,
                                    personalProfile,
                                    personalLayoutRevision,
                                    onThemeModeSaved,
                                    onUiLayoutModeSaved,
                                    { profile ->
                                        PersonalLayoutStore.setActiveProfile(appPrefs, profile)
                                        personalLayoutRevision++
                                    },
                                    { personalLayoutRevision++ },
                                    onSportSettingsSaved,
                                ) { url, memberId ->
                                    scope.launch {
                                        message = "Importerar SportAdmin…"
                                        runCatching {
                                            SupabaseSync.importSportAdmin(
                                                session,
                                                url,
                                                memberId,
                                            )
                                        }
                                            .onSuccess { result -> message = result.message }
                                            .onFailure { message = "SportAdmin-fel: ${it.message}" }
                                        refresh()
                                    }
                                }

                            5 ->
                                FamilyLocationScreen(
                                    session,
                                    members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                )
                        }
                    }
                }
            }
        }
    }

    AutomaticUpdateNotice()

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
                scope.launch {
                    val dayShift = java.time.temporal.ChronoUnit.DAYS.between(event.date, date)
                    val targets =
                        when (editScope) {
                            SeriesEditScope.THIS -> listOf(event)
                            SeriesEditScope.THIS_AND_FUTURE ->
                                matchingSeries.filter { !it.date.isBefore(event.date) }
                            SeriesEditScope.WHOLE_SERIES -> matchingSeries
                        }
                    val effectiveTargets = if (targets.isEmpty()) listOf(event) else targets
                    runCatching {
                        val splitSeriesId =
                            when {
                                event.seriesId == null -> null
                                editScope == SeriesEditScope.THIS_AND_FUTURE ->
                                    java.util.UUID.randomUUID().toString()
                                else -> event.seriesId
                            }

                        effectiveTargets.forEach { target ->
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
                            selectedDate = date
                            refresh()
                            FamilyCalendarWidget.enqueueRefresh(context)
                        }
                        .onFailure {
                            message = "Kunde inte spara aktiviteten: ${it.message}"
                        }
                }
            },
            onDelete = { deleteScope ->
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
                    }
                        .onSuccess {
                            editEvent = null
                            refresh()
                            FamilyCalendarWidget.enqueueRefresh(context)
                        }
                        .onFailure {
                            message = "Kunde inte ta bort aktiviteten: ${it.message}"
                        }
                }
            },
        )
    }

    if (showAddEvent) {
        AddEventDialog(
            members,
            selectedDate,
            addEventInitialTitle,
            { showAddEvent = false }) { title,
                                        startTime,
                                        endTime,
                                        memberId,
                                        dates,
                                        birthday,
                                        recurrence ->
            scope.launch {
                if (birthday) {
                    val today = LocalDate.now()
                    val month = dates.first().monthValue
                    val day = dates.first().dayOfMonth
                    for (year in today.year..(today.year + 20)) {
                        val birthdayDate =
                            runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
                        SupabaseSync.addEvent(
                            session,
                            "🌈 $title",
                            birthdayDate,
                            "09:00",
                            null,
                            memberId,
                        )
                    }
                } else {
                    val targetDates =
                        if (recurrence == RecurrenceMode.NONE) {
                            dates.sorted()
                        } else {
                            dates
                                .sorted()
                                .flatMap { recurringDates(it, recurrence) }
                                .distinct()
                                .sorted()
                        }
                    val seriesId =
                        if (recurrence == RecurrenceMode.NONE) null
                        else java.util.UUID.randomUUID().toString()
                    targetDates.forEach { date ->
                        SupabaseSync.addEvent(
                            session,
                            title,
                            date,
                            startTime,
                            endTime,
                            memberId,
                            seriesId,
                        )
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
    mailOffers: List<MailOffer>,
    onAdd: (String) -> Unit,
    onToggle: (SyncShoppingItem) -> Unit,
    onClear: () -> Unit,
) {
    val motionEnabled = appMotionEnabled()
    var text by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val openItems = items.filterNot { it.checked }
    val checkedItems = items.filter { it.checked }
    val total = items.size
    val done = checkedItems.size
    val progressTarget = if (total == 0) 0f else done.toFloat() / total.toFloat()
    val animatedProgress by
    animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(motionDuration(320, motionEnabled)),
        label = "shopping-progress",
    )

    fun categoryFor(name: String): String {
        val value = name.lowercase(Locale("sv", "SE"))
        return when {
            listOf(
                "äpp",
                "banan",
                "päron",
                "apels",
                "citron",
                "gurk",
                "tomat",
                "sallad",
                "lök",
                "potatis",
                "morot",
                "paprika",
                "avokado",
                "frukt",
                "grönsak",
            )
                .any { it in value } -> "Frukt & grönt"

            listOf("mjölk", "fil", "yoghurt", "ost", "smör", "grädde", "ägg", "kvarg").any {
                it in value
            } -> "Mejeri & ägg"

            listOf("kött", "kyckling", "färs", "korv", "bacon", "fisk", "lax", "skinka").any {
                it in value
            } -> "Kött & fisk"

            listOf("fryst", "glass", "pizza", "pommes").any { it in value } -> "Frys"
            listOf("schampo", "tvål", "tand", "deo", "blöj", "toalett", "hygien").any {
                it in value
            } -> "Hygien"

            listOf("disk", "tvätt", "soppås", "hushåll", "folie", "bakplåt", "rengör").any {
                it in value
            } -> "Hushåll"

            listOf(
                "bröd",
                "kaffe",
                "te",
                "pasta",
                "ris",
                "mjöl",
                "socker",
                "fling",
                "konserv",
                "sås",
                "krydd",
            )
                .any { it in value } -> "Skafferi"

            else -> "Övrigt"
        }
    }

    val categoryOrder =
        listOf(
            "Frukt & grönt",
            "Mejeri & ägg",
            "Kött & fisk",
            "Skafferi",
            "Frys",
            "Hygien",
            "Hushåll",
            "Övrigt",
        )
    val grouped = openItems.groupBy { categoryFor(it.name) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Inköp", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
            border =
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = .55f),
                ),
        ) {
            Text(
                "♛  Premium",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    Text("Familjens gemensamma inköpslista", color = Muted)
    Spacer(Modifier.height(16.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = SoftPurple.copy(alpha = .82f)),
        shape = RoundedCornerShape(24.dp),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = .42f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        if (total == 0) "Listan är tom" else "$done av $total klara",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                    Text(
                        if (openItems.isEmpty() && total > 0) "Allt är fixat"
                        else "${openItems.size} kvar att handla",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
                if (total > 0)
                    Text(
                        "${(animatedProgress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
            }
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Lägg till vara") },
            placeholder = { Text("t.ex. mjölk, bananer, kaffe") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = {
                text
                    .split(',', ';', '\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach(onAdd)
                text = ""
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(18.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Lägg till", tint = Color.Black)
        }
    }
    Text("Tips: skriv flera varor separerade med kommatecken", color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PremiumShoppingAction("🏷", "Prisjämför", true, Modifier.weight(1f))
        PremiumShoppingAction("▣", "Recept", false, Modifier.weight(1f))
        PremiumShoppingAction("☷", "Planera", false, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))

    AnimatedContent(
        targetState = items,
        transitionSpec = {
            (fadeIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled))) +
                    slideInVertically(tween(motionDuration(210, motionEnabled))) {
                        it / 16
                    }) togetherWith
                    (fadeOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled))) +
                            slideOutVertically(
                                tween(
                                    motionDuration(
                                        170,
                                        motionEnabled
                                    )
                                )
                            ) { -it / 18 })
        },
        label = "shopping-items",
    ) { visibleItems ->
        val visibleOpen = visibleItems.filterNot { it.checked }
        val visibleChecked = visibleItems.filter { it.checked }
        val visibleGrouped = visibleOpen.groupBy { categoryFor(it.name) }
        Column(
            Modifier.fillMaxWidth().animateContentSize(tween(motionDuration(220, motionEnabled)))
        ) {
            if (visibleOpen.isEmpty() && visibleChecked.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Dags att fylla listan", fontWeight = FontWeight.SemiBold)
                        Text("Lägg till det ni behöver ovan", color = Muted, fontSize = 13.sp)
                    }
                }
            }

            categoryOrder.forEach { category ->
                val categoryItems = visibleGrouped[category].orEmpty()
                if (categoryItems.isNotEmpty()) {
                    Text(
                        category,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 3.dp),
                    )
                    categoryItems.forEach { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = false, onCheckedChange = { onToggle(item) })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, color = Color.White, fontSize = 16.sp)
                                    val offersForItem =
                                        mailOffers
                                            .filter { mailOfferMatchesItem(it, item.name) }
                                            .sortedBy { it.price }
                                            .take(3)
                                    offersForItem.forEach { offer ->
                                        val unit = offer.unitText?.let { " / $it" }.orEmpty()
                                        val formattedPrice =
                                            "%.2f".format(Locale.US, offer.price).replace('.', ',')
                                        Text(
                                            "${offer.store}: $formattedPrice kr$unit",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (visibleChecked.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Klara (${visibleChecked.size})",
                        color = Muted,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { showClearConfirmation = true }) { Text("Rensa bockade") }
                }
                visibleChecked.forEach { item ->
                    Card(
                        colors =
                            CardDefaults.cardColors(containerColor = CardBg.copy(alpha = .62f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = true, onCheckedChange = { onToggle(item) })
                            Text(
                                item.name,
                                color = Muted,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Rensa bockade?") },
            text = {
                Text(
                    "${checkedItems.size} ${if (checkedItems.size == 1) "vara" else "varor"} tas bort från listan."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    }
                ) {
                    Text("Ta bort")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Avbryt")
                }
            },
        )
    }
}

@Composable
private fun PremiumShoppingAction(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color =
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .15f)
            else CardBg.copy(alpha = .72f),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .75f)
                else Color.White.copy(alpha = .08f),
            ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

private fun shareFamilyInvite(context: Context, session: FamilySession) {
    val encodedCode = Uri.encode(session.code)
    val appLink = "familjekalendern://join?code=$encodedCode"
    val text =
        "Du är inbjuden till ${session.name} i Familjekalendern 💜\n\nFamiljekod: ${session.code}\n\nHar du Familjekalendern installerad kan du öppna den här direktlänken:\n$appLink\n\nOm telefonen inte gör länken klickbar: öppna Familjekalendern och skriv familjekoden ovan under 'Anslut till familj'."
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Bjud in till familjen",
        )
    )
}

private fun copyFamilyCode(context: Context, code: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
        ClipData.newPlainText("Familjekod", code)
    )
}

private fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    )
}

@Composable
private fun SettingsScreen(
    session: FamilySession,
    members: List<SyncMember>,
    initialSportUrl: String,
    initialSportMemberId: String?,
    themeMode: ThemeMode,
    uiLayoutMode: UiLayoutMode,
    personalProfile: Int,
    personalLayoutRevision: Int,
    onThemeChanged: (ThemeMode) -> Unit,
    onUiLayoutChanged: (UiLayoutMode) -> Unit,
    onPersonalProfileChanged: (Int) -> Unit,
    onPersonalLayoutChanged: () -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
    onImport: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    var url by remember(initialSportUrl) { mutableStateOf(initialSportUrl) }
    var memberId by
    remember(initialSportMemberId, members) {
        mutableStateOf(
            initialSportMemberId?.takeIf { id -> members.any { it.id == id } }
                ?: members.firstOrNull()?.id
        )
    }

    Text("Inställningar", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText)
    Text("Familj, utseende och anslutningar", color = LuxuryTextMuted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))

    SettingsSectionCard(
        title = session.name,
        subtitle = "Familjekod · ${session.code}",
    ) {
        Button(
            onClick = { shareFamilyInvite(context, session) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Bjud in till familjen")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { copyFamilyCode(context, session.code) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("Kopiera familjekod")
        }
    }

    Spacer(Modifier.height(14.dp))
    SettingsSectionCard(
        title = "Utseende",
        subtitle = "Samma premiumkänsla, anpassad efter hur ni använder appen.",
    ) {
        UiLayoutMode.values()
            .filter { it != UiLayoutMode.PERSONAL }
            .forEach { mode ->
                val selected = uiLayoutMode == mode
                Surface(
                    color =
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                        else LuxurySurfaceElevated,
                    shape = MaterialTheme.shapes.medium,
                    modifier =
                        Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable {
                            onUiLayoutChanged(mode)
                        },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onUiLayoutChanged(mode) })
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, fontWeight = FontWeight.SemiBold, color = LuxuryText)
                            Text(
                                mode.description,
                                color = LuxuryTextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }

        if (uiLayoutMode == UiLayoutMode.PERSONAL) {
            Spacer(Modifier.height(6.dp))
            PersonalLayoutEditor(
                prefs = context.getSharedPreferences("family_calendar", 0),
                activeProfile = personalProfile,
                revision = personalLayoutRevision,
                onActiveProfileChanged = onPersonalProfileChanged,
                onChanged = onPersonalLayoutChanged,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Färgtema",
            color = LuxuryTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        ThemeMode.values().forEach { mode ->
            val selected = themeMode == mode
            Surface(
                color =
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                    else Color.Transparent,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().clickable { onThemeChanged(mode) },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = { onThemeChanged(mode) })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mode.label,
                        color = LuxuryText,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SettingsSectionCard(
        title = "SportAdmin",
        subtitle = "Synka lagets kalender automatiskt var 30:e minut.",
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Kalenderlänk") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (members.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Gäller för",
                color = LuxuryTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            members.forEach { member ->
                val selected = memberId == member.id
                Surface(
                    color = if (selected) LuxurySurfaceHigh else Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().clickable { memberId = member.id },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { memberId = member.id })
                        Box(
                            Modifier.size(9.dp)
                                .clip(CircleShape)
                                .background(Color(member.colorArgb.toInt()))
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(member.name, color = LuxuryText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                onSaveSportSettings(url.trim(), memberId)
                onImport(url.trim(), memberId)
            },
            enabled = url.isNotBlank() && memberId != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    disabledContainerColor = LuxurySurfaceHigh,
                    disabledContentColor = LuxuryTextMuted.copy(alpha = .55f),
                ),
        ) {
            Text("Spara och synka")
        }
    }

    val notificationPrefs = context.getSharedPreferences("family_calendar", 0)
    val notificationPermissionRequested =
        notificationPrefs.getBoolean("notification_permission_requested", false)
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        notificationPermissionRequested &&
        !hasNotificationPermission(context)
    ) {
        Spacer(Modifier.height(14.dp))
        SettingsSectionCard(
            title = "Aviseringar är avstängda",
            subtitle =
                "Familjekalendern behöver Androids aviseringsbehörighet för att kunna visa påminnelser och familjenotiser.",
        ) {
            Button(
                onClick = { openNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Öppna aviseringsinställningar")
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    MailSettingsCard(session)
    Spacer(Modifier.height(14.dp))
    AppUpdateSettingsCard()
    Spacer(Modifier.height(52.dp))
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = LuxuryTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun AnimatedNavIcon(icon: ImageVector, label: String, selected: Boolean) {
    val motionEnabled = appMotionEnabled()
    val scale by
    animateFloatAsState(
        targetValue = if (selected) 1.08f else .96f,
        animationSpec = tween(motionDuration(180, motionEnabled)),
        label = "nav-icon-scale",
    )
    val alpha by
    animateFloatAsState(
        targetValue = if (selected) 1f else .78f,
        animationSpec = tween(motionDuration(150, motionEnabled)),
        label = "nav-icon-alpha",
    )
    Icon(
        icon,
        contentDescription = label,
        modifier =
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
    )
}

@Composable
private fun MinimalBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    NavigationBar(containerColor = PremiumGlassRaised.copy(alpha = .96f), tonalElevation = 0.dp) {
        listOf(
            Triple(0, Icons.Default.CalendarMonth, "Kalender"),
            Triple(3, Icons.Default.People, "Familj"),
            Triple(1, Icons.Default.ShoppingCart, "Inköp"),
            Triple(2, Icons.Default.CheckCircle, "Att göra"),
            Triple(4, Icons.Default.Settings, "Inställningar"),
        )
            .forEach { (tab, icon, label) ->
                val isSelected = selected == tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    icon = { AnimatedNavIcon(icon, label, isSelected) },
                    label = { Text(label, maxLines = 1, softWrap = false, fontSize = 9.sp) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = accent,
                            selectedTextColor = accent,
                            unselectedIconColor = LuxuryTextMuted.copy(alpha = .72f),
                            unselectedTextColor = LuxuryTextMuted.copy(alpha = .72f),
                            indicatorColor = PremiumPurple.copy(alpha = .18f),
                        ),
                )
            }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    NavigationBar(containerColor = PremiumGlassRaised.copy(alpha = .96f), tonalElevation = 0.dp) {
        listOf(
            Icons.Default.CalendarMonth to "Kalender",
            Icons.Default.ShoppingCart to "Inköp",
            Icons.Default.CheckCircle to "To-Do",
            Icons.Default.People to "Familj",
            Icons.Default.Settings to "Inställningar",
            Icons.Default.LocationOn to "Plats",
        )
            .forEachIndexed { i, (icon, label) ->
                val isSelected = selected == i
                NavigationBarItem(
                    isSelected,
                    { onSelect(i) },
                    { AnimatedNavIcon(icon, label, isSelected) },
                    label = { Text(label, maxLines = 1, softWrap = false, fontSize = 9.sp) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = accent,
                            selectedTextColor = accent,
                            unselectedIconColor = LuxuryTextMuted.copy(alpha = .72f),
                            unselectedTextColor = LuxuryTextMuted.copy(alpha = .72f),
                            indicatorColor = PremiumPurple.copy(alpha = .18f),
                        ),
                )
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String?, List<LocalDate>, Boolean, RecurrenceMode) -> Unit,
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("sv", "SE")) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var endTime by remember { mutableStateOf<LocalTime?>(null) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
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
            base.dayOfMonth,
        )
            .show()
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
                    rangeStart.dayOfMonth,
                )
                    .show()
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth,
        )
            .show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(26.dp),
        containerColor = LuxurySurfaceElevated,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(if (isBirthday) "Ny födelsedag" else "Ny aktivitet", fontWeight = FontWeight.Bold)
        },
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        isBirthday,
                        { checked ->
                            isBirthday = checked
                            if (checked) recurrence = RecurrenceMode.NONE
                            if (checked && dates.size > 1) {
                                val first = dates.first()
                                dates.clear()
                                dates.add(first)
                            }
                        },
                    )
                    Text("Födelsedag – upprepas varje år")
                }

                if (!isBirthday) {
                    Spacer(Modifier.height(6.dp))
                    Text("Upprepning", fontWeight = FontWeight.Bold)
                    RecurrenceMode.values().forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable { recurrence = mode },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = recurrence == mode,
                                onClick = { recurrence = mode },
                            )
                            Text(mode.label)
                        }
                    }
                }

                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(if (isBirthday) "Namn" else "Aktivitet") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isBirthday) {
                    Spacer(Modifier.height(8.dp))
                    Text("Tid", fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showStartTimePicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                startTime?.let { "Start ${it.format(timeFormatter)}" }
                                    ?: "Välj starttid"
                            )
                        }
                        OutlinedButton(
                            onClick = { showEndTimePicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                endTime?.let { "Slut ${it.format(timeFormatter)}" }
                                    ?: "Välj sluttid"
                            )
                        }
                    }
                    if (startTime == null || endTime == null) {
                        Text(
                            "Välj både start- och sluttid.",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    } else if (!endTime!!.isAfter(startTime)) {
                        Text(
                            "Sluttiden räknas som nästa dag.",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    if (isBirthday) "Födelsedatum" else "Valda dagar",
                    fontWeight = FontWeight.Bold,
                )
                dates.sorted().forEach { date ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(date.format(dateFormatter), modifier = Modifier.weight(1f))
                        if (!isBirthday && dates.size > 1) {
                            TextButton(onClick = { dates.remove(date) }) { Text("Ta bort") }
                        }
                    }
                }

                if (isBirthday) {
                    OutlinedButton(
                        onClick = { openSingleDatePicker() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Välj födelsedatum")
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { openSingleDatePicker() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("+ En dag")
                        }
                        Button(onClick = { openRangePicker() }, modifier = Modifier.weight(1f)) {
                            Text("Flera dagar")
                        }
                    }
                }

                if (!isBirthday && dates.size > 1) {
                    Text(
                        "Samma aktivitet sparas på ${dates.size} dagar samtidigt.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                if (isBirthday) {
                    Text(
                        "Födelsedagen läggs in årligen i kalendern.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                members.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().clickable { memberId = member.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(memberId == member.id, { memberId = member.id })
                        if (member.id == ALL_FAMILY_MEMBER_ID) {
                            Text(
                                "★",
                                color = Color(0xFFFFD75E),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Box(
                                Modifier.size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(member.colorArgb.toInt()))
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(member.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedStartTime = startTime?.format(timeFormatter) ?: "09:00"
                    val selectedEndTime = endTime?.format(timeFormatter) ?: "10:00"
                    onAdd(
                        title.trim(),
                        selectedStartTime,
                        selectedEndTime,
                        memberId,
                        dates.toList(),
                        isBirthday,
                        recurrence,
                    )
                },
                enabled =
                    title.isNotBlank() &&
                            dates.isNotEmpty() &&
                            (isBirthday || (startTime != null && endTime != null)),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Purple,
                        contentColor = Color.White,
                    ),
            ) {
                Text(
                    if (isBirthday) {
                        "Lägg till födelsedag"
                    } else if (dates.size > 1) {
                        "Lägg till ${dates.size} dagar"
                    } else {
                        "Lägg till"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } },
    )

    if (showStartTimePicker) {
        EventTimePickerDialog(
            title = "Välj starttid",
            initialTime = startTime,
            defaultHour = 18,
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startTime = it
                showStartTimePicker = false
            },
        )
    }

    if (showEndTimePicker) {
        EventTimePickerDialog(
            title = "Välj sluttid",
            initialTime = endTime,
            defaultHour = 19,
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endTime = it
                showEndTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTimePickerDialog(
    title: String,
    initialTime: LocalTime?,
    defaultHour: Int,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialTime?.hour ?: defaultHour,
            initialMinute = initialTime?.minute ?: 0,
            is24Hour = true,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(state.hour, state.minute))
                }
            ) {
                Text("Klar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}
