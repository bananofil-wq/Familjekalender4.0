package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val WIDGET_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val WIDGET_SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

class FamilyCalendarWidgetWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    private data class WidgetRow(val who: String, val activity: String, val time: String)

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
            widgetIds.forEach { widgetId ->
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                if (id.isNullOrBlank() || code.isNullOrBlank()) {
                    views.setTextViewText(
                        R.id.widget_status,
                        "Öppna appen och anslut till familjen",
                    )
                    views.setTextViewText(R.id.widget_todo, "✓  To-Do    ›")
                    views.setTextViewText(R.id.widget_shopping, "🛒  Inköp    ›")
                    views.setTextViewText(R.id.widget_updated, "")
                    manager.updateAppWidget(widgetId, views)
                    return@forEach
                }
                val session = FamilySession(id, name ?: "Min familj", code)
                val today = LocalDate.now()
                val now = LocalTime.now()
                val allEvents = SupabaseSync.loadEvents(session)
                val todaysEvents =
                    allEvents
                        .filter { it.date == today }
                        .sortedWith(
                            compareBy<SyncEvent> { parseMinute(it.time) ?: Int.MAX_VALUE }
                                .thenBy { it.title }
                        )
                val tomorrowsEvents =
                    allEvents
                        .filter { it.date == today.plusDays(1) }
                        .sortedWith(
                            compareBy<SyncEvent> { parseMinute(it.time) ?: Int.MAX_VALUE }
                                .thenBy { it.title }
                        )
                val members = runCatching {
                    SupabaseSync.loadMembers(session)
                }.getOrDefault(emptyList()).associateBy { it.id }
                val openShopping = runCatching {
                    SupabaseSync.loadShopping(session).count { !it.checked }
                }.getOrDefault(0)
                val openTodos = runCatching { loadOpenTodoCount(session) }.getOrDefault(0)
                val visibleToday =
                    todaysEvents
                        .filter { event ->
                            val end = event.endTime?.let(::parseLocalTime)
                            val start = parseLocalTime(event.time)
                            when {
                                end != null -> !end.isBefore(now.minusMinutes(15))
                                start != null -> !start.isBefore(now.minusMinutes(15))
                                else -> true
                            }
                        }
                        .take(3)
                val visibleTomorrow =
                    tomorrowsEvents.take((4 - visibleToday.size).coerceIn(0, 2))
                val todayCount = todaysEvents.size
                views.setTextViewText(
                    R.id.widget_status,
                    when (todayCount) {
                        0 -> "Ingen aktivitet idag"
                        1 -> "✓ 1 aktivitet idag"
                        else -> "✓ $todayCount aktiviteter idag"
                    },
                )
                views.setTextViewText(R.id.widget_todo, "✓  $openTodos To-Do    ›")
                views.setTextViewText(R.id.widget_shopping, "🛒  $openShopping inköp    ›")
                views.setTextViewText(R.id.widget_updated, "")
                val todayContainers =
                    intArrayOf(
                        R.id.widget_event_1,
                        R.id.widget_event_2,
                        R.id.widget_event_3,
                        R.id.widget_event_4,
                    )
                val todayWho =
                    intArrayOf(
                        R.id.widget_event_1_who,
                        R.id.widget_event_2_who,
                        R.id.widget_event_3_who,
                        R.id.widget_event_4_who,
                    )
                val todayActivity =
                    intArrayOf(
                        R.id.widget_event_1_activity,
                        R.id.widget_event_2_activity,
                        R.id.widget_event_3_activity,
                        R.id.widget_event_4_activity,
                    )
                val todayTime =
                    intArrayOf(
                        R.id.widget_event_1_time,
                        R.id.widget_event_2_time,
                        R.id.widget_event_3_time,
                        R.id.widget_event_4_time,
                    )
                visibleToday.forEachIndexed { index, event ->
                    bindRow(
                        views,
                        todayContainers[index],
                        todayWho[index],
                        todayActivity[index],
                        todayTime[index],
                        toWidgetRow(event, members),
                    )
                }
                if (visibleToday.isEmpty() && visibleTomorrow.isEmpty())
                    bindRow(
                        views,
                        R.id.widget_event_1,
                        R.id.widget_event_1_who,
                        R.id.widget_event_1_activity,
                        R.id.widget_event_1_time,
                        WidgetRow("Idag", "Lugnt i kalendern", ""),
                    )
                if (visibleTomorrow.isNotEmpty()) {
                    views.setViewVisibility(R.id.widget_tomorrow_header, View.VISIBLE)
                    val tomorrowContainers =
                        intArrayOf(R.id.widget_tomorrow_1, R.id.widget_tomorrow_2)
                    val tomorrowWho =
                        intArrayOf(R.id.widget_tomorrow_1_who, R.id.widget_tomorrow_2_who)
                    val tomorrowActivity =
                        intArrayOf(R.id.widget_tomorrow_1_activity, R.id.widget_tomorrow_2_activity)
                    val tomorrowTime =
                        intArrayOf(R.id.widget_tomorrow_1_time, R.id.widget_tomorrow_2_time)
                    visibleTomorrow.forEachIndexed { index, event ->
                        bindRow(
                            views,
                            tomorrowContainers[index],
                            tomorrowWho[index],
                            tomorrowActivity[index],
                            tomorrowTime[index],
                            toWidgetRow(event, members),
                        )
                    }
                }
                manager.updateAppWidget(widgetId, views)
            }
            Result.success()
        }
            .getOrElse {
                widgetIds.forEach { widgetId ->
                    val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                    views.setTextViewText(R.id.widget_status, "Kunde inte uppdatera just nu")
                    views.setTextViewText(R.id.widget_todo, "✓  To-Do    ›")
                    views.setTextViewText(R.id.widget_shopping, "🛒  Inköp    ›")
                    views.setTextViewText(R.id.widget_updated, "")
                    manager.updateAppWidget(widgetId, views)
                }
                Result.retry()
            }
    }

    private fun bindRow(
        views: RemoteViews,
        containerId: Int,
        whoId: Int,
        activityId: Int,
        timeId: Int,
        row: WidgetRow,
    ) {
        views.setViewVisibility(containerId, View.VISIBLE)
        views.setTextViewText(whoId, row.who)
        views.setTextViewText(activityId, row.activity)
        views.setTextViewText(timeId, row.time)
    }

    private fun toWidgetRow(event: SyncEvent, members: Map<String, SyncMember>): WidgetRow {
        val who =
            when (event.memberId) {
                null,
                ALL_FAMILY_MEMBER_ID -> "Alla"

                else -> members[event.memberId]?.name?.takeIf { it.isNotBlank() } ?: "Familj"
            }
        val timeText =
            if (event.time.isBlank()) {
                "Ingen tid"
            } else {
                event.endTime?.takeIf { it.isNotBlank() }?.let { "${event.time}–$it" } ?: event.time
            }
        val activity = cleanActivityTitle(event.title, event.time, event.endTime, who)
        return WidgetRow(
            who,
            widgetActivityIcon(activity) + "  " + activity,
            timeText,
        )
    }

    private fun cleanActivityTitle(
        title: String,
        startTime: String,
        endTime: String?,
        who: String,
    ): String {
        var cleaned = title.removePrefix("🌈").trim()
        if (cleaned.startsWith(who, ignoreCase = true)) {
            cleaned = cleaned.drop(who.length).trimStart()
            cleaned = cleaned.trimStart('•', '·', '|', ':', '-', '–').trimStart()
        }
        val ranges = buildList {
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
            cleaned = cleaned.replace(startTime, " ", ignoreCase = true)
        }
        cleaned =
            cleaned
                .replace("  ", " ")
                .replace("  ", " ")
                .trim()
                .trim('•', '·', '-', '–', '|', ':')
                .trim()
        return cleaned.ifBlank { "Aktivitet" }
    }

    private fun widgetActivityIcon(title: String): String {
        val value = title.lowercase()
        return when {
            "tvätt" in value -> "🧺"
            "jobb" in value || "arbete" in value -> "💼"
            "skola" in value || "förskola" in value -> "🎓"
            "träning" in value || "innebandy" in value || "handboll" in value -> "🏃"
            "födelsedag" in value || "kalas" in value -> "🎂"
            else -> "•"
        }
    }

    private fun parseLocalTime(value: String?): LocalTime? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun parseMinute(value: String?): Int? =
        parseLocalTime(value)?.let { it.hour * 60 + it.minute }

    private suspend fun loadOpenTodoCount(session: FamilySession): Int =
        withContext(Dispatchers.IO) {
            val path = "/rest/v1/todo_items?select=checked&family_id=eq.${session.id}"
            val connection = URL("$WIDGET_SUPABASE_URL$path").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", WIDGET_SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $WIDGET_SUPABASE_ANON_KEY")
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
