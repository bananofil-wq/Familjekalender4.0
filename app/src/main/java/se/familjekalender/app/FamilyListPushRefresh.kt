package se.familjekalender.app

import android.content.Context
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension
import androidx.annotation.Keep
import org.json.JSONObject

/**
 * Receive family list changes even when the calendar Activity isn't open.
 *
 * OneSignal may start the app in the background for a notification. This callback
 * must stay short: WorkManager performs network requests outside the SDK callback.
 * We deliberately don't suppress or replace the user's normal notifications.
 */
@Keep
class FamilyListNotificationServiceExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        FamilyListPushRefresh.onIncomingPush(event.context, event.notification.additionalData)
    }
}

/**
 * Routing intentionally ignores untrusted notifications for other families,
 * non-list notifications and calendar event popups. Only the widget worker
 * loads family data; no push content is copied into widget cache.
 */
internal object FamilyListPushRefresh {
    private const val LAST_FAMILY_ID = "family_id"
    private val listPrefixes = listOf("shopping:", "todo:", "shopping-change:", "todo-change:")

    internal fun shouldRefresh(payload: JSONObject?, activeFamilyId: String?): Boolean {
        if (activeFamilyId.isNullOrBlank() || payload == null) return false
        if (payload.optString("family_id") != activeFamilyId) return false
        val key = payload.optString("dedupe_key")
        return payload.optString("type") == "family_list_changed" ||
            listPrefixes.any { key.startsWith(it) }
    }

    fun onIncomingPush(context: Context, payload: JSONObject?) {
        val appContext = context.applicationContext
        val id = appContext.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
            .getString(LAST_FAMILY_ID, null)
        if (!shouldRefresh(payload, id)) return
        FamilyCalendarWidget.enqueueRefresh(appContext)
    }
}
