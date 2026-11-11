package se.familjekalender.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable mutation queue. Creates have stable UUIDs; revisions use the server
 * updated_at timestamp as a compare-and-swap guard. Conflicts are preserved
 * for the user to review, never replayed against a newer version blindly.
 */
internal object OfflineMutationQueue {
    private const val PREFS = "family_offline_mutations"
    private const val WORK = "family_offline_creation_flush"
    private const val PERIODIC_WORK = "family_offline_creation_periodic"
    private val allowedTables = setOf("calendar_events", "shopping_items", "todo_items")
    private val stockholm = ZoneId.of("Europe/Stockholm")

    private lateinit var context: Context
    private var lastNetworkFailure = -120_000L

    fun initialize(applicationContext: Context) {
        context = applicationContext.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<OfflineCreateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic
        )
        schedule()
    }

    fun shouldQueue(): Boolean {
        if (!::context.isInitialized) return false
        if (SystemClock.elapsedRealtime() - lastNetworkFailure < 60_000L) return true
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true
    }

    fun markNetworkFailure() {
        lastNetworkFailure = SystemClock.elapsedRealtime()
    }

    private fun key(session: FamilySession) = "pending_${session.id}"

    @Synchronized
    private fun rows(session: FamilySession): JSONArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONArray(prefs.getString(key(session), "[]") ?: "[]")
    }

    @Synchronized
    fun enqueue(session: FamilySession, table: String, body: JSONObject) {
        require(::context.isInitialized) { "Offlinekö är inte initierad" }
        require(table in allowedTables) { "Ogiltig tabell för offlinekö" }
        require(body.optString("family_id") == session.id) { "Fel familj i offlinekö" }
        val rowId = body.optString("id")
        require(runCatching { UUID.fromString(rowId) }.isSuccess) {
            "Offlineposten saknar ett giltigt ID"
        }
        val existing = rows(session)
        for (index in 0 until existing.length()) {
            if (existing.getJSONObject(index).optString("id") == rowId) return
        }
        require(existing.length() < 1000) {
            "För många väntande ändringar; anslut till internet och synkronisera."
        }
        existing.put(JSONObject().put("id", rowId).put("table", table).put("body", body))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(prefs.edit().putString(key(session), existing.toString()).commit()) {
            "Kunde inte spara ändringen lokalt."
        }
        runCatching { updateLocalCache(session, table, body) }
        schedule()
    }

    private fun updateLocalCache(session: FamilySession, table: String, body: JSONObject) {
        when (table) {
            "calendar_events" -> {
                val starts = OffsetDateTime.parse(body.getString("starts_at")).atZoneSameInstant(stockholm)
                val ends = body.optString("ends_at").takeIf { it.isNotBlank() }
                    ?.let { OffsetDateTime.parse(it).atZoneSameInstant(stockholm) }
                val source = body.optString("source", "manual")
                val noTime = source.contains("no_time", ignoreCase = true)
                val row = SyncEvent(
                    id = body.getString("id"), title = body.getString("title"),
                    date = starts.toLocalDate(),
                    time = if (noTime) "" else "%02d:%02d".format(starts.hour, starts.minute),
                    memberId = if (body.isNull("member_id")) ALL_FAMILY_MEMBER_ID else body.optString("member_id"),
                    source = source,
                    endDate = if (noTime) null else ends?.toLocalDate(),
                    endTime = if (noTime) null else ends?.let { "%02d:%02d".format(it.hour, it.minute) },
                    seriesId = if (body.isNull("series_id")) null else body.optString("series_id").takeIf { it.isNotBlank() },
                )
                val old = FamilyOfflineCache.loadEvents(context, session).orEmpty()
                FamilyOfflineCache.saveEvents(context, session, (old + row).distinctBy { it.id })
            }
            "shopping_items" -> {
                val row = SyncShoppingItem(body.getString("id"), body.getString("name"), false)
                val old = FamilyOfflineCache.loadShopping(context, session).orEmpty()
                FamilyOfflineCache.saveShopping(context, session, (old + row).distinctBy { it.id })
            }
            "todo_items" -> {
                val row = SyncTodoItem(
                    body.getString("id"), body.getString("title"), false,
                    if (body.isNull("member_id")) null else body.optString("member_id")
                )
                val old = FamilyOfflineCache.loadTodo(context, session).orEmpty()
                FamilyOfflineCache.saveTodo(context, session, (old + row).distinctBy { it.id })
            }
        }
    }

    /**
     * If the target row was itself created offline, edit its pending insert
     * instead of generating an update against a server row that does not exist yet.
     */
    @Synchronized
    fun revisePendingCreate(
        session: FamilySession,
        table: String,
        targetId: String,
        action: String,
        changes: JSONObject,
    ): Boolean {
        val original = rows(session)
        val updated = JSONArray()
        var found = false
        for (i in 0 until original.length()) {
            val entry = original.getJSONObject(i)
            if (entry.optString("table") == table &&
                entry.optString("action", "create") == "create" &&
                entry.optString("id") == targetId
            ) {
                found = true
                if (action == "update") {
                    val payload = entry.getJSONObject("body")
                    val keys = changes.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        payload.put(k, changes.get(k))
                    }
                    updated.put(entry)
                }
            } else {
                updated.put(entry)
            }
        }
        if (found) {
            persist(session, updated)
            applyLocalRevision(session, table, targetId, action, changes)
        }
        return found
    }

    @Synchronized
    fun enqueueRevision(
        session: FamilySession,
        table: String,
        targetId: String,
        expectedUpdatedAt: String,
        action: String,
        changes: JSONObject,
        label: String,
    ) {
        require(table in allowedTables && action in setOf("update", "delete"))
        require(java.util.UUID.fromString(targetId).toString() == targetId)
        require(expectedUpdatedAt.isNotBlank()) {
            "Posten saknar version. Synkronisera med internet innan ändring."
        }
        if (revisePendingCreate(session, table, targetId, action, changes)) return

        val original = rows(session)
        val retained = JSONArray()
        var existing: JSONObject? = null
        for (i in 0 until original.length()) {
            val item = original.getJSONObject(i)
            if (item.optString("table") == table &&
                item.optString("target_id") == targetId &&
                item.optString("status", "queued") == "queued"
            ) existing = item
            else retained.put(item)
        }
        if (existing != null) {
            require(existing.optString("expected_updated_at") == expectedUpdatedAt) {
                "Den här posten har redan en väntande ändring."
            }
            if (action == "update" && existing.optString("action") == "update") {
                val previous = existing.getJSONObject("body")
                val keys = changes.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    previous.put(k, changes.get(k))
                }
                existing.put("body", previous)
            } else {
                existing.put("body", changes)
                existing.put("action", action)
            }
            retained.put(existing)
        } else {
            require(retained.length() < 1000) { "Offlinekön är full." }
            retained.put(JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("table", table)
                .put("target_id", targetId)
                .put("expected_updated_at", expectedUpdatedAt)
                .put("action", action)
                .put("body", changes)
                .put("label", label.take(100))
                .put("status", "queued"))
        }
        persist(session, retained)
        applyLocalRevision(session, table, targetId, action, changes)
        schedule()
    }

    @Synchronized
    fun blocked(session: FamilySession): List<JSONObject> {
        val list = rows(session)
        return buildList {
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                if (item.optString("status", "queued") == "blocked") add(item)
            }
        }
    }

    @Synchronized
    fun markBlocked(session: FamilySession, operationId: String, reason: String) {
        val list = rows(session)
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            if (item.optString("id") == operationId) {
                item.put("status", "blocked").put("reason", reason.take(250))
                break
            }
        }
        persist(session, list)
    }

    private fun persist(session: FamilySession, entries: JSONArray) {
        val ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(session), entries.toString()).commit()
        check(ok) { "Kunde inte spara offlineändringen på telefonen." }
    }

    private fun applyLocalRevision(
        session: FamilySession, table: String, targetId: String,
        action: String, changes: JSONObject
    ) {
        when (table) {
            "shopping_items" -> {
                val rows = FamilyOfflineCache.loadShopping(context, session).orEmpty()
                val revised = rows.mapNotNull {
                    if (it.id != targetId) it
                    else if (action == "delete") null
                    else it.copy(
                        name = changes.optString("name", it.name),
                        checked = if (changes.has("checked")) changes.optBoolean("checked") else it.checked,
                    )
                }
                FamilyOfflineCache.saveShopping(context, session, revised)
            }
            "todo_items" -> {
                val rows = FamilyOfflineCache.loadTodo(context, session).orEmpty()
                val revised = rows.mapNotNull {
                    if (it.id != targetId) it
                    else if (action == "delete") null
                    else it.copy(
                        title = changes.optString("title", it.title),
                        checked = if (changes.has("checked")) changes.optBoolean("checked") else it.checked,
                        memberId = if (changes.has("member_id")) {
                            if (changes.isNull("member_id")) null else changes.optString("member_id")
                        } else it.memberId,
                    )
                }
                FamilyOfflineCache.saveTodo(context, session, revised)
            }
            "calendar_events" -> {
                val rows = FamilyOfflineCache.loadEvents(context, session).orEmpty()
                val revised = rows.mapNotNull {
                    if (it.id != targetId) it
                    else if (action == "delete") null
                    else {
                        val start = changes.optString("starts_at").takeIf { it.isNotBlank() }
                            ?.let { OffsetDateTime.parse(it).atZoneSameInstant(stockholm) }
                        val end = if (changes.isNull("ends_at")) null
                            else changes.optString("ends_at").takeIf { it.isNotBlank() }
                                ?.let { OffsetDateTime.parse(it).atZoneSameInstant(stockholm) }
                        val noTime = changes.optString("source", it.source).contains("no_time", true)
                        it.copy(
                            title = changes.optString("title", it.title),
                            date = start?.toLocalDate() ?: it.date,
                            time = if (noTime) "" else start?.let {
                                "%02d:%02d".format(it.hour, it.minute)
                            } ?: it.time,
                            endDate = if (noTime) null else if (changes.has("ends_at")) end?.toLocalDate() else it.endDate,
                            endTime = if (noTime) null else if (changes.has("ends_at")) end?.let {
                                "%02d:%02d".format(it.hour, it.minute)
                            } else it.endTime,
                            memberId = if (changes.has("member_id")) {
                                if (changes.isNull("member_id")) ALL_FAMILY_MEMBER_ID else changes.optString("member_id")
                            } else it.memberId,
                            source = changes.optString("source", it.source),
                            seriesId = if (changes.has("series_id")) {
                                if (changes.isNull("series_id")) null else changes.optString("series_id")
                            } else it.seriesId,
                        )
                    }
                }
                FamilyOfflineCache.saveEvents(context, session, revised)
            }
        }
    }

    private fun hasPendingLocalRevision(session: FamilySession, table: String, id: String): Boolean {
        val list = rows(session)
        for (i in 0 until list.length()) {
            val e = list.getJSONObject(i)
            if (e.optString("table") == table &&
                e.optString("target_id") == id &&
                e.optString("status", "queued") == "queued") return true
        }
        return false
    }

    fun overlayShopping(session: FamilySession, cloud: List<SyncShoppingItem>): List<SyncShoppingItem> =
        overlayRevisions(session, "shopping_items", cloud, { it.id }) { original, patch ->
            original.copy(
                checked = if (patch.has("checked")) patch.optBoolean("checked") else original.checked,
                name = patch.optString("name", original.name),
            )
        }

    fun overlayTodo(session: FamilySession, cloud: List<SyncTodoItem>): List<SyncTodoItem> =
        overlayRevisions(session, "todo_items", cloud, { it.id }) { original, patch ->
            original.copy(
                checked = if (patch.has("checked")) patch.optBoolean("checked") else original.checked,
                title = patch.optString("title", original.title),
                memberId = if (patch.has("member_id")) {
                    if (patch.isNull("member_id")) null else patch.optString("member_id")
                } else original.memberId,
            )
        }

    fun overlayEvents(session: FamilySession, cloud: List<SyncEvent>): List<SyncEvent> {
        val localById = FamilyOfflineCache.loadEvents(context, session).orEmpty().associateBy { it.id }
        return overlayRevisions(session, "calendar_events", cloud, { it.id }) { original, _ ->
            localById[original.id] ?: original
        }
    }

    private fun <T> overlayRevisions(
        session: FamilySession, table: String, cloud: List<T>, getId: (T) -> String,
        apply: (T, JSONObject) -> T,
    ): List<T> {
        val result = cloud.toMutableList()
        val list = rows(session)
        for (i in 0 until list.length()) {
            val e = list.getJSONObject(i)
            if (e.optString("table") != table ||
                e.optString("status", "queued") != "queued") continue
            when (e.optString("action", "create")) {
                "delete" -> result.removeAll { getId(it) == e.optString("target_id") }
                "update" -> {
                    val position = result.indexOfFirst { getId(it) == e.optString("target_id") }
                    if (position >= 0) result[position] = apply(result[position], e.getJSONObject("body"))
                }
            }
        }
        return result
    }

    @Synchronized
    fun pendingCount(session: FamilySession) = rows(session).length()

    @Synchronized
    fun pending(session: FamilySession, table: String): List<JSONObject> {
        val list = rows(session)
        return buildList {
            for (i in 0 until list.length()) {
                val entry = list.optJSONObject(i) ?: continue
                if (entry.optString("table") == table &&
                    entry.optString("action", "create") == "create") {
                    entry.optJSONObject("body")?.let { add(it) }
                }
            }
        }
    }

    fun pendingEvents(session: FamilySession): List<SyncEvent> {
        val ids = pending(session, "calendar_events").map { it.optString("id") }.toSet()
        return FamilyOfflineCache.loadEvents(context, session).orEmpty().filter { it.id in ids }
    }

    fun pendingShopping(session: FamilySession): List<SyncShoppingItem> {
        val ids = pending(session, "shopping_items").map { it.optString("id") }.toSet()
        return FamilyOfflineCache.loadShopping(context, session).orEmpty().filter { it.id in ids }
    }

    fun pendingTodo(session: FamilySession): List<SyncTodoItem> {
        val ids = pending(session, "todo_items").map { it.optString("id") }.toSet()
        return FamilyOfflineCache.loadTodo(context, session).orEmpty().filter { it.id in ids }
    }

    @Synchronized
    fun head(session: FamilySession): JSONObject? {
        val list = rows(session)
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: continue
            if (entry.optString("status", "queued") == "queued") return entry
        }
        return null
    }

    @Synchronized
    fun remove(session: FamilySession, operationId: String) {
        val list = rows(session)
        val remaining = JSONArray()
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: continue
            if (entry.optString("id") != operationId) remaining.put(entry)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(session), remaining.toString()).commit()
    }

    fun requestRetry() = schedule()

    private fun schedule() {
        if (!::context.isInitialized) return
        val work = OneTimeWorkRequestBuilder<OfflineCreateWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.KEEP, work
        )
    }
}

