package se.familjekalender.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val CHILD_MODE_PREFS = "family_calendar_child_mode"
private const val CHILD_LOCATION_PREFS = "family_calendar_location"
private const val CHILD_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val CHILD_SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"
private val CHILD_STOCKHOLM = ZoneId.of("Europe/Stockholm")
private val SCHOOL_INPUT_FORMAT = DateTimeFormatter.ofPattern("H:mm")
private val SCHOOL_STORE_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

data class SyncChildModeSettings(
    val memberId: String,
    val realtimeTracking: Boolean = true,
    val schoolEnabled: Boolean = false,
    val schoolStart: String = "08:00",
    val schoolEnd: String = "15:00",
    val schoolWeekdays: Set<Int> = setOf(1, 2, 3, 4, 5),
)

internal fun defaultChildModeSettings(memberId: String) = SyncChildModeSettings(memberId = memberId)

internal object ChildModeSync {
    suspend fun loadSettings(
        session: FamilySession,
        memberId: String,
    ): SyncChildModeSettings =
        withContext(Dispatchers.IO) {
            val text =
                request(
                    method = "GET",
                    path =
                        "/rest/v1/child_mode_settings" +
                            "?select=member_id,realtime_tracking,school_enabled,school_start,school_end,school_weekdays" +
                            "&family_id=eq.${session.id}&member_id=eq.$memberId&limit=1",
                    familyCode = session.code,
                )
            val array = JSONArray(text)
            if (array.length() == 0) {
                return@withContext defaultChildModeSettings(memberId)
            }
            val row = array.getJSONObject(0)
            val weekdaysJson = row.optJSONArray("school_weekdays")
            val weekdays =
                buildSet {
                    if (weekdaysJson != null) {
                        repeat(weekdaysJson.length()) {
                            weekdaysJson.optInt(it).takeIf { value -> value in 1..7 }?.let(::add)
                        }
                    }
                }.ifEmpty { setOf(1, 2, 3, 4, 5) }

            SyncChildModeSettings(
                memberId = row.getString("member_id"),
                realtimeTracking = row.optBoolean("realtime_tracking", true),
                schoolEnabled = row.optBoolean("school_enabled", false),
                schoolStart = row.optString("school_start", "08:00").take(5),
                schoolEnd = row.optString("school_end", "15:00").take(5),
                schoolWeekdays = weekdays,
            )
        }

    suspend fun saveSettings(
        session: FamilySession,
        settings: SyncChildModeSettings,
    ) =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("family_id", session.id)
                    .put("member_id", settings.memberId)
                    .put("realtime_tracking", settings.realtimeTracking)
                    .put("school_enabled", settings.schoolEnabled)
                    .put("school_start", settings.schoolStart)
                    .put("school_end", settings.schoolEnd)
                    .put("school_weekdays", JSONArray(settings.schoolWeekdays.sorted()))
                    .put("updated_at", OffsetDateTime.now().toString())
            request(
                method = "POST",
                path = "/rest/v1/child_mode_settings?on_conflict=family_id,member_id",
                body = body,
                familyCode = session.code,
                preferExtra = "resolution=merge-duplicates",
            )
        }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        familyCode: String,
        preferExtra: String? = null,
    ): String {
        val connection = URL("$CHILD_SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("apikey", CHILD_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $CHILD_SUPABASE_ANON_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-family-code", familyCode.uppercase())
        val prefer =
            listOfNotNull("return=minimal", preferExtra).joinToString(",")
        connection.setRequestProperty("Prefer", prefer)
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use {
                it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Serverfel $code: $text")
        return text.ifBlank { "[]" }
    }
}

internal fun isSchoolScheduleActive(
    settings: SyncChildModeSettings,
    now: ZonedDateTime = ZonedDateTime.now(CHILD_STOCKHOLM),
): Boolean {
    if (!settings.schoolEnabled || now.dayOfWeek.value !in settings.schoolWeekdays) return false
    val start = parseSchoolTime(settings.schoolStart) ?: return false
    val end = parseSchoolTime(settings.schoolEnd) ?: return false
    val time = now.toLocalTime()
    return if (end.isAfter(start)) {
        !time.isBefore(start) && time.isBefore(end)
    } else {
        !time.isBefore(start) || time.isBefore(end)
    }
}

private fun parseSchoolTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value.trim(), SCHOOL_INPUT_FORMAT) }.getOrNull()

private fun normalizeSchoolTime(value: String): String? =
    parseSchoolTime(value)?.format(SCHOOL_STORE_FORMAT)

