from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')

old_call = '''                SyncedApp(
                    session!!,
                    prefs.getString("sport_url", "") ?: "",
                    themeMode,
                    { prefs.edit().putString("sport_url", it).apply() },
                    {
                        themeMode = it
                        prefs.edit().putString("theme_mode", it.name).apply()
                    }
                )
'''
new_call = '''                SyncedApp(
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
if old_call not in text:
    raise SystemExit('SyncedApp call block not found')
text = text.replace(old_call, new_call, 1)

old_sig = '''private fun SyncedApp(
    session: FamilySession,
    sportUrl: String,
    themeMode: ThemeMode,
    onSportUrlSaved: (String) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit
) {
'''
new_sig = '''private fun SyncedApp(
    session: FamilySession,
    sportUrl: String,
    sportMemberId: String?,
    themeMode: ThemeMode,
    onSportSettingsSaved: (String, String?) -> Unit,
    onThemeModeSaved: (ThemeMode) -> Unit
) {
'''
if old_sig not in text:
    raise SystemExit('SyncedApp signature not found')
text = text.replace(old_sig, new_sig, 1)

old_loop = '''    LaunchedEffect(session.id) {
        refresh()
        while (true) {
            delay(30L * 60L * 1000L)
            refresh()
        }
    }
'''
new_loop = '''    LaunchedEffect(session.id, sportUrl, sportMemberId) {
        suspend fun syncExternalCalendars() {
            if (sportUrl.isNotBlank()) {
                runCatching { SupabaseSync.importSportAdmin(session, sportUrl, sportMemberId) }
            }
        }
        syncExternalCalendars()
        refresh()
        while (true) {
            delay(30L * 60L * 1000L)
            syncExternalCalendars()
            refresh()
        }
    }
'''
if old_loop not in text:
    raise SystemExit('Refresh loop not found')
text = text.replace(old_loop, new_loop, 1)

old_settings_call = '''                        4 -> SettingsScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID }, sportUrl, themeMode, onThemeModeSaved, onSportUrlSaved) { url, memberId ->
'''
new_settings_call = '''                        4 -> SettingsScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID }, sportUrl, sportMemberId, themeMode, onThemeModeSaved, onSportSettingsSaved) { url, memberId ->
'''
if old_settings_call not in text:
    raise SystemExit('SettingsScreen invocation not found')
text = text.replace(old_settings_call, new_settings_call, 1)

old_settings_sig = '''private fun SettingsScreen(
    session: FamilySession,
    members: List<SyncMember>,
    initialSportUrl: String,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onSaveUrl: (String) -> Unit,
    onImport: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var url by remember(initialSportUrl) { mutableStateOf(initialSportUrl) }
    var memberId by remember { mutableStateOf<String?>(members.firstOrNull()?.id) }
'''
new_settings_sig = '''private fun SettingsScreen(
    session: FamilySession,
    members: List<SyncMember>,
    initialSportUrl: String,
    initialSportMemberId: String?,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
    onImport: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var url by remember(initialSportUrl) { mutableStateOf(initialSportUrl) }
    var memberId by remember(initialSportMemberId, members) {
        mutableStateOf(initialSportMemberId?.takeIf { id -> members.any { it.id == id } } ?: members.firstOrNull()?.id)
    }
'''
if old_settings_sig not in text:
    raise SystemExit('SettingsScreen signature block not found')
text = text.replace(old_settings_sig, new_settings_sig, 1)

old_sport_ui = '''    Text("SportAdmin", fontWeight = FontWeight.Bold)
    OutlinedTextField(url, { url = it }, label = { Text("Kalenderlänk") }, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = { onSaveUrl(url); onImport(url, memberId) },
        enabled = url.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Spara och importera") }
'''
new_sport_ui = '''    Text("SportAdmin", fontWeight = FontWeight.Bold)
    Text("Koppla lagets kalender till rätt familjemedlem. Den uppdateras automatiskt var 30:e minut.", color = Muted, fontSize = 12.sp)
    OutlinedTextField(url, { url = it }, label = { Text("Kalenderlänk") }, modifier = Modifier.fillMaxWidth())
    if (members.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Gäller", color = Muted, fontSize = 12.sp)
        members.forEach { member ->
            Row(
                Modifier.fillMaxWidth().clickable { memberId = member.id }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = memberId == member.id, onClick = { memberId = member.id })
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                Spacer(Modifier.width(8.dp))
                Text(member.name)
            }
        }
    }
    Button(
        onClick = { onSaveSportSettings(url.trim(), memberId); onImport(url.trim(), memberId) },
        enabled = url.isNotBlank() && memberId != null,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Spara och synka nu") }
'''
if old_sport_ui not in text:
    raise SystemExit('SportAdmin UI block not found')
text = text.replace(old_sport_ui, new_sport_ui, 1)

path.write_text(text, encoding='utf-8')
print('SportAdmin source now supports member selection and automatic 30-minute sync')
