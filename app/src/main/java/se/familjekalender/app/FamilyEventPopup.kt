package se.familjekalender.app

import android.content.Context
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Persistent in-app inbox for calendar push messages.
 * Android owns the notification presentation while the app is in the background.
 */
internal object FamilyEventPopup {
    data class Event(val id: String, val title: String, val message: String)
    private val pending = MutableStateFlow<Event?>(null)
    val current = pending.asStateFlow()
    private const val PREFS = "family_event_popup"
    private const val PENDING = "pending"
    private const val SEEN = "seen_ids"
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val raw = appContext.getSharedPreferences(PREFS, 0).getString(PENDING, null)
        pending.value = runCatching {
            raw?.let {
                val json = JSONObject(it)
                Event(json.getString("id"), json.getString("title"), json.optString("message"))
            }
        }.getOrNull()
    }

    private fun receive(id: String, title: String, message: String) {
        if (!::appContext.isInitialized || id.isBlank()) return
        val prefs = appContext.getSharedPreferences(PREFS, 0)
        val seen = prefs.getStringSet(SEEN, emptySet()).orEmpty()
        if (id in seen || pending.value?.id == id) return
        val incoming = Event(id, title.ifBlank { "Ny händelse" }, message)
        pending.value = incoming
        prefs.edit().putString(PENDING, JSONObject()
            .put("id", incoming.id).put("title", incoming.title)
            .put("message", incoming.message).toString()).apply()
    }

    private fun receivePush(data: JSONObject?, notificationId: String, title: String, body: String) {
        val type = data?.optString("type").orEmpty()
        if (type != "calendar_event_created" && type != "event_created") return
        val id = data?.optString("event_id").orEmpty()
            .ifBlank { data?.optString("dedupe_key").orEmpty() }
            .ifBlank { notificationId }
        receive(id, title, body)
    }

    val foregroundListener = object : INotificationLifecycleListener {
        override fun onWillDisplay(event: INotificationWillDisplayEvent) {
            val n = event.notification
            receivePush(n.additionalData, n.notificationId.orEmpty(), n.title.orEmpty(), n.body.orEmpty())
        }
    }

    val clickListener = object : INotificationClickListener {
        override fun onClick(event: INotificationClickEvent) {
            val n = event.notification
            receivePush(n.additionalData, n.notificationId.orEmpty(), n.title.orEmpty(), n.body.orEmpty())
        }
    }

    fun dismiss(id: String) {
        if (!::appContext.isInitialized || pending.value?.id != id) return
        val prefs = appContext.getSharedPreferences(PREFS, 0)
        val updated = (prefs.getStringSet(SEEN, emptySet()).orEmpty() + id).takeLast(200).toSet()
        prefs.edit().putStringSet(SEEN, updated).remove(PENDING).apply()
        pending.value = null
    }
}
