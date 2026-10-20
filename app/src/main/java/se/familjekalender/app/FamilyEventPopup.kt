package se.familjekalender.app

import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-app alert for newly created calendar events.
 * Background delivery remains governed by Android notification restrictions.
 */
internal object FamilyEventPopup {
    data class Event(val id: String, val title: String, val message: String)
    private val pending = MutableStateFlow<Event?>(null)
    val current = pending.asStateFlow()

    val foregroundListener = object : INotificationLifecycleListener {
        override fun onWillDisplay(event: INotificationWillDisplayEvent) {
            val notification = event.notification
            val data = notification.additionalData
            val type = data?.optString("type").orEmpty()
            val action = data?.optString("event_type").orEmpty()
            if (type !in setOf("calendar_event_created", "event_created") &&
                action !in setOf("calendar_event_created", "event_created")) return
            val id = data?.optString("event_id").orEmpty().ifBlank { notification.notificationId.orEmpty() }
            if (pending.value?.id == id) return
            pending.value = Event(id, notification.title.orEmpty().ifBlank { "Ny händelse" },
                notification.body.orEmpty())
        }
    }

    fun dismiss(id: String) {
        if (pending.value?.id == id) pending.value = null
    }
}
