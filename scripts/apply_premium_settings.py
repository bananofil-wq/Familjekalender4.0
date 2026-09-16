from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/se/familjekalender/app"

main_path = src / "MainActivity.kt"
main = main_path.read_text(encoding="utf-8")
start = main.index("@Composable\nprivate fun SettingsScreen(")
end = main.index("@Composable\nprivate fun AnimatedNavIcon", start)

settings_block = r'''@Composable
private fun SettingsScreen(
    session: FamilySession,
    members: List<SyncMember>,
    initialSportUrl: String,
    initialSportMemberId: String?,
    themeMode: ThemeMode,
    uiLayoutMode: UiLayoutMode,
    personalProfile: Int,
    personalLayoutRevision: Int,
    onThemeChanged: (ThemeMode) -> Unit,
    onUiLayoutChanged: (UiLayoutMode) -> Unit,
    onPersonalProfileChanged: (Int) -> Unit,
    onPersonalLayoutChanged: () -> Unit,
    onSaveSportSettings: (String, String?) -> Unit,
    onImport: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var url by remember(initialSportUrl) { mutableStateOf(initialSportUrl) }
    var memberId by remember(initialSportMemberId, members) {
        mutableStateOf(initialSportMemberId?.takeIf { id -> members.any { it.id == id } } ?: members.firstOrNull()?.id)
    }

    Text("Inställningar", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText)
    Text("Familj, utseende och anslutningar", color = LuxuryTextMuted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))

    SettingsSectionCard(
        title = session.name,
        subtitle = "Familjekod · ${session.code}"
    ) {
        Button(
            onClick = { shareFamilyInvite(context, session) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Bjud in till familjen")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { copyFamilyCode(context, session.code) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("Kopiera familjekod")
        }
    }

    Spacer(Modifier.height(14.dp))
    SettingsSectionCard(
        title = "Utseende",
        subtitle = "Samma premiumkänsla, anpassad efter hur ni använder appen."
    ) {
        UiLayoutMode.values().filter { it != UiLayoutMode.PERSONAL }.forEach { mode ->
            val selected = uiLayoutMode == mode
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else LuxurySurfaceElevated,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp)
                    .clickable { onUiLayoutChanged(mode) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { onUiLayoutChanged(mode) })
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, fontWeight = FontWeight.SemiBold, color = LuxuryText)
                        Text(mode.description, color = LuxuryTextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        if (uiLayoutMode == UiLayoutMode.PERSONAL) {
            Spacer(Modifier.height(6.dp))
            PersonalLayoutEditor(
                prefs = context.getSharedPreferences("family_calendar", 0),
                activeProfile = personalProfile,
                revision = personalLayoutRevision,
                onActiveProfileChanged = onPersonalProfileChanged,
                onChanged = onPersonalLayoutChanged
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Färgtema", color = LuxuryTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        ThemeMode.values().forEach { mode ->
            val selected = themeMode == mode
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else Color.Transparent,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeChanged(mode) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { onThemeChanged(mode) })
                    Spacer(Modifier.width(4.dp))
                    Text(mode.label, color = LuxuryText, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SettingsSectionCard(
        title = "SportAdmin",
        subtitle = "Synka lagets kalender automatiskt var 30:e minut."
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Kalenderlänk") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        if (members.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Gäller för", color = LuxuryTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            members.forEach { member ->
                val selected = memberId == member.id
                Surface(
                    color = if (selected) LuxurySurfaceHigh else Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { memberId = member.id }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { memberId = member.id })
                        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                        Spacer(Modifier.width(9.dp))
                        Text(member.name, color = LuxuryText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onSaveSportSettings(url.trim(), memberId); onImport(url.trim(), memberId) },
            enabled = url.isNotBlank() && memberId != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = LuxurySurfaceHigh,
                disabledContentColor = LuxuryTextMuted.copy(alpha = .55f)
            )
        ) { Text("Spara och synka") }
    }

    Spacer(Modifier.height(14.dp))
    MailSettingsCard(session)
    Spacer(Modifier.height(14.dp))
    AppUpdateSettingsCard()
    Spacer(Modifier.height(52.dp))
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = LuxuryTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

'''
main = main[:start] + settings_block + main[end:]
main_path.write_text(main, encoding="utf-8")

