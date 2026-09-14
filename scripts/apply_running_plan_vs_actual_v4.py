from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
s = p.read_text(encoding='utf-8')

old = '''private data class RunEntry(
    val event: SyncEvent,
    val distanceKm: Double,
    val durationMinutes: Int,
    val paceSecondsPerKm: Int
)'''
new = '''private data class RunEntry(
    val event: SyncEvent,
    val distanceKm: Double,
    val durationMinutes: Int,
    val paceSecondsPerKm: Int,
    val plannedDistanceKm: Double? = null
)'''
assert old in s
s = s.replace(old, new, 1)

old = '''    val pace = ((minutes * 60.0) / distance).toInt()
    return RunEntry(event, distance, minutes, pace)'''
new = '''    val pace = ((minutes * 60.0) / distance).toInt()
    val plannedDistance = if (event.title.startsWith("🏃 Löpning · ")) {
        Regex("Plan ([0-9]+(?:[.,][0-9]+)?) km").find(event.title)
            ?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
    } else null
    return RunEntry(event, distance, minutes, pace, plannedDistance)'''
assert old in s
s = s.replace(old, new, 1)

old = '''                                Text("${"%.1f".format(Locale.US, run.distanceKm)} km · ${run.durationMinutes} min", color = Muted, fontSize = 12.sp)
                            }
                            Text(paceText(run.paceSecondsPerKm), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)'''
new = '''                                Text("${"%.1f".format(Locale.US, run.distanceKm)} km · ${run.durationMinutes} min", color = Muted, fontSize = 12.sp)
                                run.plannedDistanceKm?.let { planned ->
                                    val diff = run.distanceKm - planned
                                    val diffText = when {
                                        diff > 0.049 -> "+${"%.1f".format(Locale.US, diff)} km"
                                        diff < -0.049 -> "${"%.1f".format(Locale.US, diff)} km"
                                        else -> "enligt plan"
                                    }
                                    Text(
                                        "Plan ${"%.1f".format(Locale.US, planned)} km · $diffText",
                                        color = Muted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Text(paceText(run.paceSecondsPerKm), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)'''
assert old in s
s = s.replace(old, new, 1)

old = '''                        title = "🏃 Löpning · ${"%.2f".format(Locale.US, distance)} km · $minutes min",
                        date = plan.event.date,'''
new = '''                        title = "🏃 Löpning · ${"%.2f".format(Locale.US, distance)} km · $minutes min · Plan ${"%.1f".format(Locale.US, plan.distanceKm)} km",
                        date = plan.event.date,'''
assert old in s
s = s.replace(old, new, 1)

old = '''                if (valid) {
                    val pace = (((minutes ?: 0) * 60.0) / (distance ?: 1.0)).toInt()
                    Text("Tempo: ${paceText(pace)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Text("Det planerade passet ersätts med resultatet, så kalendern får ingen dubblett.", color = Muted, fontSize = 11.sp)'''
new = '''                if (valid) {
                    val actualDistance = distance ?: 0.0
                    val pace = (((minutes ?: 0) * 60.0) / (actualDistance.takeIf { it > 0.0 } ?: 1.0)).toInt()
                    val diff = actualDistance - plan.distanceKm
                    val comparison = when {
                        diff > 0.049 -> "+${"%.1f".format(Locale.US, diff)} km över plan"
                        diff < -0.049 -> "${"%.1f".format(Locale.US, -diff)} km under plan"
                        else -> "Distansen enligt plan"
                    }
                    Text("Tempo: ${paceText(pace)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Plan ${"%.1f".format(Locale.US, plan.distanceKm)} km → faktiskt ${"%.1f".format(Locale.US, actualDistance)} km · $comparison",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
                Text("Det planerade passet ersätts med resultatet, så kalendern får ingen dubblett.", color = Muted, fontSize = 11.sp)'''
assert old in s
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
print('Applied running plan-vs-actual v4')
