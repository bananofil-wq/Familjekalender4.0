package se.familjekalender.app

// Foreground location tracking keeps family positions fresh when the UI is not open.
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FamilyLocationService : Service() {
    companion object {
        private const val CHANNEL_ID = "family_location_tracking"
        private const val NOTIFICATION_ID = 73041
        private const val LOCATION_PREFS = "family_calendar_location"
        private const val FAMILY_PREFS = "family_calendar"

        private const val LIVE_INTERVAL_MS = 3_000L
        private const val LIVE_HEARTBEAT_MS = 5_000L
        private const val LIVE_MIN_DISTANCE_M = 3f

        private const val MOVING_NETWORK_INTERVAL_MS = 45_000L
        private const val MOVING_GPS_INTERVAL_MS = 60_000L
        private const val STILL_NETWORK_INTERVAL_MS = 90_000L
        private const val STILL_GPS_INTERVAL_MS = 5 * 60_000L
        private const val NEAR_PLACE_INTERVAL_MS = 30_000L

        private const val MOVING_HEARTBEAT_MS = 60_000L
        private const val STILL_HEARTBEAT_MS = 5 * 60_000L
        private const val NEAR_PLACE_HEARTBEAT_MS = 30_000L

        private const val MIN_PUBLISH_DISTANCE_M = 20f
        private const val MAX_ACCEPTED_ACCURACY_M = 250f
        private const val MOVEMENT_RECENT_MS = 3 * 60_000L
        private const val NEAR_PLACE_MIN_DISTANCE_M = 500f

        fun start(context: Context) {
            val intent = Intent(context, FamilyLocationService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FamilyLocationService::class.java))
        }
    }

    private enum class TrackingMode {
        LIVE,
        MOVING,
        STILL,
        NEAR_PLACE,
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager

    @Volatile
    private var publishing = false
    @Volatile
    private var lastPublishedAt = 0L
    @Volatile
    private var lastPublishedLocation: Location? = null
    @Volatile
    private var cachedPlaces: List<SyncFamilyPlace> = emptyList()

    private var trackingMode: TrackingMode? = null
    private var lastObservedLocation: Location? = null
    private var lastMeaningfulMovementAt = System.currentTimeMillis()

    private val listener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) = handleLocation(location)

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!sharingEnabled() || !hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        configureTracking(
            if (liveViewActive()) TrackingMode.LIVE else TrackingMode.MOVING,
            force = true,
        )
        refreshPlaces()
        publishRecentCachedLocation()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(listener) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED

    private fun sharingEnabled(): Boolean {
        val prefs = getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("sharing_enabled", false) &&
                !prefs.getString("device_member_id", null).isNullOrBlank()
    }

    private fun liveViewActive(): Boolean =
        getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
            .getBoolean("live_view_active", false)

    private fun configureTracking(mode: TrackingMode, force: Boolean = false) {
        if (!hasLocationPermission()) return
        if (!force && trackingMode == mode) return

        runCatching { locationManager.removeUpdates(listener) }
        trackingMode = mode

        val requests =
            when (mode) {
                TrackingMode.LIVE ->
                    listOf(
                        Triple(
                            LocationManager.NETWORK_PROVIDER,
                            LIVE_INTERVAL_MS,
                            LIVE_MIN_DISTANCE_M,
                        ),
                        Triple(LocationManager.GPS_PROVIDER, LIVE_INTERVAL_MS, LIVE_MIN_DISTANCE_M),
                    )

                TrackingMode.MOVING ->
                    listOf(
                        Triple(LocationManager.NETWORK_PROVIDER, MOVING_NETWORK_INTERVAL_MS, 15f),
                        Triple(LocationManager.GPS_PROVIDER, MOVING_GPS_INTERVAL_MS, 20f),
                    )

                TrackingMode.STILL ->
                    listOf(
                        Triple(LocationManager.NETWORK_PROVIDER, STILL_NETWORK_INTERVAL_MS, 30f),
                        Triple(LocationManager.GPS_PROVIDER, STILL_GPS_INTERVAL_MS, 50f),
                    )

                TrackingMode.NEAR_PLACE ->
                    listOf(
                        Triple(LocationManager.NETWORK_PROVIDER, NEAR_PLACE_INTERVAL_MS, 10f),
                        Triple(LocationManager.GPS_PROVIDER, NEAR_PLACE_INTERVAL_MS, 10f),
                    )
            }

        requests.forEach { (provider, minTime, minDistance) ->
            runCatching {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, minTime, minDistance, listener)
                }
            }
        }
    }

    private fun refreshPlaces() {
        serviceScope.launch {
            val session = currentSession() ?: return@launch
            cachedPlaces =
                runCatching { FamilyLocationSync.loadPlaces(session) }.getOrDefault(cachedPlaces)
        }
    }

    private fun publishRecentCachedLocation() {
        if (!hasLocationPermission()) return
        val newest =
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time } ?: return
        if (System.currentTimeMillis() - newest.time <= 2 * 60_000L) {
            handleLocation(newest)
        }
    }

    private fun handleLocation(location: Location) {
        if (!sharingEnabled()) {
            stopSelf()
            return
        }
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTED_ACCURACY_M) return
        if (location.time > 0 && System.currentTimeMillis() - location.time > 2 * 60_000L) return

        val now = System.currentTimeMillis()
        val desiredMode =
            if (liveViewActive()) TrackingMode.LIVE else desiredTrackingMode(location, now)
        configureTracking(desiredMode)

        if (publishing) return

        val previous = lastPublishedLocation
        val movementThreshold =
            if (desiredMode == TrackingMode.LIVE) {
                LIVE_MIN_DISTANCE_M
            } else if (location.hasAccuracy()) {
                maxOf(MIN_PUBLISH_DISTANCE_M, (location.accuracy * 0.75f).coerceAtMost(80f))
            } else {
                MIN_PUBLISH_DISTANCE_M
            }
        val movedEnough = previous == null || previous.distanceTo(location) >= movementThreshold
        val dueForHeartbeat = now - lastPublishedAt >= heartbeatInterval(desiredMode)
        if (!movedEnough && !dueForHeartbeat) return

        publishing = true
        serviceScope.launch {
            try {
                val session = currentSession() ?: return@launch
                val locationPrefs = getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
                val memberId = locationPrefs.getString("device_member_id", null) ?: return@launch
                val showBattery = locationPrefs.getBoolean("battery_visible", true)

                FamilyLocationSync.publishLocation(
                    session = session,
                    memberId = memberId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyM = location.accuracy.takeIf { location.hasAccuracy() && it > 0f },
                    batteryPercent = if (showBattery) batteryPercent() else null,
                )

                lastPublishedAt = System.currentTimeMillis()
                lastPublishedLocation = Location(location)

                runCatching {
                    val locations = FamilyLocationSync.loadLocations(session)
                    val places = FamilyLocationSync.loadPlaces(session)
                    cachedPlaces = places
                    val members =
                        SupabaseSync.loadMembers(session).filter { it.id != ALL_FAMILY_MEMBER_ID }
                    checkLocationTransitions(
                        applicationContext,
                        session.id,
                        locations,
                        places,
                        members,
                    )
                }
            } finally {
                publishing = false
            }
        }
    }

    private fun desiredTrackingMode(location: Location, now: Long): TrackingMode {
        val previousObserved = lastObservedLocation
        val movementThreshold =
            if (location.hasAccuracy()) {
                maxOf(25f, (location.accuracy * 0.5f).coerceAtMost(60f))
            } else {
                25f
            }
        val moved = previousObserved?.distanceTo(location) ?: Float.MAX_VALUE
        val speedShowsMovement = location.hasSpeed() && location.speed >= 0.8f
        if (moved >= movementThreshold || speedShowsMovement) {
            lastMeaningfulMovementAt = now
        }
        lastObservedLocation = Location(location)

        val movingRecently = now - lastMeaningfulMovementAt <= MOVEMENT_RECENT_MS
        val closeToSavedPlace = cachedPlaces.any { place ->
            val distance = distanceToPlace(location, place)
            val inside = distance <= place.radiusM.toFloat()
            val nearBoundary = distance <= maxOf(NEAR_PLACE_MIN_DISTANCE_M, place.radiusM * 3f)
            nearBoundary && (!inside || movingRecently)
        }

        return when {
            closeToSavedPlace -> TrackingMode.NEAR_PLACE
            movingRecently -> TrackingMode.MOVING
            else -> TrackingMode.STILL
        }
    }

    private fun heartbeatInterval(mode: TrackingMode): Long =
        when (mode) {
            TrackingMode.LIVE -> LIVE_HEARTBEAT_MS
            TrackingMode.MOVING -> MOVING_HEARTBEAT_MS
            TrackingMode.STILL -> STILL_HEARTBEAT_MS
            TrackingMode.NEAR_PLACE -> NEAR_PLACE_HEARTBEAT_MS
        }

    private fun distanceToPlace(location: Location, place: SyncFamilyPlace): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            place.latitude,
            place.longitude,
            result,
        )
        return result[0]
    }

    private fun currentSession(): FamilySession? {
        val prefs = getSharedPreferences(FAMILY_PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString("family_id", null) ?: return null
        val name = prefs.getString("family_name", "Min familj") ?: "Min familj"
        val code = prefs.getString("family_code", null) ?: return null
        if (code.isBlank()) return null
        return FamilySession(id, name, code)
    }

    private fun batteryPercent(): Int? =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Platsdelning i bakgrunden",
                    NotificationManager.IMPORTANCE_LOW,
                )
                    .apply {
                        description =
                            "Håller familjens platsdelning uppdaterad även när appen inte är öppen."
                    }
            )
        }
    }

    private fun buildNotification(): Notification {
        val openApp =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
                    else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, openApp, pendingFlags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_calendar)
            .setContentTitle("Familjekalendern")
            .setContentText("Smart platsdelning aktiv i bakgrunden")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
