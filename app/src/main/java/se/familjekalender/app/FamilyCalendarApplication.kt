package se.familjekalender.app

import android.app.Application
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FamilyCalendarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OneSignal.initWithContext(this, "220bbe78-fd9a-4e5a-8d57-b84399821f7c")
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(false)
        }
    }
}
