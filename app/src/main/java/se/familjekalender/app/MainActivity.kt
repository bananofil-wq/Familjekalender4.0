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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
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
    HUGO_CHILD("Hugo-läge", "Minecraft-inspirerat barnläge med live-GPS, skolläge och dagens aktiviteter"),
    RUNNING("Sportläge", "Löpning i fokus med träningspass, progression, schema och återhämtning"),
    FULL("Fullständigt", "Alla översikter, familjeverktyg och den fulla kalendern"),
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
    private var widgetOpenTab by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetOpenTab = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
        setContent { ResponsiveApp { FamilyCalendarApp(widgetOpenTab) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetOpenTab = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
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
fun FamilyCalendarApp(initialTab: Int = -1) {
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
                        initialTab = initialTab,
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
    initialTab: Int = -1,
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
    var selectedTab by remember { mutableIntStateOf(if (initialTab in 0..5) initialTab else 0) }

    LaunchedEffect(initialTab) {
        if (initialTab in 0..5) selectedTab = initialTab
    }
    var showAddEvent by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<SyncEvent?>(null) }
    var addEventInitialTitle by remember { mutableStateOf("") }
    var assistantAddRequest by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val deviceMemberId = remember { appPrefs.getString("device_member_id", null) }

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

    LaunchedEffect(members, deviceMemberId) {
        val deviceMember = members.firstOrNull { it.id == deviceMemberId }
        if (
            deviceMember?.name.equals("Hugo", ignoreCase = true) &&
                !appPrefs.getBoolean("hugo_child_mode_initialized", false)
        ) {
            appPrefs.edit().putBoolean("hugo_child_mode_initialized", true).apply()
            selectedTab = 0
            onUiLayoutModeSaved(UiLayoutMode.HUGO_CHILD)
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
        if (selectedTab != 1) {
            if (message.startsWith("Inköpssynkfel:")) message = ""
            return@LaunchedEffect
        }

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try {
                    val latest = SupabaseSync.loadShopping(session)
                    if (latest != shopping) shopping = latest
                    if (message.startsWith("Inköpssynkfel:")) message = ""
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    // Leaving the Shopping tab cancels this coroutine by design.
                    // Propagate cancellation instead of surfacing it as a sync error.
                    throw cancelled
                } catch (error: Throwable) {
                    message = "Inköpssynkfel: ${error.message ?: "Okänt fel"}"
                }
                delay(3_000L)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedTab == 1) {
                ShoppingBottomNav(selectedTab) { selectedTab = it }
            } else {
                when (uiLayoutMode) {
                    UiLayoutMode.MINIMAL -> MinimalBottomNav(selectedTab) { selectedTab = it }
                    UiLayoutMode.HUGO_CHILD -> HugoBottomNav(selectedTab) { selectedTab = it }
                    UiLayoutMode.RUNNING -> SportBottomNav(selectedTab) { selectedTab = it }
                    else -> BottomNav(selectedTab) { selectedTab = it }
                }
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
                                session = session,
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
                                onOpenLocation = { selectedTab = 5 },
                                themeMode = themeMode,
                            )

                        UiLayoutMode.HUGO_CHILD ->
                            HugoChildModeScreen(
                                session = session,
                                member =
                                    members.firstOrNull { it.id == deviceMemberId }
                                        ?.takeIf { it.name.equals("Hugo", ignoreCase = true) }
                                        ?: members.firstOrNull {
                                            it.name.equals("Hugo", ignoreCase = true)
                                        },
                                events = events,
                                members = members,
                                onOpenLocation = { selectedTab = 5 },
                            )

                        UiLayoutMode.PERSONAL ->
                            PremiumModeBackground(themeMode = themeMode) {
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
                            PremiumModeBackground(themeMode = themeMode) {
                                FullModeDashboard(
                                    session = session,
                                    selectedDate = selectedDate,
                                    onSelectDate = { selectedDate = it },
                                    events = events,
                                    members = members,
                                    shopping = shopping,
                                    palette = palette,
                                    themeMode = themeMode,
                                    addMenuRequest = assistantAddRequest,
                                    onAssistantAdd = { assistantAddRequest++ },
                                    onAdd = {
                                        addEventInitialTitle = ""
                                        showAddEvent = true
                                    },
                                    onAddLaundry = {
                                        addEventInitialTitle = "🧺 Tvätt"
                                        showAddEvent = true
                                    },
                                    onOpenSettings = { selectedTab = 4 },
                                    onRefresh = {
                                        scope.launch { refresh() }
                                    },
                                )
                            }

                        UiLayoutMode.RUNNING ->
                            Box(Modifier.fillMaxSize().background(Color(0xFF0D0F10))) {
                                SportPremiumDashboard(
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
                                    onOpenLocation = { selectedTab = 5 },
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
                    val tabModifier =
                        if (tab == 1) {
                            Modifier.fillMaxSize().shoppingBackdrop()
                        } else {
                            Modifier.fillMaxSize()
                                .padding(18.dp)
                                .verticalScroll(rememberScrollState())
                        }
                    Column(tabModifier) {
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

                            2 -> ToDoScreen(session, members)
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
                                    { selectedTab = 3 },
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
            onSave = { title, date, time, endTime, memberId, isReminder, editScope ->
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
                                source =
                                    when {
                                        isReminder && time.isBlank() -> "reminder_no_time"
                                        isReminder -> "reminder"
                                        time.isBlank() -> "manual_no_time"
                                        else -> "manual"
                                    },
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
                                        reminder,
                                        recurrence ->
            scope.launch {
                if (reminder) {
                    SupabaseSync.addReminder(
                        session,
                        title,
                        dates.first(),
                        startTime,
                        memberId,
                    )
                } else if (birthday) {
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

private fun Modifier.shoppingBackdrop(): Modifier =
    this
        .background(Color(0xFF06111E))
        .drawBehind {
            drawRect(
                brush =
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF071827),
                            Color(0xFF10324F),
                            Color(0xFF184B6A),
                            Color(0xFF0A1825),
                        )
                    )
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0x9952B9FF),
                                Color(0x3352B9FF),
                                Color.Transparent,
                            ),
                        center = Offset(size.width * .22f, size.height * .18f),
                        radius = size.width * .72f,
                    ),
                radius = size.width * .72f,
                center = Offset(size.width * .22f, size.height * .18f),
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0x88FF9D61),
                                Color(0x28FF9D61),
                                Color.Transparent,
                            ),
                        center = Offset(size.width * .88f, size.height * .58f),
                        radius = size.width * .62f,
                    ),
                radius = size.width * .62f,
                center = Offset(size.width * .88f, size.height * .58f),
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0x443D7DFF),
                                Color.Transparent,
                            ),
                        center = Offset(size.width * .10f, size.height * .82f),
                        radius = size.width * .50f,
                    ),
                radius = size.width * .50f,
                center = Offset(size.width * .10f, size.height * .82f),
            )
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
    val shoppingScope = rememberCoroutineScope()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var selectedShoppingTool by remember { mutableStateOf<String?>(null) }
    var priceComparison by remember { mutableStateOf<ShoppingPriceComparison?>(null) }
    var comparingPrices by remember { mutableStateOf(false) }
    var priceComparisonError by remember { mutableStateOf("") }
    var recipeQuery by remember { mutableStateOf("") }
    var selectedRecipe by remember { mutableStateOf<FamilyRecipe?>(null) }
    var selectedCategory by remember { mutableStateOf("Alla") }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var shoppingMenuExpanded by remember { mutableStateOf(false) }
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
    val shoppingBlue = Color(0xFF68B8FF)
    val shoppingCyan = Color(0xFF75E8F2)
    val shoppingOrange = Color(0xFFFFA33A)
    val shoppingGlass = Color(0xB2183453)
    val shoppingGlassStrong = Color(0xD21A3859)
    val verifiedCheapestByItem =
        openItems.mapNotNull { item ->
            mailOffers
                .filter { mailOfferMatchesItem(it, item.name) }
                .minByOrNull { it.price }
                ?.let { item.id to it }
        }.toMap()
    val estimatedTotal = verifiedCheapestByItem.values.sumOf { it.price }
    val categoryEmoji =
        mapOf(
            "Frukt & grönt" to "🍏",
            "Mejeri & ägg" to "🥛",
            "Kött & fisk" to "🥩",
            "Skafferi" to "🥫",
            "Frys" to "❄️",
            "Hygien" to "🧴",
            "Hushåll" to "🧽",
            "Övrigt" to "🛒",
        )
    val availableCategories =
        listOf("Alla") +
            categoryOrder.filter { category -> openItems.any { categoryFor(it.name) == category } }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .padding(bottom = 76.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = shoppingOrange.copy(alpha = .18f),
                    border = BorderStroke(1.dp, shoppingOrange.copy(alpha = .42f)),
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = shoppingOrange,
                        modifier = Modifier.padding(10.dp).size(30.dp),
                    )
                }
                Column {
                    Text(
                        "Inköp",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "${openItems.size} kvar · $done klara",
                        color = Color.White.copy(alpha = .68f),
                        fontSize = 12.sp,
                    )
                }
            }

            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ShoppingHeaderButton(
                        icon = Icons.Default.Search,
                        description = "Sök",
                        active = searchExpanded,
                        accent = shoppingBlue,
                    ) {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) searchQuery = ""
                    }
                    ShoppingHeaderButton(
                        icon = Icons.Default.LocalOffer,
                        description = "Prisjämför",
                        active = selectedShoppingTool == "price",
                        accent = shoppingCyan,
                    ) {
                        selectedShoppingTool = "price"
                        priceComparisonError = ""
                        if (openItems.isEmpty()) {
                            priceComparison = null
                            priceComparisonError = "Lägg till minst en vara för att jämföra priser."
                        } else {
                            shoppingScope.launch {
                                comparingPrices = true
                                runCatching { ShoppingPriceService.compare(openItems.map { it.name }) }
                                    .onSuccess { priceComparison = it }
                                    .onFailure {
                                        priceComparison = null
                                        priceComparisonError =
                                            it.message ?: "Kunde inte hämta prisjämförelsen."
                                    }
                                comparingPrices = false
                            }
                        }
                    }
                    ShoppingHeaderButton(
                        icon = Icons.Default.MoreVert,
                        description = "Mer",
                        accent = shoppingBlue,
                    ) { shoppingMenuExpanded = true }
                }
                DropdownMenu(
                    expanded = shoppingMenuExpanded,
                    onDismissRequest = { shoppingMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Planera handlingen") },
                        onClick = {
                            shoppingMenuExpanded = false
                            selectedShoppingTool = "plan"
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Recept") },
                        onClick = {
                            shoppingMenuExpanded = false
                            selectedShoppingTool = "recipes"
                        },
                    )
                    if (checkedItems.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Rensa bockade") },
                            onClick = {
                                shoppingMenuExpanded = false
                                showClearConfirmation = true
                            },
                        )
                    }
                }
            }
        }

        if (searchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Sök i inköpslistan…") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = shoppingBlue)
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = shoppingBlue.copy(alpha = .72f),
                        unfocusedBorderColor = Color.White.copy(alpha = .12f),
                        focusedContainerColor = shoppingGlass.copy(alpha = .74f),
                        unfocusedContainerColor = shoppingGlass.copy(alpha = .60f),
                    ),
            )
        }

        Surface(
            shape = RoundedCornerShape(25.dp),
            color = Color(0xD61A3B5D),
            border = BorderStroke(1.dp, shoppingBlue.copy(alpha = .26f)),
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 7.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1.15f)) {
                    Text(
                        "Totalt (est.)",
                        color = Color.White.copy(alpha = .62f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (verifiedCheapestByItem.isEmpty()) "—"
                        else "${"%.0f".format(Locale("sv", "SE"), estimatedTotal)} kr",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (verifiedCheapestByItem.isNotEmpty()) {
                        Text(
                            "${verifiedCheapestByItem.size}/${openItems.size} verifierade",
                            color = shoppingCyan.copy(alpha = .84f),
                            fontSize = 9.sp,
                        )
                    }
                }

                VerticalDivider(
                    modifier = Modifier.height(50.dp),
                    color = Color.White.copy(alpha = .10f),
                )

                ShoppingSummaryStat(
                    value = openItems.size,
                    label = "kvar",
                    progress =
                        if (total == 0) 0f
                        else openItems.size.toFloat() / total.toFloat(),
                    accent = shoppingOrange,
                    modifier = Modifier.weight(.80f),
                )

                VerticalDivider(
                    modifier = Modifier.height(50.dp),
                    color = Color.White.copy(alpha = .10f),
                )

                ShoppingSummaryStat(
                    value = done,
                    label = "klara",
                    progress = animatedProgress,
                    accent = shoppingCyan,
                    modifier = Modifier.weight(.80f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            availableCategories.forEach { category ->
                val selected = selectedCategory == category
                Surface(
                    modifier = Modifier.clickable { selectedCategory = category },
                    shape = RoundedCornerShape(99.dp),
                    color =
                        if (selected) shoppingBlue.copy(alpha = .28f)
                        else shoppingGlass.copy(alpha = .72f),
                    border =
                        BorderStroke(
                            1.dp,
                            if (selected) shoppingBlue.copy(alpha = .88f)
                            else Color.White.copy(alpha = .10f),
                        ),
                    shadowElevation = if (selected) 4.dp else 0.dp,
                ) {
                    Text(
                        if (category == "Alla") "▦  Alla"
                        else "${categoryEmoji[category] ?: "•"}  $category",
                        color = if (selected) Color.White else Color.White.copy(alpha = .72f),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        selectedShoppingTool?.let { tool ->
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = .88f)),
            shape = RoundedCornerShape(20.dp),
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = .20f),
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                when (tool) {
                    "price" -> {
                        Text(
                            "Prisjämförelse",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            "Endast verifierade priser från ansluten prisdata visas. Inga uppskattade priser.",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                        when {
                            comparingPrices -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Text("Jämför priser…", color = Muted, fontSize = 12.sp)
                                }
                            }
                            priceComparisonError.isNotBlank() -> {
                                Text(
                                    priceComparisonError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                )
                            }
                            priceComparison != null -> {
                                val result = priceComparison!!
                                result.bestCompleteStore?.let { basket ->
                                    Text(
                                        "Billigaste kompletta butik: ${basket.store} · ${"%.2f".format(Locale("sv", "SE"), basket.total)} kr",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                    )
                                }
                                result.splitBasketTotal?.let { total ->
                                    Text(
                                        "Billigast om köpet delas upp: ${"%.2f".format(Locale("sv", "SE"), total)} kr",
                                        color = Color.White.copy(alpha = .84f),
                                        fontSize = 12.sp,
                                    )
                                }
                                result.splitSavingsAgainstBestCompleteStore?.let { saving ->
                                    Text(
                                        "Möjlig besparing: ${"%.2f".format(Locale("sv", "SE"), saving)} kr",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = .07f))
                                result.items.forEach { pricedItem ->
                                    val cheapest = pricedItem.cheapest
                                    if (cheapest == null) {
                                        Text(
                                            "${pricedItem.query}: inget verifierat pris hittades",
                                            color = Muted,
                                            fontSize = 11.sp,
                                        )
                                    } else {
                                        val detail =
                                            listOfNotNull(
                                                cheapest.brand,
                                                cheapest.packageText,
                                            ).joinToString(" · ")
                                        Text(
                                            "${pricedItem.query}: ${cheapest.store} · ${"%.2f".format(Locale("sv", "SE"), cheapest.price)} kr",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (detail.isNotBlank()) {
                                            Text(detail, color = Muted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    "Tryck på prislappen uppe till höger för att jämföra varorna som inte är avbockade.",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    "recipes" -> {
                        val recipeResults =
                            remember(recipeQuery) {
                                FamilyRecipeCatalog.search(recipeQuery).take(30)
                            }

                        Text(
                            "Familjens recept",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            "Sök bland recepten direkt i Familjekalendern. Allt visas och hanteras inne i appen.",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )

                        selectedRecipe?.let { recipe ->
                            BackHandler { selectedRecipe = null }

                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                                shape = RoundedCornerShape(18.dp),
                                border =
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = .28f),
                                    ),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(15.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(onClick = { selectedRecipe = null }) {
                                            Text("← Alla recept")
                                        }
                                        Text(
                                            "${recipe.timeMinutes} min · ${recipe.servings} port",
                                            color = Muted,
                                            fontSize = 11.sp,
                                        )
                                    }

                                    Text(
                                        recipe.title,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    Text(
                                        recipe.description,
                                        color = Color.White.copy(alpha = .78f),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = .13f),
                                        shape = RoundedCornerShape(99.dp),
                                    ) {
                                        Text(
                                            recipe.category,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = .08f))
                                    Text(
                                        "Ingredienser",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    recipe.ingredients.forEach { ingredient ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                ingredient.amount,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.width(76.dp),
                                            )
                                            Text(
                                                ingredient.name,
                                                color = Color.White.copy(alpha = .90f),
                                                fontSize = 12.sp,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            recipe.ingredients.forEach { ingredient ->
                                                onAdd("${ingredient.amount} ${ingredient.name}")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("Lägg ingredienser i inköpslistan")
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = .08f))
                                    Text(
                                        "Gör så här",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    recipe.steps.forEachIndexed { index, step ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                                                shape = CircleShape,
                                            ) {
                                                Text(
                                                    "${index + 1}",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                )
                                            }
                                            Text(
                                                step,
                                                color = Color.White.copy(alpha = .88f),
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedRecipe == null) {
                            OutlinedTextField(
                                value = recipeQuery,
                                onValueChange = { recipeQuery = it },
                                label = { Text("Sök recept") },
                                placeholder = { Text("t.ex. kyckling, pasta, kladdkaka") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Text(
                                if (recipeQuery.isBlank()) {
                                    "${FamilyRecipeCatalog.all.size} recept i Familjekalendern"
                                } else {
                                    "${recipeResults.size} träffar"
                                },
                                color = Muted,
                                fontSize = 11.sp,
                            )

                            if (recipeResults.isEmpty()) {
                                Text(
                                    "Inga recept matchade sökningen. Prova en rätt, råvara eller kategori.",
                                    color = Muted,
                                    fontSize = 12.sp,
                                )
                            } else {
                                recipeResults.forEach { recipe ->
                                    Surface(
                                        modifier =
                                            Modifier.fillMaxWidth().clickable {
                                                selectedRecipe = recipe
                                            },
                                        color = Color.White.copy(alpha = .045f),
                                        shape = RoundedCornerShape(14.dp),
                                        border =
                                            BorderStroke(
                                                1.dp,
                                                Color.White.copy(alpha = .07f),
                                            ),
                                    ) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(13.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    recipe.title,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    "${recipe.timeMinutes} min",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.sp,
                                                )
                                            }
                                            Text(
                                                recipe.description,
                                                color = Muted,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                            )
                                            Text(
                                                "${recipe.category} · ${recipe.servings} portioner",
                                                color = Color.White.copy(alpha = .55f),
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "plan" -> {
                        Text(
                            "Planera handlingen",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (openItems.isEmpty()) {
                            Text(
                                "Inga varor kvar att planera.",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        } else {
                            Text(
                                "${openItems.size} varor kvar · grupperade i en smidigare butiksordning.",
                                color = Muted,
                                fontSize = 11.sp,
                            )
                            categoryOrder.forEach { category ->
                                val categoryItems = grouped[category].orEmpty()
                                if (categoryItems.isNotEmpty()) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            category,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "${categoryItems.size}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { selectedShoppingTool = null },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Stäng")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    AnimatedContent(
        targetState = items,
        transitionSpec = {
            (fadeIn(tween(motionDuration(LuxuryMotion.Standard, motionEnabled))) +
                    slideInVertically(tween(motionDuration(210, motionEnabled))) { it / 16 }) togetherWith
                    (fadeOut(tween(motionDuration(LuxuryMotion.Fast, motionEnabled))) +
                            slideOutVertically(tween(motionDuration(170, motionEnabled))) { -it / 18 })
        },
        label = "shopping-items",
    ) { visibleItems ->
        val visibleOpenAll =
            visibleItems
                .filterNot { it.checked }
                .filter {
                    searchQuery.isBlank() ||
                        it.name.contains(searchQuery.trim(), ignoreCase = true)
                }
        val visibleCheckedAll =
            visibleItems
                .filter { it.checked }
                .filter {
                    searchQuery.isBlank() ||
                        it.name.contains(searchQuery.trim(), ignoreCase = true)
                }
        val visibleOpen =
            if (selectedCategory == "Alla") visibleOpenAll
            else visibleOpenAll.filter { categoryFor(it.name) == selectedCategory }
        val visibleChecked =
            if (selectedCategory == "Alla") visibleCheckedAll
            else visibleCheckedAll.filter { categoryFor(it.name) == selectedCategory }
        val visibleGrouped = visibleOpen.groupBy { categoryFor(it.name) }

        Column(
            Modifier.fillMaxWidth().animateContentSize(tween(motionDuration(220, motionEnabled))),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (visibleItems.isEmpty()) {
                Surface(
                    color = shoppingGlassStrong,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .10f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(25.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = shoppingBlue,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Dags att fylla listan",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        Text("Lägg till första varan nedan", color = Muted, fontSize = 11.sp)
                    }
                }
            } else if (visibleOpen.isEmpty() && visibleChecked.isEmpty()) {
                Surface(
                    color = shoppingGlass.copy(alpha = .74f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (searchQuery.isBlank()) "Inga varor i den här kategorin."
                        else "Ingen vara matchar “$searchQuery”.",
                        color = Color.White.copy(alpha = .65f),
                        modifier = Modifier.padding(17.dp),
                        fontSize = 11.sp,
                    )
                }
            }

            categoryOrder.forEach { category ->
                val categoryItems = visibleGrouped[category].orEmpty()
                if (categoryItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = shoppingGlassStrong,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .11f)),
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 5.dp,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(categoryEmoji[category] ?: "🛒", fontSize = 19.sp)
                                    Text(
                                        category,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = shoppingBlue.copy(alpha = .14f),
                                    border = BorderStroke(1.dp, shoppingBlue.copy(alpha = .20f)),
                                ) {
                                    Text(
                                        "${categoryItems.size} kvar",
                                        color = shoppingBlue,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = .065f))

                            categoryItems.forEachIndexed { index, item ->
                                val offersForItem =
                                    mailOffers
                                        .filter { mailOfferMatchesItem(it, item.name) }
                                        .sortedBy { it.price }
                                        .take(3)
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { onToggle(item) }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Checkbox(
                                        checked = false,
                                        onCheckedChange = { onToggle(item) },
                                        modifier = Modifier.size(34.dp),
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        color = Color.White.copy(alpha = .075f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
                                        modifier = Modifier.size(43.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                shoppingProductEmoji(item.name),
                                                fontSize = 24.sp,
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        Text(
                                            item.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                        )
                                        Text(
                                            shoppingItemMeta(item.name),
                                            color = Color.White.copy(alpha = .48f),
                                            fontSize = 9.sp,
                                        )
                                    }

                                    if (offersForItem.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            offersForItem.forEach { offer ->
                                                val formattedPrice =
                                                    "%.2f"
                                                        .format(Locale.US, offer.price)
                                                        .replace('.', ',')
                                                val storeColor = shoppingStoreColor(offer.store)
                                                Surface(
                                                    shape = RoundedCornerShape(7.dp),
                                                    color = storeColor.copy(alpha = .22f),
                                                    border =
                                                        BorderStroke(
                                                            1.dp,
                                                            storeColor.copy(alpha = .42f),
                                                        ),
                                                    modifier = Modifier.widthIn(min = 40.dp, max = 49.dp),
                                                ) {
                                                    Column(
                                                        modifier =
                                                            Modifier.padding(
                                                                horizontal = 4.dp,
                                                                vertical = 3.dp,
                                                            ),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                    ) {
                                                        Text(
                                                            shoppingStoreShortName(offer.store),
                                                            color = Color.White.copy(alpha = .72f),
                                                            fontSize = 6.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                        )
                                                        Text(
                                                            formattedPrice,
                                                            color = Color.White,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            "—",
                                            color = Color.White.copy(alpha = .28f),
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(horizontal = 5.dp),
                                        )
                                    }
                                }
                                if (index != categoryItems.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = .045f),
                                        modifier = Modifier.padding(start = 68.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (visibleChecked.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(21.dp),
                    color = shoppingGlass.copy(alpha = .58f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = shoppingCyan,
                                    modifier = Modifier.size(17.dp),
                                )
                                Text(
                                    "Klara (${visibleChecked.size})",
                                    color = Color.White.copy(alpha = .70f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                            TextButton(onClick = { showClearConfirmation = true }) {
                                Text("Rensa", color = shoppingBlue)
                            }
                        }
                        visibleChecked.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = .04f),
                                    modifier = Modifier.padding(start = 58.dp),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onToggle(item) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { onToggle(item) },
                                )
                                Text(
                                    shoppingProductEmoji(item.name),
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(end = 7.dp),
                                )
                                Text(
                                    item.name,
                                    color = Color.White.copy(alpha = .44f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

        }
    }

        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xE61A3859),
            border = BorderStroke(1.dp, shoppingBlue.copy(alpha = .36f)),
            shadowElevation = 12.dp,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
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
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = shoppingBlue,
                            contentColor = Color(0xFF061525),
                        ),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Lägg till")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Lägg till vara …") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = .045f),
                            unfocusedContainerColor = Color.White.copy(alpha = .035f),
                        ),
                )
                ShoppingHeaderButton(
                    icon = Icons.Default.LocalOffer,
                    description = "Prisjämför",
                    active = selectedShoppingTool == "price",
                    accent = shoppingCyan,
                ) {
                    selectedShoppingTool = "price"
                    priceComparisonError = ""
                    if (openItems.isNotEmpty()) {
                        shoppingScope.launch {
                            comparingPrices = true
                            runCatching { ShoppingPriceService.compare(openItems.map { it.name }) }
                                .onSuccess { priceComparison = it }
                                .onFailure {
                                    priceComparison = null
                                    priceComparisonError =
                                        it.message ?: "Kunde inte hämta prisjämförelsen."
                                }
                            comparingPrices = false
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
private fun ShoppingHeaderButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color =
            if (active) accent.copy(alpha = .22f)
            else Color(0xAA173652),
        border =
            BorderStroke(
                1.dp,
                if (active) accent.copy(alpha = .62f)
                else Color.White.copy(alpha = .10f),
            ),
        modifier = Modifier.size(39.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (active) accent else Color.White.copy(alpha = .82f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun ShoppingSummaryStat(
    value: Int,
    label: String,
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(39.dp)) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp,
                color = accent,
                trackColor = Color.White.copy(alpha = .09f),
            )
            Text(
                "$value",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            label,
            color = Color.White.copy(alpha = .58f),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun shoppingProductEmoji(name: String): String {
    val value = name.lowercase(Locale("sv", "SE"))
    return when {
        "banan" in value -> "🍌"
        "tomat" in value -> "🍅"
        "gurk" in value -> "🥒"
        "äpp" in value -> "🍎"
        "päron" in value -> "🍐"
        "apels" in value -> "🍊"
        "citron" in value -> "🍋"
        "avokado" in value -> "🥑"
        "morot" in value -> "🥕"
        "potatis" in value -> "🥔"
        "paprika" in value -> "🫑"
        "mjölk" in value -> "🥛"
        "ost" in value -> "🧀"
        "ägg" in value -> "🥚"
        "yoghurt" in value || "fil" in value -> "🥣"
        "kyckling" in value -> "🍗"
        "kött" in value || "färs" in value -> "🥩"
        "korv" in value -> "🌭"
        "lax" in value || "fisk" in value -> "🐟"
        "bröd" in value -> "🍞"
        "pasta" in value -> "🍝"
        "ris" in value -> "🍚"
        "kaffe" in value -> "☕"
        "glass" in value -> "🍨"
        "tvätt" in value -> "🧺"
        "disk" in value -> "🧽"
        "tights" in value || "byxa" in value || "tröja" in value || "kläder" in value -> "👕"
        "underlag" in value || "brun" in value -> "🟫"
        "resorb" in value || "tablett" in value || "vitamin" in value -> "💊"
        "toalett" in value -> "🧻"
        "schampo" in value || "tvål" in value -> "🧴"
        else -> "🛍️"
    }
}

private fun shoppingItemMeta(name: String): String {
    val trimmed = name.trim()
    val quantity =
        Regex("""^\d+(?:[.,]\d+)?\s*(?:kg|g|l|dl|cl|ml|st|förp|paket|pkt)?\b""", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.value
            ?.trim()
    return quantity?.takeIf { it.isNotBlank() } ?: "1 st"
}

private fun shoppingStoreShortName(store: String): String {
    val clean = store.trim()
    return when {
        clean.equals("City Gross", ignoreCase = true) -> "City"
        clean.length <= 6 -> clean
        else -> clean.take(5)
    }
}

private fun shoppingStoreColor(store: String): Color {
    val value = store.lowercase(Locale("sv", "SE"))
    return when {
        "willys" in value -> Color(0xFF75D978)
        "ica" in value -> Color(0xFFFF6B6B)
        "coop" in value -> Color(0xFF75D8D1)
        "city gross" in value -> Color(0xFFFFB55F)
        "hemköp" in value -> Color(0xFFFF7F92)
        "lidl" in value -> Color(0xFFFFD86B)
        else -> Color(0xFF73B8FF)
    }
}

@Composable
private fun PremiumShoppingAction(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(52.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color =
            if (selected) Color(0xFF68B8FF).copy(alpha = .20f)
            else Color(0xB2183453),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) Color(0xFF68B8FF).copy(alpha = .72f)
                else Color.White.copy(alpha = .10f),
            ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(icon, color = Color(0xFF75E8F2), fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (selected) Color.White else Color.White.copy(alpha = .62f),
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
    onOpenFamily: () -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
    onImport: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customBackgroundActive by remember { mutableStateOf(hasCustomBackground(context)) }
    var customBackgroundMessage by remember { mutableStateOf("") }
    val customBackgroundPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            saveCustomBackground(context, uri)
                        }
                    }
                        .onSuccess {
                            customBackgroundActive = true
                            customBackgroundMessage = "Egen bakgrund sparad."
                        }
                        .onFailure {
                            customBackgroundMessage =
                                it.message ?: "Kunde inte spara bakgrundsbilden."
                        }
                }
            }
        }
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
            onClick = onOpenFamily,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.People, null)
            Spacer(Modifier.width(8.dp))
            Text("Hantera familjen")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
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
        val currentDeviceMemberId =
            context.getSharedPreferences("family_calendar", 0).getString("device_member_id", null)
        val isHugoDevice =
            members.any {
                it.id == currentDeviceMemberId && it.name.equals("Hugo", ignoreCase = true)
            }

        UiLayoutMode.values()
            .filter {
                it != UiLayoutMode.PERSONAL &&
                    (it != UiLayoutMode.HUGO_CHILD || isHugoDevice)
            }
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

        Spacer(Modifier.height(16.dp))
        Text(
            "Egen bakgrund",
            color = LuxuryTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(7.dp))

        Surface(
            color = LuxurySurfaceElevated,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
            modifier = Modifier.fillMaxWidth().height(170.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (customBackgroundActive) {
                    CustomBackgroundImage(Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = .42f),
                                    )
                                )
                            )
                    )
                    Text(
                        "Egen bakgrund aktiv",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(18.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Ingen egen bakgrund vald",
                            color = LuxuryText,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Säsongsbakgrunden används tills du väljer en bild.",
                            color = LuxuryTextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Button(
            onClick = { customBackgroundPicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(if (customBackgroundActive) "Byt bakgrundsbild" else "Välj bild från mobilen")
        }

        if (customBackgroundActive) {
            Spacer(Modifier.height(7.dp))
            OutlinedButton(
                onClick = {
                    removeCustomBackground(context)
                    customBackgroundActive = false
                    customBackgroundMessage = "Egen bakgrund borttagen."
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Ta bort egen bakgrund")
            }
        }

        if (customBackgroundMessage.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                customBackgroundMessage,
                color = LuxuryTextMuted,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Den egna bilden används före säsongsbakgrunden i Clean och fullständigt/personligt läge. Bilden sparas lokalt på telefonen.",
            color = LuxuryTextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
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
private fun ShoppingBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items =
        listOf(
            Triple(0, Icons.Default.CalendarMonth, "Kalender"),
            Triple(1, Icons.Default.ShoppingCart, "Inköp"),
            Triple(2, Icons.Default.CheckCircle, "Att göra"),
            Triple(4, Icons.Default.Settings, "Inställningar"),
            Triple(5, Icons.Default.LocationOn, "Plats"),
        )

    Box(
        Modifier.fillMaxWidth()
            .background(Color(0xFF06101A))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Surface(
            color = Color(0xE6172E46),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFF68B8FF).copy(alpha = .22f)),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { (tab, icon, label) ->
                    val isSelected = selected == tab
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF2B9EFF),
                                                Color(0xFF0969B8),
                                            )
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint =
                                    if (isSelected) Color.White
                                    else Color.White.copy(alpha = .58f),
                                modifier = Modifier.size(if (isSelected) 25.dp else 23.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                label,
                                color =
                                    if (isSelected) Color.White
                                    else Color.White.copy(alpha = .52f),
                                fontSize = 9.sp,
                                fontWeight =
                                    if (isSelected) FontWeight.Bold
                                    else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items =
        listOf(
            Triple(0, Icons.Default.CalendarMonth, "Kalender"),
            Triple(1, Icons.Default.ShoppingCart, "Inköp"),
            Triple(2, Icons.Default.CheckCircle, "Att göra"),
            Triple(4, Icons.Default.Settings, "Inställningar"),
            Triple(5, Icons.Default.LocationOn, "Plats"),
        )

    Box(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Surface(
            color = Color(0xEE1B1727),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .13f)),
            shadowElevation = 12.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { (tab, icon, label) ->
                    val isSelected = selected == tab
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF9D5CFF),
                                                Color(0xFF6E34C9),
                                            )
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint =
                                    if (isSelected) Color.White
                                    else LuxuryTextMuted.copy(alpha = .72f),
                                modifier =
                                    Modifier.size(if (isSelected) 26.dp else 24.dp)
                                        .graphicsLayer {
                                            scaleX = if (isSelected) 1.06f else 1f
                                            scaleY = if (isSelected) 1.06f else 1f
                                        },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                label,
                                color =
                                    if (isSelected) Color.White
                                    else LuxuryTextMuted.copy(alpha = .78f),
                                fontSize = 9.sp,
                                fontWeight =
                                    if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    NavigationBar(containerColor = PremiumGlassRaised.copy(alpha = .96f), tonalElevation = 0.dp) {
        listOf(
            Triple(0, Icons.Default.CalendarMonth, "Kalender"),
            Triple(1, Icons.Default.ShoppingCart, "Inköp"),
            Triple(2, Icons.Default.CheckCircle, "To-Do"),
            Triple(4, Icons.Default.Settings, "Inställningar"),
            Triple(5, Icons.Default.LocationOn, "Plats"),
        ).forEach { (tab, icon, label) ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    members: List<SyncMember>,
    selectedDate: LocalDate,
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String?, List<LocalDate>, Boolean, Boolean, RecurrenceMode) -> Unit,
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("sv", "SE")) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var hasTime by remember { mutableStateOf(true) }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var endTime by remember { mutableStateOf<LocalTime?>(null) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var memberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
    var isBirthday by remember { mutableStateOf(false) }
    var isReminder by remember { mutableStateOf(false) }
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
            Text(when { isBirthday -> "Ny födelsedag"; isReminder -> "Ny påminnelse"; else -> "Ny aktivitet" }, fontWeight = FontWeight.Bold)
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

                Row(
                    Modifier.fillMaxWidth().clickable {
                        isReminder = !isReminder
                        if (isReminder) {
                            isBirthday = false
                            recurrence = RecurrenceMode.NONE
                            if (dates.size > 1) {
                                val first = dates.first()
                                dates.clear()
                                dates.add(first)
                            }
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        isReminder,
                        { checked ->
                            isReminder = checked
                            if (checked) {
                                isBirthday = false
                                recurrence = RecurrenceMode.NONE
                                if (dates.size > 1) {
                                    val first = dates.first()
                                    dates.clear()
                                    dates.add(first)
                                }
                            }
                        },
                    )
                    Text("Påminnelse – krockar inte med aktiviteter")
                }

                if (!isBirthday && !isReminder) {
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
                    label = { Text(when { isBirthday -> "Namn"; isReminder -> "Påminnelse"; else -> "Aktivitet" }) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isBirthday) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (isReminder) "Påminn mig" else "Tid", fontWeight = FontWeight.Bold)

                    Surface(
                        color = if (!hasTime) MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                            else Color.White.copy(alpha = .03f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!hasTime) MaterialTheme.colorScheme.primary.copy(alpha = .45f)
                            else Color.White.copy(alpha = .08f),
                        ),
                        modifier = Modifier.fillMaxWidth().clickable {
                            hasTime = !hasTime
                            if (!hasTime) {
                                startTime = null
                                endTime = null
                            }
                        },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = !hasTime,
                                onCheckedChange = { checked ->
                                    hasTime = !checked
                                    if (checked) {
                                        startTime = null
                                        endTime = null
                                    }
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("Ingen tid", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Visas på dagen utan klockslag",
                                    color = Muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    if (hasTime) {
                        Spacer(Modifier.height(8.dp))
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
                                enabled = !isReminder,
                            ) {
                                Text(
                                    endTime?.let { "Slut ${it.format(timeFormatter)}" }
                                        ?: "Välj sluttid"
                                )
                            }
                        }
                        if (startTime == null || (!isReminder && endTime == null)) {
                            Text(
                                if (isReminder) "Välj tid för påminnelsen." else "Välj både start- och sluttid.",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        } else if (!isReminder && endTime != null && startTime != null && !endTime!!.isAfter(startTime)) {
                            Text(
                                "Sluttiden räknas som nästa dag.",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
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
                    val selectedStartTime =
                        if (!hasTime && !isBirthday) "" else startTime?.format(timeFormatter) ?: "09:00"
                    val selectedEndTime =
                        if (!hasTime && !isBirthday) "" else endTime?.format(timeFormatter) ?: "10:00"
                    onAdd(
                        title.trim(),
                        selectedStartTime,
                        selectedEndTime,
                        memberId,
                        dates.toList(),
                        isBirthday,
                        isReminder,
                        recurrence,
                    )
                },
                enabled =
                    title.isNotBlank() &&
                            dates.isNotEmpty() &&
                            (isBirthday || !hasTime || (isReminder && startTime != null) || (startTime != null && endTime != null)),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Purple,
                        contentColor = Color.White,
                    ),
            ) {
                Text("Färdig")
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
