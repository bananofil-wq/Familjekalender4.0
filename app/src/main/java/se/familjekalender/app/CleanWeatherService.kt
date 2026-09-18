package se.familjekalender.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

internal data class CleanWeatherSnapshot(
    val temperatureC: Int,
    val description: String,
    val weatherCode: Int
)

internal object CleanWeatherService {
    private const val PREFS = "familjekalender_clean_weather"
    private const val CACHE_MAX_AGE_MS = 20 * 60_000L

    suspend fun loadCurrent(context: Context, force: Boolean = false): CleanWeatherSnapshot {
        if (!hasLocationPermission(context)) {
            error("Platsbehörighet krävs för vädret.")
        }

        if (!force) {
            cached(context)?.let { return it }
        }

        val location = currentLocation(context)
        val snapshot = fetch(location.latitude, location.longitude)
        saveCache(context, snapshot)
        return snapshot
    }

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private suspend fun currentLocation(context: Context): Location = withTimeout(10_000L) {
        suspendCancellableCoroutine { continuation ->
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            val cached = providers
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .filter { it.latitude.isFinite() && it.longitude.isFinite() }
                .maxByOrNull { it.time }

            if (cached != null && System.currentTimeMillis() - cached.time <= 60 * 60_000L) {
                continuation.resume(cached)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) {
                        runCatching { manager.removeUpdates(this) }
                        continuation.resume(location)
                    }
                }

                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }

            val enabledProviders = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }

            if (enabledProviders.isEmpty()) {
                if (cached != null) {
                    continuation.resume(cached)
                } else {
                    continuation.resumeWithException(IllegalStateException("Platstjänster är avstängda."))
                }
                return@suspendCancellableCoroutine
            }

            var requested = false
            enabledProviders.forEach { provider ->
                runCatching {
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    requested = true
                }
            }

            if (!requested) {
                if (cached != null) continuation.resume(cached)
                else continuation.resumeWithException(IllegalStateException("Kunde inte läsa aktuell plats."))
            }

            continuation.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
        }
    }

    private suspend fun fetch(latitude: Double, longitude: Double): CleanWeatherSnapshot = withContext(Dispatchers.IO) {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code&timezone=auto"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Vädertjänsten svarade med HTTP $code.")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(json).getJSONObject("current")
            val temperature = current.getDouble("temperature_2m").roundToInt()
            val weatherCode = current.optInt("weather_code", -1)
            CleanWeatherSnapshot(
                temperatureC = temperature,
                description = descriptionFor(weatherCode),
                weatherCode = weatherCode
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun cached(context: Context): CleanWeatherSnapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updatedAt = prefs.getLong("updated_at", 0L)
        if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > CACHE_MAX_AGE_MS) return null
        if (!prefs.contains("temperature")) return null
        return CleanWeatherSnapshot(
            temperatureC = prefs.getInt("temperature", 0),
            description = prefs.getString("description", "Väder") ?: "Väder",
            weatherCode = prefs.getInt("weather_code", -1)
        )
    }

    private fun saveCache(context: Context, snapshot: CleanWeatherSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("temperature", snapshot.temperatureC)
            .putString("description", snapshot.description)
            .putInt("weather_code", snapshot.weatherCode)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
    }

    private fun descriptionFor(code: Int): String = when (code) {
        0 -> "Klart"
        1 -> "Mestadels klart"
        2 -> "Växlande molnighet"
        3 -> "Mulet"
        45, 48 -> "Dimma"
        51, 53, 55 -> "Duggregn"
        56, 57 -> "Underkylt duggregn"
        61 -> "Lätt regn"
        63 -> "Regn"
        65 -> "Kraftigt regn"
        66, 67 -> "Underkylt regn"
        71 -> "Lätt snöfall"
        73 -> "Snöfall"
        75 -> "Kraftigt snöfall"
        77 -> "Snökorn"
        80 -> "Lätta regnskurar"
        81 -> "Regnskurar"
        82 -> "Kraftiga regnskurar"
        85, 86 -> "Snöbyar"
        95 -> "Åska"
        96, 99 -> "Åska med hagel"
        else -> "Aktuellt väder"
    }
}
