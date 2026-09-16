package se.familjekalender.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.PermissionController
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HEALTH_CONNECT_PROVIDER = "com.google.android.apps.healthdata"
private val HEALTH_CONNECT_BASE_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class)
)
private val HEALTH_STOCKHOLM = ZoneId.of("Europe/Stockholm")

data class HealthConnectRun(
    val recordId: String,
    val startTime: Instant,
    val endTime: Instant,
    val distanceKm: Double,
    val durationMinutes: Int
)

object HealthConnectSync {
    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER)

    private fun client(context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context, HEALTH_CONNECT_PROVIDER)

    fun requestedPermissions(context: Context): Set<String> {
        if (sdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return HEALTH_CONNECT_BASE_PERMISSIONS
        val healthClient = client(context)
        val backgroundAvailable = healthClient.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return if (backgroundAvailable) {
            HEALTH_CONNECT_BASE_PERMISSIONS + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        } else {
            HEALTH_CONNECT_BASE_PERMISSIONS
        }
    }

    suspend fun hasPermissions(context: Context): Boolean {
        if (sdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return false
        return client(context).permissionController.getGrantedPermissions().containsAll(HEALTH_CONNECT_BASE_PERMISSIONS)
    }

    suspend fun hasBackgroundPermission(context: Context): Boolean {
        if (sdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return false
        val healthClient = client(context)
        val backgroundAvailable = healthClient.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        if (!backgroundAvailable) return false
        return HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in
            healthClient.permissionController.getGrantedPermissions()
    }

    suspend fun readRunningSessions(context: Context, lookbackDays: Long = 30): List<HealthConnectRun> {
        if (!hasPermissions(context)) return emptyList()
        val healthClient = client(context)
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(lookbackDays.coerceIn(1, 30)))
        val response = healthClient.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = true
            )
        )

        val runs = mutableListOf<HealthConnectRun>()
        for (exercise in response.records) {
            if (
                exercise.exerciseType != ExerciseSessionRecord.EXERCISE_TYPE_RUNNING &&
                exercise.exerciseType != ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
            ) continue

            val aggregate = healthClient.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(exercise.startTime, exercise.endTime)
                )
            )
            val meters = aggregate[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
            val distanceKm = meters / 1000.0
            if (distanceKm <= 0.0) continue
            val minutes = Duration.between(exercise.startTime, exercise.endTime)
                .toMinutes()
                .toInt()
                .coerceAtLeast(1)
            runs += HealthConnectRun(
                recordId = exercise.metadata.id,
                startTime = exercise.startTime,
                endTime = exercise.endTime,
                distanceKm = distanceKm,
                durationMinutes = minutes
            )
        }
        return runs
    }

    suspend fun syncToCalendar(context: Context, session: FamilySession): Int {
        if (!hasPermissions(context)) return 0
        val prefs = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
        val memberId = prefs.getString("device_member_id", null)?.takeIf { it.isNotBlank() } ?: return 0
        val existing = SupabaseSync.loadEvents(session)
        val runs = readRunningSessions(context)
        var added = 0

        runs.forEach { run ->
            val externalKey = "health_connect:${run.recordId}"
            if (existing.any { it.seriesId == externalKey }) return@forEach

            val startLocal = run.startTime.atZone(HEALTH_STOCKHOLM)
            val endLocal = run.endTime.atZone(HEALTH_STOCKHOLM)
            val date = startLocal.toLocalDate()
            val startText = startLocal.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            val endText = endLocal.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            val title = "🏃 Löpning · ${"%.2f".format(Locale.US, run.distanceKm)} km · ${run.durationMinutes} min"

            SupabaseSync.addEvent(
                session = session,
                title = title,
                date = date,
                startTime = startText,
                endTime = endText,
                memberId = memberId,
                seriesId = externalKey
            )
            added++
        }
        return added
    }

    fun openProviderInstall(context: Context) {
        val uri = Uri.parse("market://details?id=$HEALTH_CONNECT_PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.vending")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

@Composable
fun HealthConnectSettingsCard(
    session: FamilySession,
    onSynced: suspend () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var sdkStatus by remember { mutableIntStateOf(HealthConnectSync.sdkStatus(context)) }
    var connected by remember { mutableStateOf(false) }
    var backgroundSync by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun refreshConnectionState() {
        sdkStatus = HealthConnectSync.sdkStatus(context)
        connected = HealthConnectSync.hasPermissions(context)
        backgroundSync = HealthConnectSync.hasBackgroundPermission(context)
    }

    suspend fun syncNow() {
        busy = true
        runCatching { HealthConnectSync.syncToCalendar(context, session) }
            .onSuccess { imported ->
                message = if (imported > 0) "$imported nya löppass importerade" else "Löpningen är redan synkad"
                onSynced()
            }
            .onFailure { message = "Health Connect: ${it.message ?: "synkningen misslyckades"}" }
        refreshConnectionState()
        busy = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        connected = granted.containsAll(HEALTH_CONNECT_BASE_PERMISSIONS)
        backgroundSync = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted
        if (connected) scope.launch { syncNow() }
        else message = "Behörighet till träningspass och distans behövs för synkning"
    }

    LaunchedEffect(Unit) {
        refreshConnectionState()
        if (connected) {
            runCatching { HealthConnectSync.syncToCalendar(context, session) }
                .onSuccess { imported ->
                    if (imported > 0) {
                        message = "$imported nya löppass importerade automatiskt"
                        onSynced()
                    }
                }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Health Connect", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        when {
                            sdkStatus == HealthConnectClient.SDK_UNAVAILABLE -> "Stöds inte på den här enheten"
                            sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect behöver installeras eller uppdateras"
                            connected && backgroundSync -> "Ansluten · automatisk synk är aktiv"
                            connected -> "Ansluten · synkar när Familjekalendern används"
                            else -> "Google/Android hälsodata · inte ansluten"
                        },
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Familjekalendern läser endast löppass och distans. Importen kopplas till personen som är vald på den här telefonen och visas i löpstatistiken.",
                color = Muted,
                fontSize = 11.sp
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))

            when (sdkStatus) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("Health Connect är inte tillgängligt")
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    Button(onClick = { HealthConnectSync.openProviderInstall(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Installera / uppdatera Health Connect")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            if (connected) scope.launch { syncNow() }
                            else permissionLauncher.launch(HealthConnectSync.requestedPermissions(context))
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (connected) "Synka nu" else "Anslut Health Connect")
                    }
                }
            }
        }
    }
}
