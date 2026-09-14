from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
text = path.read_text()

old = '''    val totalKm = runs.sumOf { it.distanceKm }
    val bestPace = runs.minOfOrNull { it.paceSecondsPerKm }
    val latest = runs.lastOrNull()
    val nextPlan = plannedRuns.firstOrNull()
'''
new = '''    val totalKm = runs.sumOf { it.distanceKm }
    val bestPace = runs.minOfOrNull { it.paceSecondsPerKm }
    val latest = runs.lastOrNull()
    val nextPlan = plannedRuns.firstOrNull()
    val today = LocalDate.now()
    val recent7Runs = runs.filter { !it.event.date.isBefore(today.minusDays(6)) && !it.event.date.isAfter(today) }
    val recent7Km = recent7Runs.sumOf { it.distanceKm }
    val previousForTrend = runs.dropLast(1).takeLast(3)
    val paceTrendSeconds = if (latest != null && previousForTrend.isNotEmpty()) {
        previousForTrend.map { it.paceSecondsPerKm }.average().toInt() - latest.paceSecondsPerKm
    } else null
    val paceTrendText = paceTrendSeconds?.let { delta ->
        when {
            delta >= 5 -> "↑ ${delta} s/km snabbare"
            delta <= -5 -> "↓ ${-delta} s/km långsammare"
            else -> "→ Stabilt tempo"
        }
    } ?: "Behöver fler pass"
'''
if old not in text:
    raise SystemExit('stats marker not found')
text = text.replace(old, new, 1)

old2 = '''                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat("Senast", latest?.let { "${"%.1f".format(Locale.US, it.distanceKm)} km" } ?: "–", Modifier.weight(1f))
                    RunStat("Bästa tempo", bestPace?.let(::paceText) ?: "–", Modifier.weight(1f))
                    RunStat("Totalt", "${"%.1f".format(Locale.US, totalKm)} km", Modifier.weight(1f))
                }
'''
new2 = '''                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat("Senast", latest?.let { "${"%.1f".format(Locale.US, it.distanceKm)} km" } ?: "–", Modifier.weight(1f))
                    RunStat("Bästa tempo", bestPace?.let(::paceText) ?: "–", Modifier.weight(1f))
                    RunStat("Totalt", "${"%.1f".format(Locale.US, totalKm)} km", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunStat(
                        "Senaste 7 dagar",
                        "${recent7Runs.size} pass · ${"%.1f".format(Locale.US, recent7Km)} km",
                        Modifier.weight(1f)
                    )
                    RunStat(
                        "Utveckling",
                        paceTrendText,
                        Modifier.weight(1f)
                    )
                }
'''
if old2 not in text:
    raise SystemExit('stat row marker not found')
text = text.replace(old2, new2, 1)

path.write_text(text)
print('Running progress v2 applied')
