package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray

/**
 * Today's agenda only. Future days remain available inside the calendar app.
 * Uses the ordinary RemoteViewsService adapter so scrolling works on Pixel Launcher.
 */
class FamilyCalendarWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return Factory(applicationContext, widgetId)
    }

    private class Factory(
        private val context: Context,
        private val widgetId: Int,
    ) : RemoteViewsFactory {

        private data class CachedEvent(
            val eventId: String,
            val date: String,
            val who: String,
            val activity: String,
            val time: String,
            val laundry: Boolean,
            val reminder: Boolean,
            val birthday: Boolean,
            val color: Int,
        )

        private sealed interface Item {
            data class Header(val text: String) : Item

            data class Event(val event: CachedEvent) : Item

            data class Reminder(val event: CachedEvent) : Item
        }

        private val items = mutableListOf<Item>()

        override fun onCreate() = Unit

        override fun onDestroy() {
            items.clear()
        }

        override fun onDataSetChanged() {
            items.clear()
            val prefs = context.getSharedPreferences(
                FamilyCalendarWidget.WIDGET_PREFS, Context.MODE_PRIVATE
            )
            val cached = prefs.getString(
                FamilyCalendarWidget.cachedRowsPrefKey(widgetId), "[]"
            ) ?: "[]"
            val today = LocalDate.now()
            val todayKey = today.toString()
            val todayRows = mutableListOf<CachedEvent>()
            runCatching {
                val array = JSONArray(cached)
                repeat(array.length()) { index ->
                    val obj = array.optJSONObject(index) ?: return@repeat
                    // Cached future events are deliberately not rendered, even offline.
                    if (obj.optString("date") != todayKey) return@repeat
                    val activity = obj.optString("activity")
                    todayRows += CachedEvent(
                        eventId = obj.optString("eventId"),
                        date = todayKey,
                        who = obj.optString("who"),
                        activity = activity,
                        time = obj.optString("time"),
                        laundry = obj.optBoolean("laundry", false),
                        reminder = obj.optBoolean(
                            "reminder", activity.trimStart().startsWith("🔔")
                        ),
                        birthday = obj.optBoolean("birthday", false),
                        color = obj.optInt("color", 0xFF69B9F4.toInt()),
                    )
                }
            }.onFailure {
                android.util.Log.w("FamilyCalendarWidget", "Invalid offline widget rows", it)
            }

            // Reminders (including today's) are displayed only in the pinned weekly footer.
            val activities = todayRows.filterNot { it.reminder || it.birthday }
            val count = activities.size
            val countLabel = if (count == 1) "1 aktivitet" else "$count aktiviteter"
            items += Item.Header("Idag ${today.dayOfMonth}/${today.monthValue}  ·  $countLabel")

            // Every activity is shown directly; no member/day groups hiding today's rows.
            activities.forEach { items += Item.Event(it) }
            if (activities.isEmpty()) {
                items += Item.Event(
                    CachedEvent(
                        eventId = "",
                        date = todayKey,
                        who = "",
                        activity = "Inga aktiviteter idag",
                        time = "",
                        laundry = false,
                        reminder = false,
                        birthday = false,
                        color = 0xFF758595.toInt(),
                    )
                )
            }
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            if (position !in items.indices) {
                return RemoteViews(context.packageName, R.layout.widget_event_item)
            }
            // All five photographic widget skins use the same larger, precise
            // content geometry. Existing Nuvarande/Trä rows remain untouched.
            val premium = WidgetThemeColors.premiumLook(
                WidgetThemeColors.savedChoice(context)
            ) != null
            return when (val item = items[position]) {
                is Item.Header ->
                    RemoteViews(context.packageName, if (premium)
                        R.layout.widget_event_header_premium else R.layout.widget_event_header).apply {
                        setTextViewText(R.id.widget_list_header, item.text)
                        // Header navigates to the calendar; it is never decorative.
                        setOnClickFillInIntent(R.id.widget_list_header, Intent().apply {
                            putExtra(MainActivity.EXTRA_OPEN_TAB, 0)
                        })
                        WidgetThemeColors.applyHeader(context, this)
                    }

                is Item.Event ->
                    RemoteViews(context.packageName, if (premium)
                        R.layout.widget_event_item_premium else R.layout.widget_event_item).apply {
                        val event = item.event
                        // The launcher requires item-specific fill-in intents on
                        // collection views. Without this, row tap targets are inert.
                        setOnClickFillInIntent(R.id.widget_item_root, Intent().apply {
                            putExtra(MainActivity.EXTRA_OPEN_TAB, 0)
                            if (event.eventId.isNotBlank()) {
                                putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, event.eventId)
                            }
                        })
                        setTextViewText(R.id.widget_item_who, event.who)
                        val isLaundry = event.laundry ||
                            event.activity.trimStart().startsWith("tvätt", ignoreCase = true)
                        setTextViewText(
                            R.id.widget_item_activity,
                            if (isLaundry) event.activity
                                .replace(Regex("^(\\s*🧺\\s*)+"), "").trimStart()
                            else event.activity,
                        )
                        setViewVisibility(
                            R.id.widget_item_laundry_icon,
                            if (isLaundry) View.VISIBLE else View.GONE,
                        )
                        setTextViewText(R.id.widget_item_time, event.time)
                        val liveStatus = if (WidgetFeatureSettings.enabled(
                                context, WidgetFeatureSettings.LIVE_STATUS
                            )) widgetLiveStatus(event.time, LocalTime.now()) else ""
                        setTextViewText(R.id.widget_item_live_status, liveStatus)
                        setViewVisibility(
                            R.id.widget_item_live_status,
                            if (liveStatus.isBlank()) View.GONE else View.VISIBLE,
                        )
                        setViewVisibility(
                            R.id.widget_item_time,
                            if (event.time.isBlank()) View.GONE else View.VISIBLE,
                        )
                        WidgetThemeColors.applyEvent(context, this)
                        setInt(R.id.widget_item_accent, "setColorFilter", event.color)
                    }

                is Item.Reminder ->
                    RemoteViews(context.packageName, R.layout.widget_reminder_item).apply {
                        val event = item.event
                        val clean = event.activity.trimStart()
                            .removePrefix("🔔").trimStart()
                        val who = event.who.takeIf { it.isNotBlank() && it != "Alla" }
                        setTextViewText(
                            R.id.widget_reminder_activity,
                            if (who == null) clean else "$who · $clean",
                        )
                        setTextViewText(R.id.widget_reminder_time, event.time)
                        setViewVisibility(
                            R.id.widget_reminder_time,
                            if (event.time.isBlank()) View.GONE else View.VISIBLE,
                        )
                    }
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 3

        override fun getItemId(position: Int): Long =
            when (val item = items[position]) {
                is Item.Header -> ("today:" + item.text).hashCode().toLong()
                is Item.Event -> {
                    val event = item.event
                    ("event:" + event.who + ":" + event.activity + ":" +
                        event.time + ":" + position).hashCode().toLong()
                }
                is Item.Reminder -> {
                    val event = item.event
                    ("reminder:" + event.who + ":" + event.activity + ":" +
                        event.time + ":" + position).hashCode().toLong()
                }
            }

        override fun hasStableIds(): Boolean = true
    }
}
