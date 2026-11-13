package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FamilyCalendarWidgetWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, FamilyCalendarWidget::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isEmpty()) return Result.success()

        return try {
            val prefs = applicationContext.getSharedPreferences("family_calendar", 0)
            val id = prefs.getString("family_id", null)
            val name = prefs.getString("family_name", null)
            val code = prefs.getString("family_code", null)

            if (id.isNullOrBlank() || code.isNullOrBlank()) {
                widgetIds.forEach { widgetId ->
                    cacheRows(widgetId, JSONArray())
                    applicationContext.getSharedPreferences(
                        FamilyCalendarWidget.WIDGET_PREFS, Context.MODE_PRIVATE
                    ).edit()
                        .remove(FamilyCalendarWidget.cachedSummaryPrefKey(widgetId))
                        .remove(FamilyCalendarWidget.cachedFamilyPrefKey(widgetId))
                        .apply()
                    val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                    views.setTextViewText(
                        R.id.widget_status, "Öppna appen och anslut till familjen"
                    )
                    views.setTextViewText(R.id.widget_todo, "✓  To-Do    ›")
                    views.setTextViewText(R.id.widget_shopping, "🛒  Inköp    ›")
                    views.setTextViewText(R.id.widget_updated, "")
                    manager.updateAppWidget(widgetId, views)
                    manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_event_list)
                }
                return Result.success()
            }

            val session = FamilySession(id, name ?: "Min familj", code)

            // Each network request has its own error boundary: never let a failed
            // calendar request hide a successful To-Do or shopping refresh.
            val loaded = coroutineScope {
                val events = async { fetchSafely("aktiviteter") { SupabaseSync.loadEvents(session) } }
                val members = async { fetchSafely("familjemedlemmar") { SupabaseSync.loadMembers(session) } }
                val shopping = async { fetchSafely("inköp") { SupabaseSync.loadShopping(session) } }
                val todos = async {
                    fetchSafely("To-Do") { withContext(Dispatchers.IO) { TodoSync.load(session) } }
                }
                LoadedWidgetData(
                    events = events.await(),
                    members = members.await(),
                    shopping = shopping.await(),
                    todos = todos.await(),
                )
            }

            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val cacheUntil = weekStart.plusDays(13)

            val events = loaded.events.value ?: FamilyOfflineCache.loadEvents(applicationContext, session)
            val members = (
                loaded.members.value ?: FamilyOfflineCache.loadMembers(applicationContext, session)
                    ?: emptyList()
            ).associateBy { it.id }

            // An app-side offline snapshot can restore data to a newly created widget.
            // Existing widget rows are preferred over an older offline snapshot.
            loaded.events.value?.let {
                runCatching { FamilyOfflineCache.saveEvents(applicationContext, session, it) }
            }
            loaded.members.value?.let {
                runCatching { FamilyOfflineCache.saveMembers(applicationContext, session, it) }
            }
            loaded.shopping.value?.let {
                runCatching { FamilyOfflineCache.saveShopping(applicationContext, session, it) }
            }
            loaded.todos.value?.let {
                runCatching { FamilyOfflineCache.saveTodo(applicationContext, session, it) }
            }

            // Keep all activities from this week, even when they have already finished.
            val upcomingEvents = events?.filter { event ->
                !event.date.isBefore(weekStart) && !event.date.isAfter(cacheUntil)
            }?.sortedWith(
                compareBy<SyncEvent> { it.date }
                    .thenBy { parseMinute(it.time) ?: Int.MAX_VALUE }
                    .thenBy { it.title }
            )
            val refreshedRows = upcomingEvents?.let { matching ->
                JSONArray().apply {
                    matching.groupBy { it.date }.forEach { (date, dayEvents) ->
                        appendEvents(dayEvents, date, members)
                    }
                }
            }

            widgetIds.forEach { widgetId ->
                val widgetPrefs = applicationContext.getSharedPreferences(
                    FamilyCalendarWidget.WIDGET_PREFS, Context.MODE_PRIVATE
                )
                val storedFamilyId = widgetPrefs.getString(
                    FamilyCalendarWidget.cachedFamilyPrefKey(widgetId), null
                )
                // Avoid showing cached events from another family after switching.
                if (storedFamilyId != null && storedFamilyId != session.id) {
                    widgetPrefs.edit()
                        .remove(FamilyCalendarWidget.cachedRowsPrefKey(widgetId))
                        .remove(FamilyCalendarWidget.cachedSummaryPrefKey(widgetId))
                        .apply()
                }
                widgetPrefs.edit()
                    .putString(FamilyCalendarWidget.cachedFamilyPrefKey(widgetId), session.id)
                    .apply()

                val previousRows = widgetPrefs.getString(
                    FamilyCalendarWidget.cachedRowsPrefKey(widgetId), null
                )?.let { runCatching { JSONArray(it) }.getOrNull() }
                val hasCurrentRows = previousRows?.let { array ->
                    (0 until array.length()).any { index ->
                        val date = array.optJSONObject(index)?.optString("date").orEmpty()
                        date >= weekStart.toString() && date <= cacheUntil.toString()
                    }
                } ?: false
                if (refreshedRows != null && (
                        loaded.events.value != null ||
                            previousRows == null ||
                            (!hasCurrentRows && refreshedRows.length() > 0)
                    )
                ) {
                    cacheRows(widgetId, refreshedRows)
                }

                val previous = widgetPrefs.getString(
                    FamilyCalendarWidget.cachedSummaryPrefKey(widgetId), null
                )?.let { runCatching { JSONObject(it) }.getOrNull() }
                val offlineShopping = if (loaded.shopping.value == null) {
                    FamilyOfflineCache.loadShopping(applicationContext, session)?.count { !it.checked }
                } else null
                val offlineTodo = if (loaded.todos.value == null) {
                    FamilyOfflineCache.loadTodo(applicationContext, session)?.count { !it.checked }
                } else null

                // Prefer a locally saved, verified screen snapshot over an older
                // widget label if the network is temporarily unavailable.
                val todo = loaded.todos.value?.let { widgetTodoLabel(widgetOpenTodoCount(it)) }
                    ?: offlineTodo?.let(::widgetTodoLabel)
                    ?: previous?.optString("todo")?.takeIf { it.isNotBlank() }
                    ?: "✓  To-Do    ›"
                val shopping = loaded.shopping.value?.let {
                    widgetShoppingLabel(widgetOpenShoppingCount(it))
                } ?: offlineShopping?.let(::widgetShoppingLabel)
                    ?: previous?.optString("shopping")?.takeIf { it.isNotBlank() }
                    ?: "🛒  Inköp    ›"
                val status = widgetRefreshStatus(
                    todayCount = upcomingEvents?.let { widgetActivityCountForDay(it, today) },
                    eventsUpdated = !loaded.events.failed,
                    membersUpdated = !loaded.members.failed,
                    todoUpdated = !loaded.todos.failed,
                    shoppingUpdated = !loaded.shopping.failed,
                    hasSavedEvents = previousRows != null || refreshedRows != null,
                )
                val summary = JSONObject()
                    .put("date", today.toString())
                    .put("status", status)
                    .put("todo", todo)
                    .put("shopping", shopping)
                widgetPrefs.edit()
                    .putString(FamilyCalendarWidget.cachedSummaryPrefKey(widgetId), summary.toString())
                    .apply()
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                manager.updateAppWidget(widgetId, views)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_event_list)
            }

            if (loaded.events.failed || loaded.members.failed || loaded.shopping.failed ||
                loaded.todos.failed
            ) Result.retry() else Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("FamilyCalendarWidget", "Unexpected refresh failure", error)
            widgetIds.forEach { widgetId ->
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                views.setTextViewText(R.id.widget_status, "Widgetfel · visar sparad information")
                manager.updateAppWidget(widgetId, views)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_event_list)
            }
            Result.retry()
        }
    }

    private data class FetchResult<T>(val value: T?, val failed: Boolean)

    private suspend fun <T> fetchSafely(
        category: String,
        block: suspend () -> T,
    ): FetchResult<T> = try {
        FetchResult(block(), false)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        android.util.Log.w("FamilyCalendarWidget", "Could not load $category", error)
        FetchResult(null, true)
    }

    private data class LoadedWidgetData(
        val events: FetchResult<List<SyncEvent>>,
        val members: FetchResult<List<SyncMember>>,
        val shopping: FetchResult<List<SyncShoppingItem>>,
        val todos: FetchResult<List<SyncTodoItem>>,
    )

    private fun JSONArray.appendEvents(
        events: List<SyncEvent>,
        date: LocalDate,
        members: Map<String, SyncMember>,
    ) {
        events.forEach { event ->
            val memberKey = event.memberId ?: ALL_FAMILY_MEMBER_ID
            val who = memberName(memberKey, members)
            val activity = cleanActivityTitle(event.title, event.time, event.endTime, who)
            val laundry = isLaundryEvent(event)
            val reminder = widgetIsReminder(event)
            val displayActivity = when {
                reminder -> activity.removePrefix("🔔").trimStart()
                laundry -> activity.removePrefix("🧺").trimStart()
                else -> widgetActivityIcon(activity) + "  " + activity
            }
            put(
                JSONObject()
                    .put("date", date.toString())
                    .put("memberKey", memberKey)
                    .put("who", who)
                    .put("activity", displayActivity)
                    .put("laundry", laundry)
                    .put("reminder", reminder)
                    .put("birthday", widgetIsBirthday(event))
                    .put("color", members[memberKey]?.colorArgb?.toInt() ?: 0xFF69B9F4.toInt())
                    .put("time", timeTextOrNull(event).orEmpty())
            )
        }
    }

    private fun cacheRows(widgetId: Int, rows: JSONArray) {
        applicationContext
            .getSharedPreferences(
                FamilyCalendarWidget.WIDGET_PREFS,
                Context.MODE_PRIVATE,
            )
            .edit()
            .putString(FamilyCalendarWidget.cachedRowsPrefKey(widgetId), rows.toString())
            .apply()
    }

    private fun memberName(memberKey: String, members: Map<String, SyncMember>): String =
        if (memberKey == ALL_FAMILY_MEMBER_ID) {
            "Alla"
        } else {
            members[memberKey]?.name?.takeIf { it.isNotBlank() } ?: "Familj"
        }

    private fun timeTextOrNull(event: SyncEvent): String? {
        if (event.time.isBlank()) return null
        // Some older events stored a time range only in the title, not ends_at.
        // Display it in the time column rather than leaving an orphaned "–14:00" in the title.
        val titleRange = Regex("""(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})""")
            .find(event.title)
        val inferredEnd = titleRange?.takeIf { match ->
            parseLocalTime(match.groupValues[1]) == parseLocalTime(event.time)
        }?.groupValues?.get(2)
        val ending = event.endTime?.takeIf { it.isNotBlank() } ?: inferredEnd
        return if (ending == null) event.time else "${event.time}–$ending"
    }

    private fun cleanActivityTitle(
        title: String,
        startTime: String,
        endTime: String?,
        who: String,
    ): String {
        var cleaned = title.removePrefix("🌈").removePrefix("🧺").trim()
        if (cleaned.startsWith(who, ignoreCase = true)) {
            cleaned = cleaned.drop(who.length).trimStart()
            cleaned = cleaned.trimStart('•', '·', '|', ':', '-', '–').trimStart()
        }
        val ranges =
            buildList {
                endTime
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        add("$startTime–$it")
                        add("$startTime-$it")
                        add("$startTime – $it")
                        add("$startTime - $it")
                    }
            }
        ranges.forEach { cleaned = cleaned.replace(it, " ", ignoreCase = true) }
        if (startTime.isNotBlank()) {
            cleaned = cleaned.replace(
                Regex("""(?<!\d)${Regex.escape(startTime)}\s*[-–]\s*\d{1,2}:\d{2}\b"""),
                " ",
            )
            cleaned = cleaned.replace(startTime, " ", ignoreCase = true)
        }
        cleaned =
            cleaned
                .replace("  ", " ")
                .replace("  ", " ")
                .trim()
                .trim('•', '·', '-', '–', '|', ':')
                .trim()
                .replace(Regex("""\s*[·•|]\s*[-–]\s*\d{1,2}:\d{2}\s*$"""), "")
                .trim()
        return cleaned.ifBlank { "Aktivitet" }
    }

    private fun widgetActivityIcon(title: String): String {
        val value = title.lowercase()
        return when {
            "jobb" in value || "arbete" in value -> "💼"
            "skola" in value || "förskola" in value -> "🎓"
            "träning" in value || "innebandy" in value || "handboll" in value -> "🏃"
            "födelsedag" in value || "kalas" in value -> "🎂"
            else -> "•"
        }
    }

    private fun parseLocalTime(value: String?): LocalTime? =
        value?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }

    private fun parseMinute(value: String?): Int? =
        parseLocalTime(value)?.let { it.hour * 60 + it.minute }

}
