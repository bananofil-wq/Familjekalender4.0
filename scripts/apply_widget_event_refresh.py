from pathlib import Path

path = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        "    val scope = rememberCoroutineScope()\n    val palette = paletteFor(themeMode)",
        "    val scope = rememberCoroutineScope()\n    val context = LocalContext.current\n    val palette = paletteFor(themeMode)"
    ),
    (
        "                                    SupabaseSync.updateEvent(session, event.id, title, date, time, event.endTime, event.memberId)\n                                    refresh()",
        "                                    SupabaseSync.updateEvent(session, event.id, title, date, time, event.endTime, event.memberId)\n                                    refresh()\n                                    FamilyCalendarWidget.enqueueRefresh(context)"
    ),
    (
        "                                    deleteCalendarEventsDirect(session, listOf(event.id))\n                                    refresh()",
        "                                    deleteCalendarEventsDirect(session, listOf(event.id))\n                                    refresh()\n                                    FamilyCalendarWidget.enqueueRefresh(context)"
    ),
    (
        "                refresh()\n                showAddEvent = false",
        "                refresh()\n                FamilyCalendarWidget.enqueueRefresh(context)\n                showAddEvent = false"
    ),
]

for old, new in replacements:
    if new in text:
        continue
    if old not in text:
        raise SystemExit(f"Expected snippet not found:\n{old}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Applied immediate widget refresh after calendar mutations")
