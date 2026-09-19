package se.familjekalender.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HugoHomeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
    }

    private fun buildViews(context: Context, id: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hugo_home)
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv","SE")))
        views.setTextViewText(R.id.hugo_widget_date, date.replaceFirstChar { it.uppercase() })

        fun activityIntent(action: String, uri: String, request: Int): PendingIntent {
            val intent = Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(context, id * 100 + request, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val prefs = context.getSharedPreferences(CHILD_MODE_PREFS, Context.MODE_PRIVATE)
        val kim = prefs.getString("kim_phone", "").orEmpty()
        val gabriella = prefs.getString("gabriella_phone", "").orEmpty()

        views.setOnClickPendingIntent(R.id.hugo_call_kim,
            activityIntent(Intent.ACTION_DIAL, "tel:" + Uri.encode(kim), 1))
        views.setOnClickPendingIntent(R.id.hugo_sms_kim,
            activityIntent(Intent.ACTION_SENDTO, "smsto:" + Uri.encode(kim), 2))
        views.setOnClickPendingIntent(R.id.hugo_call_gabriella,
            activityIntent(Intent.ACTION_DIAL, "tel:" + Uri.encode(gabriella), 3))
        views.setOnClickPendingIntent(R.id.hugo_sms_gabriella,
            activityIntent(Intent.ACTION_SENDTO, "smsto:" + Uri.encode(gabriella), 4))

        val open = PendingIntent.getActivity(context, id * 100 + 5,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.hugo_open_app, open)
        views.setOnClickPendingIntent(R.id.hugo_school, open)
        return views
    }
}
