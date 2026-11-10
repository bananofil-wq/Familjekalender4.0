package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import org.json.JSONArray

class FamilyCalendarWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId =
            intent.getIntExtra(
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
            val date: String,
            val memberKey: String,
            val who: String,
            val activity: String,
            val time: String,
            val laundry: Boolean,
        )

        private sealed interface Item {
            data class Header(val text: String, val dayKey: String) : Item

            data class Event(
                val who: String,
                val activity: String,
                val time: String,
                val laundry: Boolean = false,
                val groupKey: String? = null,
                val canToggle: Boolean = false,
            ) : Item
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
            val expandedMember = prefs.getString(
                FamilyCalendarWidget.expandedGroupPrefKey(widgetId), null
            )
            val expandedDay = prefs.getString(
                FamilyCalendarWidget.expandedDayPrefKey(widgetId), null
            )
            val rows = mutableListOf<CachedEvent>()
            runCatching {
                val array = JSONArray(cached)
                repeat(array.length()) { index ->
                    val obj = array.optJSONObject(index) ?: return@repeat
                    rows += CachedEvent(
                        date = obj.optString("date"),
                        memberKey = obj.optString("memberKey"),
                        who = obj.optString("who"),
                        activity = obj.optString("activity"),
                        time = obj.optString("time"),
                        laundry = obj.optBoolean("laundry", false),
                    )
                }
            }

            // All seven weekdays are always present, even on days without events.
            // The user expands a day before its rows are shown.
            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            repeat(7) { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val dateKey = date.toString()
                val dayRows = rows.filter { it.date == dateKey }
                val name = when (date) {
                    today -> "Idag"
                    today.plusDays(1) -> "Imorgon"
                    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))
                        .replaceFirstChar { it.uppercase(Locale("sv", "SE")) }
                }
                val count = dayRows.size
                val activityCount = if (count == 1) "1 aktivitet" else "$count aktiviteter"
                val isExpanded = expandedDay == dateKey
                items += Item.Header(
                    "$name ${date.dayOfMonth}/${date.monthValue} · $activityCount ${if (isExpanded) "▴" else "▾"}",
                    dayKey = dateKey,
                )
                if (!isExpanded) return@repeat

                if (dayRows.isEmpty()) {
                    items += Item.Event(who = "", activity = "Inga aktiviteter", time = "")
                    return@repeat
                }

                val groups = dayRows.groupBy { it.memberKey }.values
                    .sortedBy { group ->
                        group.firstOrNull { it.time.isNotBlank() }?.time ?: "99:99"
                    }
                groups.forEach { group ->
                    val first = group.first()
                    if (group.size == 1) {
                        items += Item.Event(
                            who = first.who,
                            activity = first.activity,
                            time = first.time,
                            laundry = first.laundry,
                        )
                    } else {
                        val groupKey = "$dateKey|${first.memberKey}"
                        val memberIsExpanded = expandedMember == groupKey
                        items += Item.Event(
                            who = first.who,
                            activity = "${group.size} aktiviteter ${if (memberIsExpanded) "▴" else "▾"}",
                            time = if (memberIsExpanded) "" else
                                group.firstOrNull { it.time.isNotBlank() }?.time.orEmpty(),
                            groupKey = groupKey,
                            canToggle = true,
                        )
                        if (memberIsExpanded) {
                            group.forEach { event ->
                                items += Item.Event(
                                    who = "",
                                    activity = event.activity,
                                    time = event.time,
                                    laundry = event.laundry,
                                )
                            }
                        }
                    }
                }
            }
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            if (position !in items.indices) {
                return RemoteViews(context.packageName, R.layout.widget_event_item)
            }

            return when (val item = items[position]) {
                is Item.Header ->
                    RemoteViews(context.packageName, R.layout.widget_event_header).apply {
                        setTextViewText(R.id.widget_list_header, item.text)
                        WidgetThemeColors.applyHeader(context, this)
                        setOnClickFillInIntent(
                            R.id.widget_list_header,
                            Intent().apply {
                                putExtra(FamilyCalendarWidget.EXTRA_ITEM_ACTION, FamilyCalendarWidget.ITEM_ACTION_TOGGLE_DAY)
                                putExtra(FamilyCalendarWidget.EXTRA_GROUP_KEY, item.dayKey)
                                putExtra(FamilyCalendarWidget.EXTRA_WIDGET_ID, widgetId)
                            },
                        )
                    }

                is Item.Event ->
                    RemoteViews(context.packageName, R.layout.widget_event_item).apply {
                        setTextViewText(R.id.widget_item_who, item.who)
                        val laundry = item.laundry ||
                            item.activity.trimStart().startsWith("tvätt", ignoreCase = true)
                        setTextViewText(
                            R.id.widget_item_activity,
                            if (laundry) item.activity.replace(Regex("^(\\s*🧺\\s*)+"), "").trimStart()
                            else item.activity,
                        )
                        setViewVisibility(
                            R.id.widget_item_laundry_icon,
                            if (laundry) android.view.View.VISIBLE else android.view.View.GONE,
                        )
                        setTextViewText(R.id.widget_item_time, item.time)
                        WidgetThemeColors.applyEvent(context, this)

                        if (item.canToggle && !item.groupKey.isNullOrBlank()) {
                            // Pixel Launcher kan skicka klicket från TextView-barnet i raden
                            // i stället för från radens root. Bind därför samma fill-in-intent
                            // till hela raden och samtliga synliga barn.
                            fun toggleIntent() =
                                Intent().apply {
                                    putExtra(
                                        FamilyCalendarWidget.EXTRA_ITEM_ACTION,
                                        FamilyCalendarWidget.ITEM_ACTION_TOGGLE,
                                    )
                                    putExtra(
                                        FamilyCalendarWidget.EXTRA_GROUP_KEY,
                                        item.groupKey,
                                    )
                                    putExtra(
                                        FamilyCalendarWidget.EXTRA_WIDGET_ID,
                                        widgetId,
                                    )
                                }

                            setOnClickFillInIntent(R.id.widget_item_root, toggleIntent())
                            setOnClickFillInIntent(R.id.widget_item_who, toggleIntent())
                            setOnClickFillInIntent(R.id.widget_item_activity, toggleIntent())
                            setOnClickFillInIntent(R.id.widget_item_time, toggleIntent())
                        }
                    }
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long =
            when (val value = items[position]) {
                is Item.Header -> ("header:" + value.dayKey).hashCode().toLong()
                is Item.Event ->
                    (
                        "event:" +
                            value.who +
                            ":" +
                            value.activity +
                            ":" +
                            value.time +
                            ":" +
                            (value.groupKey ?: "") +
                            ":" +
                            position
                    ).hashCode().toLong()
            }

        override fun hasStableIds(): Boolean = true
    }
}
