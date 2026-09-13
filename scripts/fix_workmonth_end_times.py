from pathlib import Path

compat = Path('app/src/main/java/se/familjekalender/app/CalendarBuildCompat.kt')
text = compat.read_text(encoding='utf-8')
text = text.replace(
'''internal data class WorkMonthEventInput(
    val title: String,
    val date: LocalDate,
    val startTime: String,
    val memberId: String?
)''',
'''internal data class WorkMonthEventInput(
    val title: String,
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val memberId: String?
)'''
)
old = '''    val grouped = rows.groupBy { it.startTime }
    val rules = grouped.map { (start, events) ->
        val weekdays = events.map { it.date.dayOfWeek.value }.toSet()
        val end = runCatching {
            LocalTime.parse(start, DateTimeFormatter.ofPattern("HH:mm"))
                .plusHours(8)
                .plusMinutes(18)
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault(start)
        WorkRule(weekdays = weekdays, startTime = start, endTime = end)
    }
'''
new = '''    val grouped = rows.groupBy { it.startTime to it.endTime }
    val rules = grouped.map { (times, events) ->
        val weekdays = events.map { it.date.dayOfWeek.value }.toSet()
        WorkRule(weekdays = weekdays, startTime = times.first, endTime = times.second)
    }
'''
if old not in text:
    raise SystemExit('Expected generated work-month duration fallback not found')
text = text.replace(old, new, 1)
compat.write_text(text, encoding='utf-8')

screen = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = screen.read_text(encoding='utf-8')
old = 'rows += WorkMonthEventInput(title.trim().ifBlank { "Jobb" }, date, rule.startTime, selectedMemberId)'
new = 'rows += WorkMonthEventInput(title.trim().ifBlank { "Jobb" }, date, rule.startTime, rule.endTime, selectedMemberId)'
if old not in text:
    raise SystemExit('Expected WorkMonthEventInput construction not found')
text = text.replace(old, new, 1)
screen.write_text(text, encoding='utf-8')
print('Work month now preserves each configured start/end pair')
