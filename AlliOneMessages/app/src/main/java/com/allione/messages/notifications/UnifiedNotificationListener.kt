package com.allione.messages.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.allione.messages.model.MessageSource

class UnifiedNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = when (sbn.packageName) {
            "com.facebook.orca" -> MessageSource.MESSENGER
            "com.whatsapp" -> MessageSource.WHATSAPP
            "org.thoughtcrime.securesms" -> MessageSource.SIGNAL
            else -> return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        Log.d("AlliOneMessages", "[$source] $title: $text")
    }
}
