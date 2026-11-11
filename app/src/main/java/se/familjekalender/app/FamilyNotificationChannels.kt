package se.familjekalender.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Used by the family push service on devices that have registered support for
 * this channel with OneSignal. High importance allows Android heads-up alerts
 * when the user has granted notification permission and not muted this channel.
 */
internal object FamilyNotificationChannels {
    const val FAMILY_ALERT_CHANNEL_ID = "family_activity_alerts_v1"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(FAMILY_ALERT_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            FAMILY_ALERT_CHANNEL_ID,
            "Nya familjeaktiviteter",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Tydliga aviseringar när någon lägger till en aktivitet i familjekalendern."
            enableLights(true)
            enableVibration(false)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
