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
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"
private val STOCKHOLM = ZoneId.of("Europe/Stockholm")
internal const val ALL_FAMILY_MEMBER_ID = "__all_family__"

data class FamilySession(val id: String, val name: String, val code: String)
data class SyncMember(val id: String, val name: String, val role: String, val colorArgb: Long)
data class SyncShoppingItem(val id: String, val name: String, val checked: Boolean)

data class MailOffer(
    val id: String,
    val store: String,
    val productName: String,
    val normalizedProduct: String,
    val price: Double,
    val unitText: String?,
    val validUntil: LocalDate?,
    val sourceSubject: String?,
    val createdAt: String?
)
data class SyncEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: String,
    val memberId: String?,
    val source: String,
    val endDate: LocalDate? = null,
    val endTime: String? = null,
    val seriesId: String? = null
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
            add(SyncMember(ALL_FAMILY_MEMBER_ID, "⭐ Alla", "Hela familjen", 0xFFFFD75E))
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
        if (memberId == ALL_FAMILY_MEMBER_ID) return@withContext
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
        if (!item.checked) {
            val normalized = normalizeShoppingName(item.name)
            if (normalized.isNotBlank()) {
                val historyBody = JSONObject()
                    .put("p_family_id", session.id)
                    .put("p_name", item.name.trim())
                    .put("p_normalized", normalized)
                request("POST", "/rest/v1/rpc/record_shopping_purchase", historyBody, session.code, preferRepresentation = false)
            }
        }
    }

    suspend fun clearChecked(session: FamilySession) = withContext(Dispatchers.IO) {
        request("DELETE", "/rest/v1/shopping_items?family_id=eq.${session.id}&checked=eq.true", familyCode = session.code, preferRepresentation = false)
    }

    suspend fun loadEvents(session: FamilySession): List<SyncEvent> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/calendar_events?select=id,title,starts_at,ends_at,member_id,source,series_id&family_id=eq.${session.id}&order=starts_at.asc",
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
                add(
                    SyncEvent(
                        id = row.getString("id"),
                        title = row.getString("title"),
                        date = zoned.toLocalDate(),
                        time = "%02d:%02d".format(zoned.hour, zoned.minute),
                        memberId = if (row.isNull("member_id")) ALL_FAMILY_MEMBER_ID else row.getString("member_id"),
                        source = row.optString("source", "manual"),
                        endDate = endZoned?.toLocalDate(),
                        endTime = endZoned?.let { "%02d:%02d".format(it.hour, it.minute) },
                        seriesId = if (row.isNull("series_id")) null else row.getString("series_id")
                    )
                )
            }
        }
    }

    suspend fun addEvent(
        session: FamilySession,
        title: String,
        date: LocalDate,
        time: String,
        memberId: String?
    ) = withContext(Dispatchers.IO) {
        val parsedTime = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        val startsAt = ZonedDateTime.of(date, parsedTime, STOCKHOLM).toOffsetDateTime().toString()
        val body = JSONObject()
            .put("family_id", session.id)
            .put("title", title)
            .put("starts_at", startsAt)
            .put("source", "manual")
        if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
        request("POST", "/rest/v1/calendar_events", body, session.code, preferRepresentation = false)
    }

    suspend fun addEvent(
        session: FamilySession,
        title: String,
        date: LocalDate,
        startTime: String,
        endTime: String?,
        memberId: String?,
        seriesId: String? = null
    ) = withContext(Dispatchers.IO) {
        val parsedStart = runCatching { LocalTime.parse(startTime) }.getOrElse { LocalTime.of(18, 0) }
        val starts = ZonedDateTime.of(date, parsedStart, STOCKHOLM)
        val body = JSONObject()
            .put("family_id", session.id)
            .put("title", title)
            .put("starts_at", starts.toOffsetDateTime().toString())
            .put("source", "manual")
        val parsedEnd = endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (parsedEnd != null) {
            var ends = ZonedDateTime.of(date, parsedEnd, STOCKHOLM)
            if (!ends.isAfter(starts)) ends = ends.plusDays(1)
            body.put("ends_at", ends.toOffsetDateTime().toString())
        }
        if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
        if (seriesId != null) body.put("series_id", seriesId)
        request("POST", "/rest/v1/calendar_events", body, session.code, preferRepresentation = false)
    }

    suspend fun updateEvent(
        session: FamilySession,
        eventId: String,
        title: String,
        date: LocalDate,
        time: String,
        endTime: String?,
        memberId: String?
    ) = withContext(Dispatchers.IO) {
        val parsedTime = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        val startsAt = ZonedDateTime.of(date, parsedTime, STOCKHOLM)
        val parsedEnd = endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val endsAt = parsedEnd?.let { value ->
            ZonedDateTime.of(if (value.isAfter(parsedTime)) date else date.plusDays(1), value, STOCKHOLM)
        }
        val body = JSONObject()
            .put("title", title)
            .put("starts_at", startsAt.toOffsetDateTime().toString())
            .put("updated_at", OffsetDateTime.now().toString())
        if (endsAt == null) body.put("ends_at", JSONObject.NULL) else body.put("ends_at", endsAt.toOffsetDateTime().toString())
        if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
        request(
            "PATCH",
            "/rest/v1/calendar_events?id=eq.$eventId&family_id=eq.${session.id}",
            body,
            session.code,
            preferRepresentation = false
        )
    }

    suspend fun updateEventSeriesId(
        session: FamilySession,
        eventId: String,
        seriesId: String?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("series_id", seriesId ?: JSONObject.NULL)
        request(
            "PATCH",
            "/rest/v1/calendar_events?id=eq.$eventId&family_id=eq.${session.id}",
            body,
            session.code,
            preferRepresentation = false
        )
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
            val start = parseIcsDateTime(rawStart) ?: continue
            val rawEnd = lines.firstOrNull { it.startsWith("DTEND") }?.substringAfter(':')
            val end = rawEnd?.let(::parseIcsDateTime)
            val body = JSONObject()
                .put("family_id", session.id)
                .put("title", unescapeIcs(title))
                .put("starts_at", start.toOffsetDateTime().toString())
                .put("source", "sportadmin")
                .put("external_id", uid)
            if (end != null && end.isAfter(start)) body.put("ends_at", end.toOffsetDateTime().toString())
            if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
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


    suspend fun loadMailOffers(session: FamilySession): List<MailOffer> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/mail_offers?select=id,store,product_name,normalized_product,price,unit_text,valid_until,source_subject,created_at&family_id=eq.${session.id}&order=created_at.desc&limit=200",
            familyCode = session.code
        )
        val array = JSONArray(result)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                val validUntil = if (row.isNull("valid_until")) null else runCatching { LocalDate.parse(row.getString("valid_until")) }.getOrNull()
                if (validUntil == null || !validUntil.isBefore(LocalDate.now())) {
                    add(
                        MailOffer(
                            id = row.getString("id"),
                            store = row.optString("store", "Butik"),
                            productName = row.optString("product_name"),
                            normalizedProduct = row.optString("normalized_product"),
                            price = row.optDouble("price"),
                            unitText = row.optString("unit_text").takeIf { it.isNotBlank() && it != "null" },
                            validUntil = validUntil,
                            sourceSubject = row.optString("source_subject").takeIf { it.isNotBlank() && it != "null" },
                            createdAt = row.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
        }
    }

    suspend fun loadShoppingInterestTerms(session: FamilySession): List<String> = withContext(Dispatchers.IO) {
        val names = linkedSetOf<String>()
        runCatching {
            val shopping = JSONArray(
                request(
                    "GET",
                    "/rest/v1/shopping_items?select=name&family_id=eq.${session.id}&order=updated_at.desc&limit=100",
                    familyCode = session.code
                )
            )
            repeat(shopping.length()) { index ->
                shopping.getJSONObject(index).optString("name").trim().takeIf(String::isNotBlank)?.let(names::add)
            }
        }
        runCatching {
            val history = JSONArray(
                request(
                    "GET",
                    "/rest/v1/shopping_history?select=display_name,purchase_count,last_purchased_at&family_id=eq.${session.id}&order=purchase_count.desc,last_purchased_at.desc&limit=100",
                    familyCode = session.code
                )
            )
            repeat(history.length()) { index ->
                history.getJSONObject(index).optString("display_name").trim().takeIf(String::isNotBlank)?.let(names::add)
            }
        }
        names.toList()
    }

    suspend fun upsertMailOffer(
        session: FamilySession,
        store: String,
        productName: String,
        normalizedProduct: String,
        price: Double,
        unitText: String?,
        validUntil: LocalDate?,
        sourceSubject: String?,
        sourceMessageId: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("family_id", session.id)
            .put("store", store.take(120))
            .put("product_name", productName.take(200))
            .put("normalized_product", normalizedProduct.take(200))
            .put("price", price)
            .put("source_message_id", sourceMessageId.take(200))
        if (!unitText.isNullOrBlank()) body.put("unit_text", unitText.take(40))
        if (validUntil != null) body.put("valid_until", validUntil.toString())
        if (!sourceSubject.isNullOrBlank()) body.put("source_subject", sourceSubject.take(300))
        request(
            "POST",
            "/rest/v1/mail_offers?on_conflict=family_id,source_message_id,normalized_product,price",
            body,
            session.code,
            preferRepresentation = false,
            preferExtra = "resolution=merge-duplicates"
        )
    }

    suspend fun upsertMailEvent(
        session: FamilySession,
        externalId: String,
        title: String,
        startsAt: OffsetDateTime,
        endsAt: OffsetDateTime?,
        location: String?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("family_id", session.id)
            .put("title", title.ifBlank { "Mailinbjudan" }.take(300))
            .put("starts_at", startsAt.toString())
            .put("source", "mail")
            .put("external_id", externalId.take(200))
            .put("member_id", JSONObject.NULL)
        if (endsAt != null && endsAt.isAfter(startsAt)) body.put("ends_at", endsAt.toString())
        if (!location.isNullOrBlank()) body.put("location", location.take(400))
        request(
            "POST",
            "/rest/v1/calendar_events?on_conflict=family_id,source,external_id",
            body,
            session.code,
            preferRepresentation = false,
            preferExtra = "resolution=merge-duplicates"
        )
    }

    private fun normalizeShoppingName(value: String): String = value
        .lowercase(java.util.Locale("sv", "SE"))
        .replace(Regex("[^a-z0-9åäö]+"), " ")
        .trim()

    private fun valueFor(lines: List<String>, key: String): String? =
        lines.firstOrNull { it.startsWith("$key:") || it.startsWith("$key;") }?.substringAfter(':')

    private fun unescapeIcs(value: String): String = value
        .replace("\\n", " ")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    private fun parseIcsDateTime(raw: String): ZonedDateTime? = runCatching {
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
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = true
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
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
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            if (!familyCode.isNullOrBlank()) {
                connection.setRequestProperty("x-family-code", familyCode.uppercase())
            }
            val prefer = buildList {
                if (preferRepresentation) {
                    add("return=representation")
                } else {
                    add("return=minimal")
                }
                if (!preferExtra.isNullOrBlank()) {
                    add(preferExtra)
                }
            }.joinToString(",")
            connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Serverfel $code: $text")
            }
            text.ifBlank { "[]" }
        } finally {
            connection.disconnect()
        }
    }
}
