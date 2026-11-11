package se.familjekalender.app

import biweekly.Biweekly
import java.io.IOException
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"
private val STOCKHOLM = ZoneId.of("Europe/Stockholm")
internal const val ALL_FAMILY_MEMBER_ID = "__all_family__"

data class FamilySession(val id: String, val name: String, val code: String)

data class SyncMember(val id: String, val name: String, val role: String, val colorArgb: Long)

data class SyncShoppingItem(val id: String, val name: String, val checked: Boolean, val updatedAt: String? = null)

data class SportAdminImportResult(
    val imported: Int,
    val failed: Int,
    val firstError: String?,
) {
    val message: String
        get() =
            if (failed == 0) {
                "$imported importerade"
            } else {
                "$imported importerade, $failed misslyckades: ${firstError ?: "okänt fel"}"
            }
}

data class SyncEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: String,
    val memberId: String?,
    val source: String,
    val endDate: LocalDate? = null,
    val endTime: String? = null,
    val seriesId: String? = null,
    val updatedAt: String? = null,
)

object SupabaseSync {
    suspend fun createFamily(name: String): FamilySession =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("p_name", name.ifBlank { "Min familj" })
            val result =
                familyApiRequest(
                    method = "POST",
                    path = "/rest/v1/rpc/create_family",
                    body = body,
                )
            val row = JSONArray(result).getJSONObject(0)
            FamilySession(row.getString("id"), row.getString("name"), row.getString("join_code"))
        }

    suspend fun joinFamily(code: String): FamilySession =
        withContext(Dispatchers.IO) {
            val clean = code.trim().uppercase()
            val result =
                familyApiRequest(
                    method = "GET",
                    path = "/rest/v1/families?select=id,name,join_code&limit=1",
                    familyCode = clean,
                )
            val array = JSONArray(result)
            require(array.length() > 0) { "Familjekoden hittades inte" }
            val row = array.getJSONObject(0)
            FamilySession(row.getString("id"), row.getString("name"), row.getString("join_code"))
        }

    suspend fun loadMembers(session: FamilySession): List<SyncMember> =
        withContext(Dispatchers.IO) {
            val result =
                request(
                    "GET",
                    "/rest/v1/family_members?select=id,name,role,color_argb&family_id=eq.${session.id}&order=updated_at.asc",
                    familyCode = session.code,
                )
            val array = JSONArray(result)
            buildList {
                add(SyncMember(ALL_FAMILY_MEMBER_ID, "⭐ Alla", "Hela familjen", 0xFFFFD75E))
                repeat(array.length()) {
                    val row = array.getJSONObject(it)
                    add(
                        SyncMember(
                            row.getString("id"),
                            row.getString("name"),
                            row.optString("role"),
                            row.getLong("color_argb"),
                        )
                    )
                }
            }
        }

    suspend fun addMember(session: FamilySession, name: String, role: String, colorArgb: Long) =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("family_id", session.id)
                    .put("name", name)
                    .put("role", role)
                    .put("color_argb", colorArgb)
            request(
                "POST",
                "/rest/v1/family_members",
                body,
                session.code,
                preferRepresentation = false,
            )
        }

    suspend fun updateMemberColor(session: FamilySession, memberId: String, colorArgb: Long) =
        withContext(Dispatchers.IO) {
            if (memberId == ALL_FAMILY_MEMBER_ID) return@withContext
            val body =
                JSONObject()
                    .put("color_argb", colorArgb)
                    .put("updated_at", OffsetDateTime.now().toString())
            request(
                "PATCH",
                "/rest/v1/family_members?id=eq.$memberId&family_id=eq.${session.id}",
                body,
                session.code,
                preferRepresentation = false,
            )
        }

    suspend fun loadShopping(session: FamilySession): List<SyncShoppingItem> =
        withContext(Dispatchers.IO) {
            val result =
                request(
                    "GET",
                    "/rest/v1/shopping_items?select=id,name,checked,updated_at&family_id=eq.${session.id}&order=updated_at.asc",
                    familyCode = session.code,
                )
            val array = JSONArray(result)
            buildList {
                repeat(array.length()) {
                    val row = array.getJSONObject(it)
                    add(
                        SyncShoppingItem(
                            row.getString("id"),
                            row.getString("name"),
                            row.getBoolean("checked"),
                            row.optString("updated_at").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }.let { cloud ->
                OfflineMutationQueue.overlayShopping(
                    session,
                    (cloud + OfflineMutationQueue.pendingShopping(session)).distinctBy { it.id }
                )
            }
        }

    suspend fun addShopping(
        session: FamilySession,
        name: String,
        sourceDeviceId: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("family_id", session.id)
                    .put("name", name)
                    .put("checked", false)
            if (!sourceDeviceId.isNullOrBlank()) {
                body.put("created_by_device_id", sourceDeviceId)
            }
            submitOfflineCapableCreation(session, "shopping_items", body)
        }

    suspend fun toggleShopping(session: FamilySession, item: SyncShoppingItem) =
        withContext(Dispatchers.IO) {
            // A completion is an UPDATE with optimistic locking, not a blind PATCH.
            // If the network is unavailable, the updated row waits in the offline queue.
            submitRevision(
                session, "shopping_items", item.id, item.updatedAt, "update",
                JSONObject().put("checked", !item.checked), item.name,
            )
            // Shopping history is recorded only when the client can safely confirm
            // an online completion. Never increment the history counter during a
            // queued retry, as a lost response could otherwise count it twice.
            if (!item.checked && !OfflineMutationQueue.shouldQueue() &&
                !OfflineMutationQueue.hasPendingLocalRevision(session, "shopping_items", item.id)
            ) {
                val normalized = normalizeShoppingName(item.name)
                if (normalized.isNotBlank()) {
                    runCatching {
                        request(
                            "POST", "/rest/v1/rpc/record_shopping_purchase",
                            JSONObject()
                                .put("p_family_id", session.id)
                                .put("p_name", item.name.trim())
                                .put("p_normalized", normalized),
                            session.code, preferRepresentation = false,
                        )
                    }
                }
            }
        }

    suspend fun clearChecked(
        session: FamilySession,
        existing: List<SyncShoppingItem>,
    ) = withContext(Dispatchers.IO) {
        existing.filter { it.checked }.forEach { item ->
            submitRevision(
                session, "shopping_items", item.id, item.updatedAt, "delete",
                label = item.name,
            )
        }
    }

    suspend fun loadEvents(session: FamilySession): List<SyncEvent> =
        withContext(Dispatchers.IO) {
            val result =
                request(
                    "GET",
                    "/rest/v1/calendar_events?select=id,title,starts_at,ends_at,member_id,source,series_id,updated_at&family_id=eq.${session.id}&order=starts_at.asc",
                    familyCode = session.code,
                )
            val array = JSONArray(result)
            buildList {
                repeat(array.length()) {
                    val row = array.getJSONObject(it)
                    val zoned =
                        OffsetDateTime.parse(row.getString("starts_at"))
                            .atZoneSameInstant(STOCKHOLM)
                    val endZoned =
                        if (row.isNull("ends_at")) null
                        else
                            runCatching {
                                OffsetDateTime.parse(row.getString("ends_at"))
                                    .atZoneSameInstant(STOCKHOLM)
                            }
                                .getOrNull()
                    val source = row.optString("source", "manual")
                    val noTime = source.lowercase().contains("no_time")
                    add(
                        SyncEvent(
                            id = row.getString("id"),
                            title = row.getString("title"),
                            date = zoned.toLocalDate(),
                            time = if (noTime) "" else "%02d:%02d".format(zoned.hour, zoned.minute),
                            memberId =
                                if (row.isNull("member_id")) ALL_FAMILY_MEMBER_ID
                                else row.getString("member_id"),
                            source = source,
                            endDate = if (noTime) null else endZoned?.toLocalDate(),
                            endTime =
                                if (noTime) null
                                else endZoned?.let { "%02d:%02d".format(it.hour, it.minute) },
                            seriesId =
                                if (row.isNull("series_id")) null else row.getString("series_id"),
                            updatedAt = row.optString("updated_at").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }.let { cloud ->
                OfflineMutationQueue.overlayEvents(
                    session,
                    (cloud + OfflineMutationQueue.pendingEvents(session)).distinctBy { it.id }
                )
            }
        }

    suspend fun addReminder(
        session: FamilySession,
        title: String,
        date: LocalDate,
        time: String,
        memberId: String?,
        seriesId: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            val noTime = time.isBlank()
            val parsedTime =
                if (noTime) LocalTime.MIDNIGHT
                else runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
            val startsAt =
                ZonedDateTime.of(date, parsedTime, STOCKHOLM).toOffsetDateTime().toString()
            val body =
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("family_id", session.id)
                    .put("title", "🔔 " + title.removePrefix("🔔").trim())
                    .put("starts_at", startsAt)
                    .put("source", if (noTime) "reminder_no_time" else "reminder")
            if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID)
                body.put("member_id", JSONObject.NULL)
            else body.put("member_id", memberId)
            if (seriesId != null) body.put("series_id", seriesId)
            submitOfflineCapableCreation(session, "calendar_events", body)
        }

    suspend fun addEvent(
        session: FamilySession,
        title: String,
        date: LocalDate,
        time: String,
        memberId: String?,
    ) =
        withContext(Dispatchers.IO) {
            val parsedTime = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
            val startsAt =
                ZonedDateTime.of(date, parsedTime, STOCKHOLM).toOffsetDateTime().toString()
            val body =
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("family_id", session.id)
                    .put("title", title)
                    .put("starts_at", startsAt)
                    .put("source", "manual")
            if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID)
                body.put("member_id", JSONObject.NULL)
            else body.put("member_id", memberId)
            submitOfflineCapableCreation(session, "calendar_events", body)
        }

    suspend fun addEvent(
        session: FamilySession,
        title: String,
        date: LocalDate,
        startTime: String,
        endTime: String?,
        memberId: String?,
        seriesId: String? = null,
        source: String = "manual",
    ) =
        withContext(Dispatchers.IO) {
            val noTime = startTime.isBlank()
            val parsedStart =
                if (noTime) LocalTime.MIDNIGHT
                else runCatching { LocalTime.parse(startTime) }.getOrElse { LocalTime.of(18, 0) }
            val starts = ZonedDateTime.of(date, parsedStart, STOCKHOLM)
            val body =
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("family_id", session.id)
                    .put("title", title)
                    .put("starts_at", starts.toOffsetDateTime().toString())
                    .put("source", calendarEventSource(
                        if (source == "laundry") CalendarEntryKind.LAUNDRY else CalendarEntryKind.ACTIVITY,
                        !noTime,
                    ))
            val parsedEnd =
                if (noTime) null
                else endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            if (parsedEnd != null) {
                var ends = ZonedDateTime.of(date, parsedEnd, STOCKHOLM)
                if (!ends.isAfter(starts)) ends = ends.plusDays(1)
                body.put("ends_at", ends.toOffsetDateTime().toString())
            }
            if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID)
                body.put("member_id", JSONObject.NULL)
            else body.put("member_id", memberId)
            if (seriesId != null) body.put("series_id", seriesId)
            submitOfflineCapableCreation(session, "calendar_events", body)
        }

    suspend fun updateEvent(
        session: FamilySession,
        eventId: String,
        title: String,
        date: LocalDate,
        time: String,
        endTime: String?,
        memberId: String?,
        source: String? = null,
        expectedUpdatedAt: String? = null,
        changeSeriesId: Boolean = false,
        newSeriesId: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            val noTime = time.isBlank()
            val parsedTime =
                if (noTime) LocalTime.MIDNIGHT
                else runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
            val startsAt = ZonedDateTime.of(date, parsedTime, STOCKHOLM)
            val parsedEnd =
                if (noTime) null
                else endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val endsAt = parsedEnd?.let { value ->
                ZonedDateTime.of(
                    if (value.isAfter(parsedTime)) date else date.plusDays(1),
                    value,
                    STOCKHOLM,
                )
            }
            val body =
                JSONObject()
                    .put("title", title)
                    .put("starts_at", startsAt.toOffsetDateTime().toString())

            if (endsAt == null) body.put("ends_at", JSONObject.NULL)
            else body.put("ends_at", endsAt.toOffsetDateTime().toString())
            if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID)
                body.put("member_id", JSONObject.NULL)
            else body.put("member_id", memberId)
            if (!source.isNullOrBlank()) body.put("source", source)
            if (changeSeriesId) body.put("series_id", newSeriesId ?: JSONObject.NULL)
            submitRevision(
                session, "calendar_events", eventId, expectedUpdatedAt, "update",
                body, title,
            )
        }

    suspend fun updateEventSeriesId(
        session: FamilySession,
        eventId: String,
        seriesId: String?,
    ) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("series_id", seriesId ?: JSONObject.NULL)
            request(
                "PATCH",
                "/rest/v1/calendar_events?id=eq.$eventId&family_id=eq.${session.id}",
                body,
                session.code,
                preferRepresentation = false,
            )
        }

    /**
     * Undo works both online and offline. Cancel queued deletes, then submit
     * a stable-ID restoration for each event. Existing rows are never overwritten.
     */
    suspend fun undoDeletedEvents(
        session: FamilySession, snapshots: List<SyncEvent>,
    ) = withContext(Dispatchers.IO) {
        val targets = snapshots.distinctBy { it.id }
        OfflineMutationQueue.cancelRevisions(
            session, "calendar_events", targets.map { it.id }.toSet()
        )
        targets.forEach { event ->
            val startTime = event.time.takeIf { it.isNotBlank() }
                ?.let { LocalTime.parse(it) } ?: LocalTime.MIDNIGHT
            val start = ZonedDateTime.of(event.date, startTime, STOCKHOLM)
            val row = JSONObject()
                .put("id", event.id)
                .put("family_id", session.id)
                .put("title", event.title)
                .put("starts_at", start.toOffsetDateTime().toString())
                .put("source", event.source)
                .put(
                    "member_id", event.memberId?.takeUnless {
                        it == ALL_FAMILY_MEMBER_ID
                    } ?: JSONObject.NULL,
                )
            if (event.endTime?.isNotBlank() == true) {
                val endTime = LocalTime.parse(event.endTime)
                val endDate = event.endDate ?: if (endTime.isAfter(startTime)) {
                    event.date
                } else event.date.plusDays(1)
                row.put("ends_at",
                    ZonedDateTime.of(endDate, endTime, STOCKHOLM).toOffsetDateTime().toString())
            }
            event.seriesId?.let { row.put("series_id", it) }
            submitOfflineCapableCreation(session, "calendar_events", row)
        }
    }

    /**
     * Restore the exact calendar event IDs after a user taps Undo. Re-inserting
     * the same ID twice is safe; only missing rows are added. This does not
     * overwrite events that another device has already recreated or edited.
     */
    suspend fun restoreRecentlyDeletedEvents(
        session: FamilySession,
        deleted: List<SyncEvent>,
    ): Int = withContext(Dispatchers.IO) {
        val snapshots = deleted.distinctBy { it.id }
        val rows = JSONArray()
        snapshots.forEach { event ->
            val startTime = event.time.takeIf { it.isNotBlank() }
                ?.let { LocalTime.parse(it) } ?: LocalTime.MIDNIGHT
            val start = ZonedDateTime.of(event.date, startTime, STOCKHOLM)
            val row = JSONObject()
                .put("id", event.id)
                .put("family_id", session.id)
                .put("title", event.title)
                .put("starts_at", start.toOffsetDateTime().toString())
                .put("source", event.source)
                .put(
                    "member_id",
                    event.memberId?.takeUnless { it == ALL_FAMILY_MEMBER_ID }
                        ?: JSONObject.NULL,
                )
            event.endTime?.takeIf { it.isNotBlank() }?.let { rawEnd ->
                val endTime = LocalTime.parse(rawEnd)
                val endDate = event.endDate ?: if (endTime.isAfter(startTime)) {
                    event.date
                } else {
                    event.date.plusDays(1)
                }
                row.put(
                    "ends_at",
                    ZonedDateTime.of(endDate, endTime, STOCKHOLM).toOffsetDateTime().toString()
                )
            }
            event.seriesId?.let { row.put("series_id", it) }
            rows.put(row)
        }
        var i = 0
        while (i < rows.length()) {
            val batch = JSONArray()
            val end = minOf(i + 75, rows.length())
            for (index in i until end) batch.put(rows.getJSONObject(index))
            request(
                "POST",
                "/rest/v1/calendar_events?on_conflict=id",
                batch,
                session.code,
                preferRepresentation = false,
                preferExtra = "resolution=ignore-duplicates",
            )
            i = end
        }
        snapshots.size
    }

    suspend fun importSportAdmin(
        session: FamilySession,
        webcalUrl: String,
        memberId: String?,
    ): SportAdminImportResult =
        withContext(Dispatchers.IO) {
            val text = fetchText(webcalUrl.trim())
            val calendar =
                Biweekly.parse(text).first()
                    ?: throw IllegalStateException("Kunde inte läsa SportAdmin-kalendern")

            val now = ZonedDateTime.now(STOCKHOLM)
            val recurrenceWindowStart = Date.from(now.minusMonths(1).toInstant())
            val recurrenceHorizon = Date.from(now.plusMonths(12).toInstant())
            var imported = 0
            var failed = 0
            var firstError: String? = null

            fun recordFailure(message: String) {
                failed++
                if (firstError == null) {
                    firstError = message.take(240)
                }
            }

            for (event in calendar.getEvents()) {
                val uid = event.getUid()?.getValue()
                if (uid.isNullOrBlank()) {
                    recordFailure("SportAdmin-händelse saknar UID")
                    continue
                }

                val dateStart = event.getDateStart()
                val startValue = dateStart?.getValue()
                if (dateStart == null || startValue == null) {
                    recordFailure("$uid saknar DTSTART")
                    continue
                }

                val sourceTimeZone =
                    calendar.getTimezoneInfo().getTimezone(dateStart)?.getTimeZone()
                        ?: if (calendar.getTimezoneInfo().isFloating(dateStart)) {
                            TimeZone.getTimeZone(STOCKHOLM.id)
                        } else {
                            TimeZone.getTimeZone("UTC")
                        }

                val title = event.getSummary()?.getValue()?.ifBlank { "SportAdmin" } ?: "SportAdmin"
                val location = event.getLocation()?.getValue()?.takeIf { it.isNotBlank() }
                val endValue = event.getDateEnd()?.getValue()
                val durationMillis = endValue?.time?.minus(startValue.time)?.takeIf { it > 0L }
                val recurrenceRule = event.getRecurrenceRule()

                val occurrences = mutableListOf<Date>()
                try {
                    if (recurrenceRule == null) {
                        occurrences += startValue
                    } else {
                        val iterator = recurrenceRule.getDateIterator(startValue, sourceTimeZone)
                        iterator.advanceTo(recurrenceWindowStart)
                        var generated = 0
                        while (iterator.hasNext()) {
                            val occurrence = iterator.next()
                            if (occurrence.after(recurrenceHorizon)) {
                                break
                            }
                            occurrences += occurrence
                            generated++
                            if (generated >= 10000) {
                                throw IllegalStateException(
                                    "$uid har fler än 10000 återkommande tillfällen"
                                )
                            }
                        }
                    }
                } catch (error: Exception) {
                    recordFailure(error.message ?: "$uid kunde inte expandera RRULE")
                    continue
                }

                for (occurrence in occurrences) {
                    val start =
                        sportAdminDateTime(
                            occurrence,
                            startValue.hasTime(),
                            sourceTimeZone,
                        )
                    val end = durationMillis?.let { millis ->
                        start.plusNanos(millis * 1_000_000L)
                    }
                    val externalId =
                        if (recurrenceRule == null) {
                            uid
                        } else {
                            "$uid:${occurrence.time}"
                        }
                            .take(200)

                    val body =
                        JSONObject()
                            .put("family_id", session.id)
                            .put("title", title)
                            .put("starts_at", start.toOffsetDateTime().toString())
                            .put("source", "sportadmin")
                            .put("external_id", externalId)

                    if (end != null && end.isAfter(start)) {
                        body.put("ends_at", end.toOffsetDateTime().toString())
                    }
                    if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) {
                        body.put("member_id", JSONObject.NULL)
                    } else {
                        body.put("member_id", memberId)
                    }
                    if (location != null) {
                        body.put("location", location)
                    }

                    try {
                        request(
                            "POST",
                            "/rest/v1/calendar_events?on_conflict=family_id,source,external_id",
                            body,
                            session.code,
                            preferRepresentation = false,
                            preferExtra = "resolution=merge-duplicates",
                        )
                        imported++
                    } catch (error: Exception) {
                        recordFailure(error.message ?: "$externalId kunde inte importeras")
                    }
                }
            }

            SportAdminImportResult(
                imported = imported,
                failed = failed,
                firstError = firstError,
            )
        }

    suspend fun loadShoppingInterestTerms(session: FamilySession): List<String> =
        withContext(Dispatchers.IO) {
            val names = linkedSetOf<String>()
            runCatching {
                val shopping =
                    JSONArray(
                        request(
                            "GET",
                            "/rest/v1/shopping_items?select=name&family_id=eq.${session.id}&order=updated_at.desc&limit=100",
                            familyCode = session.code,
                        )
                    )
                repeat(shopping.length()) { index ->
                    shopping
                        .getJSONObject(index)
                        .optString("name")
                        .trim()
                        .takeIf(String::isNotBlank)
                        ?.let(names::add)
                }
            }
            runCatching {
                val history =
                    JSONArray(
                        request(
                            "GET",
                            "/rest/v1/shopping_history?select=display_name,purchase_count,last_purchased_at&family_id=eq.${session.id}&order=purchase_count.desc,last_purchased_at.desc&limit=100",
                            familyCode = session.code,
                        )
                    )
                repeat(history.length()) { index ->
                    history
                        .getJSONObject(index)
                        .optString("display_name")
                        .trim()
                        .takeIf(String::isNotBlank)
                        ?.let(names::add)
                }
            }
            names.toList()
        }


    /**
     * Exports a family-scoped, portable snapshot. The family access code and device
     * credentials are deliberately excluded. The caller chooses the destination.
     */
    suspend fun exportFamilyBackup(session: FamilySession): String =
        withContext(Dispatchers.IO) {
            val backup = JSONObject()
                .put("format", "familjekalender-backup")
                .put("version", 1)
                .put("family_id", session.id)
                .put("exported_at", OffsetDateTime.now().toString())
            val tables = linkedMapOf(
                "members" to ("family_members" to "id,name,role,color_argb"),
                "events" to ("calendar_events" to "id,title,starts_at,ends_at,member_id,source,series_id,location,description,external_id"),
                "shopping" to ("shopping_items" to "id,name,checked"),
                "todo" to ("todo_items" to "id,title,checked,member_id"),
            )
            tables.forEach { (key, table) ->
                val (name, columns) = table
                val result = request(
                    "GET",
                    "/rest/v1/$name?select=$columns&family_id=eq.${session.id}&order=id.asc",
                    familyCode = session.code,
                )
                backup.put(key, JSONArray(result))
            }
            backup.toString(2)
        }

    data class FamilyRestoreSummary(
        val familyMembers: Int,
        val events: Int,
        val shopping: Int,
        val todos: Int,
    ) {
        val total: Int get() = familyMembers + events + shopping + todos
    }

    /**
     * Same-family restore; insert only absent UUIDs. It never deletes or overwrites newer
     * cloud records. Re-importing the same file is safe and doesn't create duplicates.
     * Import order preserves member foreign-key references.
     */
    suspend fun restoreFamilyBackup(
        session: FamilySession,
        document: String,
    ): FamilyRestoreSummary = withContext(Dispatchers.IO) {
        require(document.length <= 15_000_000) { "Säkerhetskopian är för stor." }
        val backup = JSONObject(document)
        require(backup.optString("format") == "familjekalender-backup" &&
            backup.optInt("version") == 1) { "Filformatet stöds inte." }
        require(backup.optString("family_id") == session.id) {
            "Säkerhetskopian tillhör en annan familj."
        }

        data class BackupTable(val key: String, val table: String, val columns: List<String>)
        val tables = listOf(
            BackupTable("members", "family_members", listOf("id", "name", "role", "color_argb")),
            BackupTable("events", "calendar_events",
                listOf("id", "title", "starts_at", "ends_at", "member_id", "source",
                    "series_id", "location", "description", "external_id")),
            BackupTable("shopping", "shopping_items", listOf("id", "name", "checked")),
            BackupTable("todo", "todo_items", listOf("id", "title", "checked", "member_id")),
        )
        // Validate the complete file before making even the first write.
        val validated = linkedMapOf<String, JSONArray>()
        var totalRows = 0
        tables.forEach { table ->
            val entries = backup.optJSONArray(table.key)
                ?: error("Säkerhetskopian saknar ${table.key}.")
            totalRows += entries.length()
            require(totalRows <= 20_000) { "För många poster i säkerhetskopian." }
            val safeRows = JSONArray()
            for (i in 0 until entries.length()) {
                val input = entries.optJSONObject(i)
                    ?: error("Ogiltig post i ${table.key}.")
                val id = input.optString("id")
                require(runCatching { java.util.UUID.fromString(id) }.isSuccess) {
                    "Ogiltigt post-ID i ${table.key}."
                }
                val row = JSONObject().put("id", id).put("family_id", session.id)
                table.columns.filter { it != "id" }.forEach { column ->
                    if (input.has(column)) row.put(column, input.get(column))
                }
                val requiredName = if (table.key == "members" || table.key == "shopping")
                    "name" else "title"
                require(row.optString(requiredName).isNotBlank()) {
                    "En post i ${table.key} saknar namn."
                }
                if (table.key == "events") {
                    require(row.optString("starts_at").isNotBlank()) {
                        "En kalenderhändelse saknar datum."
                    }
                }
                safeRows.put(row)
            }
            validated[table.table] = safeRows
        }

        validated.forEach { (table, rows) ->
            var index = 0
            while (index < rows.length()) {
                val batch = JSONArray()
                val end = minOf(index + 75, rows.length())
                for (i in index until end) batch.put(rows.getJSONObject(i))
                request(
                    "POST",
                    "/rest/v1/$table?on_conflict=id",
                    batch,
                    session.code,
                    preferRepresentation = false,
                    preferExtra = "resolution=ignore-duplicates",
                )
                index = end
            }
        }

        FamilyRestoreSummary(
            validated.getValue("family_members").length(),
            validated.getValue("calendar_events").length(),
            validated.getValue("shopping_items").length(),
            validated.getValue("todo_items").length(),
        )
    }

    private fun normalizeShoppingName(value: String): String =
        value.lowercase(java.util.Locale("sv", "SE")).replace(Regex("[^a-z0-9åäö]+"), " ").trim()

    private fun sportAdminDateTime(
        date: Date,
        hasTime: Boolean,
        sourceTimeZone: TimeZone,
    ): ZonedDateTime {
        if (hasTime) {
            return Instant.ofEpochMilli(date.time).atZone(STOCKHOLM)
        }

        val sourceDate =
            Instant.ofEpochMilli(date.time).atZone(sourceTimeZone.toZoneId()).toLocalDate()
        return ZonedDateTime.of(sourceDate, LocalTime.NOON, STOCKHOLM)
    }

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

    private fun familyApiRequest(
        method: String,
        path: String,
        body: JSONObject? = null,
        familyCode: String? = null,
    ): String {
        val connection =
            URL("$SUPABASE_URL/functions/v1/family-api").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload =
                JSONObject()
                    .put("method", method)
                    .put("path", path)
                    .put("preferRepresentation", true)
            if (body != null) {
                payload.put("body", body)
            }
            if (!familyCode.isNullOrBlank()) {
                payload.put("familyCode", familyCode.uppercase())
            }

            connection.outputStream.use {
                it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val code = connection.responseCode
            val stream =
                if (code in 200..299) {
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

    /**
     * Creates only new, client-identified rows. A lost response cannot create a duplicate
     * when WorkManager sends the same UUID again. Server HTTP validation failures are
     * reported to the user, not hidden in the offline queue.
     */
    internal fun submitOfflineCapableCreation(
        session: FamilySession,
        table: String,
        body: JSONObject,
    ) {
        require(table in setOf("calendar_events", "shopping_items", "todo_items"))
        if (OfflineMutationQueue.shouldQueue()) {
            OfflineMutationQueue.enqueue(session, table, body)
            return
        }
        try {
            request(
                "POST", "/rest/v1/$table?on_conflict=id", body, session.code,
                preferRepresentation = false, preferExtra = "resolution=ignore-duplicates",
            )
        } catch (networkError: IOException) {
            OfflineMutationQueue.markNetworkFailure()
            OfflineMutationQueue.enqueue(session, table, body)
        }
    }

    internal suspend fun replayOfflineCreation(
        session: FamilySession,
        table: String,
        body: JSONObject,
    ) = withContext(Dispatchers.IO) {
        require(table in setOf("calendar_events", "shopping_items", "todo_items"))
        require(body.optString("family_id") == session.id)
        require(runCatching { UUID.fromString(body.optString("id")) }.isSuccess)
        request(
            "POST", "/rest/v1/$table?on_conflict=id", body, session.code,
            preferRepresentation = false, preferExtra = "resolution=ignore-duplicates",
        )
    }

    /**
     * All editing and deletion paths use the same version-checked mutation.
     * Pending creates can be changed before first sync, while existing records
     * require updated_at. Server conflicts are never converted into new writes.
     */
    internal suspend fun submitRevision(
        session: FamilySession,
        table: String,
        targetId: String,
        baseUpdatedAt: String?,
        action: String,
        changes: JSONObject = JSONObject(),
        label: String = "",
    ) = withContext(Dispatchers.IO) {
        require(table in setOf("calendar_events", "todo_items", "shopping_items"))
        require(action == "update" || action == "delete")
        if (OfflineMutationQueue.revisePendingCreate(session, table, targetId, action, changes)) {
            return@withContext
        }
        val version = baseUpdatedAt?.takeIf { it.isNotBlank() }
            ?: throw FamilyRevisionConflict(
                "Posten har ingen versionsstämpel. Öppna appen online och försök igen."
            )
        if (OfflineMutationQueue.shouldQueue() ||
            OfflineMutationQueue.hasPendingLocalRevision(session, table, targetId)
        ) {
            OfflineMutationQueue.enqueueRevision(
                session, table, targetId, version, action, changes, label
            )
            return@withContext
        }
        try {
            applyCheckedRevision(session, table, targetId, version, action, changes)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (networkFailure: java.io.IOException) {
            OfflineMutationQueue.markNetworkFailure()
            OfflineMutationQueue.enqueueRevision(
                session, table, targetId, version, action, changes, label
            )
        }
    }

    /** Raised when the phone attempts to change data someone else has since edited. */
    internal class FamilyRevisionConflict(message: String) : IllegalStateException(message)

    /**
     * Compare-and-swap, enforced by PostgREST using a row's exact updated_at value.
     * Successful changes advance updated_at through the database trigger. If a response
     * is lost, inspect the server row before deciding whether retry is safe.
     */
    internal suspend fun applyCheckedRevision(
        session: FamilySession,
        table: String,
        targetId: String,
        expectedUpdatedAt: String,
        action: String,
        changes: JSONObject = JSONObject(),
    ) = withContext(Dispatchers.IO) {
        require(table in setOf("calendar_events", "todo_items", "shopping_items"))
        require(action == "update" || action == "delete")
        require(runCatching { UUID.fromString(targetId) }.isSuccess)
        require(expectedUpdatedAt.isNotBlank()) {
            "Versionsinformation saknas. Öppna kalendern med internet och försök igen."
        }
        val versionParam = java.net.URLEncoder.encode(
            expectedUpdatedAt, StandardCharsets.UTF_8.name()
        )
        val base = "/rest/v1/$table?id=eq.$targetId&family_id=eq.${session.id}"
        val conditionalPath = "$base&updated_at=eq.$versionParam"
        val answer = request(
            if (action == "delete") "DELETE" else "PATCH",
            conditionalPath,
            if (action == "delete") null else changes,
            session.code,
            preferRepresentation = true,
        )
        if (JSONArray(answer).length() > 0) return@withContext

        // Zero affected rows: either this exact request already succeeded or another
        // device changed the row. Never retry blindly against the newer revision.
        val now = JSONArray(request(
            "GET", "$base&select=*&limit=1", familyCode = session.code
        ))
        if (now.length() == 0) {
            if (action == "delete") return@withContext
            throw FamilyRevisionConflict("Händelsen har redan tagits bort på en annan telefon.")
        }
        if (action == "update" && serverHasChanges(now.getJSONObject(0), changes)) {
            return@withContext
        }
        throw FamilyRevisionConflict(
            "Ändringen stoppades eftersom någon annan uppdaterat posten. " +
                "Läs in den senaste versionen innan du försöker igen."
        )
    }

    private fun serverHasChanges(server: JSONObject, changes: JSONObject): Boolean {
        val names = changes.keys()
        while (names.hasNext()) {
            val key = names.next()
            val wanted = changes.opt(key)
            val actual = server.opt(key)
            val value = if (wanted == null || wanted == JSONObject.NULL) null
                else wanted.toString()
            val current = if (actual == null || actual == JSONObject.NULL) null
                else actual.toString()
            val equal = revisionFieldMatches(key, value, current)
            if (!equal) return false
        }
        return true
    }

    private fun request(
        method: String,
        path: String,
        body: Any? = null,
        familyCode: String? = null,
        preferRepresentation: Boolean = true,
        preferExtra: String? = null,
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
            }
                .joinToString(",")
            connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream =
                if (code in 200..299) {
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
