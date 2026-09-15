package se.familjekalender.app

/** Data used by the running dashboard after a Strava activity has been imported. */
data class StravaRun(
    val activityId: Long,
    val athleteMemberId: String,
    val name: String,
    val distanceMeters: Double,
    val movingTimeSeconds: Int,
    val elapsedTimeSeconds: Int,
    val startDateLocal: String,
    val averageSpeedMetersPerSecond: Double?,
    val maxSpeedMetersPerSecond: Double?,
    val totalElevationGainMeters: Double?,
    val route: List<RoutePoint> = emptyList()
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
    val paceSecondsPerKm: Int?
        get() = if (distanceMeters > 0 && movingTimeSeconds > 0) {
            (movingTimeSeconds / (distanceMeters / 1000.0)).toInt()
        } else null
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    /** Seconds from activity start when stream timing is available. */
    val elapsedSeconds: Int? = null
)

/**
 * Connection state deliberately contains no client secret. Strava OAuth secrets must stay
 * server-side; the Android client stores only short-lived/session identifiers returned by
 * the Familjekalender backend.
 */
data class StravaConnectionState(
    val connected: Boolean = false,
    val athleteId: Long? = null,
    val athleteName: String? = null,
    val lastSyncEpochMillis: Long? = null
)
