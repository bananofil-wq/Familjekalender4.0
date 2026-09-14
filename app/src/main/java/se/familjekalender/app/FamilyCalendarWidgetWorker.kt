package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val WIDGET_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val WIDGET_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

class FamilyCalendarWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

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
                    views.setTextViewText(R.id.widget_status, "Öppna appen och anslut till familjen")
                    views.setTextViewText(R.id.widget_summary, "Familjekoll aktiveras när appen är ansluten")
                    manager.updateAppWidget(widgetId, views)
                    return@forEach
                }

                val session = FamilySession(id, name ?: "Min familj", code)
                val today = LocalDate.now()
                val now = LocalTime.now()
                val allEvents = SupabaseSync.loadEvents(session)
                val todaysEvents = allEvents
                    .filter { it.date == today }
                    .sortedBy { it.time }
                val tomorrowsEvents = allEvents
                    .filter { it.date == today.plusDays(1) }
                    .sortedBy { it.time }
                val members = runCatching { SupabaseSync.loadMembers(session) }
                    .getOrDefault(emptyList())
                    .associateBy { it.id }
                val openShopping = runCatching { SupabaseSync.loadShopping(session).count { !it.checked } }
                    .getOrDefault(0)
                val openTodos = runCatching { loadOpenTodoCount(session) }
                    .getOrDefault(0)
                val conflicts = countConflicts(todaysEvents)

                val upcomingToday = todaysEvents
                    .filter { event ->
                        runCatching { LocalTime.parse(event.time) }.getOrNull()?.let { !it.isBefore(now.minusMinutes(15)) } ?: true
                    }
                    .take(4)
                val tomorrowSlots = (4 - upcomingToday.size).coerceAtLeast(0)
                val upcomingTomorrow = tomorrowsEvents.take(tomorrowSlots)

                val status = when {
                    conflicts > 0 -> "⚠ $conflicts ${if (conflicts == 1) "krock" else "krockar"} idag"
                    todaysEvents.isEmpty() -> "Ingen aktivitet planerad idag"
                    else -> "✓ ${todaysEvents.size} ${if (todaysEvents.size == 1) "aktivitet" else "aktiviteter"} idag"
                }
                views.setTextViewText(R.id.widget_status, status)
                views.setTextViewText(
                    R.id.widget_summary,
                    "✓ $openTodos To-Do   •   🛒 $openShopping inköp   •   ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                )

                val rowIds = intArrayOf(
                    R.id.widget_event_1,
                    R.id.widget_event_2,
                    R.id.widget_event_3,
                    R.id.widget_event_4
                )

                val widgetItems = buildList {
                    upcomingToday.forEach { add(false to it) }
                    upcomingTomorrow.forEach { add(true to it) }
                }

                if (widgetItems.isEmpty()) {
                    views.setViewVisibility(R.id.widget_event_1, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_event_1,
                        if (todaysEvents.isEmpty() && tomorrowsEvents.isEmpty()) "Lugnt idag och imorgon" else "Inget mer tidsatt idag"
                    )
                } else {
                    widgetItems.forEachIndexed { index, (isTomorrow, event) ->
                        val who = when (event.memberId) {
                            null, ALL_FAMILY_MEMBER_ID -> "Alla"
                            else -> members[event.memberId]?.name.orEmpty()
                        }
                        val title = event.title.removePrefix("🌈").trim()
                        val suffix = if (who.isBlank()) "" else " • $who"
                        val prefix = if (isTomorrow) "Imorgon ${event.time}" else event.time
                        views.setViewVisibility(rowIds[index], View.VISIBLE)
                        views.setTextViewText(rowIds[index], "$prefix  $title$suffix")
                    }
                }

                manager.updateAppWidget(widgetId, views)
            }
            Result.success()
        }.getOrElse {
            widgetIds.forEach { widgetId ->
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                views.setTextViewText(R.id.widget_status, "Kunde inte uppdatera just nu")
                views.setTextViewText(R.id.widget_summary, "Tryck ↻ för att försöka igen")
                manager.updateAppWidget(widgetId, views)
            }
            Result.retry()
        }
    }

    private suspend fun loadOpenTodoCount(session: FamilySession): Int = withContext(Dispatchers.IO) {
        val path = "/rest/v1/todo_items?select=checked&family_id=eq.${session.id}"
        val connection = URL("$WIDGET_SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", WIDGET_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $WIDGET_SUPABASE_ANON_KEY")
        connection.setRequestProperty("x-family-code", session.code.uppercase())
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) return@withContext 0
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

    private fun countConflicts(events: List<SyncEvent>): Int {
        val grouped = events
            .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
            .groupBy { it.memberId }
        var conflicts = 0
        grouped.values.forEach { memberEvents ->
            val ranges = memberEvents.mapNotNull { event ->
                val start = runCatching { LocalTime.parse(event.time) }.getOrNull() ?: return@mapNotNull null
                val startMinutes = start.hour * 60 + start.minute
                val parsedEnd = event.endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                var endMinutes = parsedEnd?.let { it.hour * 60 + it.minute } ?: (startMinutes + 60)
                if (endMinutes <= startMinutes) endMinutes += 24 * 60
                startMinutes to endMinutes
            }.sortedBy { it.first }
            for (i in ranges.indices) {
                for (j in i + 1 until ranges.size) {
                    if (ranges[j].first >= ranges[i].second) break
                    conflicts++
                }
            }
        }
        return conflicts
    }
}
