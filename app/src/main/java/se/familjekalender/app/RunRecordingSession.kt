package se.familjekalender.app

import android.location.Location
import java.util.UUID

/**
 * In-memory recording session. Android location/foreground-service code feeds Location samples into
 * this class; UI can observe the immutable snapshot and persist the finished run.
 */
class RunRecordingSession {
    var state: RunRecordingState = RunRecordingState.IDLE
        private set

    var memberId: String? = null
        private set

    var startedAtMillis: Long = 0L
        private set

    private val route = mutableListOf<RecordedRoutePoint>()

    val points: List<RecordedRoutePoint>
        get() = route.toList()

    fun start(memberId: String, nowMillis: Long = System.currentTimeMillis()) {
        require(memberId.isNotBlank())
        this.memberId = memberId
        startedAtMillis = nowMillis
        route.clear()
        state = RunRecordingState.RECORDING
    }

    fun add(location: Location) {
        if (state != RunRecordingState.RECORDING) return
        // Reject very poor fixes so a single GPS jump does not destroy the displayed route.
        if (location.hasAccuracy() && location.accuracy > 50f) return
        route +=
            RecordedRoutePoint(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            )
    }

    fun finish(nowMillis: Long = System.currentTimeMillis()): RecordedRun? {
        val owner = memberId ?: return null
        if (state != RunRecordingState.RECORDING) return null
        state = RunRecordingState.FINISHED
        return RecordedRun(
            id = UUID.randomUUID().toString(),
            memberId = owner,
            startedAtMillis = startedAtMillis,
            finishedAtMillis = nowMillis,
            points = route.toList(),
        )
    }

    fun reset() {
        state = RunRecordingState.IDLE
        memberId = null
        startedAtMillis = 0L
        route.clear()
    }
}
