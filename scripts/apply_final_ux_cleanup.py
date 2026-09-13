from pathlib import Path

assistant = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = assistant.read_text()
old = '''            Spacer(Modifier.height(14.dp))\n            Text("Idag", fontSize = 17.sp, fontWeight = FontWeight.Bold)\n            if (todaysEvents.isEmpty()) {\n                Text("Inga aktiviteter inlagda.", fontSize = 14.sp, color = Muted)\n            } else {\n                todaysEvents.take(3).forEach { event ->\n                    Text("• ${eventLine(event, members)}", fontSize = 14.sp, color = Color.White)\n                }\n                if (todaysEvents.size > 3) Text("+ ${todaysEvents.size - 3} till", fontSize = 12.sp, color = Muted)\n            }\n\n            Spacer(Modifier.height(14.dp))\n'''
new = '''            // FamilyTodayCard above already gives the full at-a-glance day overview.\n            // Keep this card focused on planning, todo/shopping counts and conflicts.\n            Spacer(Modifier.height(14.dp))\n'''
if old in text:
    assistant.write_text(text.replace(old, new, 1))
elif 'FamilyTodayCard above already gives' not in text:
    raise SystemExit('Assistant duplicate-today anchor not found')

print('Final UX cleanup applied')
