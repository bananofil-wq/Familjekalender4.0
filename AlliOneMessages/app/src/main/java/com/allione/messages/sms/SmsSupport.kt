package com.allione.messages.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.allione.messages.MainActivity
import com.allione.messages.data.MessageStore
import com.allione.messages.model.MessageSource
import com.allione.messages.model.UnifiedMessage

object SmsSupport {
    fun loadInbox(context: Context): List<UnifiedMessage> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val result = mutableListOf<UnifiedMessage>()
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressI = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyI = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateI = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            var count = 0
            while (c.moveToNext() && count < 200) {
                result += UnifiedMessage(
                    id = "sms:${c.getLong(idI)}",
                    sender = c.getString(addressI) ?: "Okänt nummer",
                    preview = c.getString(bodyI).orEmpty(),
                    timestamp = c.getLong(dateI),
                    source = MessageSource.SMS,
                    unread = false
                )
                count++
            }
        }
        return result
    }

    fun send(context: Context, number: String, text: String): Boolean {
        if (number.isBlank() || text.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            manager.sendTextMessage(number, null, text, null, null)
            MessageStore.save(
                context,
                UnifiedMessage(
                    id = "sms:sent:${System.currentTimeMillis()}",
                    sender = number,
                    preview = text,
                    timestamp = System.currentTimeMillis(),
                    source = MessageSource.SMS,
                    unread = false
                )
            )
            true
        }.getOrDefault(false)
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        Telephony.Sms.Intents.getMessagesFromIntent(intent).forEachIndexed { index, sms ->
            val sender = sms.originatingAddress ?: "Okänt nummer"
            val body = sms.messageBody.orEmpty()
            val timestamp = sms.timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
            MessageStore.save(
                context,
                UnifiedMessage(
                    id = "sms:incoming:$sender:$timestamp:$index",
                    sender = sender,
                    preview = body,
                    timestamp = timestamp,
                    source = MessageSource.SMS,
                    unread = true
                )
            )
            showNotification(context, sender, body)
        }
    }

    private fun showNotification(context: Context, sender: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "allione_messages"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "AlliOne Messages", NotificationManager.IMPORTANCE_HIGH))
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(sender)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification) }
    }
}
