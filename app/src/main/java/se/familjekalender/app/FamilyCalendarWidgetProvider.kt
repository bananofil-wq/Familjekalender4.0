package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class FamilyCalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FamilyCalendarWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
            val widgetPrefs = context.getSharedPreferences("family_calendar_widget", Context.MODE_PRIVATE)
            val showClock = widgetPrefs.getBoolean("show_clock_$appWidgetId", true)
            val showDate = widgetPrefs.getBoolean("show_date_$appWidgetId", true)
            val showToday = widgetPrefs.getBoolean("show_today_$appWidgetId", true)
            val showTomorrow = widgetPrefs.getBoolean("show_tomorrow_$appWidgetId", true)
            val maxItems = widgetPrefs.getInt("max_items_$appWidgetId", 4).coerceIn(1, 8)

            val views = RemoteViews(context.packageName, R.layout.widget_family_calendar)
            views.setViewVisibility(R.id.widget_clock, if (showClock) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_date, if (showDate) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_today_title, if (showToday) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_today, if (showToday) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_tomorrow_title, if (showTomorrow) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_tomorrow, if (showTomorrow) View.VISIBLE else View.GONE)

            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv", "SE"))
            val formattedDate = today.format(formatter).replaceFirstChar { it.uppercase() }
            views.setTextViewText(R.id.widget_date, formattedDate)

            val themeName = prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
            val theme = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.AUTO)
            val palette = paletteFor(theme, today)
            val accent = palette.accent.value.toLong()
            val accentArgb = Color.argb(
                ((accent shr 24) and 0xFF).toInt(),
                ((accent shr 16) and 0xFF).toInt(),
                ((accent shr 8) and 0xFF).toInt(),
                (accent and 0xFF).toInt()
            )
            views.setTextColor(R.id.widget_today_title, accentArgb)
            views.setTextColor(R.id.widget_tomorrow_title, accentArgb)

            val openIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            manager.updateAppWidget(appWidgetId, views)

            val familyId = prefs.getString("family_id", null)
            if (familyId == null) {
                views.setTextViewText(R.id.widget_today, "Öppna appen och anslut familjen")
                views.setTextViewText(R.id.widget_tomorrow, "")
                manager.updateAppWidget(appWidgetId, views)
                return
            }

            val session = FamilySession(
                familyId,
                prefs.getString("family_name", "Min familj") ?: "Min familj",
                prefs.getString("family_code", "") ?: ""
            )

            scope.launch {
                runCatching {
                    val events = SupabaseSync.loadEvents(session)
                    val members = SupabaseSync.loadMembers(session).associateBy { it.id }
                    val todayEvents = events.filter { it.date == today }.sortedBy { it.time }
                    val tomorrowEvents = events.filter { it.date == today.plusDays(1) }.sortedBy { it.time }
                    views.setTextViewText(R.id.widget_today, formatEvents(todayEvents, members, maxItems))
                    views.setTextViewText(R.id.widget_tomorrow, formatEvents(tomorrowEvents, members, maxItems))
                    manager.updateAppWidget(appWidgetId, views)
                }.onFailure {
                    views.setTextViewText(R.id.widget_today, "Kunde inte uppdatera aktiviteter")
                    manager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private fun formatEvents(events: List<SyncEvent>, members: Map<String, SyncMember>, maxItems: Int): String {
            if (events.isEmpty()) return "Inga aktiviteter"
            val shown = events.take(maxItems).joinToString("\n") { event ->
                val icon = when {
                    event.title.startsWith("🌈") -> "🌈"
                    event.title.startsWith("🧺") -> "🧺"
                    event.memberId == ALL_FAMILY_MEMBER_ID -> "★"
                    else -> "•"
                }
                val title = event.title.removePrefix("🌈").removePrefix("🧺").trim()
                val member = members[event.memberId]?.name?.removePrefix("⭐ ")
                buildString {
                    append(icon).append(' ').append(event.time).append("  ").append(title)
                    if (!member.isNullOrBlank() && event.memberId != ALL_FAMILY_MEMBER_ID) append(" · ").append(member)
                }
            }
            val extra = events.size - maxItems
            return if (extra > 0) "$shown\n+$extra till" else shown
        }
    }
}