internal object SchoolModeController {
    fun hasPolicyAccess(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    fun requestPolicyAccess(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun apply(context: Context, active: Boolean): Boolean {
        if (!hasPolicyAccess(context)) return false
        val prefs = context.getSharedPreferences(CHILD_MODE_PREFS, Context.MODE_PRIVATE)
        val alreadyApplied = prefs.getBoolean("school_dnd_applied", false)
        if (alreadyApplied == active) return true

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            manager.setInterruptionFilter(
                if (active) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }.onFailure { return false }

        prefs.edit().putBoolean("school_dnd_applied", active).apply()
        return true
    }
}

@Composable
internal fun HugoBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    NavigationBar(containerColor = Color(0xFF152016), tonalElevation = 0.dp) {
        listOf(
            Triple(0, Icons.Default.Home, "Hugo"),
            Triple(5, Icons.Default.LocationOn, "Plats"),
            Triple(4, Icons.Default.Settings, "Inställningar"),
        ).forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = label) },
                label = {
                    Text(
                        label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF8BE15F),
                        selectedTextColor = Color(0xFF8BE15F),
                        indicatorColor = Color(0xFF294929),
                        unselectedIconColor = Color.White.copy(alpha = .70f),
                        unselectedTextColor = Color.White.copy(alpha = .70f),
                    ),
            )
        }
    }
}

