from pathlib import Path

# 1) Work month: create one stable series for every saved month instead of
# delegating creation to the legacy batch endpoint, which has no series_id support.
compat = Path('app/src/main/java/se/familjekalender/app/CalendarBuildCompat.kt')
text = compat.read_text()
old = '''    if (rows.isEmpty()) return 0\n\n    val grouped = rows.groupBy { it.startTime to it.endTime }\n    val rules = grouped.map { (times, events) ->\n        val weekdays = events.map { it.date.dayOfWeek.value }.toSet()\n        WorkRule(weekdays = weekdays, startTime = times.first, endTime = times.second)\n    }\n\n    return saveWorkMonth(\n        session = session,\n        month = month,\n        title = title,\n        memberId = memberId,\n        rules = rules\n    )\n'''
new = '''    if (rows.isEmpty()) return 0\n\n    val seriesId = java.util.UUID.randomUUID().toString()\n    rows.forEach { row ->\n        SupabaseSync.addEvent(\n            session,\n            row.title,\n            row.date,\n            row.startTime,\n            row.endTime,\n            row.memberId,\n            seriesId\n        )\n    }\n    return rows.size\n'''
if old in text:
    compat.write_text(text.replace(old, new, 1))
elif 'val seriesId = java.util.UUID.randomUUID().toString()' not in text:
    raise SystemExit('Work month anchor not found')

# 2) Template-generated schedules: deterministic series id per stored template row.
recurring = Path('app/src/main/java/se/familjekalender/app/RecurringScheduleSync.kt')
text = recurring.read_text()
old = '''                    SupabaseSync.addEvent(session, template.title, date, template.startTime, template.endTime, template.memberId)\n                    created++\n'''
new = '''                    val seriesId = java.util.UUID.nameUUIDFromBytes(\n                        "${session.id}|template|${template.id}".toByteArray(StandardCharsets.UTF_8)\n                    ).toString()\n                    SupabaseSync.addEvent(\n                        session,\n                        template.title,\n                        date,\n                        template.startTime,\n                        template.endTime,\n                        template.memberId,\n                        seriesId\n                    )\n                    created++\n'''
if old in text:
    recurring.write_text(text.replace(old, new, 1))
elif '|template|' not in text:
    raise SystemExit('Template apply anchor not found')

# 3) Week copying: preserve a real source series. Legacy non-series activities
# receive one new series id per logical title/time/member pattern during the copy.
card = Path('app/src/main/java/se/familjekalender/app/RecurringLifeCard.kt')
text = card.read_text()
old_head = '''    var copied = 0\n    var skipped = 0\n    sourceEvents.forEach { event ->\n'''
new_head = '''    var copied = 0\n    var skipped = 0\n    val legacySeriesIds = mutableMapOf<String, String>()\n    sourceEvents.forEach { event ->\n'''
if old_head in text:
    text = text.replace(old_head, new_head, 1)
elif 'legacySeriesIds = mutableMapOf' not in text:
    raise SystemExit('Copy week head anchor not found')

old_add = '''            SupabaseSync.addEvent(session, event.title, targetDate, event.time, event.endTime, event.memberId)\n            copied++\n'''
new_add = '''            val seriesId = event.seriesId ?: run {\n                val key = listOf(\n                    event.title,\n                    event.time,\n                    event.endTime ?: "",\n                    event.memberId ?: ALL_FAMILY_MEMBER_ID\n                ).joinToString("|")\n                legacySeriesIds.getOrPut(key) { java.util.UUID.randomUUID().toString() }\n            }\n            SupabaseSync.addEvent(\n                session,\n                event.title,\n                targetDate,\n                event.time,\n                event.endTime,\n                event.memberId,\n                seriesId\n            )\n            copied++\n'''
if old_add in text:
    text = text.replace(old_add, new_add, 1)
elif 'legacySeriesIds.getOrPut' not in text:
    raise SystemExit('Copy week add anchor not found')
card.write_text(text)

print('Complete series identity coverage applied')
