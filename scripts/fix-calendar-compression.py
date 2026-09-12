from pathlib import Path

main = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = main.read_text(encoding='utf-8')
old = '''            if (selectedTab == 0) {
                Column(Modifier.fillMaxSize()) {
                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }
                    Box(Modifier.weight(1f)) {
                        ExactCalendarScreen(
                            selectedDate,
                            { selectedDate = it },
                            events,
                            members,
                            palette,
                            themeMode,
                            onAdd = { showAddEvent = true },
                            addMenuRequest = assistantAddRequest
                        )
                    }
                }
            } else {'''
new = '''            if (selectedTab == 0) {
                // The assistant card can be quite tall on busy days. Let the calendar tab
                // scroll instead of forcing the month grid into whatever height remains.
                // ExactCalendarScreen gets a stable viewport so all six week rows keep
                // their intended proportions on different phone aspect ratios.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(590.dp)
                    ) {
                        ExactCalendarScreen(
                            selectedDate,
                            { selectedDate = it },
                            events,
                            members,
                            palette,
                            themeMode,
                            onAdd = { showAddEvent = true },
                            addMenuRequest = assistantAddRequest
                        )
                    }
                }
            } else {'''
if old not in text:
    raise SystemExit('MainActivity target block not found; refusing unsafe patch')
main.write_text(text.replace(old, new, 1), encoding='utf-8')

calendar = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = calendar.read_text(encoding='utf-8')
old = '''        val calendarMinHeight = 300.dp
        val heroHeight = (maxHeight - calendarMinHeight - 8.dp).coerceIn(96.dp, 160.dp)'''
new = '''        val calendarMinHeight = 390.dp
        // Keep some of the seasonal artwork visible above the calendar, but never let
        // that hero area steal the height needed by the six week rows.
        val heroHeight = (maxHeight - calendarMinHeight - 8.dp).coerceIn(72.dp, 135.dp)'''
if old not in text:
    raise SystemExit('ExactCalendarScreen sizing block not found; refusing unsafe patch')
calendar.write_text(text.replace(old, new, 1), encoding='utf-8')
