from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
old = 'SupabaseSync.updateEvent(session, event.id, title, date, time, event.memberId)'
new = 'SupabaseSync.updateEvent(session, event.id, title, date, time, event.endTime, event.memberId)'
if old not in text:
    raise SystemExit('Legacy updateEvent call not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('MainActivity updateEvent call now preserves endTime')
