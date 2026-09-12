from pathlib import Path

# Keeps the family-location tab wiring reproducible.
p = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
s = p.read_text()
if 'import androidx.compose.material.icons.filled.LocationOn' not in s:
    s = s.replace(
        'import androidx.compose.material.icons.filled.ContentCopy\n',
        'import androidx.compose.material.icons.filled.ContentCopy\nimport androidx.compose.material.icons.filled.LocationOn\n'
    )
settings_block = '''                        4 -> SettingsScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID }, sportUrl, themeMode, onThemeModeSaved, onSportUrlSaved) { url, memberId ->
                            scope.launch {
                                message = "Importerar SportAdmin…"
                                runCatching { SupabaseSync.importSportAdmin(session, url, memberId) }
                                    .onSuccess { message = "$it SportAdmin-aktiviteter synkade" }
                                    .onFailure { message = "SportAdmin-fel: ${it.message}" }
                                refresh()
                            }
                        }
'''
if '5 -> FamilyLocationScreen' not in s:
    if settings_block not in s:
        raise SystemExit('Settings tab block not found')
    s = s.replace(settings_block, settings_block + '                        5 -> FamilyLocationScreen(session, members.filter { it.id != ALL_FAMILY_MEMBER_ID })\n')
nav_block = '''            Icons.Default.People to "Familj",
            Icons.Default.Settings to "Inställningar"
'''
if 'Icons.Default.LocationOn to "Plats"' not in s:
    if nav_block not in s:
        raise SystemExit('Bottom nav block not found')
    s = s.replace(nav_block, '''            Icons.Default.People to "Familj",
            Icons.Default.Settings to "Inställningar",
            Icons.Default.LocationOn to "Plats"
''')
p.write_text(s)

p = Path('app/src/main/java/se/familjekalender/app/FamilyLocationScreen.kt')
s = p.read_text()
s = s.replace('import androidx.compose.foundation.rememberScrollState\n', '')
s = s.replace('import androidx.compose.foundation.verticalScroll\n', '')
s = s.replace('''        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),''', '''        Modifier
            .fillMaxWidth(),''')
p.write_text(s)

p = Path('app/src/main/AndroidManifest.xml')
s = p.read_text()
marker = '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
if 'android.permission.ACCESS_FINE_LOCATION' not in s:
    if marker not in s:
        raise SystemExit('Manifest permission marker not found')
    s = s.replace(marker, marker + '    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />\n    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />\n')
p.write_text(s)
