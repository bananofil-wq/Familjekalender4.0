package se.familjekalender.app

import android.content.Context
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A durable queue of family calendar notifications.
 * Android shows its own notifications while the app is in the background.
 * Foreground notifications and notification taps are surfaced in the in-app dialog.
 */
internal object FamilyEventPopup {
    data class Event(val id: String, val title: String, val message: String)

    private const val PREFS = "family_event_popup"
    private const val LEGACY_PENDING = "pending"
    private const val QUEUE = "pending_queue"
    private const val SEEN = "seen_ids"
    private val queue = mutableListOf<Event>()
    private val pending = MutableStateFlow<Event?>(null)
    val current = pending.asStateFlow()
    private lateinit var appContext: Context

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, 0)
        queue.clear()
        val savedQueue = prefs.getString(QUEUE, null)
        if (savedQueue != null) {
            runCatching {
                val array = JSONArray(savedQueue)
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    queue.add(Event(json.getString("id"), json.optString("title"), json.optString("message")))
                }
            }.onFailure { queue.clear() }
        } else {
            val previous = prefs.getString(LEGACY_PENDING, null)
            runCatching {
                previous?.let {
                    val json = JSONObject(it)
                    queue.add(Event(json.getString("id"), json.optString("title"), json.optString("message")))
                }
            }
            prefs.edit().remove(LEGACY_PENDING).apply()
        }
        pending.value = queue.firstOrNull()
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        queue.forEach { event ->
            array.put(JSONObject()
                .put("id", event.id)
                .put("title", event.title)
                .put("message", event.message))
        }
        appContext.getSharedPreferences(PREFS, 0).edit()
            .putString(QUEUE, array.toString()).apply()
        pending.value = queue.firstOrNull()
    }

    @Synchronized
    private fun receive(id: String, title: String, message: String) {
        if (!::appContext.isInitialized || id.isBlank()) return
        val seen = appContext.getSharedPreferences(PREFS, 0)
            .getStringSet(SEEN, emptySet()).orEmpty()
        if (id in seen || queue.any { it.id == id }) return
        queue.add(Event(id, title.ifBlank { "Ny händelse" }, message))
        persist()
    }

    private fun receivePush(data: JSONObject?, notificationId: String, title: String, body: String) {
        val type = data?.optString("type").orEmpty()
        if (type != "calendar_event_created" && type != "event_created") return
        val id = data?.optString("event_id").orEmpty()
            .ifBlank { data?.optString("dedupe_key").orEmpty() }
            .ifBlank { notificationId }
        val dialogTitle = if (title.isBlank() || title == "Familjekalendern") "Ny aktivitet" else title
        receive(id, dialogTitle, body)
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

    @Synchronized
    fun dismiss(id: String) {
        if (!::appContext.isInitialized || queue.firstOrNull()?.id != id) return
        val prefs = appContext.getSharedPreferences(PREFS, 0)
        val updated = (prefs.getStringSet(SEEN, emptySet()).orEmpty() + id)
            .toList().takeLast(200).toSet()
        queue.removeAt(0)
        prefs.edit().putStringSet(SEEN, updated).apply()
        persist()
    }
}