internal class OfflineCreateWorker(
    appContext: Context, params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
        val familyId = prefs.getString("family_id", null)
        val familyCode = prefs.getString("family_code", null)
        if (familyId.isNullOrBlank() || familyCode.isNullOrBlank()) return Result.success()
        val session = FamilySession(familyId, prefs.getString("family_name", "") ?: "", familyCode)

        repeat(1000) {
            val operation = OfflineMutationQueue.head(session) ?: run {
                FamilyCalendarWidget.enqueueRefresh(applicationContext)
                return Result.success()
            }
            val id = operation.optString("id")
            val table = operation.optString("table")
            val body = operation.optJSONObject("body") ?: return Result.failure()
            try {
                if (operation.optString("action", "create") == "create") {
                    SupabaseSync.replayOfflineCreation(session, table, body)
                } else {
                    SupabaseSync.applyCheckedRevision(
                        session = session,
                        table = table,
                        targetId = operation.getString("target_id"),
                        expectedUpdatedAt = operation.getString("expected_updated_at"),
                        action = operation.getString("action"),
                        changes = body,
                    )
                }
                OfflineMutationQueue.remove(session, id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: SupabaseSync.FamilyRevisionConflict) {
                OfflineMutationQueue.markBlocked(session, id, failure.message ?: "Ändrad på annan telefon")
            } catch (failure: IOException) {
                OfflineMutationQueue.markNetworkFailure()
                return Result.retry()
            } catch (failure: Exception) {
                OfflineMutationQueue.markBlocked(session, id, failure.message ?: "Servern avvisade ändringen")
            }
        }
        return Result.retry()
    }
}
