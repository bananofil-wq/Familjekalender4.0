package se.familjekalender.app

import android.content.Context

/**
 * Keeps the Strava connection scoped to one Familjekalender member.
 * This means Bella can connect Strava without Kim (or anyone else in the family)
 * needing a Strava account. OAuth tokens themselves are intentionally not stored here.
 */
class MemberStravaConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("member_strava_connections", Context.MODE_PRIVATE)

    fun state(memberId: String): StravaConnectionState = StravaConnectionState(
        connected = prefs.getBoolean("$memberId.connected", false),
        athleteId = prefs.getLong("$memberId.athleteId", -1L).takeIf { it >= 0 },
        athleteName = prefs.getString("$memberId.athleteName", null),
        lastSyncEpochMillis = prefs.getLong("$memberId.lastSync", -1L).takeIf { it >= 0 }
    )

    fun markConnected(memberId: String, athleteId: Long, athleteName: String?) {
        prefs.edit()
            .putBoolean("$memberId.connected", true)
            .putLong("$memberId.athleteId", athleteId)
            .putString("$memberId.athleteName", athleteName)
            .apply()
    }

    fun markSynced(memberId: String, epochMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("$memberId.lastSync", epochMillis).apply()
    }

    fun disconnect(memberId: String) {
        prefs.edit()
            .remove("$memberId.connected")
            .remove("$memberId.athleteId")
            .remove("$memberId.athleteName")
            .remove("$memberId.lastSync")
            .apply()
    }
}
