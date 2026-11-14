package se.familjekalender.app

import android.Manifest
import android.app.DatePickerDialog
import android.speech.RecognizerIntent
import android.app.Activity
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

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
    private var widgetOpenRequestId by mutableIntStateOf(0)
    private var widgetQuickAddRequestId by mutableIntStateOf(0)
    private var requestedEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetOpenTab = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
        intent.getStringExtra(EXTRA_OPEN_EVENT_ID)?.let { eventId ->
            requestedEventId = "calendar:" + eventId + "#" + widgetOpenRequestId
        }
        if (intent.getBooleanExtra(EXTRA_WIDGET_QUICK_ADD, false)) widgetQuickAddRequestId++
        setContent {
            ResponsiveApp {
                FamilyCalendarApp(widgetOpenTab, requestedEventId, widgetOpenRequestId, widgetQuickAddRequestId)
                val popup by FamilyEventPopup.current.collectAsState()
                popup?.let { event ->
                    val selectedTheme = resolveCleanVisualTheme(
                        getSharedPreferences("family_calendar", MODE_PRIVATE)
                            .getString("clean_visual_theme", CleanVisualTheme.CURRENT.name)
                    )
                    FamilyEventPopupDialog(
                        theme = selectedTheme,
                        event = event,
                        onClose = { FamilyEventPopup.dismiss(event.id) },
                        onShowEvent = {
                            requestedEventId = event.id
                            FamilyEventPopup.dismiss(event.id)
                            widgetOpenTab = 0
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetOpenTab = intent.getIntExtra(EXTRA_OPEN_TAB, -1)
        // Repeated taps on the same widget shortcut must always navigate again.
        // Tab value alone does not change after the first tap.
        if (intent.hasExtra(EXTRA_OPEN_TAB)) widgetOpenRequestId++
        intent.getStringExtra(EXTRA_OPEN_EVENT_ID)?.let { eventId ->
            requestedEventId = "calendar:" + eventId + "#" + widgetOpenRequestId
        }
        if (intent.getBooleanExtra(EXTRA_WIDGET_QUICK_ADD, false)) widgetQuickAddRequestId++
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
        const val EXTRA_WIDGET_QUICK_ADD = "widget_quick_add"
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
fun FamilyCalendarApp(
    initialTab: Int = -1,
    requestedEventId: String? = null,
    widgetOpenRequestId: Int = 0,
    widgetQuickAddRequestId: Int = 0,
) {
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
    var cleanVisualTheme by remember {
        mutableStateOf(
            resolveCleanVisualTheme(prefs.getString("clean_visual_theme", CleanVisualTheme.CURRENT.name))
        )
    }
    val palette = paletteFor(themeMode)
    FamiljekalenderLuxuryTheme(
        palette = palette,
        lightMode =
            uiLayoutMode == UiLayoutMode.MINIMAL &&
                isLightCleanTheme(cleanVisualTheme),
        nordicDarkMode =
            uiLayoutMode == UiLayoutMode.MINIMAL &&
                cleanVisualTheme == CleanVisualTheme.NORDIC_DAY_PLANNER_DARK,
        cleanVisualTheme =
            cleanVisualTheme.takeIf { uiLayoutMode == UiLayoutMode.MINIMAL },
    ) {
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
                        cleanVisualTheme,
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
                        {
                            cleanVisualTheme = it
                            prefs.edit().putString("clean_visual_theme", it.name).apply()
                            if (WidgetThemeColors.savedChoice(context) == WidgetThemeColors.FOLLOW_CALENDAR) {
                                FamilyCalendarWidget.refreshTheme(context)
                            }
                        },
                        initialTab = initialTab,
                        navigationRequestId = widgetOpenRequestId,
                        requestedEventId = requestedEventId,
                        quickAddRequestId = widgetQuickAddRequestId,
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
    cleanVisualTheme: CleanVisualTheme,
    onSportSettingsSaved: (String, String?) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit,
    onUiLayoutModeSaved: (UiLayoutMode) -> Unit,
    onCleanVisualThemeSaved: (CleanVisualTheme) -> Unit,
    initialTab: Int = -1,
    navigationRequestId: Int = 0,
    requestedEventId: String? = null,
    quickAddRequestId: Int = 0,
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
    var events by remember { mutableStateOf(emptyList<SyncEvent>()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTab by remember { mutableIntStateOf(if (initialTab in 0..5) initialTab else 0) }

    // Android system back should navigate inside the app before closing the activity.
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    LaunchedEffect(initialTab, navigationRequestId) {
        if (initialTab in 0..5) selectedTab = initialTab
    }
    var showAddEvent by remember { mutableStateOf(false) }
    var showWidgetQuickAdd by remember { mutableStateOf(false) }
    var addEventInitialKind by remember { mutableStateOf(CalendarEntryKind.ACTIVITY) }
    LaunchedEffect(quickAddRequestId) {
        if (quickAddRequestId > 0) {
            selectedTab = 0
            showWidgetQuickAdd = true
        }
    }
    var editEvent by remember { mutableStateOf<SyncEvent?>(null) }
    var handledEventRequest by remember { mutableStateOf<String?>(null) }

    // Opening a notification should select the exact event, not just the calendar tab.
    // If sync is still loading, retry automatically when the event list arrives.
    LaunchedEffect(requestedEventId, events) {
        val requested = requestedEventId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (requested == handledEventRequest) return@LaunchedEffect

        val target =
            if (requested.startsWith("calendar-series:")) {
                val seriesId = requested.removePrefix("calendar-series:")
                events.filter { it.seriesId == seriesId }
                    .sortedWith(compareBy<SyncEvent> { it.date.isBefore(LocalDate.now()) }.thenBy { it.date })
                    .firstOrNull()
            } else {
                val eventId = requested.removePrefix("calendar:").substringBefore("#")
                events.firstOrNull { it.id == eventId }
            }

        if (target != null) {
            handledEventRequest = requested
            selectedTab = 0
            selectedDate = target.date
            editEvent = target
        }
    }

    var addEventInitialTitle by remember { mutableStateOf("") }
    var assistantAddRequest by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var loadedFromNetwork by remember(session.id) { mutableStateOf(false) }
    val deviceMemberId = remember { appPrefs.getString("device_member_id", null) }

    // Register a phone-specific encrypted inbox after the user picks their identity.
    // This mailbox is never added to shared events or offline family snapshots.
    LaunchedEffect(session.id, deviceMemberId) {
        if (!deviceMemberId.isNullOrBlank()) {
            try {
                PrivateReminderSync.refreshInbox(context, session)
                FamilyCalendarWidget.enqueueRefresh(context)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Offline and older app versions can continue using public calendar data.
            }
        }
    }

    // Show the last saved family data immediately if the phone starts without internet.
    LaunchedEffect(session.id) {
        val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Triple(
                FamilyOfflineCache.loadMembers(context, session),
                FamilyOfflineCache.loadShopping(context, session),
                FamilyOfflineCache.loadEvents(context, session),
            )
        }
        if (!loadedFromNetwork) {
            cached.first?.let { members = it }
            cached.second?.let { shopping = it }
            cached.third?.let { events = it }
        }
    }

    suspend fun refresh() {
        runCatching {
            val latestMembers = SupabaseSync.loadMembers(session)
            val latestShopping = SupabaseSync.loadShopping(session)
            val loadedEvents = SupabaseSync.loadEvents(session)
            val latestEvents = RecurringScheduleSync.filterPausedScheduleEvents(session, loadedEvents)
            members = latestMembers
            shopping = latestShopping
            events = latestEvents
            loadedFromNetwork = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    FamilyOfflineCache.saveMembers(context, session, latestMembers)
                    FamilyOfflineCache.saveShopping(context, session, latestShopping)
                    FamilyOfflineCache.saveEvents(context, session, latestEvents)
                }
            }
        }
            .onSuccess {
                if (message.startsWith("Synkfel:")) message = ""
            }
            .onFailure {
                // Show offline items created since the last successful network refresh.
                val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Triple(
                        FamilyOfflineCache.loadMembers(context, session),
                        FamilyOfflineCache.loadShopping(context, session),
                        FamilyOfflineCache.loadEvents(context, session),
                    )
                }
                saved.first?.let { members = it }
                saved.second?.let { shopping = it }
                saved.third?.let { events = it }
                message = "Synkfel: Visar senast sparade uppgifter. ${it.message}"
            }
    }

    // Event push can arrive between scheduled syncs. Fetch the latest events
    // immediately before attempting to navigate to the referenced event.
    LaunchedEffect(requestedEventId) {
        if (!requestedEventId.isNullOrBlank()) refresh()
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

    // Stop retired mail jobs and remove local mail login data on upgraded devices.
    LaunchedEffect(Unit) {
        val work = androidx.work.WorkManager.getInstance(context)
        work.cancelUniqueWork("familjekalender_mail_sync")
        work.cancelUniqueWork("familjekalender_mail_sync_now")
        context.getSharedPreferences("familjekalender_mail_accounts", Context.MODE_PRIVATE)
            .edit().clear().apply()
        runCatching {
            val keys = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keys.containsAlias("familjekalender_mail_accounts_v1")) {
                keys.deleteEntry("familjekalender_mail_accounts_v1")
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session.id, sportUrl, sportMemberId, lifecycle) {
        suspend fun syncExternalCalendars() {
            if (sportUrl.isNotBlank()) {
                runCatching { SupabaseSync.importSportAdmin(session, sportUrl, sportMemberId) }
            }
        }

        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Refresh the home widget whenever the app returns to foreground,
            // regardless of which tab the user has selected.
            FamilyCalendarWidget.enqueueRefresh(context)
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
                    if (latest != shopping) {
                        shopping = latest
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { FamilyOfflineCache.saveShopping(context, session, latest) }
                        }
                    }
                    FamilyCalendarWidget.publishShoppingSnapshot(context, session, latest)
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

    val undoSnackbar = remember { SnackbarHostState() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(undoSnackbar) },
        bottomBar = {
            when (uiLayoutMode) {
                UiLayoutMode.MINIMAL ->
                    MinimalBottomNav(selectedTab, cleanVisualTheme) { selectedTab = it }
                UiLayoutMode.HUGO_CHILD -> HugoBottomNav(selectedTab) { selectedTab = it }
                UiLayoutMode.RUNNING -> SportBottomNav(selectedTab) { selectedTab = it }
                else -> BottomNav(selectedTab) { selectedTab = it }
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
                                shopping = shopping,
                                onAdd = {
                                    addEventInitialTitle = ""
                                    showAddEvent = true
                                },
                                onEdit = { editEvent = it },
                                onOpenShopping = { selectedTab = 1 },
                                onOpenTodo = { selectedTab = 2 },
                                onOpenFamily = { selectedTab = 3 },
                                onOpenSettings = { selectedTab = 4 },
                                onOpenLocation = { selectedTab = 5 },
                                themeMode = themeMode,
                                cleanVisualTheme = cleanVisualTheme,
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
                                        addEventInitialTitle = "Tvätt"
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
                                    onEdit = { editEvent = it },
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
                        when (tab) {
                            1 -> Modifier.fillMaxSize()
                            4 -> Modifier.fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF191323), Color(0xFF100D19))
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 18.dp)
                                .verticalScroll(rememberScrollState())
                            else -> Modifier.fillMaxSize()
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
                                    themeMode = themeMode,
                                    lightMode = uiLayoutMode == UiLayoutMode.MINIMAL,
                                    nordicDarkMode =
                                        uiLayoutMode == UiLayoutMode.MINIMAL &&
                                            cleanVisualTheme == CleanVisualTheme.NORDIC_DAY_PLANNER_DARK,
                                    onBack = { selectedTab = 0 },
                                    onAdd = { name ->
                                        try {
                                            val existingDone = shopping.firstOrNull {
                                                it.checked &&
                                                    SmartShoppingCatalog.normalized(it.name) ==
                                                    SmartShoppingCatalog.normalized(name)
                                            }
                                            if (existingDone != null) {
                                                SupabaseSync.toggleShopping(session, existingDone)
                                            } else {
                                                SupabaseSync.addShopping(
                                                    session,
                                                    name,
                                                    appPrefs.getString("push_device_id", null),
                                                )
                                            }
                                            refresh()
                                            if (OfflineMutationQueue.pendingCount(session) > 0) {
                                                message = "Inköpet är sparat på telefonen och synkas när nätet fungerar."
                                            }
                                            true
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            message = "Kunde inte lägga till varan: ${failure.message}"
                                            false
                                        }
                                    },
                                    onToggle = { item ->
                                        try {
                                            SupabaseSync.toggleShopping(session, item)
                                            refresh()
                                            true
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            message = "Kunde inte ändra varan: ${failure.message}"
                                            false
                                        }
                                    },
                                    onClear = { selected ->
                                        try {
                                            SupabaseSync.clearChecked(session, selected)
                                            refresh()
                                            true
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            message = "Kunde inte ta bort klara varor: ${failure.message}"
                                            refresh()
                                            false
                                        }
                                    },
                                    onDelete = { item ->
                                        try {
                                            SupabaseSync.deleteShopping(session, item)
                                            refresh()
                                            true
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            message = "Kunde inte ta bort varan: ${failure.message}"
                                            false
                                        }
                                    },
                                    onRename = { item, newName ->
                                        try {
                                            SupabaseSync.renameShopping(session, item, newName)
                                            refresh()
                                            true
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            message = "Kunde inte ändra namnet: ${failure.message}"
                                            false
                                        }
                                    },
                                    onRestore = { deleted ->
                                        try {
                                            SupabaseSync.restoreShopping(session, deleted)
                                            refresh()
                                            true
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            message = "Kunde inte ångra borttagningen: ${failure.message}"
                                            false
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
                                SettingsReadabilityTheme {
                                SettingsScreen(
                                    session,
                                    members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                                    sportUrl,
                                    sportMemberId,
                                    themeMode,
                                    uiLayoutMode,
                                    cleanVisualTheme,
                                    personalProfile,
                                    personalLayoutRevision,
                                    onThemeModeSaved,
                                    onUiLayoutModeSaved,
                                    onCleanVisualThemeSaved,
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
            onSave = { title, date, time, endTime, memberId, entryKind, editScope ->
                val dayShift = java.time.temporal.ChronoUnit.DAYS.between(event.date, date)
                val targets = when (editScope) {
                    SeriesEditScope.THIS -> listOf(event)
                    SeriesEditScope.THIS_AND_FUTURE ->
                        matchingSeries.filter { !it.date.isBefore(event.date) }
                    SeriesEditScope.WHOLE_SERIES -> matchingSeries
                }.ifEmpty { listOf(event) }
                val splitSeriesId = when {
                    event.seriesId == null -> null
                    editScope == SeriesEditScope.THIS_AND_FUTURE ->
                        java.util.UUID.randomUUID().toString()
                    else -> event.seriesId
                }
                targets.forEach { target ->
                    val targetDate = if (editScope == SeriesEditScope.THIS) date
                        else target.date.plusDays(dayShift)
                    SupabaseSync.updateEvent(
                        session, target.id, title, targetDate, time, endTime, memberId,
                        source = calendarEventSource(entryKind, time.isNotBlank()),
                    )
                    when {
                        event.seriesId == null -> Unit
                        editScope == SeriesEditScope.THIS ->
                            SupabaseSync.updateEventSeriesId(session, target.id, null)
                        editScope == SeriesEditScope.THIS_AND_FUTURE ->
                            SupabaseSync.updateEventSeriesId(session, target.id, splitSeriesId)
                        editScope == SeriesEditScope.WHOLE_SERIES -> Unit
                    }
                }
                editEvent = null
                selectedDate = date
                refresh()
                FamilyCalendarWidget.enqueueRefresh(context)
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
                            scope.launch {
                                val response = undoSnackbar.showSnackbar(
                                    message = if (effectiveTargets.size == 1) {
                                        "Aktiviteten är borttagen."
                                    } else {
                                        "${effectiveTargets.size} aktiviteter är borttagna."
                                    },
                                    actionLabel = "Ångra",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long,
                                )
                                if (response == SnackbarResult.ActionPerformed) {
                                    runCatching {
                                        SupabaseSync.restoreRecentlyDeletedEvents(session, effectiveTargets)
                                    }.onSuccess {
                                        refresh()
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                        message = "Aktiviteterna har återställts."
                                    }.onFailure { error ->
                                        message = "Kunde inte ångra borttagningen: ${error.message}"
                                    }
                                }
                            }
                        }
                        .onFailure {
                            message = "Kunde inte ta bort aktiviteten: ${it.message}"
                        }
                }
            },
        )
    }

    if (showWidgetQuickAdd) {
        AlertDialog(
            onDismissRequest = { showWidgetQuickAdd = false },
            title = { Text("Lägg till") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("Aktivitet", "Påminnelse", "To-do", "Inköp").forEachIndexed { index, label ->
                        TextButton(
                            onClick = {
                                showWidgetQuickAdd = false
                                when (index) {
                                    0, 1 -> {
                                        selectedTab = 0
                                        selectedDate = LocalDate.now()
                                        addEventInitialTitle = ""
                                        addEventInitialKind = if (index == 1)
                                            CalendarEntryKind.REMINDER else CalendarEntryKind.ACTIVITY
                                        showAddEvent = true
                                    }
                                    2 -> selectedTab = 2
                                    else -> selectedTab = 1
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWidgetQuickAdd = false }) { Text("Stäng") }
            },
        )
    }

    if (showAddEvent) {
        AddEventDialog(
            members,
            selectedDate,
            addEventInitialTitle,
            initialKind = addEventInitialKind,
            lightStyle = uiLayoutMode == UiLayoutMode.MINIMAL &&
                cleanVisualTheme == CleanVisualTheme.NORDIC_DAY_PLANNER,
            nordicDarkStyle = uiLayoutMode == UiLayoutMode.MINIMAL &&
                cleanVisualTheme == CleanVisualTheme.NORDIC_DAY_PLANNER_DARK,
            onDismiss = {
                showAddEvent = false
                addEventInitialKind = CalendarEntryKind.ACTIVITY
            }) { title,
                                        startTime,
                                        endTime,
                                        memberId,
                                        dates,
                                        birthday,
                                        entryKind,
                                        recurrence,
                                        keepUntilRemoved,
                                        privateOnly ->
            if (entryKind == CalendarEntryKind.REMINDER && privateOnly) {
                require(!memberId.isNullOrBlank() && memberId != ALL_FAMILY_MEMBER_ID) {
                    "Välj en familjemedlem som privat mottagare"
                }
                PrivateReminderSync.send(
                    context, session, memberId, title,
                    dates.minOrNull() ?: LocalDate.now(),
                    startTime, keepUntilRemoved,
                )
            } else if (entryKind == CalendarEntryKind.REMINDER) {
                val targetDates = when {
                    keepUntilRemoved -> listOf(dates.minOrNull()!!)
                    recurrence == RecurrenceMode.NONE -> dates.sorted()
                    else -> dates.sorted().flatMap { recurringDates(it, recurrence) }
                        .distinct().sorted()
                }
                val seriesId = if (keepUntilRemoved || recurrence == RecurrenceMode.NONE)
                    null else java.util.UUID.randomUUID().toString()
                targetDates.forEach { date ->
                    SupabaseSync.addReminder(
                        session, title, date, startTime, memberId, seriesId,
                        keepUntilRemoved = keepUntilRemoved,
                    )
                }
            } else if (birthday) {
                val today = LocalDate.now()
                val month = dates.first().monthValue
                val day = dates.first().dayOfMonth
                for (year in today.year..(today.year + 20)) {
                    val birthdayDate = runCatching { LocalDate.of(year, month, day) }
                        .getOrNull() ?: continue
                    SupabaseSync.addEvent(
                        session, "🌈 $title", birthdayDate, "09:00", null, memberId,
                    )
                }
            } else {
                val targetDates = if (recurrence == RecurrenceMode.NONE) dates.sorted()
                    else dates.sorted().flatMap { recurringDates(it, recurrence) }
                        .distinct().sorted()
                val seriesId = if (recurrence == RecurrenceMode.NONE) null
                    else java.util.UUID.randomUUID().toString()
                targetDates.forEach { date ->
                    SupabaseSync.addEvent(
                        session, title, date, startTime, endTime, memberId, seriesId,
                        source = if (entryKind == CalendarEntryKind.LAUNDRY) "laundry" else "manual",
                    )
                }
            }
            refresh()
            FamilyCalendarWidget.enqueueRefresh(context)
            showAddEvent = false
            addEventInitialKind = CalendarEntryKind.ACTIVITY
            val pending = OfflineMutationQueue.pendingCount(session)
            if (pending > 0) {
                message = "$pending poster sparades lokalt och väntar på internet."
            }
        }
    }
}

private fun Modifier.shoppingBackdrop(): Modifier =
    this
        .background(Color(0x4406111E))
        .drawBehind {
            drawRect(
                brush =
                    Brush.verticalGradient(
                        listOf(
                            Color(0x99071827),
                            Color(0x7710324F),
                            Color(0x44184B6A),
                            Color(0xAA0A1825),
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
    themeMode: ThemeMode,
    lightMode: Boolean = false,
    nordicDarkMode: Boolean = false,
    onBack: () -> Unit,
    onAdd: suspend (String) -> Boolean,
    onToggle: suspend (SyncShoppingItem) -> Boolean,
    onClear: suspend (List<SyncShoppingItem>) -> Boolean,
    onDelete: suspend (SyncShoppingItem) -> Boolean,
    onRename: suspend (SyncShoppingItem, String) -> Boolean,
    onRestore: suspend (List<SyncShoppingItem>) -> Boolean,
) {
    val themedSurfaceMode = lightMode || nordicDarkMode
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Alla") }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(true) }
    var itemMenuId by remember { mutableStateOf<String?>(null) }
    var editingItem by remember { mutableStateOf<SyncShoppingItem?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deletingItem by remember { mutableStateOf<SyncShoppingItem?>(null) }
    var busyIds by remember { mutableStateOf(setOf<String>()) }
    var bulkBusy by remember { mutableStateOf(false) }
    val shoppingScope = rememberCoroutineScope()
    val shoppingSnackbar = remember { SnackbarHostState() }
    val currentItems by rememberUpdatedState(items)

    fun toggleWithUndo(item: SyncShoppingItem) {
        if (item.id in busyIds) return
        busyIds = busyIds + item.id
        shoppingScope.launch {
            try {
                if (onToggle(item)) {
                    busyIds = busyIds - item.id
                    val result = shoppingSnackbar.showSnackbar(
                        message = if (item.checked) item.name + " tillbaka i listan"
                            else item.name + " markerad som klar",
                        actionLabel = "Ångra",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed &&
                        currentItems.firstOrNull { it.id == item.id }?.checked == !item.checked
                    ) {
                        onToggle(item.copy(checked = !item.checked))
                    }
                }
            } finally {
                busyIds = busyIds - item.id
            }
        }
    }

    val voiceLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { text = it }
            }
        }

    // Categories are derived from names, so all family members see the same grouping.
    fun categoryFor(name: String): String = SmartShoppingCatalog.categoryFor(name)
    val categoryOrder = SmartShoppingCatalog.categoryOrder
    val categoryEmoji = SmartShoppingCatalog.categoryEmoji
    var sortMode by remember { mutableStateOf("Butiksordning") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var previewPhotoName by remember { mutableStateOf<String?>(null) }

    val openItems = items.filterNot { it.checked }
    val checkedItems = items.filter { it.checked }
    val total = items.size
    val done = checkedItems.size

    val availableCategories =
        listOf("Alla") +
            categoryOrder.filter { category -> openItems.any { categoryFor(it.name) == category } }

    val filteredOpen =
        openItems.filter { item ->
            (selectedCategory == "Alla" || categoryFor(item.name) == selectedCategory) &&
                (searchQuery.isBlank() || item.name.contains(searchQuery.trim(), ignoreCase = true))
        }
    val groupsInOrder =
        if (sortMode == "A–Ö") categoryOrder.sorted()
        else categoryOrder
    val visibleGroups =
        groupsInOrder.mapNotNull { category ->
            val inCategory = filteredOpen.filter { categoryFor(it.name) == category }
            val sorted = when (sortMode) {
                "Senaste" -> inCategory.reversed()
                else -> inCategory.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                )
            }
            sorted.takeIf { it.isNotEmpty() }?.let { category to it }
        }

    val suggestions = SmartShoppingCatalog.suggestions(
        text,
        items.map { it.name },
    )
    val typedName = text.trim().substringBefore(',').substringBefore(';').trim()
    val duplicateName = typedName.isNotBlank() &&
        openItems.any { SmartShoppingCatalog.normalized(it.name) ==
            SmartShoppingCatalog.normalized(typedName) }

    val blue =
        if (themedSurfaceMode) MaterialTheme.colorScheme.primary
        else SettingsMatchedPalette.accent
    val cyan =
        if (themedSurfaceMode) MaterialTheme.colorScheme.secondary
        else SettingsMatchedPalette.expandedBorder
    val orange =
        if (themedSurfaceMode) MaterialTheme.colorScheme.tertiary
        else SettingsMatchedPalette.accent
    val pageText = if (themedSurfaceMode) MaterialTheme.colorScheme.onBackground else Color.White
    val pageMuted =
        if (themedSurfaceMode) MaterialTheme.colorScheme.onSurfaceVariant
        else Color.White.copy(alpha = .68f)
    val glass =
        if (themedSurfaceMode) MaterialTheme.colorScheme.surface
        else SettingsMatchedPalette.surface
    val softSurface =
        if (themedSurfaceMode) MaterialTheme.colorScheme.surfaceVariant
        else SettingsMatchedPalette.surface
    val border =
        if (themedSurfaceMode) MaterialTheme.colorScheme.outlineVariant
        else SettingsMatchedPalette.border

    ShoppingPageBackground(lightMode = themedSurfaceMode, themeMode = themeMode) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 10.dp,
                        bottom = 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShoppingHeaderButton(
                            icon = Icons.Default.ArrowBack,
                            description = "Tillbaka",
                            accent = blue,
                            lightMode = themedSurfaceMode,
                            onClick = onBack,
                        )
                        Spacer(Modifier.width(8.dp))
                        SettingsMatchedIconTile(Icons.Default.ShoppingCart)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Inköp",
                                color = pageText,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 32.sp,
                            )
                            Text(
                                "${openItems.size} kvar · $done klara",
                                color = if (themedSurfaceMode) pageMuted else Color.White.copy(alpha = .74f),
                                fontSize = 12.sp,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShoppingHeaderButton(
                                icon = Icons.Default.Search,
                                description = "Sök",
                                active = searchExpanded,
                                accent = blue,
                                lightMode = themedSurfaceMode,
                            ) {
                                searchExpanded = !searchExpanded
                                if (!searchExpanded) searchQuery = ""
                            }
                            Box {
                                ShoppingHeaderButton(
                                    icon = Icons.Default.MoreVert,
                                    description = "Mer",
                                    accent = blue,
                                    lightMode = themedSurfaceMode,
                                ) { menuExpanded = true }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    if (checkedItems.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = softSurface,
                            border = BorderStroke(1.dp, border),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).clickable {
                                            completedExpanded = !completedExpanded
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = cyan,
                                            modifier = Modifier.size(21.dp),
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Column {
                                            Text(
                                                "Klara · " + checkedItems.size,
                                                color = pageText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                if (completedExpanded) "Tryck på bocken för att ångra"
                                                else "Tryck här för att visa",
                                                color = pageMuted,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            if (!bulkBusy) {
                                                bulkBusy = true
                                                shoppingScope.launch {
                                                    try {
                                                        var restored = 0
                                                        checkedItems.forEach {
                                                            if (onToggle(it)) restored++
                                                        }
                                                        shoppingSnackbar.showSnackbar(
                                                            if (restored == checkedItems.size)
                                                                "Alla klara varor återställda"
                                                            else "Återställde " + restored + " av " +
                                                                checkedItems.size + " varor",
                                                        )
                                                    } finally {
                                                        bulkBusy = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !bulkBusy,
                                    ) {
                                        Text("Ångra alla", fontSize = 11.sp, color = blue)
                                    }
                                    TextButton(
                                        onClick = { showClearConfirmation = true },
                                        enabled = !bulkBusy,
                                    ) {
                                        Text("Rensa", fontSize = 11.sp, color = blue)
                                    }
                                }
                                if (completedExpanded) {
                                    HorizontalDivider(color = border)
                                    checkedItems.forEachIndexed { index, checked ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .heightIn(min = 54.dp)
                                                .padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            ReferenceShoppingCheck(
                                                checked = true,
                                                lightMode = themedSurfaceMode,
                                                onClick = { toggleWithUndo(checked) },
                                            )
                                            Spacer(Modifier.width(9.dp))
                                            ShoppingProductPicture(
                                                name = checked.name,
                                                modifier = Modifier.size(38.dp),
                                                loadPhoto = false,
                                            )
                                            Spacer(Modifier.width(9.dp))
                                            Text(
                                                checked.name,
                                                modifier = Modifier.weight(1f),
                                                color = pageMuted,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textDecoration =
                                                    androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                            )
                                            Box {
                                                IconButton(
                                                    onClick = { itemMenuId = checked.id },
                                                    modifier = Modifier.size(32.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Ändra eller ta bort " + checked.name,
                                                        tint = pageMuted,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = itemMenuId == checked.id,
                                                    onDismissRequest = { itemMenuId = null },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Ångra avbockning") },
                                                        onClick = {
                                                            itemMenuId = null
                                                            toggleWithUndo(checked)
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Ändra namn") },
                                                        onClick = {
                                                            itemMenuId = null
                                                            editingItem = checked
                                                            editingName = checked.name
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Ta bort vara") },
                                                        onClick = {
                                                            itemMenuId = null
                                                            deletingItem = checked
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        if (index < checkedItems.lastIndex) {
                                            HorizontalDivider(
                                                Modifier.padding(start = 49.dp),
                                                color = border,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (typedName.isNotBlank()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 82.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = softSurface,
                    border = BorderStroke(1.dp, border),
                    shadowElevation = 10.dp,
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ShoppingProductPicture(
                                name = typedName,
                                modifier = Modifier.size(46.dp),
                                loadPhoto = previewPhotoName == typedName,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    typedName,
                                    color = pageText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (duplicateName) "Finns redan på listan"
                                    else "Kategori: " + categoryFor(typedName),
                                    color = if (duplicateName) orange else pageMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        val moreSuggestions = suggestions
                            .filterNot {
                                SmartShoppingCatalog.normalized(it) ==
                                    SmartShoppingCatalog.normalized(typedName)
                            }
                            .take(3)
                        if (moreSuggestions.isNotEmpty()) {
                            HorizontalDivider(
                                Modifier.padding(vertical = 8.dp),
                                color = border,
                            )
                            moreSuggestions.forEach { suggested ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            text = suggested
                                            previewPhotoName = suggested
                                        }
                                        .padding(horizontal = 5.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    ShoppingProductPicture(
                                        name = suggested,
                                        modifier = Modifier.size(31.dp),
                                        loadPhoto = false,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            suggested,
                                            color = pageText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            categoryFor(suggested),
                                            color = pageMuted,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Välj " + suggested,
                                        tint = blue,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (themedSurfaceMode) MaterialTheme.colorScheme.surface else SettingsMatchedPalette.surface,
                border = BorderStroke(1.dp, if (themedSurfaceMode) border else SettingsMatchedPalette.border),
                shadowElevation = 14.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().settingsMatchedFill(24.dp).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FilledIconButton(
                        onClick = {
                            val namesAlreadyPresent = openItems
                                .map { SmartShoppingCatalog.normalized(it.name) }
                                .toMutableSet()
                            text
                                .split(',', ';', '\n')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .filter { namesAlreadyPresent.add(SmartShoppingCatalog.normalized(it)) }
                                .forEach(onAdd)
                            text = ""
                            previewPhotoName = null
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor =
                                    if (themedSurfaceMode) MaterialTheme.colorScheme.primary
                                    else SettingsMatchedPalette.accent,
                                contentColor =
                                    if (themedSurfaceMode) MaterialTheme.colorScheme.onPrimary
                                    else Color.White,
                                disabledContainerColor =
                                    if (themedSurfaceMode) MaterialTheme.colorScheme.surfaceVariant
                                    else SettingsMatchedPalette.sectionSurface,
                                disabledContentColor = Color.White.copy(alpha = .42f),
                            ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Lägg till", modifier = Modifier.size(27.dp))
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            previewPhotoName = null
                        },
                        placeholder = { Text("Lägg till vara …") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = if (themedSurfaceMode) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = .055f),
                                unfocusedContainerColor = if (themedSurfaceMode) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = .045f),
                            ),
                    )

                    ShoppingHeaderButton(
                        icon = Icons.Default.Mic,
                        description = "Röstinmatning",
                        accent = blue,
                        lightMode = themedSurfaceMode,
                    ) {
                        val speechIntent =
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sv-SE")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Lägg till vara")
                            }
                        runCatching { voiceLauncher.launch(speechIntent) }
                    }
                    ShoppingHeaderButton(
                        icon = Icons.Default.AutoAwesome,
                        description = "Smart",
                        accent = blue,
                        lightMode = themedSurfaceMode,
                    ) {
                        searchExpanded = true
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
private fun ShoppingPageBackground(
    lightMode: Boolean,
    themeMode: ThemeMode,
    content: @Composable BoxScope.() -> Unit,
) {
    // Match the Settings page background, without changing the calendar theme.
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        content = content,
    )
}

@Composable
private fun ReferenceShoppingCheck(
    checked: Boolean,
    lightMode: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(30.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color =
            if (checked) {
                if (lightMode) MaterialTheme.colorScheme.primary else Color(0xFF269DFF)
            } else {
                Color.Transparent
            },
        border =
            BorderStroke(
                2.dp,
                if (lightMode && !checked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f)
                else Color.White.copy(alpha = if (checked) .95f else .82f),
            ),
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ShoppingHeaderButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    accent: Color,
    lightMode: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color =
            if (active) accent.copy(alpha = .18f)
            else if (lightMode) MaterialTheme.colorScheme.surfaceVariant
            else SettingsMatchedPalette.surface,
        border =
            BorderStroke(
                1.dp,
                if (active) accent.copy(alpha = .62f)
                else if (lightMode) MaterialTheme.colorScheme.outlineVariant
                else SettingsMatchedPalette.border,
            ),
        modifier = Modifier.size(39.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint =
                    if (active) accent
                    else if (lightMode) MaterialTheme.colorScheme.onSurface
                    else SettingsMatchedPalette.subText,
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
    lightMode: Boolean = false,
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
                trackColor =
                    if (lightMode) MaterialTheme.colorScheme.outlineVariant
                    else Color.White.copy(alpha = .09f),
            )
            Text(
                "$value",
                color =
                    if (lightMode) MaterialTheme.colorScheme.onSurface
                    else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            label,
            color =
                if (lightMode) MaterialTheme.colorScheme.onSurfaceVariant
                else Color.White.copy(alpha = .58f),
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
            if (selected) SettingsMatchedPalette.accent.copy(alpha = .20f)
            else SettingsMatchedPalette.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) SettingsMatchedPalette.accent.copy(alpha = .72f)
                else SettingsMatchedPalette.border,
            ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(icon, color = SettingsMatchedPalette.accent, fontSize = 16.sp)
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
    cleanVisualTheme: CleanVisualTheme,
    personalProfile: Int,
    personalLayoutRevision: Int,
    onThemeChanged: (ThemeMode) -> Unit,
    onUiLayoutChanged: (UiLayoutMode) -> Unit,
    onCleanVisualThemeChanged: (CleanVisualTheme) -> Unit,
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
    var showThemeGallery by remember { mutableStateOf(false) }
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

    var expandedCategory by remember { mutableStateOf<String?>(null) }

    Text(
        "Inställningar",
        fontSize = 27.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(3.dp))
    Text(
        "Välj vad du vill ändra. Bara ett område visas åt gången.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(14.dp))

    SettingsCategoryCard(
        title = "Kalenderns utseende",
        subtitle = "Kalenderläge, teman, färger och bakgrund",
        icon = Icons.Default.AutoAwesome,
        expanded = expandedCategory == "utseende",
        onToggle = { expandedCategory = if (expandedCategory == "utseende") null else "utseende" },
    ) {
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
                            else MaterialTheme.colorScheme.surfaceVariant,
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
                                Text(mode.label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    mode.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    
            if (uiLayoutMode == UiLayoutMode.MINIMAL) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Clean-tema",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(93.dp).height(70.dp).clip(RoundedCornerShape(11.dp)),
                    ) {
                        ThemeMiniature(
                            theme = cleanVisualTheme,
                            widget = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            cleanVisualTheme.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                            onClick = { showThemeGallery = true },
                            modifier = Modifier.fillMaxWidth().height(41.dp),
                        ) { Text("Välj kalendertema", fontSize = 12.sp) }
                    }
                }
                if (showThemeGallery) {
                    AlertDialog(
                        onDismissRequest = { showThemeGallery = false },
                        title = { Text("Välj kalendertema") },
                        text = {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 460.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                    // Small image-like previews instead of color-only text chips.
                                    // Preserve the compact three-column grid for Clean settings.
                                    selectableCleanVisualThemes.chunked(3).forEach { themes ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            themes.forEach { theme ->
                                                val selected = cleanVisualTheme == theme
                                                val spec = cleanThemeSpec(theme)
                                                Surface(
                                                    modifier = Modifier.weight(1f)
                                                        .clickable {
                                                            onCleanVisualThemeChanged(theme)
                                                            showThemeGallery = false
                                                        },
                                                    shape = RoundedCornerShape(11.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        if (selected) 2.dp else 1.dp,
                                                        if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outlineVariant,
                                                    ),
                                                    shadowElevation = if (selected) 3.dp else 0.dp,
                                                ) {
                                                    Column(Modifier.padding(5.dp)) {
                                                        Box(
                                                            Modifier.fillMaxWidth().height(99.dp)
                                                                .clip(RoundedCornerShape(8.dp)),
                                                        ) {
                                                            ThemeMiniature(
                                                                theme = theme,
                                                                widget = false,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                            if (selected) {
                                                                Icon(
                                                                    Icons.Default.CheckCircle,
                                                                    contentDescription = "Valt",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.align(Alignment.TopEnd)
                                                                        .padding(4.dp).size(17.dp)
                                                                        .background(spec.accentStrong, CircleShape),
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            theme.label,
                                                            fontSize = 10.sp, lineHeight = 12.sp,
                                                            maxLines = 2, minLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = if (selected) FontWeight.Bold
                                                                else FontWeight.Medium,
                                                        )
                                                    }
                                                }
                                            }
                                            repeat(3 - themes.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                        Spacer(Modifier.height(7.dp))
                                    }
                    
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showThemeGallery = false }) { Text("Stäng") }
                        },
                    )
                }
                Text(
                    "Valt tema: ${cleanVisualTheme.label}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    cleanVisualTheme.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
    
            Spacer(Modifier.height(8.dp))
            Text(
                "Färgtema",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
    
            Spacer(Modifier.height(16.dp))
            Text(
                "Egen bakgrund",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(7.dp))
    
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Säsongsbakgrunden används tills du väljer en bild.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
    
            Spacer(Modifier.height(6.dp))
            Text(
                "Den egna bilden används före säsongsbakgrunden i Clean och fullständigt/personligt läge. Bilden sparas lokalt på telefonen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
    Spacer(Modifier.height(9.dp))

    SettingsCategoryCard(
        title = "Widget",
        subtitle = "Tema och utseende på hemskärmen",
        icon = Icons.Default.CalendarMonth,
        expanded = expandedCategory == "widget",
        onToggle = { expandedCategory = if (expandedCategory == "widget") null else "widget" },
    ) {
        SettingsSectionCard(
            title = "Widget på hemskärmen",
            subtitle = "Eget tema för widgeten, oberoende av kalendertemat.",
        ) {
            WidgetThemeSettings(cleanVisualTheme)
        }
    }
    Spacer(Modifier.height(9.dp))

    SettingsCategoryCard(
        title = "Familj och delning",
        subtitle = "Medlemmar, inbjudningar och familjekod",
        icon = Icons.Default.People,
        expanded = expandedCategory == "familj",
        onToggle = { expandedCategory = if (expandedCategory == "familj") null else "familj" },
    ) {
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                onClick = { copyFamilyCode(context, session.code) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text("Kopiera familjekod")
            }
        }
    }
    Spacer(Modifier.height(9.dp))

    SettingsCategoryCard(
        title = "Aviseringar",
        subtitle = "Popup, ljud och behörigheter",
        icon = Icons.Default.CheckCircle,
        expanded = expandedCategory == "notiser",
        onToggle = { expandedCategory = if (expandedCategory == "notiser") null else "notiser" },
    ) {
        SettingsSectionCard(
            title = "Familjenotiser",
            subtitle = "Popup-avisering för nya familjeaktiviteter när Android tillåter det.",
        ) {
            Text(
                "Nya aktiviteter använder en egen högprioriterad aviseringskanal. " +
                    "Android bestämmer om en popup visas utifrån kanalens behörighet och " +
                    "telefonens Stör ej- och aviseringsinställningar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                onClick = { openNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Hantera popup och ljud") }
        }
        Spacer(Modifier.height(12.dp))
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
    }
    Spacer(Modifier.height(9.dp))

    SettingsCategoryCard(
        title = "Kalenderkopplingar",
        subtitle = "SportAdmin och synkronisering",
        icon = Icons.Default.Share,
        expanded = expandedCategory == "kopplingar",
        onToggle = { expandedCategory = if (expandedCategory == "kopplingar") null else "kopplingar" },
    ) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                members.forEach { member ->
                    val selected = memberId == member.id
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
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
                            Text(member.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
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
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f),
                    ),
            ) {
                Text("Spara och synka")
            }
        }
    }
    Spacer(Modifier.height(9.dp))

    SettingsCategoryCard(
        title = "Säkerhetskopior och offline",
        subtitle = "Spara, återställ och hantera lokal data",
        icon = Icons.Default.Settings,
        expanded = expandedCategory == "lagring",
        onToggle = { expandedCategory = if (expandedCategory == "lagring") null else "lagring" },
    ) {
        SettingsSectionCard(
            title = "Säkerhetskopiering och återställning",
            subtitle = "Exportera familjens data och återställ saknade uppgifter utan dubbletter.",
        ) {
            FamilyBackupSettings(session)
        }
        Spacer(Modifier.height(12.dp))
        SettingsSectionCard(
            title = "Offlineåtkomst",
            subtitle = "Öppna senast synkade aktiviteter, inköp och uppgifter utan internet.",
        ) {
            val savedAt = remember(session.id) { FamilyOfflineCache.lastSavedAt(context, session) }
            var confirmClear by remember { mutableStateOf(false) }
            var wasCleared by remember { mutableStateOf(false) }
            Text(
                if (wasCleared) "Sparad offlineinformation har rensats från den här telefonen."
                else if (savedAt == null) "Ingen offlinekopia sparad ännu."
                else "Senast sparade kalenderuppgifter: $savedAt",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val queued = remember(session.id) { mutableIntStateOf(OfflineMutationQueue.pendingCount(session)) }
            Spacer(Modifier.height(7.dp))
            Text(
                if (queued.intValue == 0) "Inga aktiviteter, inköp eller To-do väntar på uppladdning."
                else "${queued.intValue} nya poster väntar på att skickas när internet fungerar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (queued.intValue > 0) {
                TextButton(onClick = {
                    OfflineMutationQueue.requestRetry()
                    queued.intValue = OfflineMutationQueue.pendingCount(session)
                }) { Text("Försök synka väntande poster") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) { Text("Rensa offlinekopior från telefonen") }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Rensa offlinekopior?") },
                    text = { Text("Detta raderar bara senast sparad information på den här telefonen. Ingenting tas bort från familjens gemensamma kalender.") },
                    confirmButton = {
                        Button(onClick = {
                            FamilyOfflineCache.clear(context, session)
                            wasCleared = true
                            confirmClear = false
                        }) { Text("Rensa") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text("Avbryt") }
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(9.dp))

    SettingsCategoryCard(
        title = "App och uppdateringar",
        subtitle = "Version och nya uppdateringar",
        icon = Icons.Default.MoreHoriz,
        expanded = expandedCategory == "appen",
        onToggle = { expandedCategory = if (expandedCategory == "appen") null else "appen" },
    ) {
        AppUpdateSettingsCard()
    }
    Spacer(Modifier.height(9.dp))

    Spacer(Modifier.height(30.dp))
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val frame = RoundedCornerShape(22.dp)
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = frame,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF251F30)),
        border = BorderStroke(
            1.dp,
            if (expanded) Color(0xFFAC83ED) else Color(0xFF645777),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(Color(0xFF302A3C), Color(0xFF211C2C)))
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .padding(horizontal = 15.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF9861E7), Color(0xFF6231B2))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color(0xFFFFFFFF),
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        subtitle,
                        color = Color(0xFFE0DCE9),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = if (expanded) "Stäng $title" else "Öppna $title",
                    tint = Color(0xFFE0D3F5),
                    modifier = Modifier.size(22.dp)
                        .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                )
            }
            if (expanded) {
                Spacer(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        .height(1.dp).background(Color(0xFF675A76))
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262231)),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, Color(0xFF685B7A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF302B3A), Color(0xFF24202E)))
                )
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                title,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    subtitle,
                    color = Color(0xFFE0DCE9),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
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
            Triple(2, Icons.Default.CheckCircle, "Att göra"),
            Triple(1, Icons.Default.ShoppingCart, "Inköp"),
            Triple(3, Icons.Default.People, "Recept"),
            Triple(4, Icons.Default.Settings, "Inställningar"),
        )

    Box(
        Modifier.fillMaxWidth()
            .background(Color(0xFF06101A))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Surface(
            color = Color(0xB8172E46),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFF68B8FF).copy(alpha = .22f)),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .height(56.dp)
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
                                modifier = Modifier.size(if (isSelected) 24.dp else 21.dp),
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
private fun MinimalBottomNav(
    selected: Int,
    cleanVisualTheme: CleanVisualTheme,
    onSelect: (Int) -> Unit,
) {
    if (isPremiumMaterialTheme(cleanVisualTheme)) {
        OakReferenceBottomNav(selected, onSelect, cleanVisualTheme)
        return
    }
    val spec = cleanThemeSpec(cleanVisualTheme)
    val oakNav = cleanVisualTheme == CleanVisualTheme.OAK_WOOD
    val navShape =
        when (cleanVisualTheme) {
            CleanVisualTheme.CURRENT -> RoundedCornerShape(30.dp)
            CleanVisualTheme.OAK_WOOD -> RoundedCornerShape(25.dp)
            CleanVisualTheme.NORDIC_DAY_PLANNER,
            CleanVisualTheme.NORDIC_DAY_PLANNER_DARK -> RoundedCornerShape(0.dp)
            CleanVisualTheme.BRUTALIST, CleanVisualTheme.SWISS ->
                RoundedCornerShape(4.dp)
            CleanVisualTheme.CYBERPUNK -> RoundedCornerShape(10.dp)
            CleanVisualTheme.PURE_CALENDAR -> RoundedCornerShape(13.dp)
            CleanVisualTheme.MIDNIGHT_GOLD -> RoundedCornerShape(4.dp)
            CleanVisualTheme.PASTEL_FLOW -> RoundedCornerShape(26.dp)
            CleanVisualTheme.CRYSTAL_GLASS -> RoundedCornerShape(23.dp)
            CleanVisualTheme.EDITORIAL_PLANNER -> RoundedCornerShape(0.dp)
            CleanVisualTheme.NEON_PULSE -> RoundedCornerShape(5.dp)
            CleanVisualTheme.EARTH_SAGE -> RoundedCornerShape(21.dp)
            CleanVisualTheme.RETRO_DIGITAL -> RoundedCornerShape(2.dp)
            CleanVisualTheme.FOREST_PRO -> RoundedCornerShape(8.dp)
            CleanVisualTheme.FAMILY_SPECTRUM -> RoundedCornerShape(19.dp)
            CleanVisualTheme.GOTHAM_NIGHT -> RoundedCornerShape(5.dp)
            else -> RoundedCornerShape(spec.cardRadius)
        }
    val activeShape =
        when (cleanVisualTheme) {
            CleanVisualTheme.BRUTALIST, CleanVisualTheme.SWISS ->
                RoundedCornerShape(2.dp)
            CleanVisualTheme.CYBERPUNK -> RoundedCornerShape(8.dp)
            CleanVisualTheme.PURE_CALENDAR -> RoundedCornerShape(9.dp)
            CleanVisualTheme.MIDNIGHT_GOLD -> RoundedCornerShape(2.dp)
            CleanVisualTheme.PASTEL_FLOW -> RoundedCornerShape(20.dp)
            CleanVisualTheme.CRYSTAL_GLASS -> RoundedCornerShape(16.dp)
            CleanVisualTheme.EDITORIAL_PLANNER -> RoundedCornerShape(0.dp)
            CleanVisualTheme.NEON_PULSE -> RoundedCornerShape(2.dp)
            CleanVisualTheme.EARTH_SAGE -> RoundedCornerShape(17.dp)
            CleanVisualTheme.RETRO_DIGITAL -> RoundedCornerShape(0.dp)
            CleanVisualTheme.FOREST_PRO -> RoundedCornerShape(4.dp)
            CleanVisualTheme.FAMILY_SPECTRUM -> RoundedCornerShape(13.dp)
            CleanVisualTheme.OAK_WOOD -> RoundedCornerShape(21.dp)
            CleanVisualTheme.GOTHAM_NIGHT -> RoundedCornerShape(5.dp)
            else -> RoundedCornerShape(18.dp)
        }
    val items =
        if (isNordicDayPlannerTheme(cleanVisualTheme)) {
            listOf(
                Triple(0, Icons.Default.CalendarMonth, "Kalender"),
                Triple(2, Icons.Default.CheckCircle, "Att göra"),
                Triple(3, Icons.Default.People, "Familj"),
                Triple(4, Icons.Default.MoreHoriz, "Mer"),
            )
        } else {
            listOf(
                Triple(0, Icons.Default.CalendarMonth, "Kalender"),
                Triple(1, Icons.Default.ShoppingCart, "Inköp"),
                Triple(2, Icons.Default.CheckCircle, "Att göra"),
                Triple(4, Icons.Default.Settings, "Inställningar"),
                Triple(5, Icons.Default.LocationOn, "Plats"),
            )
        }

    Box(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Surface(
            color = spec.navSurface,
            shape = navShape,
            border = BorderStroke(1.dp, spec.navBorder),
            shadowElevation =
                if (cleanVisualTheme == CleanVisualTheme.CURRENT || oakNav) 12.dp else spec.shadow,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                if (oakNav) {
                    OakPhotographicSurface(
                        Modifier.matchParentSize(),
                        opacity = .31f,
                    )
                }
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
                            .clip(activeShape)
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        Brush.verticalGradient(
                                            if (cleanVisualTheme == CleanVisualTheme.CURRENT) {
                                                listOf(
                                                    Color(0xFF9D5CFF),
                                                    Color(0xFF6E34C9),
                                                )
                                            } else {
                                                listOf(
                                                    spec.selectedTop,
                                                    spec.selectedBottom,
                                                )
                                            }
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
                                    if (isSelected) spec.selectedText
                                    else if (oakNav) Color(0xFFF1D4AF)
                                    else spec.muted.copy(alpha = .82f),
                                modifier =
                                    Modifier.size(
                                        if (isSelected) 26.dp else 24.dp
                                    )
                                        .graphicsLayer {
                                            scaleX = if (isSelected) 1.06f else 1f
                                            scaleY = if (isSelected) 1.06f else 1f
                                        },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                label,
                                color =
                                    if (isSelected) spec.selectedText
                                    else if (oakNav) Color(0xFFF1D4AF)
                                    else spec.muted.copy(alpha = .88f),
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
}

/** Replaces the old generic wood-tinted bar with the carved five-tab reference. */
@Composable
private fun OakReferenceBottomNav(selected: Int, onSelect: (Int) -> Unit, visualTheme: CleanVisualTheme = CleanVisualTheme.OAK_WOOD) {
    val skin = premiumSkin(visualTheme)
    // Native Compose vectors/text remain crisp at any screen density.
    // The reference wood atlas is used for grain only, never baked labels.
    val items = listOf(
        Triple(0, Icons.Default.CalendarMonth, "Kalender"),
        Triple(1, Icons.Default.ShoppingCart, "Inköp"),
        Triple(2, Icons.Default.CheckCircle, "Att göra"),
        Triple(4, Icons.Default.Settings, "Inställningar"),
        Triple(5, Icons.Default.LocationOn, "Plats"),
    )
    Box(
        Modifier.fillMaxWidth().background(skin.spec.backgroundBottom)
            .navigationBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        val frame = RoundedCornerShape(25.dp)
        Surface(
            modifier = Modifier.fillMaxWidth().shadow(7.dp, frame),
            shape = frame,
            color = skin.spec.navSurface,
            border = BorderStroke(1.5.dp, skin.border),
        ) {
            Box {
                androidx.compose.runtime.CompositionLocalProvider(LocalPremiumMaterialTheme provides visualTheme) {
                    PremiumMaterialSurface(Modifier.matchParentSize(), opacity = .71f)
                }
                Row(
                    Modifier.fillMaxWidth().height(72.dp).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { (tab, icon, label) ->
                        val active = selected == tab
                        val shape = RoundedCornerShape(22.dp)
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .then(
                                    if (active) Modifier
                                        .shadow(3.dp, shape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    skin.buttonTop,
                                                    skin.buttonMid,
                                                    skin.buttonBottom,
                                                )
                                            ), shape
                                        )
                                        .border(1.dp, skin.border, shape)
                                    else Modifier
                                )
                                .clickable { onSelect(tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (active) OakPhotographicSurface(
                                Modifier.matchParentSize().clip(shape),
                                opacity = .43f, material = OakPhotoMaterial.PALE,
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    icon, contentDescription = label,
                                    tint = if (active) skin.headerInk else skin.panelInk,
                                    modifier = Modifier.size(if (active) 26.dp else 23.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    label,
                                    color = if (active) skin.headerInk else skin.panelInk,
                                    fontSize = 9.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
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
    initialKind: CalendarEntryKind = CalendarEntryKind.ACTIVITY,
    lightStyle: Boolean = false,
    nordicDarkStyle: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: suspend (String, String, String, String?, List<LocalDate>, Boolean, CalendarEntryKind, RecurrenceMode, Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val dialogBackground =
        when {
            lightStyle -> Color(0xFFFFFEFA)
            nordicDarkStyle -> MaterialTheme.colorScheme.surface
            else -> LuxurySurfaceElevated
        }
    val dialogText =
        when {
            lightStyle -> Color(0xFF17334A)
            nordicDarkStyle -> MaterialTheme.colorScheme.onSurface
            else -> Color.White
        }
    val dialogMuted =
        when {
            lightStyle -> Color(0xFF667782)
            nordicDarkStyle -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> Muted
        }
    val dialogAccent =
        when {
            lightStyle -> Color(0xFF0875A8)
            nordicDarkStyle -> MaterialTheme.colorScheme.primary
            else -> Purple
        }
    val dialogField =
        when {
            lightStyle -> Color(0xFFFFFFFF)
            nordicDarkStyle -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        }
    val dialogBorder =
        when {
            lightStyle -> Color(0xFFB9C8D1)
            nordicDarkStyle -> MaterialTheme.colorScheme.outlineVariant
            else -> Color.White.copy(alpha = .28f)
        }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale("sv", "SE")) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    val saveScope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var hasTime by remember(initialKind) { mutableStateOf(initialKind != CalendarEntryKind.REMINDER) }
    var startTime by remember { mutableStateOf<LocalTime?>(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf<LocalTime?>(LocalTime.of(10, 0)) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var memberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
    var isBirthday by remember { mutableStateOf(false) }
    var entryKind by remember(initialTitle, initialKind) {
        mutableStateOf(
            if (initialKind == CalendarEntryKind.REMINDER) CalendarEntryKind.REMINDER
            else if (initialTitle.trim().equals("Tvätt", ignoreCase = true)) CalendarEntryKind.LAUNDRY
            else CalendarEntryKind.ACTIVITY
        )
    }
    val isReminder = entryKind == CalendarEntryKind.REMINDER
    val isLaundry = entryKind == CalendarEntryKind.LAUNDRY
    var recurrence by remember { mutableStateOf(RecurrenceMode.NONE) }
    var keepUntilRemoved by remember { mutableStateOf(false) }
    var privateOnly by remember { mutableStateOf(false) }
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
        containerColor = dialogBackground,
        titleContentColor = dialogText,
        textContentColor = dialogText,
        title = {
            Text(
                when {
                    isBirthday -> "Ny födelsedag"
                    isReminder -> "Ny påminnelse"
                    isLaundry -> "Ny tvätt"
                    else -> "Ny aktivitet"
                }, fontWeight = FontWeight.Bold
            )
        },
        text = {
            CompositionLocalProvider(LocalContentColor provides dialogText) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        isBirthday = !isBirthday
                        if (isBirthday) {
                            recurrence = RecurrenceMode.NONE
                            entryKind = CalendarEntryKind.ACTIVITY
                        }
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
                            if (checked) {
                                recurrence = RecurrenceMode.NONE
                                entryKind = CalendarEntryKind.ACTIVITY
                            }
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
                    Spacer(Modifier.height(8.dp))
                    Text("Typ av händelse", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            CalendarEntryKind.ACTIVITY to "Aktivitet",
                            CalendarEntryKind.REMINDER to "Påminnelse",
                            CalendarEntryKind.LAUNDRY to "Tvätt",
                        ).forEach { (option, label) ->
                            val selected = entryKind == option
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) dialogAccent.copy(alpha = .18f) else dialogField,
                                border = BorderStroke(1.dp, if (selected) dialogAccent else dialogBorder),
                                modifier = Modifier.weight(1f).clickable {
                                    if (entryKind == CalendarEntryKind.LAUNDRY &&
                                        option != CalendarEntryKind.LAUNDRY &&
                                        title.trim().equals("Tvätt", ignoreCase = true)
                                    ) title = ""
                                    entryKind = option
                                    if (option == CalendarEntryKind.LAUNDRY && title.isBlank()) title = "Tvätt"
                                },
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 2.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Text(
                        when (entryKind) {
                            CalendarEntryKind.ACTIVITY -> "En aktivitet kan varna för tidskrockar."
                            CalendarEntryKind.REMINDER -> "Påminnelser räknas inte som kalenderkrockar."
                            CalendarEntryKind.LAUNDRY -> "Tvätt får en tvättmaskinsikon i kalendern och widgeten."
                        },
                        color = dialogMuted,
                        fontSize = 11.sp,
                    )
                }

                if (!isBirthday) {
                    Spacer(Modifier.height(6.dp))
                    Text("Upprepning", fontWeight = FontWeight.Bold)
                    RecurrenceMode.values().forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                recurrence = mode
                                if (mode != RecurrenceMode.NONE) {
                                    keepUntilRemoved = false
                                    privateOnly = false
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = recurrence == mode,
                                onClick = {
                                    recurrence = mode
                                    if (mode != RecurrenceMode.NONE) {
                                        keepUntilRemoved = false
                                        privateOnly = false
                                    }
                                },
                            )
                            Text(mode.label)
                        }
                    }
                }

                if (isReminder) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            privateOnly = !privateOnly
                            if (privateOnly) {
                                recurrence = RecurrenceMode.NONE
                                if (dates.size > 1) {
                                    val first = dates.minOrNull()!!
                                    dates.clear()
                                    dates.add(first)
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = privateOnly,
                            onCheckedChange = { checked ->
                                privateOnly = checked
                                if (checked) {
                                    recurrence = RecurrenceMode.NONE
                                    if (dates.size > 1) {
                                        val first = dates.minOrNull()!!
                                        dates.clear()
                                        dates.add(first)
                                    }
                                }
                            },
                        )
                        Column {
                            Text("Privat – enbart vald mottagare", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Skickas krypterat och syns inte i familjens kalender",
                                color = dialogMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    if (privateOnly &&
                        (memberId.isNullOrBlank() || memberId == ALL_FAMILY_MEMBER_ID)) {
                        Text("Välj mottagare längre ner",
                            color = dialogAccent, fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            keepUntilRemoved = !keepUntilRemoved
                            if (keepUntilRemoved) {
                                recurrence = RecurrenceMode.NONE
                                if (dates.size > 1) {
                                    val first = dates.minOrNull()!!
                                    dates.clear()
                                    dates.add(first)
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = keepUntilRemoved,
                            onCheckedChange = { checked ->
                                keepUntilRemoved = checked
                                if (checked) {
                                    recurrence = RecurrenceMode.NONE
                                    if (dates.size > 1) {
                                        val first = dates.minOrNull()!!
                                        dates.clear()
                                        dates.add(first)
                                    }
                                }
                            },
                        )
                        Column {
                            Text("Utan slutdatum", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Ligger kvar i widgeten tills du tar bort påminnelsen",
                                color = dialogMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(when { isBirthday -> "Namn"; isReminder -> "Påminnelse"; isLaundry -> "Tvätt"; else -> "Aktivitet" }) },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = dialogText,
                            unfocusedTextColor = dialogText,
                            focusedLabelColor = dialogAccent,
                            unfocusedLabelColor = dialogMuted,
                            cursorColor = dialogAccent,
                            focusedBorderColor = dialogAccent,
                            unfocusedBorderColor = dialogBorder,
                            focusedContainerColor = dialogField,
                            unfocusedContainerColor = dialogField,
                        ),
                )

                if (!isBirthday) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (isReminder) "Påminn mig" else "Tid", fontWeight = FontWeight.Bold)

                    Surface(
                        color =
                            if (!hasTime) dialogAccent.copy(alpha = if (lightStyle) .10f else .12f)
                            else if (lightStyle) Color(0xFFF5F8FA)
                            else if (nordicDarkStyle) MaterialTheme.colorScheme.surfaceVariant
                            else Color.White.copy(alpha = .03f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!hasTime) dialogAccent.copy(alpha = .45f)
                            else if (lightStyle) Color(0xFFD4DFE6)
                            else if (nordicDarkStyle) MaterialTheme.colorScheme.outlineVariant
                            else Color.White.copy(alpha = .08f),
                        ),
                        modifier = Modifier.fillMaxWidth().clickable {
                            hasTime = !hasTime
                            if (hasTime) {
                                if (startTime == null) startTime = LocalTime.of(9, 0)
                                if (endTime == null) endTime = LocalTime.of(10, 0)
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
                                    if (!checked) {
                                        if (startTime == null) startTime = LocalTime.of(9, 0)
                                        if (endTime == null) endTime = LocalTime.of(10, 0)
                                    }
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("Ingen tid", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Visas på dagen utan klockslag",
                                    color = dialogMuted,
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
                                color = dialogMuted,
                                fontSize = 12.sp,
                            )
                        } else if (!isReminder && endTime != null && startTime != null && !endTime!!.isAfter(startTime)) {
                            Text(
                                "Sluttiden räknas som nästa dag.",
                                color = dialogMuted,
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
                } else if (isReminder && (keepUntilRemoved || privateOnly)) {
                    OutlinedButton(
                        onClick = { openSingleDatePicker() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Välj startdatum")
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
                        color = dialogMuted,
                        fontSize = 12.sp,
                    )
                }
                if (isBirthday) {
                    Text(
                        "Födelsedagen läggs in årligen i kalendern.",
                        color = dialogMuted,
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
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                saveError?.let { error ->
                    Text(
                        error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Button(
                    onClick = {
                        if (!saving) {
                            saving = true
                            saveError = null
                            val selectedStartTime =
                                if (!hasTime && !isBirthday) "" else startTime?.format(timeFormatter) ?: "09:00"
                            val selectedEndTime =
                                if (!hasTime && !isBirthday) "" else endTime?.format(timeFormatter) ?: "10:00"
                            saveScope.launch {
                                try {
                                    onAdd(
                                        title.trim(),
                                        selectedStartTime,
                                        selectedEndTime,
                                        memberId,
                                        dates.toList(),
                                        isBirthday,
                                        entryKind,
                                        recurrence,
                                        isReminder && keepUntilRemoved,
                                        isReminder && privateOnly,
                                    )
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    saveError = "Kunde inte spara: " + (error.message ?: "okänt fel")
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    enabled = !saving &&
                        title.isNotBlank() &&
                        dates.isNotEmpty() &&
                        (isBirthday || !hasTime || (isReminder && startTime != null) ||
                            (startTime != null && endTime != null)) &&
                        (!isReminder || !privateOnly ||
                            (!memberId.isNullOrBlank() && memberId != ALL_FAMILY_MEMBER_ID)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dialogAccent,
                        contentColor = Color.White,
                    ),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp), strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (saving) "Sparar…" else "Färdig")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = dialogAccent),
            ) { Text("Avbryt") }
        },
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
