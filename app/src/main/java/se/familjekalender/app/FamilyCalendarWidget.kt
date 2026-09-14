package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
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
        if (intent.action == ACTION_REFRESH) {
            showUpdating(context)
            enqueueRefresh(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "se.familjekalender.app.WIDGET_REFRESH"
        private const val IMMEDIATE_WORK_NAME = "family_calendar_widget_refresh"
        private const val PERIODIC_WORK_NAME = "family_calendar_widget_periodic_refresh"

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueueRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<FamilyCalendarWidgetWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePeriodicRefresh(context: Context) {
            val request = PeriodicWorkRequestBuilder<FamilyCalendarWidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun baseViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_family_calendar)
            val today = LocalDate.now()
            val dateText = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv", "SE")))
                .replaceFirstChar { it.uppercase() }
            views.setTextViewText(R.id.widget_date, dateText)
            clearRows(views)
            bindActions(context, views, appWidgetId)
            return views
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

        private fun bindActions(context: Context, views: RemoteViews, appWidgetId: Int) {
            val openIntent = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            val refreshIntent = Intent(context, FamilyCalendarWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                appWidgetId + 10_000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)
        }

        private fun clearRows(views: RemoteViews) {
            intArrayOf(
                R.id.widget_event_1,
                R.id.widget_event_2,
                R.id.widget_event_3,
                R.id.widget_event_4
            ).forEach {
                views.setViewVisibility(it, View.GONE)
                views.setTextViewText(it, "")
            }
        }
    }
}
