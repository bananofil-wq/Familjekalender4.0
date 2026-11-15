package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.widget.RemoteViews
import java.time.LocalDate
import org.json.JSONArray

/**
 * Optional third look for the existing FamilyCalendarWidget provider.
 *
 * TextClock elements in widget_family_calendar_aurora.xml update themselves on the
 * launcher (minute ticks, time zone, daylight saving time and midnight). We never
 * rasterize an example clock or run a per-minute background worker.
 */
internal object AuroraWidgetViews {
    fun build(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_family_calendar_aurora)
        val prefs = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
        val familyId = prefs.getString("family_id", null)
        val familyCode = prefs.getString("family_code", null)
        val session = if (!familyId.isNullOrBlank() && !familyCode.isNullOrBlank()) {
            FamilySession(familyId, prefs.getString("family_name", "Min familj") ?: "Min familj", familyCode)
        } else null

        fun navigate(tab: Int, offset: Int, eventId: String? = null): PendingIntent {
            val uri = Uri.parse("familjekalender://aurora/$widgetId/$tab/$offset")
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
                if (!eventId.isNullOrBlank()) putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, eventId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, widgetId * 10 + offset, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        views.setOnClickPendingIntent(R.id.widget_aurora_date, navigate(0, 1))
        views.setOnClickPendingIntent(R.id.widget_aurora_time, navigate(0, 2))
        views.setOnClickPendingIntent(R.id.widget_todo, navigate(2, 3))
        views.setOnClickPendingIntent(R.id.widget_shopping, navigate(1, 4))
        views.setOnClickPendingIntent(R.id.widget_aurora_activity_tab, navigate(0, 5))

        // Phone battery, not the hard-coded 84% shown in the reference.
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val charging = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val chargeIcon = if (
            charging == BatteryManager.BATTERY_STATUS_CHARGING ||
            charging == BatteryManager.BATTERY_STATUS_FULL
        ) " ⚡" else ""
        views.setTextViewText(
            R.id.widget_aurora_battery,
            (percent?.toString()?.plus("%") ?: "—%") + chargeIcon,
        )

        // Read existing weather cache; no misleading fixed "8°" when disconnected.
        val weatherPrefs = context.getSharedPreferences("familjekalender_clean_weather", Context.MODE_PRIVATE)
        val refreshedAt = weatherPrefs.getLong("updated_at", 0L)
        val fresh = refreshedAt > 0L && System.currentTimeMillis() - refreshedAt in 0L..1_800_000L
        val temperature = if (fresh && weatherPrefs.contains("temperature")) {
            weatherPrefs.getInt("temperature", 0).toString() + "°"
        } else "—°"
        views.setTextViewText(R.id.widget_aurora_weather, "Staffanstorp • $temperature")

        val todo = session?.let { FamilyOfflineCache.loadTodo(context, it) }.orEmpty()
        val shopping = session?.let { FamilyOfflineCache.loadShopping(context, it) }.orEmpty()
        val total = todo.size + shopping.size
        val done = todo.count { it.checked } + shopping.count { it.checked }
        val progress = if (total > 0) ((done * 100.0) / total + 0.5).toInt() else 0
        views.setTextViewText(R.id.widget_aurora_progress_pct, "$progress%")
        views.setImageViewBitmap(R.id.widget_aurora_dynamic_ring,
            AuroraProgressArtwork.bitmap(progress))

        val oldWidgetCache = context.getSharedPreferences(
            FamilyCalendarWidget.WIDGET_PREFS, Context.MODE_PRIVATE
        )
        val cacheFamily = oldWidgetCache.getString(
            FamilyCalendarWidget.cachedFamilyPrefKey(widgetId), null
        )
        val today = LocalDate.now()
        val eventsFromWidget = if (session != null &&
            (cacheFamily == null || cacheFamily == session.id)
        ) {
            val raw = oldWidgetCache.getString(
                FamilyCalendarWidget.cachedRowsPrefKey(widgetId), "[]"
            ) ?: "[]"
            val rows = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
            (0 until rows.length()).mapNotNull { index ->
                val event = rows.optJSONObject(index) ?: return@mapNotNull null
                if (event.optString("date") != today.toString() ||
                    event.optBoolean("reminder", false) ||
                    event.optBoolean("birthday", false)
                ) null else Triple(
                    event.optString("time"), event.optString("activity"), event.optString("eventId")
                )
            }
        } else emptyList()

        val visible = if (eventsFromWidget.isNotEmpty()) {
            eventsFromWidget
        } else {
            session?.let { FamilyOfflineCache.loadEvents(context, it) }.orEmpty()
                .filter {
                    it.date == today && !it.title.trimStart().startsWith("🔔") &&
                        !it.source.contains("reminder", ignoreCase = true) &&
                        !it.title.trimStart().startsWith("🌈")
                }.sortedWith(compareBy({ it.time }, { it.title }))
                .map { Triple(it.time, it.title, it.id) }
        }.take(2)

        val first = visible.getOrNull(0)
        val second = visible.getOrNull(1)
        fun line(event: Triple<String, String, String>?): String {
            if (event == null) return ""
            val time = event.first.substringBefore("–").substringBefore("-").trim()
            val name = event.second.trim().removePrefix("🧺").trim()
            return if (time.isBlank()) name else "$time • $name"
        }
        views.setTextViewText(
            R.id.widget_aurora_event1,
            if (session == null) "Öppna appen och anslut till familjen"
            else if (first == null) "Inga aktiviteter idag" else line(first),
        )
        views.setTextViewText(R.id.widget_aurora_event2, line(second))
        views.setOnClickPendingIntent(
            R.id.widget_aurora_event1, navigate(0, 6, first?.third)
        )
        views.setOnClickPendingIntent(
            R.id.widget_aurora_event2, navigate(0, 7, second?.third)
        )
        // The × is not a destructive action. Open the event in the calendar instead.
        views.setOnClickPendingIntent(
            R.id.widget_aurora_event2_more, navigate(0, 8, second?.third)
        )
        return views
    }
}
