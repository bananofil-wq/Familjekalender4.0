from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/SchoolScheduleDialog.kt')
text = path.read_text(encoding='utf-8')
old = '''                                    SupabaseSync.addEvent(
                                        activeSession,
                                        "$type · ${times.first}–${times.second}",
                                        date,
                                        times.first,
                                        selectedMemberId
                                    )'''
new = '''                                    SupabaseSync.addEvent(
                                        activeSession,
                                        "$type · ${times.first}–${times.second}",
                                        date,
                                        times.first,
                                        times.second,
                                        selectedMemberId
                                    )'''
if old not in text:
    raise SystemExit('Expected school schedule writer not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('School/preschool/daycare schedules now persist selected end times')
