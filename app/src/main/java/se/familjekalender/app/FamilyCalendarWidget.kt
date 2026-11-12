package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.view.View
import org.json.JSONArray
import androidx.compose.ui.graphics.toArgb
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

class FamilyCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = baseViews(context, appWidgetId)
            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_event_list)
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
                val groupKey = intent.getStringExtra(EXTRA_GROUP_KEY)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !groupKey.isNullOrBlank()) {
                    when (intent.getStringExtra(EXTRA_ITEM_ACTION)) {
                        ITEM_ACTION_TOGGLE -> toggleGroup(context, appWidgetId, groupKey)
                        ITEM_ACTION_TOGGLE_DAY -> toggleDay(context, appWidgetId, groupKey)
                    }
                }
            }

            // Behålls för redan utplacerade widgetar som kan ha ett äldre PendingIntent cacheat.
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
        internal const val ITEM_ACTION_TOGGLE_DAY = "toggle_day"
        internal const val WIDGET_PREFS = "family_calendar_widget"
        private const val IMMEDIATE_WORK_NAME = "family_calendar_widget_refresh"
        private const val PERIODIC_WORK_NAME = "family_calendar_widget_periodic_refresh"

        internal fun expandedGroupPrefKey(appWidgetId: Int) = "expanded_group_$appWidgetId"

        internal fun expandedDayPrefKey(appWidgetId: Int) = "expanded_day_$appWidgetId"

        internal fun expandedDayUpdatedOnPrefKey(appWidgetId: Int) = "expanded_day_updated_on_$appWidgetId"

        internal fun collapsedTodayPrefKey(appWidgetId: Int) = "collapsed_today_$appWidgetId"

        internal fun cachedSummaryPrefKey(appWidgetId: Int) = "cached_summary_$appWidgetId"

        internal fun cachedRowsPrefKey(appWidgetId: Int) = "cached_rows_$appWidgetId"

        internal fun cachedFamilyPrefKey(appWidgetId: Int) = "cached_family_$appWidgetId"

        /**
         * Publish the same validated list count shown by the app immediately,
         * without waiting for the periodic widget worker.
         * An empty successful list intentionally writes 0 (not the old cached count).
         */
        fun publishTodoSnapshot(
            context: Context, session: FamilySession, items: List<SyncTodoItem>,
        ) {
            publishCounter(context, session, "todo", widgetTodoLabel(widgetOpenTodoCount(items)))
        }

        fun publishShoppingSnapshot(
            context: Context, session: FamilySession, items: List<SyncShoppingItem>,
        ) {
            publishCounter(context, session, "shopping",
                widgetShoppingLabel(widgetOpenShoppingCount(items)))
        }

        private fun publishCounter(
            context: Context, session: FamilySession, key: String, label: String,
        ) {
            val appFamilyId = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
                .getString("family_id", null)
            if (appFamilyId != session.id) return

            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FamilyCalendarWidget::class.java)
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val viewId = if (key == "todo") R.id.widget_todo else R.id.widget_shopping
            manager.getAppWidgetIds(component).forEach { widgetId ->
                val cacheFamilyId = prefs.getString(cachedFamilyPrefKey(widgetId), null)
                if (cacheFamilyId != null && cacheFamilyId != session.id) return@forEach

                val old = prefs.getString(cachedSummaryPrefKey(widgetId), null)
                val values = old?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
                    ?: org.json.JSONObject()
                values.put(key, label)
                prefs.edit().putString(cachedSummaryPrefKey(widgetId), values.toString()).apply()
                val views = RemoteViews(context.packageName, R.layout.widget_family_calendar)
                views.setTextViewText(viewId, label)
                manager.partiallyUpdateAppWidget(widgetId, views)
            }
        }


        private fun networkConstraints() =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueueRefresh(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<FamilyCalendarWidgetWorker>()
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
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val summary = prefs.getString(cachedSummaryPrefKey(appWidgetId), null)
            val values = summary?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            val today = LocalDate.now()
            val savedDate = values?.optString("date").orEmpty()
            val summaryStatus = if (savedDate.isEmpty() || savedDate == today.toString())
                values?.optString("status") else null
            val fallbackStatus = if (prefs.contains(cachedRowsPrefKey(appWidgetId)))
                "Visar sparade aktiviteter" else "Uppdaterar…"
            views.setTextViewText(R.id.widget_status, summaryStatus ?: fallbackStatus)
            views.setTextViewText(R.id.widget_todo, values?.optString("todo") ?: "✓  To-Do    ›")
            views.setTextViewText(R.id.widget_shopping, values?.optString("shopping") ?: "🛒  Inköp    ›")
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

            // Använd den etablerade RemoteViewsService-vägen på alla Android-versioner.
            // Då får Pixel Launcher en vanlig scrollbar ListView utan den inline-adapter
            // som tidigare gjorde hela widgeten oläsbar.
            val serviceIntent =
                Intent(context, FamilyCalendarWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("familjekalender://widget/list/$appWidgetId")
                }
            views.setRemoteAdapter(R.id.widget_event_list, serviceIntent)

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
            showWeeklyReminders(context, appWidgetId, views, prefs, today)
            WidgetThemeColors.apply(context, views)
            return views
        }

        /**
         * Keep a compact week preview pinned below the scrollable daily agenda.
         * Uses the existing (family-scoped) widget cache: no second network request
         * and the content remains available during temporary sync failures.
         */
        private fun showWeeklyReminders(
            context: Context,
            appWidgetId: Int,
            views: RemoteViews,
            prefs: android.content.SharedPreferences,
            today: LocalDate,
        ) {
            val raw = prefs.getString(cachedRowsPrefKey(appWidgetId), null)
            val reminders = buildList {
                val json = runCatching { JSONArray(raw ?: "[]") }.getOrNull()
                    ?: JSONArray()
                repeat(json.length()) { index ->
                    val row = json.optJSONObject(index) ?: return@repeat
                    val title = row.optString("activity").trim()
                    if (!row.optBoolean("reminder", title.startsWith("🔔"))) return@repeat
                    val date = runCatching { LocalDate.parse(row.optString("date")) }
                        .getOrNull() ?: return@repeat
                    val cleaned = title.removePrefix("🔔").trim()
                    if (cleaned.isBlank()) return@repeat
                    add(
                        WidgetWeeklyReminder(
                            date = date,
                            title = cleaned,
                            time = row.optString("time"),
                            person = row.optString("who"),
                        )
                    )
                }
            }

            val preview = weeklyReminderPreview(today, reminders, maxVisible = 3)
            views.setViewVisibility(
                R.id.widget_week_reminders,
                if (preview.visible.isEmpty()) View.GONE else View.VISIBLE,
            )
            val rowIds = intArrayOf(
                R.id.widget_week_reminder_1,
                R.id.widget_week_reminder_2,
                R.id.widget_week_reminder_3,
            )
            rowIds.forEachIndexed { index, viewId ->
                val reminder = preview.visible.getOrNull(index)
                views.setViewVisibility(
                    viewId, if (reminder == null) View.GONE else View.VISIBLE,
                )
                views.setTextViewText(
                    viewId, reminder?.let { weeklyReminderLabel(it) } ?: "",
                )
            }
            val hasMore = preview.extraCount > 0
            views.setViewVisibility(
                R.id.widget_week_reminder_more,
                if (hasMore) View.VISIBLE else View.GONE,
            )
            views.setTextViewText(
                R.id.widget_week_reminder_more,
                if (hasMore) "+${preview.extraCount} fler i kalendern  ›" else "",
            )

            // Only the week summary opens the calendar; the daily ListView remains
            // independent and scrollable as before.
            views.setOnClickPendingIntent(
                R.id.widget_week_reminders_title,
                openCalendarPendingIntent(context, appWidgetId, 1_020),
            )
            rowIds.forEachIndexed { index, viewId ->
                views.setOnClickPendingIntent(
                    viewId, openCalendarPendingIntent(context, appWidgetId, 1_021 + index),
                )
            }
            views.setOnClickPendingIntent(
                R.id.widget_week_reminder_more,
                openCalendarPendingIntent(context, appWidgetId, 1_025),
            )
        }

        private fun toggleGroup(context: Context, appWidgetId: Int, groupKey: String) {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val prefKey = expandedGroupPrefKey(appWidgetId)
            val current = prefs.getString(prefKey, null)
            prefs.edit()
                .putString(prefKey, if (current == groupKey) null else groupKey)
                .apply()

            AppWidgetManager.getInstance(context)
                .notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_event_list)
        }

        private fun toggleDay(context: Context, appWidgetId: Int, dateKey: String) {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val todayKey = LocalDate.now().toString()
            val key = expandedDayPrefKey(appWidgetId)
            val expanded = prefs.getString(key, null)
            val editor = prefs.edit()

            // Today's activities stay visible even when another day is manually opened.
            // A deliberate tap on Today may still collapse/expand them until midnight.
            val hasTodayActivities = runCatching {
                val rows = org.json.JSONArray(
                    prefs.getString(cachedRowsPrefKey(appWidgetId), "[]") ?: "[]"
                )
                (0 until rows.length()).any { index ->
                    rows.optJSONObject(index)?.optString("date") == todayKey
                }
            }.getOrDefault(false)

            if (dateKey == todayKey && hasTodayActivities) {
                val collapsedKey = collapsedTodayPrefKey(appWidgetId)
                if (prefs.getString(collapsedKey, null) == todayKey) {
                    editor.remove(collapsedKey)
                } else {
                    editor.putString(collapsedKey, todayKey)
                }
                // Legacy manual Today selection must not defeat the explicit collapse.
                if (expanded == todayKey) {
                    editor.remove(key).remove(expandedDayUpdatedOnPrefKey(appWidgetId))
                }
            } else if (expanded == dateKey) {
                editor.remove(key).remove(expandedDayUpdatedOnPrefKey(appWidgetId))
            } else {
                editor.putString(key, dateKey)
                    .putString(expandedDayUpdatedOnPrefKey(appWidgetId), todayKey)
            }
            editor.apply()
            AppWidgetManager.getInstance(context)
                .notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_event_list)
        }

        /**
         * Restyle existing widgets WITHOUT rebuilding their RemoteViews or blanking their
         * To-Do/shopping counts, date status and pending click intents.
         */
        fun refreshTheme(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FamilyCalendarWidget::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                val styleOnly = RemoteViews(context.packageName, R.layout.widget_family_calendar)
                WidgetThemeColors.apply(context, styleOnly)
                manager.partiallyUpdateAppWidget(appWidgetId, styleOnly)
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_event_list)
            }
        }

        private fun refreshAllWidgetsFromCache(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FamilyCalendarWidget::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                manager.updateAppWidget(appWidgetId, baseViews(context, appWidgetId))
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_event_list)
            }
        }

        private fun showUpdating(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FamilyCalendarWidget::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                val views = baseViews(context, appWidgetId)
                // Keep existing activities and counts visible during a manual refresh.
                manager.updateAppWidget(appWidgetId, views)
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_event_list)
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

        private fun bindActions(context: Context, views: RemoteViews, appWidgetId: Int) {
            // Bara datumdelen öppnar kalendern. Root och aktivitetslistan är medvetet
            // utan generellt kalenderklick så att scroll/expand inte kapas av launcher.
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

            fun openTabPendingIntent(tab: Int, requestOffset: Int): PendingIntent {
                val intent =
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse("familjekalender://widget/shortcut/$appWidgetId/$tab")
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
