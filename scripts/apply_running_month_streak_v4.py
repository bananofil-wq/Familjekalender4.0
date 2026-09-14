from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
text = path.read_text()

old = '''    val recent7Runs = runs.filter { !it.event.date.isBefore(today.minusDays(6)) && !it.event.date.isAfter(today) }
    val recent7Km = recent7Runs.sumOf { it.distanceKm }
    val previousForTrend = runs.dropLast(1).takeLast(3)
'''
new = '''    val recent7Runs = runs.filter { !it.event.date.isBefore(today.minusDays(6)) && !it.event.date.isAfter(today) }
    val recent7Km = recent7Runs.sumOf { it.distanceKm }
    val monthRuns = runs.filter { it.event.date.year == today.year && it.event.date.month == today.month }
    val monthKm = monthRuns.sumOf { it.distanceKm }
    val activeWeeks = runs
        .map { java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear().let(it.event.date::get) to it.event.date.year }
        .distinct()
        .size
    val previousForTrend = runs.dropLast(1).takeLast(3)
'''
if old not in text:
    raise SystemExit('metric marker not found')
text = text.replace(old, new, 1)

old2 = '''                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat(
                        "Längsta pass",
                        longestDistance?.let { "${"%.1f".format(Locale.US, it)} km" } ?: "–",
                        Modifier.weight(1f)
                    )
                    RunStat(
                        "Personbästa",
                        latestPbText,
                        Modifier.weight(1f)
                    )
                }
'''
new2 = '''                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat(
                        "Den här månaden",
                        "${monthRuns.size} pass · ${"%.1f".format(Locale.US, monthKm)} km",
                        Modifier.weight(1f)
                    )
                    RunStat(
                        "Aktiva veckor",
                        if (activeWeeks > 0) "$activeWeeks totalt" else "–",
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat(
                        "Längsta pass",
                        longestDistance?.let { "${"%.1f".format(Locale.US, it)} km" } ?: "–",
                        Modifier.weight(1f)
                    )
                    RunStat(
                        "Personbästa",
                        latestPbText,
                        Modifier.weight(1f)
                    )
                }
'''
if old2 not in text:
    raise SystemExit('stats marker not found')
text = text.replace(old2, new2, 1)

path.write_text(text)
print('Running month/streak v4 applied')
