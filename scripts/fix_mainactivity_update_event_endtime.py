from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
old = 'SupabaseSync.updateEvent(session, event.id, title, date, time, event.memberId)'
new = 'SupabaseSync.updateEvent(session, event.id, title, date, time, event.endTime, event.memberId)'
if new in text:
    print('MainActivity updateEvent call already fixed')
elif old in text:
    path.write_text(text.replace(old, new, 1), encoding='utf-8')
    print('Fixed MainActivity updateEvent call to preserve endTime')
else:
    raise SystemExit('Expected MainActivity updateEvent call not found')
