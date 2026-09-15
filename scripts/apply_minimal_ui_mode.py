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


theme_enum = '''enum class ThemeMode(val label: String, val emoji: String) {
    AUTO("Automatisk", "✨"),
    SPRING("Vår", "🌸"),
    SUMMER("Sommar", "☀️"),
    AUTUMN("Höst", "🍂"),
    WINTER("Vinter", "❄️"),
    CLASSIC("Klassisk", "💜")
}
'''
ui_enum = theme_enum + '''
enum class UiLayoutMode(val label: String, val description: String) {
    FULL("Fullständig", "Alla översikter och familjeverktyg direkt på startsidan"),
    MINIMAL("Minimalistisk", "Ren kalender och dagsagenda med samma familjedata")
}
'''
replace_once(theme_enum, ui_enum, "UiLayoutMode enum")

palette_anchor = '''    val palette = paletteFor(themeMode)
'''
layout_state = '''    var uiLayoutMode by remember {
        mutableStateOf(
            runCatching {
                UiLayoutMode.valueOf(
                    prefs.getString("ui_layout_mode", UiLayoutMode.FULL.name) ?: UiLayoutMode.FULL.name
                )
            }.getOrDefault(UiLayoutMode.FULL)
        )
    }
    val palette = paletteFor(themeMode)
'''
replace_once(palette_anchor, layout_state, "UI mode state")

synced_call_old = '''                    SyncedApp(
                        session!!,
                        prefs.getString("sport_url", "") ?: "",
                        prefs.getString("sport_member_id", null),
                        themeMode,
                        { url, memberId ->
                            prefs.edit()
                                .putString("sport_url", url)
                                .putString("sport_member_id", memberId)
                                .apply()
                        },
                        {
                            themeMode = it
                            prefs.edit().putString("theme_mode", it.name).apply()
                        }
                    )
'''
synced_call_new = '''                    SyncedApp(
                        session!!,
                        prefs.getString("sport_url", "") ?: "",
                        prefs.getString("sport_member_id", null),
                        themeMode,
                        uiLayoutMode,
                        { url, memberId ->
                            prefs.edit()
                                .putString("sport_url", url)
                                .putString("sport_member_id", memberId)
                                .apply()
                        },
                        {
                            themeMode = it
                            prefs.edit().putString("theme_mode", it.name).apply()
                        },
                        {
                            uiLayoutMode = it
                            prefs.edit().putString("ui_layout_mode", it.name).apply()
                        }
                    )
'''
replace_once(synced_call_old, synced_call_new, "SyncedApp invocation")

synced_signature_old = '''private fun SyncedApp(
    session: FamilySession,
    sportUrl: String,
    sportMemberId: String?,
    themeMode: ThemeMode,
    onSportSettingsSaved: (String, String?) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit
) {
'''
synced_signature_new = '''private fun SyncedApp(
    session: FamilySession,
    sportUrl: String,
    sportMemberId: String?,
    themeMode: ThemeMode,
    uiLayoutMode: UiLayoutMode,
    onSportSettingsSaved: (String, String?) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit,
    onUiLayoutModeSaved: (UiLayoutMode) -> Unit
) {
'''
replace_once(synced_signature_old, synced_signature_new, "SyncedApp signature")

scaffold_old = '''    Scaffold(containerColor = Bg, bottomBar = { BottomNav(selectedTab) { selectedTab = it } }) { padding ->
'''
scaffold_new = '''    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (uiLayoutMode == UiLayoutMode.MINIMAL) {
                MinimalBottomNav(selectedTab) { selectedTab = it }
            } else {
                BottomNav(selectedTab) { selectedTab = it }
            }
        }
    ) { padding ->
'''
replace_once(scaffold_old, scaffold_new, "layout-aware bottom navigation")

