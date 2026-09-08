package se.familjekalender.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.YearMonth

private const val CALENDAR_BATCH_URL = "https://zigychfkpgypjuovgyqq.supabase.co/functions/v1/calendar-batch"

data class WorkRule(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String
)

fun currentFamilySession(context: Context): FamilySession? {
    val prefs = context.getSharedPreferences("family_calendar", 0)
    val id = prefs.getString("family_id", null) ?: return null
    val name = prefs.getString("family_name", "Min familj") ?: "Min familj"
    val code = prefs.getString("family_code", "") ?: ""
    if (code.isBlank()) return null
    return FamilySession(id, name, code)
}

suspend fun saveWorkMonth(
    session: FamilySession,
    month: YearMonth,
    title: String,
    memberId: String?,
    rules: List<WorkRule>
): Int = withContext(Dispatchers.IO) {
    val body = JSONObject()
        .put("action", "save_work_month")
        .put("familyId", session.id)
        .put("familyCode", session.code)
        .put("year", month.year)
        .put("month", month.monthValue)
        .put("title", title.ifBlank { "Jobb" })
        .put("memberId", memberId ?: JSONObject.NULL)
        .put("rules", JSONArray().apply {
            rules.forEach { rule ->
                put(JSONObject()
                    .put("weekdays", JSONArray(rule.weekdays.sorted()))
                    .put("start", rule.startTime)
                    .put("end", rule.endTime))
            }
        })
    val response = calendarBatchRequest(body)
    JSONObject(response).optInt("created", 0)
}

suspend fun deleteWorkMonth(
    session: FamilySession,
    month: YearMonth,
    memberId: String?
): Int = withContext(Dispatchers.IO) {
    val body = JSONObject()
        .put("action", "delete_work_month")
        .put("familyId", session.id)
        .put("familyCode", session.code)
        .put("year", month.year)
        .put("month", month.monthValue)
        .put("memberId", memberId ?: JSONObject.NULL)
    val response = calendarBatchRequest(body)
    JSONObject(response).optInt("deleted", 0)
}

suspend fun deleteCalendarEvent(
    session: FamilySession,
    eventId: String
): Int = withContext(Dispatchers.IO) {
    val body = JSONObject()
        .put("action", "delete_event")
        .put("familyId", session.id)
        .put("familyCode", session.code)
        .put("eventId", eventId)
    val response = calendarBatchRequest(body)
    JSONObject(response).optInt("deleted", 0)
}

private fun calendarBatchRequest(body: JSONObject): String {
    val connection = URL(CALENDAR_BATCH_URL).openConnection() as HttpURLConnection
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
