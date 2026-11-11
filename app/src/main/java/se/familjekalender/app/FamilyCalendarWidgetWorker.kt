package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val WIDGET_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val WIDGET_SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

class FamilyCalendarWidgetWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, FamilyCalendarWidget::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isEmpty()) return Result.success()

        return runCatching {
            val prefs = applicationContext.getSharedPreferences("family_calendar", 0)
            val id = prefs.getString("family_id", null)
            val name = prefs.getString("family_name", null)
            val code = prefs.getString("family_code", null)

            if (id.isNullOrBlank() || code.isNullOrBlank()) {
                widgetIds.forEach { widgetId ->
                    cacheRows(widgetId, JSONArray())
                    applicationContext.getSharedPreferences(FamilyCalendarWidget.WIDGET_PREFS, Context.MODE_PRIVATE)
                        .edit().remove(FamilyCalendarWidget.cachedSummaryPrefKey(widgetId)).apply()
                    val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                    views.setTextViewText(
                        R.id.widget_status,
                        "Öppna appen och anslut till familjen",
                    )
                    views.setTextViewText(R.id.widget_todo, "✓  To-Do    ›")
                    views.setTextViewText(R.id.widget_shopping, "🛒  Inköp    ›")
                    views.setTextViewText(R.id.widget_updated, "")
                    manager.updateAppWidget(widgetId, views)
                    manager.notifyAppWidgetViewDataChanged(
                        widgetId,
                        R.id.widget_event_list,
                    )
                }
                return Result.success()
            }

            val session = FamilySession(id, name ?: "Min familj", code)

            // Hämtningarna är oberoende och körs parallellt. Det minskar väntan när
            // widgeten uppdateras manuellt eller efter den periodiska refreshen.
            val loaded =
                coroutineScope {
                    val eventsDeferred = async { SupabaseSync.loadEvents(session) }
                    val membersDeferred =
                        async {
                            runCatching { SupabaseSync.loadMembers(session) }
                                .getOrDefault(emptyList())
                        }
                    val shoppingDeferred =
                        async {
                            runCatching {
                                SupabaseSync.loadShopping(session).count { !it.checked }
                            }.getOrDefault(0)
                        }
                    val todoDeferred =
                        async {
                            runCatching { loadOpenTodoCount(session) }.getOrDefault(0)
                        }

                    LoadedWidgetData(
                        events = eventsDeferred.await(),
                        members = membersDeferred.await().associateBy { it.id },
                        openShopping = shoppingDeferred.await(),
                        openTodos = todoDeferred.await(),
                    )
                }

            val today = LocalDate.now()
            val cacheUntil = today.plusDays(13)
            val now = LocalTime.now()
            // Store two weeks, so the next week's overview still works when offline.
            val upcomingEvents = loaded.events
                .filter { event ->
                    !event.date.isBefore(today) && !event.date.isAfter(cacheUntil) &&
                        (event.date != today || isStillRelevantToday(event, now))
                }
                .sortedWith(compareBy<SyncEvent> { it.date }
                    .thenBy { parseMinute(it.time) ?: Int.MAX_VALUE }
                    .thenBy { it.title })

            val cachedArray = JSONArray().apply {
                upcomingEvents.groupBy { it.date }.forEach { (date, dayEvents) ->
                    appendEvents(dayEvents, date, loaded.members)
                }
            }

            widgetIds.forEach { widgetId ->
                cacheRows(widgetId, cachedArray)
                val todayCount = upcomingEvents.count { it.date == today }
                val status = when (todayCount) {
                    0 -> "Inga aktiviteter kvar idag"
                    1 -> "✓ 1 aktivitet kvar idag"
                    else -> "✓ $todayCount aktiviteter kvar idag"
                }
                val todo = "✓  ${loaded.openTodos} To-Do    ›"
                val shopping = "🛒  ${loaded.openShopping} inköp    ›"
                val summary = JSONObject()
                    .put("date", today.toString())
                    .put("status", status)
                    .put("todo", todo)
                    .put("shopping", shopping)
                applicationContext.getSharedPreferences(FamilyCalendarWidget.WIDGET_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(FamilyCalendarWidget.cachedSummaryPrefKey(widgetId), summary.toString()).apply()
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                manager.updateAppWidget(widgetId, views)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_event_list)
            }

            Result.success()
        }.getOrElse {
            widgetIds.forEach { widgetId ->
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                views.setTextViewText(R.id.widget_status, "Kunde inte uppdatera just nu")
                // Keep cached rows and counts when the network is unavailable.
                manager.updateAppWidget(widgetId, views)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_event_list)
            }
            Result.retry()
        }
    }

    private data class LoadedWidgetData(
        val events: List<SyncEvent>,
        val members: Map<String, SyncMember>,
        val openShopping: Int,
        val openTodos: Int,
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
            val displayActivity =
                if (laundry) activity.removePrefix("🧺").trimStart()
                else widgetActivityIcon(activity) + "  " + activity
            put(
                JSONObject()
                    .put("date", date.toString())
                    .put("memberKey", memberKey)
                    .put("who", who)
                    .put("activity", displayActivity)
                    .put("laundry", laundry)
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

    private fun isStillRelevantToday(event: SyncEvent, now: LocalTime): Boolean {
        if (event.time.isBlank()) return true
        val end = parseLocalTime(event.endTime)
        if (end != null) return !now.isAfter(end)
        val start = parseLocalTime(event.time) ?: return true
        return !now.isAfter(start.plusMinutes(15))
    }

    private fun eventComparator(): Comparator<SyncEvent> =
        compareBy<SyncEvent> { parseMinute(it.time) ?: Int.MAX_VALUE }
            .thenBy { it.title }

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

    private suspend fun loadOpenTodoCount(session: FamilySession): Int =
        withContext(Dispatchers.IO) {
            val path = "/rest/v1/todo_items?select=checked&family_id=eq.${session.id}"
            val connection = URL("$WIDGET_SUPABASE_URL$path").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("apikey", WIDGET_SUPABASE_ANON_KEY)
            connection.setRequestProperty(
                "Authorization",
                "Bearer $WIDGET_SUPABASE_ANON_KEY",
            )
            connection.setRequestProperty("x-family-code", session.code.uppercase())
            try {
                if (connection.responseCode !in 200..299) return@withContext 0
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(text.ifBlank { "[]" })
                var count = 0
                repeat(array.length()) { index ->
                    if (!array.getJSONObject(index).optBoolean("checked")) count++
                }
                count
            } finally {
                connection.disconnect()
            }
        }
}
