from pathlib import Path


def apply_once(path: Path, old: str, new: str, marker: str, label: str) -> None:
    text = path.read_text()
    if marker in text:
        print(f'{label}: already applied')
        return
    if old not in text:
        raise SystemExit(f'{label} anchor not found')
    path.write_text(text.replace(old, new, 1))
    print(f'{label}: applied')


# Work rotation: one stable series id per saved rotation.
exact = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
work_old = '''                    scope.launch {\n                        runCatching {\n                            repeat(52) { weekIndex ->\n                                val template = weeks[weekIndex % rotationWeeks]\n                                val monday = startDate.plusWeeks(weekIndex.toLong())\n                                template.weekdays.sorted().forEach { day ->\n                                    val date = monday.plusDays((day - 1).toLong())\n                                    val times = template.dayTimes[day] ?: (template.startTime to template.endTime)\n                                    val eventTitle = \"Jobb · ${times.first}–${times.second}\"\n                                    SupabaseSync.addEvent(activeSession, eventTitle, date, times.first, times.second, selectedMemberId)\n                                }\n                            }\n                        }.onSuccess { onChanged() }\n'''
work_new = '''                    scope.launch {\n                        runCatching {\n                            val seriesId = java.util.UUID.randomUUID().toString()\n                            repeat(52) { weekIndex ->\n                                val template = weeks[weekIndex % rotationWeeks]\n                                val monday = startDate.plusWeeks(weekIndex.toLong())\n                                template.weekdays.sorted().forEach { day ->\n                                    val date = monday.plusDays((day - 1).toLong())\n                                    val times = template.dayTimes[day] ?: (template.startTime to template.endTime)\n                                    val eventTitle = \"Jobb · ${times.first}–${times.second}\"\n                                    SupabaseSync.addEvent(activeSession, eventTitle, date, times.first, times.second, selectedMemberId, seriesId)\n                                }\n                            }\n                        }.onSuccess { onChanged() }\n'''
apply_once(
    exact,
    work_old,
    work_new,
    'SupabaseSync.addEvent(activeSession, eventTitle, date, times.first, times.second, selectedMemberId, seriesId)',
    'Work rotation',
)

# School/daycare schedule: one stable series id per saved schedule.
school = Path('app/src/main/java/se/familjekalender/app/SchoolScheduleDialog.kt')
school_old = '''                    scope.launch {\n                        runCatching {\n                            repeat(52) { weekIndex ->\n                                val template = weeks[weekIndex % rotationWeeks]\n                                val monday = startDate.plusWeeks(weekIndex.toLong())\n                                template.weekdays.sorted().forEach { day ->\n                                    val date = monday.plusDays((day - 1).toLong())\n                                    val times = template.dayTimes[day] ?: (\"07:30\" to \"16:00\")\n                                    SupabaseSync.addEvent(\n                                        activeSession,\n                                        \"$type · ${times.first}–${times.second}\",\n                                        date,\n                                        times.first,\n                                        times.second,\n                                        selectedMemberId\n                                    )\n                                }\n                            }\n                        }.onSuccess { onChanged() }\n'''
school_new = '''                    scope.launch {\n                        runCatching {\n                            val seriesId = java.util.UUID.randomUUID().toString()\n                            repeat(52) { weekIndex ->\n                                val template = weeks[weekIndex % rotationWeeks]\n                                val monday = startDate.plusWeeks(weekIndex.toLong())\n                                template.weekdays.sorted().forEach { day ->\n                                    val date = monday.plusDays((day - 1).toLong())\n                                    val times = template.dayTimes[day] ?: (\"07:30\" to \"16:00\")\n                                    SupabaseSync.addEvent(\n                                        activeSession,\n                                        \"$type · ${times.first}–${times.second}\",\n                                        date,\n                                        times.first,\n                                        times.second,\n                                        selectedMemberId,\n                                        seriesId\n                                    )\n                                }\n                            }\n                        }.onSuccess { onChanged() }\n'''
apply_once(
    school,
    school_old,
    school_new,
    'selectedMemberId,\n                                        seriesId',
    'School/daycare schedule',
)

print('Stable series ID patch complete')
