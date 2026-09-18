package se.familjekalender.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RecordedRunStore(context: Context) {
    private val prefs = context.getSharedPreferences("recorded_runs", Context.MODE_PRIVATE)

    fun save(run: RecordedRun) {
        val all = JSONArray(prefs.getString("runs", "[]") ?: "[]")
        all.put(run.toJson())
        prefs.edit().putString("runs", all.toString()).apply()
    }

    fun runsFor(memberId: String): List<RecordedRun> {
        val all = runCatching {
            JSONArray(prefs.getString("runs", "[]") ?: "[]")
        }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until all.length()) {
                runCatching { all.getJSONObject(i).toRun() }
                    .getOrNull()
                    ?.let { if (it.memberId == memberId) add(it) }
            }
        }
            .sortedByDescending { it.startedAtMillis }
    }

    private fun RecordedRun.toJson() =
        JSONObject().apply {
            put("id", id)
            put("memberId", memberId)
            put("started", startedAtMillis)
            put("finished", finishedAtMillis)
            put(
                "points",
                JSONArray().apply {
                    points.forEach { p ->
                        put(
                            JSONObject().apply {
                                put("lat", p.latitude)
                                put("lon", p.longitude)
                                put("time", p.timestampMillis)
                                p.accuracyMeters?.let { put("accuracy", it.toDouble()) }
                                p.altitudeMeters?.let { put("altitude", it) }
                            }
                        )
                    }
                },
            )
        }

    private fun JSONObject.toRun(): RecordedRun {
        val a = getJSONArray("points")
        val points = buildList {
            for (i in 0 until a.length()) add(
                a.getJSONObject(i).let { p ->
                    RecordedRoutePoint(
                        p.getDouble("lat"),
                        p.getDouble("lon"),
                        p.getLong("time"),
                        p.optDouble("accuracy", Double.NaN).takeUnless(Double::isNaN)?.toFloat(),
                        p.optDouble("altitude", Double.NaN).takeUnless(Double::isNaN),
                    )
                }
            )
        }
        return RecordedRun(
            getString("id"),
            getString("memberId"),
            getLong("started"),
            getLong("finished"),
            points,
        )
    }
}
