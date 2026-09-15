from pathlib import Path
import re

path = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")
original = text

# Keep the existing enum values for backwards compatibility with saved preferences,
# but present only the two clear user-facing choices requested for the app.
text = text.replace(
    'FULL("Fullständig", "Alla översikter och familjeverktyg direkt på startsidan")',
    'FULL("Löpning & vardag", "Veckan, dagens åtaganden och löpningen i en lugn personlig vy")'
)
text = text.replace(
    'MINIMAL("Minimalistisk", "Ren kalender och dagsagenda med samma familjedata")',
    'MINIMAL("Clean", "Ren månadskalender med en diskret markering per dag")'
)
text = text.replace(
    'PERSONAL("Personlig", "Tre egna layouter där du väljer moduler och deras position")',
    'PERSONAL("Anpassad", "Avancerad modulvy")'
)

# New installs should open in the calm calendar. Existing FULL selections continue
# to work and now resolve to the running/everyday dashboard.
text = text.replace(
    'prefs.getString("ui_layout_mode", UiLayoutMode.FULL.name) ?: UiLayoutMode.FULL.name',
    'prefs.getString("ui_layout_mode", UiLayoutMode.MINIMAL.name) ?: UiLayoutMode.MINIMAL.name'
)
text = text.replace(
    '}.getOrDefault(UiLayoutMode.FULL)',
    '}.getOrDefault(UiLayoutMode.MINIMAL)'
)

# Replace the old overloaded FULL start page with the focused running/everyday view.
if 'RunningLifeDashboard(' not in text:
    pattern = re.compile(
        r'''                \} else \{\n                // The assistant card can be quite tall on busy days\..*?\n                \}\n                \}\n            \} else \{''',
        re.DOTALL,
    )
    replacement = '''                } else {
                    RunningLifeDashboard(
                        session = session,
                        selectedDate = selectedDate,
                        onSelectDate = { selectedDate = it },
                        events = events,
                        members = members,
                        onAdd = {
                            addEventInitialTitle = ""
                            showAddEvent = true
                        },
                        onOpenSettings = { selectedTab = 4 },
                        onRefresh = {
                            refresh()
                            FamilyCalendarWidget.enqueueRefresh(context)
                        }
                    )
                }
            } else {'''
    text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit("Could not replace old FULL calendar dashboard")

# Settings now expose the two simple choices. The old PERSONAL value remains only
# as a compatibility path for devices that already had it selected.
text = text.replace(
    'Text("Välj hur appens kalenderstart ska visas. Ditt senaste val sparas som standard på den här telefonen tills du själv byter igen.", color = Muted, fontSize = 12.sp)',
    'Text("Välj mellan en ren kalender och en vardagsvy med löpningen i fokus. Valet sparas på den här telefonen.", color = Muted, fontSize = 12.sp)'
)
text = text.replace(
    'UiLayoutMode.values().forEach { mode ->',
    'UiLayoutMode.values().filter { it != UiLayoutMode.PERSONAL }.forEach { mode ->',
    1,
)

if text == original:
    print("Clean and running/everyday modes already applied")
else:
    path.write_text(text, encoding="utf-8")
    print("Applied clean calendar and running/everyday dashboard")
