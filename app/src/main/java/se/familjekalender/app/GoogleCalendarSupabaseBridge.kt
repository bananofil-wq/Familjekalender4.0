package se.familjekalender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZonedDateTime

private const val GOOGLE_SYNC_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val GOOGLE_SYNC_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

suspend fun SupabaseSync.upsertExternalEvent(
    session: FamilySession,
    source: String,
    externalId: String,
    title: String,
    startsAt: ZonedDateTime,
    endsAt: ZonedDateTime?,
    memberId: String?
) = withContext(Dispatchers.IO) {
    val body = JSONObject()
        .put("family_id", session.id)
        .put("title", title)
        .put("starts_at", startsAt.toOffsetDateTime().toString())
        .put("source", source)
        .put("external_id", externalId)
        .put("updated_at", OffsetDateTime.now().toString())

    if (endsAt != null && endsAt.isAfter(startsAt)) {
        body.put("ends_at", endsAt.toOffsetDateTime().toString())
    } else {
        body.put("ends_at", JSONObject.NULL)
    }

    if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) {
        body.put("member_id", JSONObject.NULL)
    } else {
        body.put("member_id", memberId)
    }

    val connection = URL(
        "$GOOGLE_SYNC_SUPABASE_URL/rest/v1/calendar_events?on_conflict=family_id,source,external_id"
    ).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 15000
    connection.readTimeout = 20000
    connection.setRequestProperty("apikey", GOOGLE_SYNC_SUPABASE_ANON_KEY)
    connection.setRequestProperty("Authorization", "Bearer $GOOGLE_SYNC_SUPABASE_ANON_KEY")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("x-family-code", session.code.uppercase())
    connection.setRequestProperty("Prefer", "return=minimal,resolution=merge-duplicates")
    connection.doOutput = true
    connection.outputStream.use {
        it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
    }

    val code = connection.responseCode
    if (code !in 200..299) {
        val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw IllegalStateException("Google Kalender kunde inte sparas: $code $error")
    }
    connection.inputStream?.close()
}
