from pathlib import Path

# One-time integration helper for the family assistant card.
path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
needle = '''            if (selectedTab == 0) {\n                ExactCalendarScreen(selectedDate, { selectedDate = it }, events, members, palette) { showAddEvent = true }\n            } else {\n'''
replacement = '''            if (selectedTab == 0) {\n                Column(Modifier.fillMaxSize()) {\n                    FamilyAssistantCard(session, events, members, shopping)\n                    Box(Modifier.weight(1f)) {\n                        ExactCalendarScreen(selectedDate, { selectedDate = it }, events, members, palette) { showAddEvent = true }\n                    }\n                }\n            } else {\n'''
if needle not in text:
    raise SystemExit('Calendar screen anchor not found')
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
print('Integrated FamilyAssistantCard above calendar')
