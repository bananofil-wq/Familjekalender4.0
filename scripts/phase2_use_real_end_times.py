from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text(encoding='utf-8')

old = '''private fun assistantTimeRange(event: SyncEvent): AssistantTimeRange? {
    val start = minutesOfDay(event.time) ?: return null
    val range = Regex("(\\\\d{2}:\\\\d{2})[–-](\\\\d{2}:\\\\d{2})").find(event.title)
    val explicitEnd = range?.groupValues?.getOrNull(2)?.let(::minutesOfDay)
    val end = when {
        explicitEnd == null -> start + 60
        explicitEnd > start -> explicitEnd
        else -> explicitEnd + 24 * 60
    }
    return AssistantTimeRange(start, end)
}
'''

new = '''private fun assistantTimeRange(event: SyncEvent): AssistantTimeRange? {
    val start = minutesOfDay(event.time) ?: return null

    val storedEnd = event.endTime?.let(::minutesOfDay)?.let { endMinutes ->
        val dayOffset = when {
            event.endDate == null -> if (endMinutes > start) 0 else 1
            event.endDate.isAfter(event.date) -> 1
            else -> 0
        }
        endMinutes + dayOffset * 24 * 60
    }
    if (storedEnd != null && storedEnd > start) {
        return AssistantTimeRange(start, storedEnd)
    }

    // Older rows and generated school schedules may not have ends_at yet.
    // Keep title parsing as a compatibility fallback, then finally assume one hour.
    val range = Regex("(\\\\d{2}:\\\\d{2})[–-](\\\\d{2}:\\\\d{2})").find(event.title)
    val titleEnd = range?.groupValues?.getOrNull(2)?.let(::minutesOfDay)
    val fallbackEnd = when {
        titleEnd == null -> start + 60
        titleEnd > start -> titleEnd
        else -> titleEnd + 24 * 60
    }
    return AssistantTimeRange(start, fallbackEnd)
}
'''

if old not in text:
    raise SystemExit('Expected assistantTimeRange block not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('Family assistant now prioritizes stored ends_at values')