@Composable
internal fun HugoLiveLocationCard(
    session: FamilySession,
    members: List<SyncMember>,
    onOpenLocation: () -> Unit,
    modifier: Modifier = Modifier,
    showMap: Boolean = true,
) {
    val hugo = remember(members) {
        members.firstOrNull { it.name.equals("Hugo", ignoreCase = true) }
    } ?: return

    var location by remember(hugo.id) { mutableStateOf<SyncFamilyLocation?>(null) }
    var settings by remember(hugo.id) { mutableStateOf(defaultChildModeSettings(hugo.id)) }
    var error by remember(hugo.id) { mutableStateOf<String?>(null) }
    var showSchoolSettings by remember { mutableStateOf(false) }

    LaunchedEffect(session.id, hugo.id) {
        var nextSettingsRefresh = 0L
        while (isActive) {
            runCatching { FamilyLocationSync.loadLocations(session) }
                .onSuccess { rows ->
                    location = rows.firstOrNull { it.memberId == hugo.id }
                    error = null
                }
                .onFailure { error = it.message }

            val now = System.currentTimeMillis()
            if (now >= nextSettingsRefresh) {
                runCatching { ChildModeSync.loadSettings(session, hugo.id) }
                    .onSuccess { settings = it }
                nextSettingsRefresh = now + 30_000L
            }
            delay(3_000L)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD91B1823)),
        border = BorderStroke(1.dp, Color(0xFF6FCF59).copy(alpha = .28f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF294929),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = Color(0xFF8BE15F),
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Hugo · liveposition",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        when {
                            location == null && error != null -> "Kunde inte hämta position"
                            location == null -> "Väntar på Hugos GPS"
                            else -> formatChildUpdated(location!!.updatedAt)
                        },
                        color = Color.White.copy(alpha = .66f),
                        fontSize = 12.sp,
                    )
                }
                location?.batteryPercent?.let { battery ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = if (battery <= 20) Color(0xFFFFB86B) else Color(0xFF8BE15F),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            " $battery%",
                            color = Color.White.copy(alpha = .78f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            if (showMap && location != null) {
                LocationMapCard(
                    locations = listOfNotNull(location),
                    members = listOf(hugo),
                    selectedMemberId = hugo.id,
                    batteryVisible = true,
                    mapHeight = 178.dp,
                    showControls = false,
                    showDetails = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenLocation,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Följ Hugo")
                }
                OutlinedButton(
                    onClick = { showSchoolSettings = true },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color(0xFF8BE15F).copy(alpha = .36f)),
                ) {
                    Icon(Icons.Default.School, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (settings.schoolEnabled) {
                            "Skola ${settings.schoolStart}–${settings.schoolEnd}"
                        } else {
                            "Skola"
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }

    if (showSchoolSettings) {
        HugoSchoolSettingsDialog(
            session = session,
            member = hugo,
            initialSettings = settings,
            onDismiss = { showSchoolSettings = false },
            onSaved = {
                settings = it
                showSchoolSettings = false
            },
        )
    }
}

@Composable
internal fun HugoChildModeScreen(
    session: FamilySession,
    member: SyncMember?,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    onOpenLocation: () -> Unit,
) {
    if (member == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hugo kunde inte hittas i familjen.")
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationPrefs =
        remember { context.getSharedPreferences(CHILD_LOCATION_PREFS, Context.MODE_PRIVATE) }
    val childPrefs = remember { context.getSharedPreferences(CHILD_MODE_PREFS, Context.MODE_PRIVATE) }
    var settings by remember(member.id) { mutableStateOf(defaultChildModeSettings(member.id)) }
    var manualSchool by remember(member.id) {
        mutableStateOf(childPrefs.getBoolean("manual_school_${member.id}", false))
    }
    var scheduleSchool by remember { mutableStateOf(false) }
    var dndAccess by remember { mutableStateOf(SchoolModeController.hasPolicyAccess(context)) }
    var showSchedule by remember { mutableStateOf(false) }
    var gpsEnabled by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    fun hasBackgroundLocation(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            gpsEnabled =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (gpsEnabled) {
                locationPrefs
                    .edit()
                    .putString("device_member_id", member.id)
                    .putBoolean("sharing_enabled", true)
                    .putBoolean("child_realtime_tracking", settings.realtimeTracking)
                    .apply()
                FamilyLocationService.start(context)
            }
        }

    LaunchedEffect(session.id, member.id) {
        locationPrefs.edit().putString("device_member_id", member.id).apply()
        while (isActive) {
            runCatching { ChildModeSync.loadSettings(session, member.id) }
                .onSuccess { latest ->
                    settings = latest
                    locationPrefs
                        .edit()
                        .putBoolean("child_realtime_tracking", latest.realtimeTracking)
                        .apply()
                }

            scheduleSchool = isSchoolScheduleActive(settings)
            dndAccess = SchoolModeController.hasPolicyAccess(context)
            SchoolModeController.apply(context, scheduleSchool || manualSchool)

            gpsEnabled =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

            if (gpsEnabled) {
                locationPrefs.edit().putBoolean("sharing_enabled", true).apply()
                FamilyLocationService.start(context)
            }
            delay(15_000L)
        }
    }

    val schoolActive = scheduleSchool || manualSchool
    val todayEvents =
        remember(events, member.id) {
            val today = LocalDate.now()
            events
                .filter {
                    it.date == today &&
                        (it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID)
                }
                .sortedWith(compareBy<SyncEvent> { it.time }.thenBy { it.title })
        }

    Box(Modifier.fillMaxSize()) {
        HugoPixelBackground()

        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    "HUGO",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "BARNLÄGE",
                    color = Color(0xFFB8F28E),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }

            PixelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = Color(0xFF8BE15F),
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (gpsEnabled && settings.realtimeTracking) "GPS LIVE" else "GPS",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            when {
                                !gpsEnabled -> "Platsbehörighet behövs"
                                settings.realtimeTracking -> "Position delas med familjen i nära realtid"
                                else -> "Smart platsdelning är aktiv"
                            },
                            color = Color.White.copy(alpha = .70f),
                            fontSize = 12.sp,
                        )
                    }
                }

                if (!gpsEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F8B3B),
                                contentColor = Color.White,
                            ),
                    ) {
                        Text("Aktivera GPS", fontFamily = FontFamily.Monospace)
                    }
                } else if (!hasBackgroundLocation() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Tillåt plats hela tiden", fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenLocation) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Öppna karta")
                }
            }

            PixelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = if (schoolActive) Color(0xFFFFD166) else Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SKOLA",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                        )
                        Text(
                            if (schoolActive) "Ljud och vibrationer av" else "Tryck för tyst skolläge",
                            color = Color.White.copy(alpha = .70f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = schoolActive,
                        onCheckedChange = { enabled ->
                            if (scheduleSchool && !enabled) {
                                // Schemat styr under aktiv skoltid.
                                return@Switch
                            }
                            manualSchool = enabled
                            childPrefs
                                .edit()
                                .putBoolean("manual_school_${member.id}", enabled)
                                .apply()
                            if (enabled && !SchoolModeController.hasPolicyAccess(context)) {
                                SchoolModeController.requestPolicyAccess(context)
                            } else {
                                SchoolModeController.apply(context, enabled || scheduleSchool)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Color.Black.copy(alpha = .20f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showSchedule = true },
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color(0xFFB8F28E),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (settings.schoolEnabled) {
                                "Schema ${settings.schoolStart}–${settings.schoolEnd}"
                            } else {
                                "Skolschema avstängt"
                            },
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "ÄNDRA",
                            color = Color(0xFFB8F28E),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                        )
                    }
                }

                if (schoolActive && !dndAccess) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { SchoolModeController.requestPolicyAccess(context) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFFFD166)),
                    ) {
                        Icon(Icons.Default.VolumeOff, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ge tillgång till Stör ej")
                    }
                }
            }

            PixelCard {
                Text(
                    "IDAG",
                    color = Color(0xFFB8F28E),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (todayEvents.isEmpty()) {
                    Text(
                        "Inga aktiviteter idag.",
                        color = Color.White.copy(alpha = .72f),
                        fontSize = 13.sp,
                    )
                } else {
                    todayEvents.take(5).forEach { event ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                event.time,
                                color = Color(0xFFFFD166),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(50.dp),
                            )
                            Text(
                                event.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSchedule) {
        HugoSchoolSettingsDialog(
            session = session,
            member = member,
            initialSettings = settings,
            onDismiss = { showSchedule = false },
            onSaved = {
                settings = it
                locationPrefs
                    .edit()
                    .putBoolean("child_realtime_tracking", it.realtimeTracking)
                    .apply()
                showSchedule = false
                scope.launch {
                    scheduleSchool = isSchoolScheduleActive(it)
                    SchoolModeController.apply(context, scheduleSchool || manualSchool)
                }
            },
        )
    }
}

@Composable
private fun HugoPixelBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF76A9D5))
        val block = 34.dp.toPx()
        val horizon = size.height * .56f
        var y = horizon
        var row = 0
        while (y < size.height + block) {
            var x = 0f
            var col = 0
            while (x < size.width + block) {
                val color =
                    when {
                        row == 0 -> if (col % 3 == 0) Color(0xFF6FB64B) else Color(0xFF73BD4E)
                        (row + col) % 4 == 0 -> Color(0xFF6C4A2C)
                        (row + col) % 3 == 0 -> Color(0xFF795334)
                        else -> Color(0xFF5F4128)
                    }
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(block + 1f, block + 1f),
                )
                x += block
                col++
            }
            y += block
            row++
        }
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xB30A1110)),
                    startY = 0f,
                    endY = size.height,
                )
        )
    }
}

