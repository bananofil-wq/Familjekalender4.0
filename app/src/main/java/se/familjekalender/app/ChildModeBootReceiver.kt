package se.familjekalender.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChildModeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("family_calendar_location", Context.MODE_PRIVATE)
        val shouldResume =
            prefs.getBoolean("sharing_enabled", false) &&
                prefs.getBoolean("child_realtime_tracking", false) &&
                !prefs.getString("device_member_id", null).isNullOrBlank()
        if (shouldResume) {
            FamilyLocationService.start(context)
        }
    }
}
