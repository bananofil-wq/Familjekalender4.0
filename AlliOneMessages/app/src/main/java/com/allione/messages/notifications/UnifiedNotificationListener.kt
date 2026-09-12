package com.allione.messages.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.allione.messages.data.MessageStore
import com.allione.messages.model.MessageSource
import com.allione.messages.model.UnifiedMessage

class UnifiedNotificationListener : NotificationListenerService() {
    companion object {
        @Volatile private var instance: UnifiedNotificationListener? = null

        fun reply(notificationKey: String, text: String): Boolean {
            val service = instance ?: return false
            val sbn = service.activeNotifications?.firstOrNull { it.key == notificationKey } ?: return false
            val action = sbn.notification.actions?.firstOrNull { a -> !a.remoteInputs.isNullOrEmpty() } ?: return false
            val inputs = action.remoteInputs ?: return false
            return runCatching {
                val intent = Intent()
                val bundle = Bundle()
                inputs.forEach { bundle.putCharSequence(it.resultKey, text) }
                RemoteInput.addResultsToIntent(inputs, intent, bundle)
                action.actionIntent.send(service, 0, intent)
                true
            }.getOrDefault(false)
        }

        fun open(notificationKey: String): Boolean {
            val service = instance ?: return false
            val sbn = service.activeNotifications?.firstOrNull { it.key == notificationKey } ?: return false
            val pending: PendingIntent = sbn.notification.contentIntent ?: return false
            return runCatching { pending.send(); true }.getOrDefault(false)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = when (sbn.packageName) {
            "com.facebook.orca" -> MessageSource.MESSENGER
            "com.whatsapp" -> MessageSource.WHATSAPP
            "org.thoughtcrime.securesms" -> MessageSource.SIGNAL
            else -> return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            .ifBlank { source.name.lowercase().replaceFirstChar { it.uppercase() } }
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return
        if (text.isBlank()) return

        MessageStore.save(
            applicationContext,
            UnifiedMessage(
                id = "notification:${sbn.key}:${sbn.postTime}",
                sender = title,
                preview = text,
                timestamp = sbn.postTime,
                source = source,
                unread = true,
                packageName = sbn.packageName,
                notificationKey = sbn.key
            )
        )
    }
}