@Composable
private fun PixelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xE6222B20),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(2.dp, Color(0xFF3E5B34)),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun HugoSchoolSettingsDialog(
    session: FamilySession,
    member: SyncMember,
    initialSettings: SyncChildModeSettings,
    onDismiss: () -> Unit,
    onSaved: (SyncChildModeSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var enabled by remember(initialSettings) { mutableStateOf(initialSettings.schoolEnabled) }
    var realtime by remember(initialSettings) { mutableStateOf(initialSettings.realtimeTracking) }
    var start by remember(initialSettings) { mutableStateOf(initialSettings.schoolStart) }
    var end by remember(initialSettings) { mutableStateOf(initialSettings.schoolEnd) }
    var weekdays by remember(initialSettings) { mutableStateOf(initialSettings.schoolWeekdays) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A2018),
        title = {
            Column {
                Text("Hugos skolläge", fontWeight = FontWeight.Bold)
                Text(
                    "Tyst läge och liveposition",
                    color = Color.White.copy(alpha = .62f),
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatiskt skolläge", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Stänger av ljud och vibrationer under schemat.",
                            color = Color.White.copy(alpha = .62f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live-GPS", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Hög frekvens för att ni ska kunna följa Hugo.",
                            color = Color.White.copy(alpha = .62f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(checked = realtime, onCheckedChange = { realtime = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it.take(5) },
                        label = { Text("Från") },
                        placeholder = { Text("08:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it.take(5) },
                        label = { Text("Till") },
                        placeholder = { Text("15:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("Aktiva dagar", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    listOf("M", "T", "O", "T", "F").forEachIndexed { index, label ->
                        val day = index + 1
                        FilterChip(
                            selected = day in weekdays,
                            onClick = {
                                weekdays =
                                    weekdays.toMutableSet().apply {
                                        if (day in this) remove(day) else add(day)
                                    }
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(
                    "Hugos telefon behöver platsbehörighet och tillgång till Stör ej för att allt ska fungera automatiskt.",
                    color = Color.White.copy(alpha = .58f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedStart = normalizeSchoolTime(start)
                    val normalizedEnd = normalizeSchoolTime(end)
                    when {
                        normalizedStart == null -> error = "Kontrollera starttiden."
                        normalizedEnd == null -> error = "Kontrollera sluttiden."
                        weekdays.isEmpty() -> error = "Välj minst en skoldag."
                        else -> {
                            saving = true
                            error = null
                            val next =
                                SyncChildModeSettings(
                                    memberId = member.id,
                                    realtimeTracking = realtime,
                                    schoolEnabled = enabled,
                                    schoolStart = normalizedStart,
                                    schoolEnd = normalizedEnd,
                                    schoolWeekdays = weekdays,
                                )
                            scope.launch {
                                runCatching { ChildModeSync.saveSettings(session, next) }
                                    .onSuccess { onSaved(next) }
                                    .onFailure { error = it.message ?: "Kunde inte spara" }
                                saving = false
                            }
                        }
                    }
                },
                enabled = !saving,
            ) {
                Text(if (saving) "Sparar…" else "Spara")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Avbryt") }
        },
    )
}

private fun formatChildUpdated(value: String): String {
    val updated =
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: return "Position mottagen"
    val seconds =
        Duration.between(updated, java.time.Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 8 -> "Live · nu"
        seconds < 60 -> "Live · ${seconds}s sedan"
        seconds < 3600 -> "Senast ${seconds / 60} min sedan"
        else -> "Senast ${seconds / 3600} h sedan"
    }
}
