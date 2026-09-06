package se.familjekalender.app

import android.app.Application
import android.content.SharedPreferences
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class FamilyCalendarApplication : Application() {
    private lateinit var prefs: SharedPreferences
    private val appScope = CoroutineScope(Dispatchers.IO)
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "family_id") attachPushIdentity()
    }

    override fun onCreate() {
        super.onCreate()
        OneSignal.initWithContext(this, "220bbe78-fd9a-4e5a-8d57-b84399821f7c")

        prefs = getSharedPreferences("family_calendar", 0)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        attachPushIdentity()

        appScope.launch {
            OneSignal.Notifications.requestPermission(false)
        }
    }

    private fun attachPushIdentity() {
        val familyId = prefs.getString("family_id", null) ?: return
        var deviceId = prefs.getString("push_device_id", null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("push_device_id", deviceId).apply()
        }

        OneSignal.login(deviceId)
        appScope.launch {
            repeat(4) { attempt ->
                if (attempt > 0) delay(1500L * attempt)
                OneSignal.User.addTags(
                    mapOf(
                        "family_id" to familyId,
                        "platform" to "android"
                    )
                )
            }
        }
    }
}
