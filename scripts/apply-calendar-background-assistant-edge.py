from pathlib import Path

calendar = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = calendar.read_text(encoding='utf-8')

old = 'SeasonalPhoto(mode, Modifier.align(Alignment.TopCenter).height(heroHeight))'
new = 'SeasonalPhoto(mode, Modifier.matchParentSize())'
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('Could not find seasonal photo placement to patch')

old = '''        modifier = modifier.aspectRatio(1.52f),\n        contentScale = ContentScale.Fit,\n        alignment = Alignment.TopCenter'''
new = '''        modifier = modifier,\n        contentScale = ContentScale.Crop,\n        alignment = Alignment.TopCenter'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('Could not find seasonal photo scaling block to patch')

# Move the month card upward by roughly one centimetre without changing the
# calendar grid size. We only reduce the seasonal hero spacer above it.
old = '        val heroHeight = (maxHeight - calendarMinHeight - 8.dp).coerceIn(72.dp, 135.dp)'
new = '        val heroHeight = (maxHeight - calendarMinHeight - 46.dp).coerceIn(34.dp, 97.dp)'
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('Could not find calendar hero height to patch')

calendar.write_text(text, encoding='utf-8')

assistant = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = assistant.read_text(encoding='utf-8')
old = '''    Card(\n        colors = CardDefaults.cardColors(containerColor = CardBg),\n        shape = RoundedCornerShape(22.dp),\n        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)\n    ) {'''
new = '''    Card(\n        colors = CardDefaults.cardColors(containerColor = CardBg),\n        shape = RoundedCornerShape(22.dp),\n        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),\n        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()\n    ) {'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('Could not find assistant card block to patch')
assistant.write_text(text, encoding='utf-8')
