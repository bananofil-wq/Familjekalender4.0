package se.familjekalender.app

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val EVENT_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val EVENT_SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJIUzI1NiIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"
private val EVENT_STOCKHOLM = ZoneId.of("Europe/Stockholm")

/**
 * Full event writer used by the activity dialog. Unlike the old compatibility shim, this persists
 * both starts_at and ends_at so planning/conflict logic can use the actual duration instead of
 * silently throwing the selected end time away.
 */
suspend fun SupabaseSync.addEvent(
    session: FamilySession,
    title: String,
    date: LocalDate,
    startTime: String,
    endTime: String?,
    memberId: String?,
) =
    withContext(Dispatchers.IO) {
        val parsedStart = runCatching {
            LocalTime.parse(startTime)
        }.getOrElse { LocalTime.of(18, 0) }
        val parsedEnd = endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val start = ZonedDateTime.of(date, parsedStart, EVENT_STOCKHOLM)
        val end = parsedEnd?.let { value ->
            ZonedDateTime.of(
                if (value.isAfter(parsedStart)) date else date.plusDays(1),
                value,
                EVENT_STOCKHOLM,
            )
        }

        val body =
            JSONObject()
                .put("family_id", session.id)
                .put("title", title)
                .put("starts_at", start.toOffsetDateTime().toString())
                .put("source", "manual")
        if (end != null) body.put("ends_at", end.toOffsetDateTime().toString())
        if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) {
            body.put("member_id", JSONObject.NULL)
        } else {
            body.put("member_id", memberId)
        }

        val connection =
            URL("$EVENT_SUPABASE_URL/rest/v1/calendar_events").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", EVENT_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $EVENT_SUPABASE_ANON_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-family-code", session.code.uppercase())
        connection.setRequestProperty("Prefer", "return=minimal")
        connection.doOutput = true
        connection.outputStream.use {
            it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("Serverfel $code: $text")
        }
        Unit
    }
