from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = path.read_text()

bad = '''                    }\n                    }\n                }\n            },\n            confirmButton = { Button(onClick = { editingWeek = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White)) { Text("Klar") } },'''
good = '''                    }\n                }\n            },\n            confirmButton = { Button(onClick = { editingWeek = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White)) { Text("Klar") } },'''

if bad in text:
    text = text.replace(bad, good, 1)
    path.write_text(text)
elif good not in text:
    raise SystemExit('Expected rotation editor closing block was not found')

# Guardrails
check = path.read_text()
assert 'Arbetsdagar och tider' in check
assert 'dayTimes: Map<Int, Pair<String, String>>' in check
assert bad not in check
