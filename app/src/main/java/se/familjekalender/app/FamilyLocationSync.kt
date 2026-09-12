package se.familjekalender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime

private const val LOCATION_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val LOCATION_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

data class SyncFamilyLocation(
    val memberId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val batteryPercent: Int?,
    val updatedAt: String
)

data class SyncFamilyPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val arrivalAlerts: Boolean,
    val departureAlerts: Boolean
)

object FamilyLocationSync {
    suspend fun loadLocations(session: FamilySession): List<SyncFamilyLocation> = withContext(Dispatchers.IO) {
        val text = request(
            "GET",
            "/rest/v1/family_locations?select=member_id,latitude,longitude,accuracy_m,battery_percent,updated_at&family_id=eq.${session.id}&sharing_enabled=eq.true&order=updated_at.desc",
            familyCode = session.code
        )
        val array = JSONArray(text)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(
                    SyncFamilyLocation(
                        memberId = row.getString("member_id"),
                        latitude = row.getDouble("latitude"),
                        longitude = row.getDouble("longitude"),
                        accuracyM = if (row.isNull("accuracy_m")) null else row.getDouble("accuracy_m").toFloat(),
                        batteryPercent = if (row.isNull("battery_percent")) null else row.getInt("battery_percent"),
                        updatedAt = row.getString("updated_at")
                    )
                )
            }
        }
    }

    suspend fun publishLocation(
        session: FamilySession,
        memberId: String,
        latitude: Double,
        longitude: Double,
        accuracyM: Float?,
        batteryPercent: Int?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("family_id", session.id)
            .put("member_id", memberId)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("sharing_enabled", true)
            .put("updated_at", OffsetDateTime.now().toString())
        accuracyM?.let { body.put("accuracy_m", it) }
        batteryPercent?.let { body.put("battery_percent", it) }
        request(
            "POST",
            "/rest/v1/family_locations?on_conflict=family_id,member_id",
            body,
            session.code,
            preferRepresentation = false,
            preferExtra = "resolution=merge-duplicates"
        )
    }

    suspend fun stopSharing(session: FamilySession, memberId: String) = withContext(Dispatchers.IO) {
        request(
            "PATCH",
            "/rest/v1/family_locations?family_id=eq.${session.id}&member_id=eq.$memberId",
            JSONObject().put("sharing_enabled", false).put("updated_at", OffsetDateTime.now().toString()),
            session.code,
            preferRepresentation = false
        )
    }

    suspend fun loadPlaces(session: FamilySession): List<SyncFamilyPlace> = withContext(Dispatchers.IO) {
        val text = request(
            "GET",
            "/rest/v1/family_places?select=id,name,latitude,longitude,radius_m,arrival_alerts,departure_alerts&family_id=eq.${session.id}&order=created_at.asc",
            familyCode = session.code
        )
        val array = JSONArray(text)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(
                    SyncFamilyPlace(
                        id = row.getString("id"),
                        name = row.getString("name"),
                        latitude = row.getDouble("latitude"),
                        longitude = row.getDouble("longitude"),
                        radiusM = row.getInt("radius_m"),
                        arrivalAlerts = row.getBoolean("arrival_alerts"),
                        departureAlerts = row.getBoolean("departure_alerts")
                    )
                )
            }
        }
    }

    suspend fun addPlace(
        session: FamilySession,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusM: Int = 150
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("family_id", session.id)
            .put("name", name)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("radius_m", radiusM)
            .put("arrival_alerts", true)
            .put("departure_alerts", true)
        request("POST", "/rest/v1/family_places", body, session.code, preferRepresentation = false)
    }

    suspend fun updatePlaceAlerts(session: FamilySession, place: SyncFamilyPlace, arrival: Boolean, departure: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("arrival_alerts", arrival)
            .put("departure_alerts", departure)
            .put("updated_at", OffsetDateTime.now().toString())
        request("PATCH", "/rest/v1/family_places?id=eq.${place.id}&family_id=eq.${session.id}", body, session.code, preferRepresentation = false)
    }

    suspend fun deletePlace(session: FamilySession, placeId: String) = withContext(Dispatchers.IO) {
        request("DELETE", "/rest/v1/family_places?id=eq.$placeId&family_id=eq.${session.id}", familyCode = session.code, preferRepresentation = false)
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        familyCode: String? = null,
        preferRepresentation: Boolean = true,
        preferExtra: String? = null
    ): String {
        val connection = URL("$LOCATION_SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", LOCATION_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $LOCATION_SUPABASE_ANON_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        if (!familyCode.isNullOrBlank()) connection.setRequestProperty("x-family-code", familyCode.uppercase())
        val prefer = buildList {
            add(if (preferRepresentation) "return=representation" else "return=minimal")
            if (!preferExtra.isNullOrBlank()) add(preferExtra)
        }.joinToString(",")
        connection.setRequestProperty("Prefer", prefer)
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Serverfel $code: $text")
        return text.ifBlank { "[]" }
    }
}
