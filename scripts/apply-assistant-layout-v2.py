from pathlib import Path

main = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = main.read_text(encoding='utf-8')
text = text.replace(
'''    var showAddEvent by remember { mutableStateOf(false) }\n    var message by remember { mutableStateOf("") }''',
'''    var showAddEvent by remember { mutableStateOf(false) }\n    var assistantAddRequest by remember { mutableIntStateOf(0) }\n    var message by remember { mutableStateOf("") }'''
)
text = text.replace(
'''                    FamilyAssistantCard(session, events, members, shopping)\n                    Box(Modifier.weight(1f)) {\n                        ExactCalendarScreen(selectedDate, { selectedDate = it }, events, members, palette) { showAddEvent = true }\n                    }''',
'''                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }\n                    Box(Modifier.weight(1f)) {\n                        ExactCalendarScreen(\n                            selectedDate,\n                            { selectedDate = it },\n                            events,\n                            members,\n                            palette,\n                            onAdd = { showAddEvent = true },\n                            addMenuRequest = assistantAddRequest\n                        )\n                    }'''
)
main.write_text(text, encoding='utf-8')

cal = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = cal.read_text(encoding='utf-8')
text = text.replace(
'''    palette: SeasonPalette,\n    onAdd: () -> Unit\n) {''',
'''    palette: SeasonPalette,\n    onAdd: () -> Unit,\n    addMenuRequest: Int = 0\n) {''',
1
)
text = text.replace(
'''    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)\n\n    fun refreshActivity()''',
'''    LaunchedEffect(addMenuRequest) {\n        if (addMenuRequest > 0) showAddMenu = true\n    }\n\n    fun refreshActivity()''',
1
)
start = text.find('            DayPanel(\n')
if start == -1:
    raise SystemExit('DayPanel call not found')
end_marker = '''            )\n        }\n    }\n\n    if (showAddMenu) {'''
end = text.find(end_marker, start)
if end == -1:
    raise SystemExit('DayPanel end not found')
text = text[:start] + '''        }\n    }\n\n    if (showAddMenu) {''' + text[end + len(end_marker):]
cal.write_text(text, encoding='utf-8')
