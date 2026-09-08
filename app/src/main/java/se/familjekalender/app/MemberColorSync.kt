package se.familjekalender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime

private const val COLOR_SYNC_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val COLOR_SYNC_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

internal suspend fun updateMemberColor(session: FamilySession, memberId: String, colorArgb: Long) = withContext(Dispatchers.IO) {
    val connection = URL("$COLOR_SYNC_URL/rest/v1/family_members?id=eq.$memberId&family_id=eq.${session.id}").openConnection() as HttpURLConnection
    connection.requestMethod = "PATCH"
    connection.connectTimeout = 15000
    connection.readTimeout = 20000
    connection.setRequestProperty("apikey", COLOR_SYNC_KEY)
    connection.setRequestProperty("Authorization", "Bearer $COLOR_SYNC_KEY")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("x-family-code", session.code.uppercase())
    connection.setRequestProperty("Prefer", "return=minimal")
    connection.doOutput = true
    val body = JSONObject()
        .put("color_argb", colorArgb)
        .put("updated_at", OffsetDateTime.now().toString())
    connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
    val code = connection.responseCode
    if (code !in 200..299) {
        val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw IllegalStateException("Kunde inte spara färgen ($code): $text")
    }
}