calendar_anchor = '''            if (selectedTab == 0) {
'''
minimal_branch = '''                if (uiLayoutMode == UiLayoutMode.MINIMAL) {
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
'''
if minimal_branch not in text:
    if calendar_anchor not in text:
        raise SystemExit("Could not find calendar tab anchor")
    calendar_pos = text.index(calendar_anchor)
    insert_pos = calendar_pos + len(calendar_anchor)
    text = text[:insert_pos] + minimal_branch + text[insert_pos:]

    close_anchor = '''            } else {
                Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
'''
    close_pos = text.find(close_anchor, insert_pos + len(minimal_branch))
    if close_pos < 0:
        raise SystemExit("Could not find calendar tab closing anchor")
    text = text[:close_pos] + '''                }
''' + text[close_pos:]

family_anchor = '''                        3 -> {
                            RunningProgressCard(
'''
family_new = '''                        3 -> {
                            if (uiLayoutMode == UiLayoutMode.MINIMAL) {
                                OutlinedButton(
                                    onClick = { selectedTab = 5 },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                    Text(" Familjens plats")
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            RunningProgressCard(
'''
replace_once(family_anchor, family_new, "minimal family location access")

settings_call_old = '''                        4 -> SettingsScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID }, sportUrl, sportMemberId, themeMode, onThemeModeSaved, onSportSettingsSaved) { url, memberId ->
'''
settings_call_new = '''                        4 -> SettingsScreen(
                            session,
                            members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                            sportUrl,
                            sportMemberId,
                            themeMode,
                            uiLayoutMode,
                            onThemeModeSaved,
                            onUiLayoutModeSaved,
                            onSportSettingsSaved
                        ) { url, memberId ->
'''
replace_once(settings_call_old, settings_call_new, "SettingsScreen invocation")

settings_signature_old = '''private fun SettingsScreen(
    session: FamilySession,
    members: List<SyncMember>,
    initialSportUrl: String,
    initialSportMemberId: String?,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
    onImport: (String, String?) -> Unit
) {
'''
settings_signature_new = '''private fun SettingsScreen(
    session: FamilySession,
    members: List<SyncMember>,
    initialSportUrl: String,
    initialSportMemberId: String?,
    themeMode: ThemeMode,
    uiLayoutMode: UiLayoutMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onUiLayoutChanged: (UiLayoutMode) -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
    onImport: (String, String?) -> Unit
) {
'''
replace_once(settings_signature_old, settings_signature_new, "SettingsScreen signature")

settings_theme_anchor = '''    Spacer(Modifier.height(16.dp))
    Text("Tema", fontWeight = FontWeight.Bold)
'''
settings_ui_block = '''    Spacer(Modifier.height(16.dp))
    Text("Gränssnitt", fontWeight = FontWeight.Bold)
    Text("Välj hur appens kalenderstart ska visas. Valet gäller bara den här telefonen.", color = Muted, fontSize = 12.sp)
    UiLayoutMode.values().forEach { mode ->
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
    Text("Tema", fontWeight = FontWeight.Bold)
'''
replace_once(settings_theme_anchor, settings_ui_block, "Settings UI selector")

bottom_nav_anchor = '''@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
'''
minimal_nav = '''@Composable
private fun MinimalBottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val accent = Color(0xFFA66CFF)
    val mappedSelection = when (selected) {
        0 -> 0
        1 -> 1
        2 -> 2
        3, 5 -> 3
        else -> -1
    }
    NavigationBar(containerColor = Color(0xFA0B0B10), tonalElevation = 0.dp) {
        listOf(
            Triple(0, Icons.Default.CalendarMonth, "Kalender"),
            Triple(1, Icons.Default.ShoppingCart, "Inköp"),
            Triple(2, Icons.Default.CheckCircle, "To-do"),
            Triple(3, Icons.Default.People, "Familj")
        ).forEachIndexed { index, (tab, icon, label) ->
            NavigationBarItem(
                selected = mappedSelection == index,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, maxLines = 1, softWrap = false, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    unselectedIconColor = Color(0xFFAAA8B7),
                    unselectedTextColor = Color(0xFFAAA8B7),
                    indicatorColor = accent.copy(alpha = .12f)
                )
            )
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
'''
replace_once(bottom_nav_anchor, minimal_nav, "minimal bottom navigation")

if text == original:
    print("Minimal UI mode already applied")
else:
    path.write_text(text, encoding="utf-8")
    print("Applied selectable minimalist UI mode")
