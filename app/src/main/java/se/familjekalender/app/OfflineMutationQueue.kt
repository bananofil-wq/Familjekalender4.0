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
 * Durable, idempotent offline creation queue. Only INSERT operations with client-generated
 * UUIDs are queued; changes/deletes are intentionally NOT silently retried, to avoid
 * replacing another family member's edits with stale offline state.
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

    @Synchronized
    fun pendingCount(session: FamilySession) = rows(session).length()

    @Synchronized
    fun pending(session: FamilySession, table: String): List<JSONObject> {
        val list = rows(session)
        return buildList {
            for (i in 0 until list.length()) {
                val entry = list.optJSONObject(i) ?: continue
                if (entry.optString("table") == table) {
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
    fun head(session: FamilySession): JSONObject? = rows(session).optJSONObject(0)

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
                SupabaseSync.replayOfflineCreation(session, table, body)
                OfflineMutationQueue.remove(session, id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                OfflineMutationQueue.markNetworkFailure()
                return Result.retry()
            } catch (failure: Exception) {
                // Leave the entry on disk, so the user can resolve a rejected server write.
                return Result.failure()
            }
        }
        return Result.retry()
    }
}
