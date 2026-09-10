from pathlib import Path

p = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
s = p.read_text(encoding="utf-8")

anchor = s.index("    if (showAddEvent) {")
scoped = s[anchor:]

wanted = "val targetDates = if (recurrence == RecurrenceMode.NONE)"
if wanted not in scoped:
    old = '''                } else {
                    dates.sorted().forEach { date ->
                        SupabaseSync.addEvent(session, title, date, startTime, endTime, memberId)
                    }
                }
'''
    new = '''                } else {
                    val targetDates = if (recurrence == RecurrenceMode.NONE) {
                        dates.sorted()
                    } else {
                        dates.sorted().flatMap { recurringDates(it, recurrence) }.distinct().sorted()
                    }
                    targetDates.forEach { date ->
                        SupabaseSync.addEvent(session, title, date, startTime, endTime, memberId)
                    }
                }
'''
    if old not in scoped:
        raise SystemExit("Expected calendar save block not found")
    scoped = scoped.replace(old, new, 1)
    s = s[:anchor] + scoped
    p.write_text(s, encoding="utf-8")

final = p.read_text(encoding="utf-8")
assert wanted in final
assert "flatMap { recurringDates(it, recurrence) }" in final
print("recurrence save wiring verified")
