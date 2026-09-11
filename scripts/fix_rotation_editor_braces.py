from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = path.read_text()

anchor = 'title = { Text("Redigera vecka ${index + 1}", fontWeight = FontWeight.Bold) }'
pos = text.find(anchor)
if pos < 0:
    raise SystemExit('Rotation editor anchor not found')

head, tail = text[:pos], text[pos:]
bad = '''                    }\n                    }\n                }\n            },\n            confirmButton = { Button(onClick = { editingWeek = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White)) { Text("Klar") } },'''
good = '''                    }\n                }\n            },\n            confirmButton = { Button(onClick = { editingWeek = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White)) { Text("Klar") } },'''

if bad in tail:
    tail = tail.replace(bad, good)
elif good not in tail:
    raise SystemExit('Expected rotation editor closing block was not found')

text = head + tail
path.write_text(text)

check_tail = path.read_text()[pos:]
assert 'Arbetsdagar och tider' in check_tail
assert bad not in check_tail
