package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class FamilyCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FamilyCalendarWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_family_calendar)
        val today = LocalDate.now()
        val dateText = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv", "SE")))
            .replaceFirstChar { it.uppercase() }
        views.setTextViewText(R.id.widget_date, dateText)
        views.setTextViewText(R.id.widget_status, "Uppdaterar…")
        clearRows(views)
        bindActions(context, views, appWidgetId)
        manager.updateAppWidget(appWidgetId, views)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("family_calendar", 0)
                val id = prefs.getString("family_id", null)
                val name = prefs.getString("family_name", null)
                val code = prefs.getString("family_code", null)
                if (id.isNullOrBlank() || code.isNullOrBlank()) {
                    views.setTextViewText(R.id.widget_status, "Öppna appen och anslut till familjen")
                    manager.updateAppWidget(appWidgetId, views)
                    return@launch
                }

                val session = FamilySession(id, name ?: "Min familj", code)
                val events = SupabaseSync.loadEvents(session)
                    .filter { it.date == today }
                    .sortedBy { it.time }
                    .take(4)
                val members = runCatching { SupabaseSync.loadMembers(session) }.getOrDefault(emptyList())
                    .associateBy { it.id }

                if (events.isEmpty()) {
                    views.setTextViewText(R.id.widget_status, "Inget planerat idag")
                } else {
                    views.setTextViewText(R.id.widget_status, "${events.size} ${if (events.size == 1) "sak" else "saker"} idag")
                    val rowIds = intArrayOf(R.id.widget_event_1, R.id.widget_event_2, R.id.widget_event_3, R.id.widget_event_4)
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
                manager.updateAppWidget(appWidgetId, views)
            } catch (_: Throwable) {
                views.setTextViewText(R.id.widget_status, "Kunde inte uppdatera just nu")
                manager.updateAppWidget(appWidgetId, views)
            } finally {
                pending.finish()
            }
        }
    }

    private fun bindActions(context: Context, views: RemoteViews, appWidgetId: Int) {
        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context,
            appWidgetId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPending)

        val refreshIntent = Intent(context, FamilyCalendarWidget::class.java).apply { action = ACTION_REFRESH }
        val refreshPending = PendingIntent.getBroadcast(
            context,
            appWidgetId + 10_000,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)
    }

    private fun clearRows(views: RemoteViews) {
        intArrayOf(R.id.widget_event_1, R.id.widget_event_2, R.id.widget_event_3, R.id.widget_event_4).forEach {
            views.setViewVisibility(it, View.GONE)
            views.setTextViewText(it, "")
        }
    }

    companion object {
        private const val ACTION_REFRESH = "se.familjekalender.app.WIDGET_REFRESH"
    }
}
