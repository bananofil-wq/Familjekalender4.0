from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
text = path.read_text()

old = '''    val totalKm = runs.sumOf { it.distanceKm }\n    val bestPace = runs.minOfOrNull { it.paceSecondsPerKm }\n    val latest = runs.lastOrNull()\n'''
new = '''    val totalKm = runs.sumOf { it.distanceKm }\n    val bestPace = runs.minOfOrNull { it.paceSecondsPerKm }\n    val longestDistance = runs.maxOfOrNull { it.distanceKm }\n    val latest = runs.lastOrNull()\n'''
if old not in text:
    raise SystemExit('stats marker not found')
text = text.replace(old, new, 1)

old = '''    val paceTrendText = paceTrendSeconds?.let { delta ->\n        when {\n            delta >= 5 -> "↑ ${delta} s/km snabbare"\n            delta <= -5 -> "↓ ${-delta} s/km långsammare"\n            else -> "→ Stabilt tempo"\n        }\n    } ?: "Behöver fler pass"\n'''
new = '''    val paceTrendText = paceTrendSeconds?.let { delta ->\n        when {\n            delta >= 5 -> "↑ ${delta} s/km snabbare"\n            delta <= -5 -> "↓ ${-delta} s/km långsammare"\n            else -> "→ Stabilt tempo"\n        }\n    } ?: "Behöver fler pass"\n    val latestPbText = when {\n        latest == null -> "Inga pass ännu"\n        bestPace != null && latest.paceSecondsPerKm == bestPace && longestDistance != null && latest.distanceKm == longestDistance -> "🏆 Tempo + distans"\n        bestPace != null && latest.paceSecondsPerKm == bestPace -> "🏆 Bästa tempo"\n        longestDistance != null && latest.distanceKm == longestDistance -> "🏆 Längsta pass"\n        else -> "Fortsätt bygga formen"\n    }\n'''
if old not in text:
    raise SystemExit('trend marker not found')
text = text.replace(old, new, 1)

old = '''                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    RunStat(\n                        "Senaste 7 dagar",\n                        "${recent7Runs.size} pass · ${"%.1f".format(Locale.US, recent7Km)} km",\n                        Modifier.weight(1f)\n                    )\n                    RunStat(\n                        "Utveckling",\n                        paceTrendText,\n                        Modifier.weight(1f)\n                    )\n                }\n\n                if (plannedRuns.isNotEmpty()) {\n'''
new = '''                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    RunStat(\n                        "Senaste 7 dagar",\n                        "${recent7Runs.size} pass · ${"%.1f".format(Locale.US, recent7Km)} km",\n                        Modifier.weight(1f)\n                    )\n                    RunStat(\n                        "Utveckling",\n                        paceTrendText,\n                        Modifier.weight(1f)\n                    )\n                }\n                Spacer(Modifier.height(8.dp))\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    RunStat(\n                        "Längsta pass",\n                        longestDistance?.let { "${"%.1f".format(Locale.US, it)} km" } ?: "–",\n                        Modifier.weight(1f)\n                    )\n                    RunStat(\n                        "Personbästa",\n                        latestPbText,\n                        Modifier.weight(1f)\n                    )\n                }\n\n                if (plannedRuns.isNotEmpty()) {\n'''
if old not in text:
    raise SystemExit('ui marker not found')
text = text.replace(old, new, 1)

path.write_text(text)
