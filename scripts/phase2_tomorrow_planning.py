from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text(encoding='utf-8')

old_state = '''    val conflicts = conflictLines(todaysEvents, members)
    val planning = (familyPlanningLines(todaysEvents, members) + coordinationLines(todaysEvents, members)).distinct()
    val greeting = when (LocalTime.now().hour) {
'''
new_state = '''    val conflicts = conflictLines(todaysEvents, members)
    val planning = (familyPlanningLines(todaysEvents, members) + coordinationLines(todaysEvents, members)).distinct()
    val tomorrowConflicts = conflictLines(tomorrowsEvents, members)
    val tomorrowPlanning = (familyPlanningLines(tomorrowsEvents, members) + coordinationLines(tomorrowsEvents, members)).distinct()
    val greeting = when (LocalTime.now().hour) {
'''
if old_state not in text:
    raise SystemExit('Assistant state insertion point not found')
text = text.replace(old_state, new_state, 1)

old_ui = '''            if (planning.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Behöver planeras", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                planning.take(3).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
'''
new_ui = '''            if (planning.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Behöver planeras", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                planning.take(3).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
            if (tomorrowConflicts.isNotEmpty() || tomorrowPlanning.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Inför imorgon", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                tomorrowConflicts.take(1).forEach {
                    Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                tomorrowPlanning.take(2).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                }
            }
'''
if old_ui not in text:
    raise SystemExit('Assistant planning UI insertion point not found')
text = text.replace(old_ui, new_ui, 1)

path.write_text(text, encoding='utf-8')
print('Added tomorrow planning warnings to family assistant')
