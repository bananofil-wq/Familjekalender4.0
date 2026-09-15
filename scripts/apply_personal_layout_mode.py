from pathlib import Path

path = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")
original = text


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Could not find anchor for {label}")
    text = text.replace(old, new, 1)

replace_once(
'''enum class UiLayoutMode(val label: String, val description: String) {
    FULL("Fullständig", "Alla översikter och familjeverktyg direkt på startsidan"),
    MINIMAL("Minimalistisk", "Ren kalender och dagsagenda med samma familjedata")
}
''',
'''enum class UiLayoutMode(val label: String, val description: String) {
    FULL("Fullständig", "Alla översikter och familjeverktyg direkt på startsidan"),
    MINIMAL("Minimalistisk", "Ren kalender och dagsagenda med samma familjedata"),
    PERSONAL("Personlig", "Tre egna layouter där du väljer moduler och deras position")
}
''',
"personal ui enum"
)

replace_once(
'''    val context = LocalContext.current
    val palette = paletteFor(themeMode)
    var members by remember { mutableStateOf(emptyList<SyncMember>()) }
''',
'''    val context = LocalContext.current
    val appPrefs = remember { context.getSharedPreferences("family_calendar", 0) }
    val palette = paletteFor(themeMode)
    var personalLayoutRevision by remember { mutableIntStateOf(0) }
    val personalProfile = remember(personalLayoutRevision) { PersonalLayoutStore.activeProfile(appPrefs) }
    var members by remember { mutableStateOf(emptyList<SyncMember>()) }
''',
"personal layout state"
)

replace_once(
'''                if (uiLayoutMode == UiLayoutMode.MINIMAL) {
                    MinimalCalendarScreen(
                        selectedDate = selectedDate,
                        onSelect = { selectedDate = it },
                        events = events,
                        members = members,
                        onAdd = {
                            addEventInitialTitle = ""
                            showAddEvent = true
                        },
                        onOpenSettings = { selectedTab = 4 }
                    )
                } else {
''',
'''                if (uiLayoutMode == UiLayoutMode.MINIMAL) {
                    MinimalCalendarScreen(
                        selectedDate = selectedDate,
                        onSelect = { selectedDate = it },
                        events = events,
                        members = members,
                        onAdd = {
                            addEventInitialTitle = ""
                            showAddEvent = true
                        },
                        onOpenSettings = { selectedTab = 4 }
                    )
                } else if (uiLayoutMode == UiLayoutMode.PERSONAL) {
                    PersonalCalendarScreen(
                        session = session,
                        prefs = appPrefs,
                        profile = personalProfile,
                        revision = personalLayoutRevision,
                        selectedDate = selectedDate,
                        onSelectDate = { selectedDate = it },
                        events = events,
                        members = members,
                        shopping = shopping,
                        palette = palette,
                        themeMode = themeMode,
                        onAdd = {
                            addEventInitialTitle = ""
                            showAddEvent = true
                        },
                        onRefresh = {
                            refresh()
                            FamilyCalendarWidget.enqueueRefresh(context)
                        }
                    )
                } else {
''',
"personal calendar branch"
)

replace_once(
'''                            uiLayoutMode,
                            onThemeModeSaved,
                            onUiLayoutModeSaved,
                            onSportSettingsSaved
''',
'''                            uiLayoutMode,
                            personalProfile,
                            personalLayoutRevision,
                            onThemeModeSaved,
                            onUiLayoutModeSaved,
                            { profile ->
                                PersonalLayoutStore.setActiveProfile(appPrefs, profile)
                                personalLayoutRevision++
                            },
                            { personalLayoutRevision++ },
                            onSportSettingsSaved
''',
"settings personal args"
)

replace_once(
'''    themeMode: ThemeMode,
    uiLayoutMode: UiLayoutMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onUiLayoutChanged: (UiLayoutMode) -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
''',
'''    themeMode: ThemeMode,
    uiLayoutMode: UiLayoutMode,
    personalProfile: Int,
    personalLayoutRevision: Int,
    onThemeChanged: (ThemeMode) -> Unit,
    onUiLayoutChanged: (UiLayoutMode) -> Unit,
    onPersonalProfileChanged: (Int) -> Unit,
    onPersonalLayoutChanged: () -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
''',
"settings signature personal args"
)

replace_once(
'''    Text("Gränssnitt", fontWeight = FontWeight.Bold)
    Text("Välj hur appens kalenderstart ska visas. Valet gäller bara den här telefonen.", color = Muted, fontSize = 12.sp)
''',
'''    Text("Gränssnitt", fontWeight = FontWeight.Bold)
    Text("Välj hur appens kalenderstart ska visas. Ditt senaste val sparas som standard på den här telefonen tills du själv byter igen.", color = Muted, fontSize = 12.sp)
''',
"persistent default copy"
)

anchor = '''    UiLayoutMode.values().forEach { mode ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onUiLayoutChanged(mode) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = uiLayoutMode == mode, onClick = { onUiLayoutChanged(mode) })
            Column(Modifier.weight(1f)) {
                Text(mode.label, fontWeight = FontWeight.SemiBold)
                Text(mode.description, color = Muted, fontSize = 11.sp)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
'''
replacement = '''    UiLayoutMode.values().forEach { mode ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onUiLayoutChanged(mode) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = uiLayoutMode == mode, onClick = { onUiLayoutChanged(mode) })
            Column(Modifier.weight(1f)) {
                Text(mode.label, fontWeight = FontWeight.SemiBold)
                Text(mode.description, color = Muted, fontSize = 11.sp)
            }
        }
    }

    if (uiLayoutMode == UiLayoutMode.PERSONAL) {
        Spacer(Modifier.height(8.dp))
        PersonalLayoutEditor(
            prefs = context.getSharedPreferences("family_calendar", 0),
            activeProfile = personalProfile,
            revision = personalLayoutRevision,
            onActiveProfileChanged = onPersonalProfileChanged,
            onChanged = onPersonalLayoutChanged
        )
    }

    Spacer(Modifier.height(16.dp))
'''
replace_once(anchor, replacement, "personal layout editor")

if text == original:
    print("Personal layout mode already applied")
else:
    path.write_text(text, encoding="utf-8")
    print("Applied modular personal layout mode")
