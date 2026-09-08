package se.familjekalender.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXAiLCJyZWYiOiJ6aWd5Y2hma3BneXBqdW92Z3lxcSIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzg4NjUyNjc0LCJleHAiOjIxMDQyMjg2NzR9.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"
private val STOCKHOLM = ZoneId.of("Europe/Stockholm")

data class FamilySession(val id: String, val name: String, val code: String)
data class SyncMember(val id: String, val name: String, val role: String, val colorArgb: Long)
data class SyncShoppingItem(val id: String, val name: String, val checked: Boolean)
data class SyncEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: String,
    val memberId: String?,
    val source: String
)

object SupabaseSync {
    suspend fun createFamily(name: String): FamilySession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("p_name", name.ifBlank { "Min familj" })
        val result = request("POST", "/rest/v1/rpc/create_family", body = body)
        val row = JSONArray(result).getJSONObject(0)
        FamilySession(row.getString("id"), row.getString("name"), row.getString("join_code"))
    }

    suspend fun joinFamily(code: String): FamilySession = withContext(Dispatchers.IO) {
        val clean = code.trim().uppercase()
        val result = request(
            "GET",
            "/rest/v1/families?select=id,name,join_code&limit=1",
            familyCode = clean
        )
        val array = JSONArray(result)
        require(array.length() > 0) { "Familjekoden hittades inte" }
        val row = array.getJSONObject(0)
        FamilySession(row.getString("id"), row.getString("name"), row.getString("join_code"))
    }

    suspend fun loadMembers(session: FamilySession): List<SyncMember> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/family_members?select=id,name,role,color_argb&family_id=eq.${session.id}&order=updated_at.asc",
            familyCode = session.code
        )
        val array = JSONArray(result)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(SyncMember(row.getString("id"), row.getString("name"), row.optString("role"), row.getLong("color_argb")))
            }
        }
    }

    suspend fun addMember(session: FamilySession, name: String, role: String, colorArgb: Long) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("family_id", session.id)
            .put("name", name)
            .put("role", role)
            .put("color_argb", colorArgb)
        request("POST", "/rest/v1/family_members", body, session.code, preferRepresentation = false)
    }

    suspend fun updateMemberColor(session: FamilySession, memberId: String, colorArgb: Long) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("color_argb", colorArgb)
            .put("updated_at", OffsetDateTime.now().toString())
        request(
            "PATCH",
            "/rest/v1/family_members?id=eq.$memberId&family_id=eq.${session.id}",
            body,
            session.code,
            preferRepresentation = false
        )
    }

    suspend fun loadShopping(session: FamilySession): List<SyncShoppingItem> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/shopping_items?select=id,name,checked&family_id=eq.${session.id}&order=updated_at.asc",
            familyCode = session.code
        )
        val array = JSONArray(result)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(SyncShoppingItem(row.getString("id"), row.getString("name"), row.getBoolean("checked")))
            }
        }
    }

    suspend fun addShopping(session: FamilySession, name: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("family_id", session.id).put("name", name).put("checked", false)
        request("POST", "/rest/v1/shopping_items", body, session.code, preferRepresentation = false)
    }

    suspend fun toggleShopping(session: FamilySession, item: SyncShoppingItem) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("checked", !item.checked).put("updated_at", OffsetDateTime.now().toString())
        request("PATCH", "/rest/v1/shopping_items?id=eq.${item.id}", body, session.code, preferRepresentation = false)
    }

    suspend fun clearChecked(session: FamilySession) = withContext(Dispatchers.IO) {
        request("DELETE", "/rest/v1/shopping_items?family_id=eq.${session.id}&checked=eq.true", familyCode = session.code, preferRepresentation = false)
    }

    suspend fun loadEvents(session: FamilySession): List<SyncEvent> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/calendar_events?select=id,title,starts_at,ends_at,member_id,source&family_id=eq.${session.id}&order=starts_at.asc",
            familyCode = session.code
        )
        val array = JSONArray(result)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                val zoned = OffsetDateTime.parse(row.getString("starts_at")).atZoneSameInstant(STOCKHOLM)
                val endZoned = if (row.isNull("ends_at")) null else runCatching {
                    OffsetDateTime.parse(row.getString("ends_at")).atZoneSameInstant(STOCKHOLM)
                }.getOrNull()
                val startText = "%02d:%02d".format(zoned.hour, zoned.minute)
                val timeText = endZoned?.let { "$startText–%02d:%02d".format(it.hour, it.minute) } ?: startText
                add(
                    SyncEvent(
                        id = row.getString("id"),
                        title = row.getString("title"),
                        date = zoned.toLocalDate(),
                        time = timeText,
                        memberId = if (row.isNull("member_id")) null else row.getString("member_id"),
                        source = row.optString("source", "manual")
                    )
                )
            }
        }
    }

    suspend fun addEvent(
        session: FamilySession,
        title: String,
        date: LocalDate,
        startTime: String,
        endTime: String?,
        memberId: String?
    ) = withContext(Dispatchers.IO) {
        val parsedStart = runCatching { LocalTime.parse(startTime) }.getOrElse { LocalTime.of(18, 0) }
        val startsAt = ZonedDateTime.of(date, parsedStart, STOCKHOLM)
        val parsedEnd = endTime?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val endsAt = parsedEnd?.let {
            val endDate = if (it <= parsedStart) date.plusDays(1) else date
            ZonedDateTime.of(endDate, it, STOCKHOLM)
        }
        val body = JSONObject()
            .put("family_id", session.id)
            .put("title", title)
            .put("starts_at", startsAt.toOffsetDateTime().toString())
            .put("source", "manual")
        if (endsAt != null) body.put("ends_at", endsAt.toOffsetDateTime().toString())
        if (memberId == null) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
        request("POST", "/rest/v1/calendar_events", body, session.code, preferRepresentation = false)
    }

    suspend fun importSportAdmin(session: FamilySession, webcalUrl: String, memberId: String?): Int = withContext(Dispatchers.IO) {
        val text = fetchText(webcalUrl.trim())
        val unfolded = text.replace("\r\n ", "").replace("\n ", "")
        val blocks = unfolded.split("BEGIN:VEVENT").drop(1).mapNotNull { it.substringBefore("END:VEVENT", "").takeIf(String::isNotBlank) }
        var imported = 0
        for (block in blocks) {
            val lines = block.lines()
            val uid = valueFor(lines, "UID") ?: continue
            val title = valueFor(lines, "SUMMARY")?.ifBlank { "SportAdmin" } ?: "SportAdmin"
            val rawStart = lines.firstOrNull { it.startsWith("DTSTART") }?.substringAfter(':') ?: continue
            val start = parseIcsStart(rawStart) ?: continue
            val body = JSONObject()
                .put("family_id", session.id)
                .put("title", unescapeIcs(title))
                .put("starts_at", start.toOffsetDateTime().toString())
                .put("source", "sportadmin")
                .put("external_id", uid)
            val rawEnd = lines.firstOrNull { it.startsWith("DTEND") }?.substringAfter(':')
            val end = rawEnd?.let { parseIcsStart(it) }
            if (end != null) body.put("ends_at", end.toOffsetDateTime().toString())
            if (memberId == null) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
            val location = valueFor(lines, "LOCATION")
            if (!location.isNullOrBlank()) body.put("location", unescapeIcs(location))
            try {
                request("POST", "/rest/v1/calendar_events?on_conflict=family_id,source,external_id", body, session.code, preferRepresentation = false, preferExtra = "resolution=merge-duplicates")
                imported++
            } catch (_: Exception) {
            }
        }
        imported
    }

    private fun valueFor(lines: List<String>, key: String): String? =
        lines.firstOrNull { it.startsWith("$key:") || it.startsWith("$key;") }?.substringAfter(':')

    private fun unescapeIcs(value: String): String = value
        .replace("\\n", " ")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    private fun parseIcsStart(raw: String): ZonedDateTime? = runCatching {
        when {
            raw.endsWith("Z") && raw.length >= 16 -> ZonedDateTime.parse(
                raw,
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
            ).withZoneSameInstant(STOCKHOLM)
            raw.length >= 15 -> ZonedDateTime.of(
                java.time.LocalDateTime.parse(raw.take(15), java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")),
                STOCKHOLM
            )
            raw.length == 8 -> ZonedDateTime.of(LocalDate.parse(raw, java.time.format.DateTimeFormatter.BASIC_ISO_DATE), LocalTime.NOON, STOCKHOLM)
            else -> return null
        }
    }.getOrNull()

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = true
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        familyCode: String? = null,
        preferRepresentation: Boolean = true,
        preferExtra: String? = null
    ): String {
        val connection = URL("$SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        if (!familyCode.isNullOrBlank()) connection.setRequestProperty("x-family-code", familyCode.uppercase())
        val prefer = buildList {
            if (preferRepresentation) add("return=representation") else add("return=minimal")
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
