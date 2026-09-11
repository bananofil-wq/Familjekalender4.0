from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
s = p.read_text()
old = '''                        val dayEvents = if (day == null) emptyList() else events.filter { it.date == day }.take(3)\n'''
new = '''                        val dayEvents = if (day == null) {\n                            emptyList()\n                        } else {\n                            val allDayEvents = events.filter { it.date == day }\n                            val firstBirthday = allDayEvents.firstOrNull { isBirthdayEvent(it) }\n                            buildList {\n                                if (firstBirthday != null) add(firstBirthday)\n                                addAll(allDayEvents.filterNot { isBirthdayEvent(it) }.take(3))\n                            }.take(3)\n                        }\n'''
if old not in s:
    raise SystemExit('target not found')
s = s.replace(old, new, 1)
p.write_text(s)
# trigger single-birthday-rainbow workflow
