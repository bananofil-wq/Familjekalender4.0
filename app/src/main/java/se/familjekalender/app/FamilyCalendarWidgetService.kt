package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
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

            val dates = rows.map { it.date }.distinct()
            dates.forEachIndexed { dateIndex, date ->
                if (dateIndex > 0) items += Item.Header("Imorgon")
                val dayRows = rows.filter { it.date == date }
                val groups =
                    dayRows.groupBy { it.memberKey }
                        .values
                        .sortedBy { group ->
                            group.map { it.time }.firstOrNull { it.isNotBlank() } ?: "99:99"
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
                                        group.firstOrNull { it.time.isNotBlank() }?.time.orEmpty()
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

        override fun getViewAt(position: Int): RemoteViews =
            when (val item = items[position]) {
                is Item.Header ->
                    RemoteViews(context.packageName, R.layout.widget_event_header).apply {
                        setTextViewText(R.id.widget_list_header, item.text)
                    }

                is Item.Event ->
                    RemoteViews(context.packageName, R.layout.widget_event_item).apply {
                        setTextViewText(R.id.widget_item_who, item.who)
                        setTextViewText(R.id.widget_item_activity, item.activity)
                        setTextViewText(R.id.widget_item_time, item.time)

                        val fillIn =
                            Intent().apply {
                                putExtra(
                                    FamilyCalendarWidget.EXTRA_ITEM_ACTION,
                                    if (item.canToggle) {
                                        FamilyCalendarWidget.ITEM_ACTION_TOGGLE
                                    } else {
                                        FamilyCalendarWidget.ITEM_ACTION_OPEN
                                    },
                                )
                                item.groupKey?.let {
                                    putExtra(FamilyCalendarWidget.EXTRA_GROUP_KEY, it)
                                }
                                putExtra(FamilyCalendarWidget.EXTRA_WIDGET_ID, widgetId)
                            }
                        setOnClickFillInIntent(R.id.widget_item_root, fillIn)
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
