package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate
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
        )

        private sealed interface Item {
            data class Header(val text: String) : Item

            data class Event(
                val who: String,
                val activity: String,
                val time: String,
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
            val prefs =
                context.getSharedPreferences(
                    FamilyCalendarWidget.WIDGET_PREFS,
                    Context.MODE_PRIVATE,
                )
            val cached =
                prefs.getString(FamilyCalendarWidget.cachedRowsPrefKey(widgetId), "[]")
                    ?: "[]"
            val expanded =
                prefs.getString(FamilyCalendarWidget.expandedGroupPrefKey(widgetId), null)

            val rows = mutableListOf<CachedEvent>()
            runCatching {
                val array = JSONArray(cached)
                repeat(array.length()) { index ->
                    val obj = array.optJSONObject(index) ?: return@repeat
                    rows +=
                        CachedEvent(
                            date = obj.optString("date"),
                            memberKey = obj.optString("memberKey"),
                            who = obj.optString("who"),
                            activity = obj.optString("activity"),
                            time = obj.optString("time"),
                        )
                }
            }

            val today = LocalDate.now().toString()
            val tomorrow = LocalDate.now().plusDays(1).toString()

            rows.map { it.date }
                .distinct()
                .sorted()
                .forEach { date ->
                    val dayRows = rows.filter { it.date == date }

                    if (date == tomorrow) {
                        val count = dayRows.size
                        items +=
                            Item.Header(
                                if (count == 1) {
                                    "Imorgon • 1 aktivitet"
                                } else {
                                    "Imorgon • $count aktiviteter"
                                }
                            )
                    } else if (date != today) {
                        items += Item.Header(date)
                    }

                    val groups =
                        dayRows
                            .groupBy { it.memberKey }
                            .values
                            .sortedBy { group ->
                                group.firstOrNull { it.time.isNotBlank() }?.time ?: "99:99"
                            }

                    groups.forEach { group ->
                        val first = group.first()
                        if (group.size == 1) {
                            items +=
                                Item.Event(
                                    who = first.who,
                                    activity = first.activity,
                                    time = first.time,
                                )
                        } else {
                            val groupKey = "$date|${first.memberKey}"
                            val isExpanded = expanded == groupKey
                            items +=
                                Item.Event(
                                    who = first.who,
                                    activity = "${group.size} aktiviteter",
                                    time =
                                        if (isExpanded) {
                                            ""
                                        } else {
                                            group.firstOrNull { it.time.isNotBlank() }
                                                ?.time
                                                .orEmpty()
                                        },
                                    groupKey = groupKey,
                                    canToggle = true,
                                )

                            if (isExpanded) {
                                group.forEach { event ->
                                    items +=
                                        Item.Event(
                                            who = "",
                                            activity = event.activity,
                                            time = event.time,
                                        )
                                }
                            }
                        }
                    }
                }

            if (items.isEmpty()) {
                items += Item.Event("Idag", "Lugnt i kalendern", "")
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
                    }

                is Item.Event ->
                    RemoteViews(context.packageName, R.layout.widget_event_item).apply {
                        setTextViewText(R.id.widget_item_who, item.who)
                        setTextViewText(R.id.widget_item_activity, item.activity)
                        setTextViewText(R.id.widget_item_time, item.time)

                        if (item.canToggle && !item.groupKey.isNullOrBlank()) {
                            setOnClickFillInIntent(
                                R.id.widget_item_root,
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
                                },
                            )
                        }
                    }
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long =
            when (val value = items[position]) {
                is Item.Header -> ("header:" + value.text + ":" + position).hashCode().toLong()
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
