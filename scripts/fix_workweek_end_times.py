from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = path.read_text(encoding='utf-8')
old = 'SupabaseSync.addEvent(activeSession, eventTitle, date, times.first, selectedMemberId)'
new = 'SupabaseSync.addEvent(activeSession, eventTitle, date, times.first, times.second, selectedMemberId)'
if old not in text:
    raise SystemExit('Expected workweek event writer not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('Workweek now persists selected end times')
