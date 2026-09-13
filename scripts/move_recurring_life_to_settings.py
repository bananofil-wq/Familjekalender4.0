from pathlib import Path
import subprocess

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
base = subprocess.check_output(['git','show','2be23d11d728b2e576f2f68214b024472ca17aee:app/src/main/java/se/familjekalender/app/MainActivity.kt'], text=True)

old_home = '''                    FamilyTodayCard(events = events, members = members)\n                    RecurringLifeCard(session = session, events = events) { scope.launch { refresh() } }\n                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }'''
new_home = '''                    FamilyTodayCard(events = events, members = members)\n                    FamilyAssistantCard(session, events, members, shopping) { assistantAddRequest++ }'''
if old_home not in base:
    raise SystemExit('Calendar home anchor not found')
base = base.replace(old_home, new_home, 1)

old_settings_call = '''                        4 -> SettingsScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID }, sportUrl, sportMemberId, themeMode, onThemeModeSaved, onSportSettingsSaved) { url, memberId ->\n                            scope.launch {\n                                message = "Importerar SportAdmin…"\n                                runCatching { SupabaseSync.importSportAdmin(session, url, memberId) }\n                                    .onSuccess { message = "$it SportAdmin-aktiviteter synkade" }\n                                    .onFailure { message = "SportAdmin-fel: ${it.message}" }\n                                refresh()\n                            }\n                        }'''
new_settings_call = '''                        4 -> SettingsScreen(\n                            session,\n                            members.filter { it.id != ALL_FAMILY_MEMBER_ID },\n                            events,\n                            sportUrl,\n                            sportMemberId,\n                            themeMode,\n                            onThemeModeSaved,\n                            onSportSettingsSaved,\n                            { scope.launch { refresh() } }\n                        ) { url, memberId ->\n                            scope.launch {\n                                message = "Importerar SportAdmin…"\n                                runCatching { SupabaseSync.importSportAdmin(session, url, memberId) }\n                                    .onSuccess { message = "$it SportAdmin-aktiviteter synkade" }\n                                    .onFailure { message = "SportAdmin-fel: ${it.message}" }\n                                refresh()\n                            }\n                        }'''
if old_settings_call not in base:
    raise SystemExit('Settings call anchor not found')
base = base.replace(old_settings_call, new_settings_call, 1)

old_signature = '''private fun SettingsScreen(\n    session: FamilySession,\n    members: List<SyncMember>,\n    initialSportUrl: String,\n    initialSportMemberId: String?,\n    themeMode: ThemeMode,\n    onThemeChanged: (ThemeMode) -> Unit,\n    onSaveSportSettings: (String, String?) -> Unit,\n    onImport: (String, String?) -> Unit\n) {'''
new_signature = '''private fun SettingsScreen(\n    session: FamilySession,\n    members: List<SyncMember>,\n    events: List<SyncEvent>,\n    initialSportUrl: String,\n    initialSportMemberId: String?,\n    themeMode: ThemeMode,\n    onThemeChanged: (ThemeMode) -> Unit,\n    onSaveSportSettings: (String, String?) -> Unit,\n    onRecurringLifeChanged: () -> Unit,\n    onImport: (String, String?) -> Unit\n) {'''
if old_signature not in base:
    raise SystemExit('Settings signature anchor not found')
base = base.replace(old_signature, new_signature, 1)

old_update = '''    Spacer(Modifier.height(20.dp))\n    AppUpdateSettingsCard()'''
new_update = '''    Spacer(Modifier.height(20.dp))\n    Text("Planering", fontWeight = FontWeight.Bold)\n    Text("Veckomall, kopiering av veckor samt lov och semester.", color = Muted, fontSize = 12.sp)\n    Spacer(Modifier.height(8.dp))\n    RecurringLifeCard(session = session, events = events, onChanged = onRecurringLifeChanged)\n\n    Spacer(Modifier.height(20.dp))\n    AppUpdateSettingsCard()'''
if old_update not in base:
    raise SystemExit('Update card anchor not found')
base = base.replace(old_update, new_update, 1)

path.write_text(base)
print('Restored MainActivity and moved recurring-life tools to Settings')
