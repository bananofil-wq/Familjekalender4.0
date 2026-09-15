package se.familjekalender.app

import android.Manifest
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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RunRecordingService : Service() {
    companion object {
        private const val ACTION_START = "se.familjekalender.app.RUN_START"
        private const val ACTION_STOP = "se.familjekalender.app.RUN_STOP"
        private const val EXTRA_MEMBER_ID = "member_id"
        private const val PREFS = "run_recording_state"
        private const val CHANNEL_ID = "run_recording"
        private const val NOTIFICATION_ID = 73042

        fun start(context: Context, memberId: String) {
            val intent = Intent(context, RunRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MEMBER_ID, memberId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun finish(context: Context) {
            val intent = Intent(context, RunRecordingService::class.java).apply { action = ACTION_STOP }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun snapshot(context: Context): RunRecordingSnapshot {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return RunRecordingSnapshot(
                active = prefs.getBoolean("active", false),
                memberId = prefs.getString("member_id", null),
                startedAtMillis = prefs.getLong("started_at", 0L),
                points = decodePoints(prefs.getString("points", "[]") ?: "[]"),
                lastFinishedRunId = prefs.getString("last_finished_run_id", null)
            )
        }

        private fun decodePoints(raw: String): List<RecordedRoutePoint> = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val p = array.getJSONObject(i)
                    add(RecordedRoutePoint(
                        latitude = p.getDouble("lat"),
                        longitude = p.getDouble("lon"),
                        timestampMillis = p.getLong("time"),
                        accuracyMeters = p.optDouble("accuracy", Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
                        altitudeMeters = p.optDouble("altitude", Double.NaN).takeUnless { it.isNaN() }
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private lateinit var locationManager: LocationManager
    private var memberId: String? = null
    private var startedAtMillis: Long = 0L
    private val points = mutableListOf<RecordedRoutePoint>()
    private var lastLocation: Location? = null
    private var lastNotificationAt = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = accept(location)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startForeground(NOTIFICATION_ID, notification("Förbereder GPS…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent.getStringExtra(EXTRA_MEMBER_ID))
            ACTION_STOP -> complete()
            else -> restoreIfActive()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(listener) }
        super.onDestroy()
    }

    private fun begin(owner: String?) {
        if (owner.isNullOrBlank() || !hasPermission()) {
            stopSelf()
            return
        }
        memberId = owner
        startedAtMillis = System.currentTimeMillis()
        points.clear()
        lastLocation = null
        persist(active = true)
        requestUpdates()
        updateNotification(force = true)
    }

    private fun restoreIfActive() {
        val state = snapshot(this)
        if (!state.active || state.memberId.isNullOrBlank() || !hasPermission()) {
            stopSelf()
            return
        }
        memberId = state.memberId
        startedAtMillis = state.startedAtMillis
        points.clear()
        points.addAll(state.points)
        requestUpdates()
        updateNotification(force = true)
    }

    private fun requestUpdates() {
        if (!hasPermission()) return
        runCatching { locationManager.removeUpdates(listener) }
        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 2f, listener)
            }
        }
        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3_000L, 5f, listener)
            }
        }
    }

    private fun accept(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 45f) return
        if (location.time > 0L && System.currentTimeMillis() - location.time > 30_000L) return

        val previous = lastLocation
        if (previous != null) {
            val dtSeconds = ((location.time.takeIf { it > 0 } ?: System.currentTimeMillis()) -
                (previous.time.takeIf { it > 0 } ?: System.currentTimeMillis())) / 1000.0
            if (dtSeconds > 0.0) {
                val speed = previous.distanceTo(location) / dtSeconds
                if (speed > 12.0) return
            }
            if (previous.distanceTo(location) < 1.5f) return
        }

        val point = RecordedRoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            timestampMillis = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() }
        )
        points += point
        lastLocation = Location(location)
        persist(active = true)
        updateNotification()
    }

    private fun complete() {
        val owner = memberId ?: snapshot(this).memberId
        val state = snapshot(this)
        val started = startedAtMillis.takeIf { it > 0 } ?: state.startedAtMillis
        val route = if (points.isNotEmpty()) points.toList() else state.points
        if (!owner.isNullOrBlank() && started > 0L) {
            val run = RecordedRun(
                id = UUID.randomUUID().toString(),
                memberId = owner,
                startedAtMillis = started,
                finishedAtMillis = System.currentTimeMillis(),
                points = route
            )
            RecordedRunStore(this).save(run)
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("active", false)
                .putString("last_finished_run_id", run.id)
                .apply()
        } else {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("active", false).apply()
        }
        runCatching { locationManager.removeUpdates(listener) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persist(active: Boolean) {
        val encoded = JSONArray().apply {
            points.forEach { p -> put(JSONObject().apply {
                put("lat", p.latitude); put("lon", p.longitude); put("time", p.timestampMillis)
                p.accuracyMeters?.let { put("accuracy", it.toDouble()) }
                p.altitudeMeters?.let { put("altitude", it) }
            }) }
        }.toString()
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("active", active)
            .putString("member_id", memberId)
            .putLong("started_at", startedAtMillis)
            .putString("points", encoded)
            .apply()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun updateNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAt < 5_000L) return
        lastNotificationAt = now
        val distanceKm = runDistanceKm(points)
        val elapsed = ((now - startedAtMillis).coerceAtLeast(0L) / 1000L)
        val text = "%.2f km · %02d:%02d:%02d".format(distanceKm, elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_calendar)
        .setContentTitle("Löprunda spelas in")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        ))
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Löpinspelning", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visas medan Familjekalendern spelar in en löprunda med GPS."
                }
            )
        }
    }
}

data class RunRecordingSnapshot(
    val active: Boolean,
    val memberId: String?,
    val startedAtMillis: Long,
    val points: List<RecordedRoutePoint>,
    val lastFinishedRunId: String?
)

fun runDistanceKm(points: List<RecordedRoutePoint>): Double {
    if (points.size < 2) return 0.0
    var meters = 0.0
    val result = FloatArray(1)
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        meters += result[0]
    }
    return meters / 1000.0
}
