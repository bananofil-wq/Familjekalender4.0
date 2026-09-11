from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = path.read_text(encoding='utf-8')
old = 'isBirthdayEvent(event) -> Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { Text("🌈", fontSize = 15.sp, lineHeight = 18.sp, maxLines = 1, softWrap = false) }'
new = 'isBirthdayEvent(event) -> BirthdayRainbowIcon(Modifier.size(width = 20.dp, height = 16.dp))'
if old not in text:
    raise SystemExit('Birthday rainbow target not found')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
