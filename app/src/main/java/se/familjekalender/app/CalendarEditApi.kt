package se.familjekalender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

private const val CALENDAR_EDIT_FUNCTION_URL = "https://zigychfkpgypjuovgyqq.supabase.co/functions/v1/calendar-edit"

suspend fun updateCalendarEventMemberDirect(
    session: FamilySession,
    eventId: String,
    memberId: String?
): Int = withContext(Dispatchers.IO) {
    val body = JSONObject()
        .put("action", "update_member")
        .put("familyId", session.id)
        .put("familyCode", session.code)
        .put("eventId", eventId)
    if (memberId == null) body.put("memberId", JSONObject.NULL) else body.put("memberId", memberId)
    JSONObject(calendarEditRequest(body)).optInt("updated", 0)
}

suspend fun deleteCalendarEventsDirect(
    session: FamilySession,
    eventIds: List<String>
): Int = withContext(Dispatchers.IO) {
    val ids = eventIds.distinct().filter { it.isNotBlank() }
    if (ids.isEmpty()) return@withContext 0
    val body = JSONObject()
        .put("action", "delete_events")
        .put("familyId", session.id)
        .put("familyCode", session.code)
        .put("eventIds", JSONArray(ids))
    JSONObject(calendarEditRequest(body)).optInt("deleted", 0)
}

private fun calendarEditRequest(body: JSONObject): String {
    val connection = URL(CALENDAR_EDIT_FUNCTION_URL).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true
    connection.outputStream.use {
        it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
    }
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (code !in 200..299) throw IllegalStateException("Serverfel $code: $text")
    return text.ifBlank { "{}" }
}
