from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
s = p.read_text()
line = '                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White),\n'
count = s.count(line)
if count < 5:
    raise SystemExit(f'Expected at least 5 duplicate color lines, found {count}')
# Collapse the first run of five identical lines to one.
needle = line * 5
if needle not in s:
    raise SystemExit('Five-line duplicate block not found')
s = s.replace(needle, line, 1)
p.write_text(s)
print('Collapsed duplicate Button colors arguments')
