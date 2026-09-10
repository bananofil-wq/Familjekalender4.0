from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
s = p.read_text()
line = '                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White),\n'
pair = line + line
if pair not in s:
    raise SystemExit('No adjacent duplicate color arguments found')
while pair in s:
    s = s.replace(pair, line)
p.write_text(s)
print('Collapsed adjacent duplicate Button colors arguments')
