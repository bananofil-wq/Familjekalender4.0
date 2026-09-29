package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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

class FamilyCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = baseViews(context, appWidgetId)
            views.setTextViewText(R.id.widget_status, "Uppdaterar…")
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
                if (
                    intent.getStringExtra(EXTRA_ITEM_ACTION) == ITEM_ACTION_TOGGLE &&
                    appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                    !groupKey.isNullOrBlank()
                ) {
                    toggleGroup(context, appWidgetId, groupKey)
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
            return views
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
                views.setTextViewText(R.id.widget_status, "Uppdaterar…")
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
