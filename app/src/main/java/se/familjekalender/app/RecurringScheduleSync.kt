package se.familjekalender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate

private const val RECURRING_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val RECURRING_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

data class ScheduleTemplate(
    val id: String,
    val name: String,
    val title: String,
    val weekdays: List<Int>,
    val startTime: String,
    val endTime: String?,
    val memberId: String?
)

data class SchedulePause(
    val id: String,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val scope: String,
    val seriesId: String?,
    val memberId: String?,
    val titlePrefix: String?
)

data class TemplateApplyResult(val created: Int, val skipped: Int, val paused: Int)

object RecurringScheduleSync {
    suspend fun loadTemplates(session: FamilySession): List<ScheduleTemplate> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/family_schedule_templates?select=id,name,title,weekdays,start_time,end_time,member_id&family_id=eq.${session.id}&order=created_at.asc",
            session.code
        )
        val array = JSONArray(result)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                val days = row.getJSONArray("weekdays")
                add(
                    ScheduleTemplate(
                        id = row.getString("id"),
                        name = row.getString("name"),
                        title = row.getString("title"),
                        weekdays = List(days.length()) { index -> days.getInt(index) },
                        startTime = row.getString("start_time").take(5),
                        endTime = if (row.isNull("end_time")) null else row.getString("end_time").take(5),
                        memberId = if (row.isNull("member_id")) ALL_FAMILY_MEMBER_ID else row.getString("member_id")
                    )
                )
            }
        }
    }

    suspend fun replaceTemplateFromWeek(
        session: FamilySession,
        templateName: String,
        events: List<SyncEvent>,
        weekStart: LocalDate
    ): Int = withContext(Dispatchers.IO) {
        request(
            "DELETE",
            "/rest/v1/family_schedule_templates?family_id=eq.${session.id}&name=eq.${encode(templateName)}",
            session.code
        )
        val weekEnd = weekStart.plusDays(6)
        val grouped = events
            .filter { it.source == "manual" && !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
            .groupBy { listOf(it.title, it.time, it.endTime ?: "", it.memberId ?: ALL_FAMILY_MEMBER_ID) }
        grouped.forEach { (_, rows) ->
            val first = rows.first()
            val weekdays = rows.map { it.date.dayOfWeek.value }.distinct().sorted()
            val body = JSONObject()
                .put("family_id", session.id)
                .put("name", templateName)
                .put("title", first.title)
                .put("weekdays", JSONArray(weekdays))
                .put("start_time", first.time)
            if (!first.endTime.isNullOrBlank()) body.put("end_time", first.endTime) else body.put("end_time", JSONObject.NULL)
            if (first.memberId == null || first.memberId == ALL_FAMILY_MEMBER_ID) body.put("member_id", JSONObject.NULL) else body.put("member_id", first.memberId)
            request("POST", "/rest/v1/family_schedule_templates", session.code, body)
        }
        grouped.size
    }

    private fun pauseMatches(pause: SchedulePause, date: LocalDate, template: ScheduleTemplate): Boolean {
        if (date.isBefore(pause.startsOn) || date.isAfter(pause.endsOn)) return false
        if (pause.scope == "all_schedules") return true
        val memberMatches = pause.memberId == null || pause.memberId == template.memberId
        val titleMatches = pause.titlePrefix.isNullOrBlank() || template.title.startsWith(pause.titlePrefix, ignoreCase = true)
        return memberMatches && titleMatches
    }

    suspend fun applyTemplateToWeek(
        session: FamilySession,
        templateName: String,
        targetWeekStart: LocalDate,
        existingEvents: List<SyncEvent>
    ): TemplateApplyResult {
        val templates = loadTemplates(session).filter { it.name == templateName }
        val pauses = loadPauses(session)
        var created = 0
        var skipped = 0
        var paused = 0
        templates.forEach { template ->
            template.weekdays.forEach { weekday ->
                val date = targetWeekStart.plusDays((weekday - 1).toLong())
                if (pauses.any { pauseMatches(it, date, template) }) {
                    paused++
                    return@forEach
                }
                val targetMember = template.memberId ?: ALL_FAMILY_MEMBER_ID
                val duplicate = existingEvents.any { event ->
                    val existingMember = event.memberId ?: ALL_FAMILY_MEMBER_ID
                    event.date == date &&
                        event.title == template.title &&
                        event.time == template.startTime &&
                        event.endTime == template.endTime &&
                        existingMember == targetMember
                }
                if (duplicate) {
                    skipped++
                } else {
                    val seriesId = java.util.UUID.nameUUIDFromBytes(
                        "${session.id}|template|${template.id}".toByteArray(StandardCharsets.UTF_8)
                    ).toString()
                    SupabaseSync.addEvent(
                        session,
                        template.title,
                        date,
                        template.startTime,
                        template.endTime,
                        template.memberId,
                        seriesId
                    )
                    created++
                }
            }
        }
        return TemplateApplyResult(created, skipped, paused)
    }

    suspend fun loadPauses(session: FamilySession): List<SchedulePause> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/family_schedule_pauses?select=id,starts_on,ends_on,scope,series_id,member_id,title_prefix&family_id=eq.${session.id}&order=starts_on.asc",
            session.code
        )
        val array = JSONArray(result)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(
                    SchedulePause(
                        id = row.getString("id"),
                        startsOn = LocalDate.parse(row.getString("starts_on")),
                        endsOn = LocalDate.parse(row.getString("ends_on")),
                        scope = row.getString("scope"),
                        seriesId = if (row.isNull("series_id")) null else row.getString("series_id"),
                        memberId = if (row.isNull("member_id")) null else row.getString("member_id"),
                        titlePrefix = if (row.isNull("title_prefix")) null else row.getString("title_prefix")
                    )
                )
            }
        }
    }


    private fun pauseMatchesEvent(pause: SchedulePause, event: SyncEvent): Boolean {
        if (event.seriesId.isNullOrBlank()) return false
        if (event.date.isBefore(pause.startsOn) || event.date.isAfter(pause.endsOn)) return false
        if (pause.scope == "all_schedules") return true
        if (pause.scope == "series" && !pause.seriesId.isNullOrBlank()) return pause.seriesId == event.seriesId
        val memberMatches = pause.memberId == null || pause.memberId == event.memberId
        val titleMatches = pause.titlePrefix.isNullOrBlank() || event.title.startsWith(pause.titlePrefix, ignoreCase = true)
        return memberMatches && titleMatches
    }

    suspend fun filterPausedScheduleEvents(
        session: FamilySession,
        events: List<SyncEvent>
    ): List<SyncEvent> {
        val pauses = loadPauses(session)
        if (pauses.isEmpty()) return events
        return events.filterNot { event -> pauses.any { pause -> pauseMatchesEvent(pause, event) } }
    }

    suspend fun addGlobalPause(session: FamilySession, startsOn: LocalDate, endsOn: LocalDate) = withContext(Dispatchers.IO) {
        require(!endsOn.isBefore(startsOn)) { "Slutdatum måste vara samma dag eller efter startdatum" }
        val body = JSONObject()
            .put("family_id", session.id)
            .put("starts_on", startsOn.toString())
            .put("ends_on", endsOn.toString())
            .put("scope", "all_schedules")
            .put("series_id", JSONObject.NULL)
            .put("member_id", JSONObject.NULL)
            .put("title_prefix", JSONObject.NULL)
        request("POST", "/rest/v1/family_schedule_pauses", session.code, body)
    }

    suspend fun deletePause(session: FamilySession, pauseId: String) = withContext(Dispatchers.IO) {
        request(
            "DELETE",
            "/rest/v1/family_schedule_pauses?id=eq.${encode(pauseId)}&family_id=eq.${session.id}",
            session.code
        )
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun request(method: String, path: String, familyCode: String, body: JSONObject? = null): String {
        val connection = URL("$RECURRING_SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", RECURRING_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $RECURRING_SUPABASE_ANON_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-family-code", familyCode.uppercase())
        connection.setRequestProperty("Prefer", "return=minimal")
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
