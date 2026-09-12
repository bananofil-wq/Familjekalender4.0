from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text()
old = '''                            { event ->
                                scope.launch {
                                    deleteCalendarEventsDirect(session, listOf(event.id))
                                    refresh()
                                }
                            }
                        )'''
new = '''                            { event ->
                                scope.launch {
                                    deleteCalendarEventsDirect(session, listOf(event.id))
                                    refresh()
                                }
                            },
                            { eventsToDelete ->
                                scope.launch {
                                    deleteCalendarEventsDirect(session, eventsToDelete.map { it.id })
                                    refresh()
                                }
                            }
                        )'''
if old not in text:
    raise SystemExit('EditableFamilyScreen delete callback block not found')
path.write_text(text.replace(old, new, 1))
