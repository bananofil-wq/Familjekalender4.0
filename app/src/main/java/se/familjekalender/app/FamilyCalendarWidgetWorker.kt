package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                    manager.updateAppWidget(widgetId, views)
                    return@forEach
                }

                val session = FamilySession(id, name ?: "Min familj", code)
                val today = LocalDate.now()
                val events = SupabaseSync.loadEvents(session)
                    .filter { it.date == today }
                    .sortedBy { it.time }
                    .take(4)
                val members = runCatching { SupabaseSync.loadMembers(session) }
                    .getOrDefault(emptyList())
                    .associateBy { it.id }

                if (events.isEmpty()) {
                    views.setTextViewText(R.id.widget_status, "Inget planerat idag • ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}")
                } else {
                    views.setTextViewText(
                        R.id.widget_status,
                        "${events.size} ${if (events.size == 1) "sak" else "saker"} idag • ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    )
                    val rowIds = intArrayOf(
                        R.id.widget_event_1,
                        R.id.widget_event_2,
                        R.id.widget_event_3,
                        R.id.widget_event_4
                    )
                    events.forEachIndexed { index, event ->
                        val who = when (event.memberId) {
                            null, ALL_FAMILY_MEMBER_ID -> "Alla"
                            else -> members[event.memberId]?.name.orEmpty()
                        }
                        val title = event.title.removePrefix("🌈").trim()
                        val suffix = if (who.isBlank()) "" else " • $who"
                        views.setViewVisibility(rowIds[index], View.VISIBLE)
                        views.setTextViewText(rowIds[index], "${event.time}  $title$suffix")
                    }
                }
                manager.updateAppWidget(widgetId, views)
            }
            Result.success()
        }.getOrElse {
            widgetIds.forEach { widgetId ->
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                views.setTextViewText(R.id.widget_status, "Kunde inte uppdatera just nu")
                manager.updateAppWidget(widgetId, views)
            }
            Result.retry()
        }
    }
}
