from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
text = path.read_text(encoding='utf-8')

old_parse = '''private fun parseRun(event: SyncEvent): RunEntry? {
    if (!event.title.startsWith("🏃 RUN|")) return null
    val parts = event.title.removePrefix("🏃 RUN|").split('|')
    if (parts.size < 2) return null
    val distance = parts[0].toDoubleOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (distance <= 0.0 || minutes <= 0) return null
    val pace = ((minutes * 60.0) / distance).toInt()
    return RunEntry(event, distance, minutes, pace)
}
'''

new_parse = '''private fun parseRun(event: SyncEvent): RunEntry? {
    val distance: Double
    val minutes: Int
    when {
        event.title.startsWith("🏃 RUN|") -> {
            val parts = event.title.removePrefix("🏃 RUN|").split('|')
            if (parts.size < 2) return null
            distance = parts[0].toDoubleOrNull() ?: return null
            minutes = parts[1].toIntOrNull() ?: return null
        }
        event.title.startsWith("🏃 Löpning · ") -> {
            val body = event.title.removePrefix("🏃 Löpning · ")
            val parts = body.split(" · ")
            if (parts.size < 2) return null
            distance = parts[0].removeSuffix(" km").replace(',', '.').toDoubleOrNull() ?: return null
            minutes = parts[1].removeSuffix(" min").trim().toIntOrNull() ?: return null
        }
        else -> return null
    }
    if (distance <= 0.0 || minutes <= 0) return null
    val pace = ((minutes * 60.0) / distance).toInt()
    return RunEntry(event, distance, minutes, pace)
}
'''

if old_parse not in text:
    raise SystemExit('parseRun target not found')
text = text.replace(old_parse, new_parse, 1)

old_title = 'title = "🏃 RUN|${"%.2f".format(Locale.US, distance)}|$minutes",'
new_title = 'title = "🏃 Löpning · ${"%.2f".format(Locale.US, distance)} km · $minutes min",'
if old_title not in text:
    raise SystemExit('run title target not found')
text = text.replace(old_title, new_title, 1)

path.write_text(text, encoding='utf-8')
print('Readable running entries applied')
