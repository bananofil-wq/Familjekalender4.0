package se.familjekalender.app

/** One GPS sample captured by Familjekalender during a run. */
data class RecordedRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
)

data class RecordedRun(
    val id: String,
    val memberId: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val points: List<RecordedRoutePoint>,
) {
    val durationMillis: Long
        get() = (finishedAtMillis - startedAtMillis).coerceAtLeast(0L)
}

enum class RunRecordingState {
    IDLE,
    RECORDING,
    FINISHED,
}
