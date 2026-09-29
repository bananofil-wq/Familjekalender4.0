package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONArray

class FamilyCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = baseViews(context, appWidgetId)
            views.setTextViewText(R.id.widget_status, "Uppdaterar…")
            manager.updateAppWidget(appWidgetId, views)
        }
        schedulePeriodicRefresh(context)
        enqueueRefresh(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicRefresh(context)
        enqueueRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                refreshAllWidgetsFromCache(context)
                schedulePeriodicRefresh(context)
                enqueueRefresh(context)
            }

            ACTION_REFRESH -> {
                showUpdating(context)
                enqueueRefresh(context)
            }

            ACTION_ITEM -> {
                val appWidgetId =
                    intent.getIntExtra(
                        EXTRA_WIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID,
                    )
                when (intent.getStringExtra(EXTRA_ITEM_ACTION)) {
                    ITEM_ACTION_TOGGLE -> {
                        val groupKey = intent.getStringExtra(EXTRA_GROUP_KEY)
                        if (
                            appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                            !groupKey.isNullOrBlank()
                        ) {
                            toggleGroup(context, appWidgetId, groupKey)
                        }
                    }

                    ITEM_ACTION_OPEN -> openCalendar(context)
                }
            }

            ACTION_TOGGLE_GROUP -> {
                val appWidgetId =
                    intent.getIntExtra(
                        EXTRA_WIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID,
                    )
                val groupKey = intent.getStringExtra(EXTRA_GROUP_KEY)
                if (
                    appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                    !groupKey.isNullOrBlank()
                ) {
                    toggleGroup(context, appWidgetId, groupKey)
                }
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "se.familjekalender.app.WIDGET_REFRESH"
        private const val ACTION_TOGGLE_GROUP = "se.familjekalender.app.WIDGET_TOGGLE_GROUP"
        internal const val ACTION_ITEM = "se.familjekalender.app.WIDGET_ITEM"
        internal const val EXTRA_WIDGET_ID = "widget_id"
        internal const val EXTRA_GROUP_KEY = "group_key"
        internal const val EXTRA_ITEM_ACTION = "item_action"
        internal const val ITEM_ACTION_TOGGLE = "toggle"
        internal const val ITEM_ACTION_OPEN = "open"
        internal const val WIDGET_PREFS = "family_calendar_widget"
        private const val IMMEDIATE_WORK_NAME = "family_calendar_widget_refresh"
        private const val PERIODIC_WORK_NAME = "family_calendar_widget_periodic_refresh"

        internal fun expandedGroupPrefKey(appWidgetId: Int) = "expanded_group_$appWidgetId"

        internal fun cachedRowsPrefKey(appWidgetId: Int) = "cached_rows_$appWidgetId"

        private fun networkConstraints() =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueueRefresh(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<FamilyCalendarWidgetWorker>()
                    .setConstraints(networkConstraints())
                    .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }

        fun schedulePeriodicRefresh(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<FamilyCalendarWidgetWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(networkConstraints())
                    .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
        }

        fun baseViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_family_calendar)
            val today = LocalDate.now()
            val dateText =
                today
                    .format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv", "SE")))
                    .replaceFirstChar { it.uppercase() }

            views.setTextViewText(
                R.id.widget_badge_day,
                today.dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale("sv", "SE"))
                    .uppercase(Locale("sv", "SE")),
            )
            views.setTextViewText(R.id.widget_badge_number, today.dayOfMonth.toString())
            views.setTextViewText(R.id.widget_date, dateText)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setRemoteAdapter(
                    R.id.widget_event_list,
                    buildInlineCollection(context, appWidgetId),
                )
            } else {
                val serviceIntent =
                    Intent(context, FamilyCalendarWidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = Uri.parse("familjekalender://widget/list/$appWidgetId")
                    }
                views.setRemoteAdapter(R.id.widget_event_list, serviceIntent)
            }

            val templateIntent =
                Intent(context, FamilyCalendarWidget::class.java).apply {
                    action = ACTION_ITEM
                    data = Uri.parse("familjekalender://widget/item/$appWidgetId")
                }
            val templatePending =
                PendingIntent.getBroadcast(
                    context,
                    40_000 + appWidgetId,
                    templateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            views.setPendingIntentTemplate(R.id.widget_event_list, templatePending)

            bindActions(context, views, appWidgetId)
            return views
        }

        private fun toggleGroup(context: Context, appWidgetId: Int, groupKey: String) {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val prefKey = expandedGroupPrefKey(appWidgetId)
            val current = prefs.getString(prefKey, null)
            prefs.edit().putString(prefKey, if (current == groupKey) null else groupKey).apply()

            val manager = AppWidgetManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.updateAppWidget(appWidgetId, baseViews(context, appWidgetId))
            } else {
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_event_list)
            }
        }

        private fun openCalendar(context: Context) {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_TAB, 0)
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }

        private data class CachedWidgetEvent(
            val date: String,
            val memberKey: String,
            val who: String,
            val activity: String,
            val time: String,
        )

        private sealed interface CachedWidgetItem {
            data class Header(val text: String) : CachedWidgetItem

            data class Event(
                val who: String,
                val activity: String,
                val time: String,
                val groupKey: String? = null,
                val canToggle: Boolean = false,
            ) : CachedWidgetItem
        }

        private fun cachedItems(context: Context, appWidgetId: Int): List<CachedWidgetItem> {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val expanded = prefs.getString(expandedGroupPrefKey(appWidgetId), null)
            val raw = prefs.getString(cachedRowsPrefKey(appWidgetId), "[]") ?: "[]"

            val rows =
                buildList {
                    runCatching {
                        val array = JSONArray(raw)
                        repeat(array.length()) { index ->
                            val obj = array.optJSONObject(index) ?: return@repeat
                            add(
                                CachedWidgetEvent(
                                    date = obj.optString("date"),
                                    memberKey = obj.optString("memberKey"),
                                    who = obj.optString("who"),
                                    activity = obj.optString("activity"),
                                    time = obj.optString("time"),
                                )
                            )
                        }
                    }
                }

            if (rows.isEmpty()) return emptyList()

            return buildList {
                rows.map { it.date }.distinct().forEachIndexed { dateIndex, date ->
                    if (dateIndex > 0) add(CachedWidgetItem.Header("Imorgon"))

                    rows.filter { it.date == date }
                        .groupBy { it.memberKey }
                        .values
                        .sortedBy { group ->
                            group.firstOrNull { it.time.isNotBlank() }?.time ?: "99:99"
                        }
                        .forEach { group ->
                            val first = group.first()
                            if (group.size == 1) {
                                add(
                                    CachedWidgetItem.Event(
                                        who = first.who,
                                        activity = first.activity,
                                        time = first.time,
                                    )
                                )
                            } else {
                                val groupKey = "$date|${first.memberKey}"
                                val isExpanded = expanded == groupKey
                                add(
                                    CachedWidgetItem.Event(
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
                                )
                                if (isExpanded) {
                                    group.forEach { event ->
                                        add(
                                            CachedWidgetItem.Event(
                                                who = "",
                                                activity = event.activity,
                                                time = event.time,
                                            )
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        }

        @android.annotation.TargetApi(31)
        private fun buildInlineCollection(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews.RemoteCollectionItems {
            val builder =
                RemoteViews.RemoteCollectionItems.Builder()
                    .setHasStableIds(true)
                    .setViewTypeCount(2)

            val items = cachedItems(context, appWidgetId)
            if (items.isEmpty()) {
                val empty =
                    RemoteViews(context.packageName, R.layout.widget_event_item).apply {
                        setTextViewText(R.id.widget_item_who, "Idag")
                        setTextViewText(R.id.widget_item_activity, "Uppdaterar kalendern…")
                        setTextViewText(R.id.widget_item_time, "")
                        setOnClickFillInIntent(
                            R.id.widget_item_root,
                            Intent().apply {
                                putExtra(EXTRA_ITEM_ACTION, ITEM_ACTION_OPEN)
                                putExtra(EXTRA_WIDGET_ID, appWidgetId)
                            },
                        )
                    }
                builder.addItem(1L, empty)
            } else {
                items.forEachIndexed { index, item ->
                    when (item) {
                        is CachedWidgetItem.Header -> {
                            val row =
                                RemoteViews(
                                    context.packageName,
                                    R.layout.widget_event_header,
                                ).apply {
                                    setTextViewText(R.id.widget_list_header, item.text)
                                    setOnClickFillInIntent(
                                        R.id.widget_list_header,
                                        Intent().apply {
                                            putExtra(EXTRA_ITEM_ACTION, ITEM_ACTION_OPEN)
                                            putExtra(EXTRA_WIDGET_ID, appWidgetId)
                                        },
                                    )
                                }
                            builder.addItem(
                                ("header:${item.text}:$index").hashCode().toLong(),
                                row,
                            )
                        }

                        is CachedWidgetItem.Event -> {
                            val row =
                                RemoteViews(
                                    context.packageName,
                                    R.layout.widget_event_item,
                                ).apply {
                                    setTextViewText(R.id.widget_item_who, item.who)
                                    setTextViewText(R.id.widget_item_activity, item.activity)
                                    setTextViewText(R.id.widget_item_time, item.time)
                                    setOnClickFillInIntent(
                                        R.id.widget_item_root,
                                        Intent().apply {
                                            putExtra(
                                                EXTRA_ITEM_ACTION,
                                                if (item.canToggle) {
                                                    ITEM_ACTION_TOGGLE
                                                } else {
                                                    ITEM_ACTION_OPEN
                                                },
                                            )
                                            item.groupKey?.let {
                                                putExtra(EXTRA_GROUP_KEY, it)
                                            }
                                            putExtra(EXTRA_WIDGET_ID, appWidgetId)
                                        },
                                    )
                                }
                            builder.addItem(
                                (
                                    "event:${item.who}:${item.activity}:${item.time}:" +
                                        (item.groupKey ?: "") +
                                        ":$index"
                                ).hashCode().toLong(),
                                row,
                            )
                        }
                    }
                }
            }

            return builder.build()
        }

        private fun refreshAllWidgetsFromCache(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FamilyCalendarWidget::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, baseViews(context, appWidgetId))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    manager.notifyAppWidgetViewDataChanged(
                        appWidgetId,
                        R.id.widget_event_list,
                    )
                }
            }
        }

        private fun showUpdating(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FamilyCalendarWidget::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                val views = baseViews(context, appWidgetId)
                views.setTextViewText(R.id.widget_status, "Uppdaterar…")
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        internal fun openCalendarPendingIntent(
            context: Context,
            appWidgetId: Int,
            requestOffset: Int,
        ): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_TAB, 0)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            return PendingIntent.getActivity(
                context,
                appWidgetId + requestOffset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun toggleGroupPendingIntent(
            context: Context,
            appWidgetId: Int,
            groupKey: String,
            requestOffset: Int,
        ): PendingIntent {
            val intent =
                Intent(context, FamilyCalendarWidget::class.java).apply {
                    action = ACTION_TOGGLE_GROUP
                    data =
                        Uri.parse(
                            "familjekalender://widget/group/$appWidgetId/${Uri.encode(groupKey)}",
                        )
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    putExtra(EXTRA_GROUP_KEY, groupKey)
                }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId + requestOffset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun bindActions(context: Context, views: RemoteViews, appWidgetId: Int) {
            val openCalendar = openCalendarPendingIntent(context, appWidgetId, 0)
            views.setOnClickPendingIntent(R.id.widget_root, openCalendar)
            views.setOnClickPendingIntent(
                R.id.widget_badge_day,
                openCalendarPendingIntent(context, appWidgetId, 1_000),
            )
            views.setOnClickPendingIntent(
                R.id.widget_badge_number,
                openCalendarPendingIntent(context, appWidgetId, 1_001),
            )
            views.setOnClickPendingIntent(
                R.id.widget_date,
                openCalendarPendingIntent(context, appWidgetId, 1_002),
            )
            views.setOnClickPendingIntent(
                R.id.widget_status,
                openCalendarPendingIntent(context, appWidgetId, 1_003),
            )

            fun openTabPendingIntent(tab: Int, requestOffset: Int): PendingIntent {
                val intent =
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                return PendingIntent.getActivity(
                    context,
                    appWidgetId + requestOffset,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            views.setOnClickPendingIntent(R.id.widget_todo, openTabPendingIntent(2, 20_000))
            views.setOnClickPendingIntent(R.id.widget_shopping, openTabPendingIntent(1, 30_000))

            val refreshIntent =
                Intent(context, FamilyCalendarWidget::class.java).apply {
                    action = ACTION_REFRESH
                }
            val refreshPending =
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId + 10_000,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)
        }
    }
}