mail_path = src / "MailSettingsCard.kt"
mail = mail_path.read_text(encoding="utf-8")
mail = mail.replace(
    '    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {\n        Column(Modifier.padding(16.dp)) {\n            Text("Mailkoppling", fontWeight = FontWeight.Bold, fontSize = 18.sp)\n            Text(\n                "Koppla Gmail med Google. Kalenderinbjudningar från mail läggs in automatiskt och erbjudanden kan matchas mot inköpslistan.",\n                color = Muted,\n                fontSize = 12.sp\n            )',
    '    Card(\n        colors = CardDefaults.cardColors(containerColor = LuxurySurface),\n        shape = MaterialTheme.shapes.large,\n        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),\n        modifier = Modifier.fillMaxWidth()\n    ) {\n        Column(Modifier.padding(18.dp)) {\n            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n                Column(Modifier.weight(1f)) {\n                    Text("E-post", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = LuxuryText)\n                    Text("Kalenderinbjudningar och erbjudanden kan läggas in automatiskt.", color = LuxuryTextMuted, fontSize = 12.sp)\n                }\n                Text("AUTO", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)\n            }'
)
mail = mail.replace('                Text("Inga mailkonton kopplade ännu.", color = Muted, fontSize = 13.sp)', '                Text("Ingen e-post är ansluten ännu.", color = LuxuryTextMuted, fontSize = 12.sp)')
mail = mail.replace(
    '                modifier = Modifier.fillMaxWidth()\n            ) { Text(if (syncing) "Arbetar…" else "Koppla Gmail") }',
    '                modifier = Modifier.fillMaxWidth().height(52.dp),\n                shape = MaterialTheme.shapes.medium\n            ) { Text(if (syncing) "Arbetar…" else "Koppla Gmail") }'
)
mail = mail.replace(
    '                modifier = Modifier.fillMaxWidth()\n            ) { Text("Annan e-post (IMAP)") }',
    '                modifier = Modifier.fillMaxWidth().height(50.dp),\n                shape = MaterialTheme.shapes.medium\n            ) { Text("Annan e-post (IMAP)") }'
)
mail = mail.replace(
    '            if (status.isNotBlank()) Text(status, color = Muted, fontSize = 12.sp)\n            Spacer(Modifier.height(6.dp))\n            Text(\n                "Gmail använder Googles behörighetsflöde och inget Gmail-lösenord sparas i appen. Automatisk kontroll körs var 30:e minut.",\n                color = Muted,\n                fontSize = 11.sp\n            )\n            Text(\n                "För iCloud, Yahoo och annan IMAP krypteras kontouppgifterna med Android Keystore och sparas bara på den här telefonen.",\n                color = Muted,\n                fontSize = 11.sp\n            )',
    '            if (status.isNotBlank()) {\n                Spacer(Modifier.height(8.dp))\n                Card(\n                    colors = CardDefaults.cardColors(containerColor = LuxurySurfaceElevated),\n                    shape = MaterialTheme.shapes.medium,\n                    modifier = Modifier.fillMaxWidth()\n                ) {\n                    Text(status, modifier = Modifier.padding(12.dp), color = LuxuryTextMuted, fontSize = 12.sp)\n                }\n            }\n            Spacer(Modifier.height(10.dp))\n            Text("Privat och säkert", color = LuxuryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)\n            Spacer(Modifier.height(3.dp))\n            Text(\n                "Google-inloggning används för Gmail och inget Gmail-lösenord sparas. Övriga IMAP-konton skyddas med Android Keystore på den här telefonen.",\n                color = LuxuryTextMuted,\n                fontSize = 11.sp,\n                lineHeight = 16.sp\n            )'
)
mail_path.write_text(mail, encoding="utf-8")

update_path = src / "AppUpdate.kt"
update = update_path.read_text(encoding="utf-8")
start = update.index("@Composable\ninternal fun AppUpdateSettingsCard()")
replacement = r'''@Composable
internal fun AppUpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"
    }
    var checking by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var statusText by remember { mutableStateOf("Tryck för att kontrollera om en ny version finns.") }
    var statusIsError by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Appuppdatering", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText)
                    Text("Installerad version · $currentVersion", color = LuxuryTextMuted, fontSize = 12.sp)
                }
                Text("SYSTEM", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = LuxurySurfaceElevated),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    statusText,
                    modifier = Modifier.padding(12.dp),
                    color = if (statusIsError) MaterialTheme.colorScheme.error else LuxuryTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        checking = true
                        statusIsError = false
                        statusText = "Söker efter uppdatering…"
                        runCatching { findAvailableUpdate(currentVersion) }
                            .onSuccess { update ->
                                availableUpdate = update
                                statusText = if (update == null) {
                                    "Du har den senaste publicerade versionen."
                                } else {
                                    "Ny version ${update.version} finns tillgänglig."
                                }
                            }
                            .onFailure { error ->
                                availableUpdate = null
                                statusIsError = true
                                statusText = "Kunde inte kontrollera uppdateringar: ${error.message ?: "okänt fel"}"
                            }
                        checking = false
                    }
                },
                enabled = !checking && !updating,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (checking) "Kontrollerar…" else "Sök efter uppdatering")
            }

            availableUpdate?.let { update ->
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!canInstallPackages(context)) {
                            statusIsError = false
                            statusText = "Tillåt installation från Familjekalendern och gå sedan tillbaka hit."
                            openUnknownSourcesSettings(context)
                        } else {
                            scope.launch {
                                updating = true
                                statusIsError = false
                                statusText = "Laddar ner version ${update.version}…"
                                runCatching { downloadUpdateApk(context, update) }
                                    .onSuccess { apkUri ->
                                        statusText = "Uppdateringen är nedladdad. Bekräfta installationen i Android."
                                        launchInstaller(context, apkUri)
                                    }
                                    .onFailure { error ->
                                        statusIsError = true
                                        statusText = "Kunde inte installera uppdateringen: ${error.message ?: "okänt fel"}"
                                    }
                                updating = false
                            }
                        }
                    },
                    enabled = !checking && !updating,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (updating) "Laddar ner…" else "Uppdatera nu")
                }
            }
        }
    }
}
'''
update = update[:start] + replacement + "\n"
update_path.write_text(update, encoding="utf-8")

print("Premium settings pass applied")
