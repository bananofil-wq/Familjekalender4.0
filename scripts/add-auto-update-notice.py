from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/AppUpdate.kt')
s = p.read_text()

s = s.replace('import androidx.compose.material3.CardDefaults\n', 'import androidx.compose.material3.CardDefaults\nimport androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.TextButton\n')
s = s.replace('import androidx.compose.runtime.Composable\n', 'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n')

needle = '@Composable\ninternal fun AppUpdateSettingsCard() {'
insert = '''@Composable
internal fun AutomaticUpdateNotice() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"
    }
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var updating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentVersion) {
        runCatching { findAvailableUpdate(currentVersion) }
            .onSuccess { availableUpdate = it }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("Ny uppdatering finns") },
            text = {
                Column {
                    Text("Familjekalender ${update.version} är tillgänglig.")
                    errorText?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !updating,
                    onClick = {
                        if (!canInstallPackages(context)) {
                            openUnknownSourcesSettings(context)
                            errorText = "Tillåt installation från Familjekalendern och öppna sedan appen igen."
                        } else {
                            scope.launch {
                                updating = true
                                errorText = null
                                runCatching { downloadUpdateApk(context, update) }
                                    .onSuccess { launchInstaller(context, it) }
                                    .onFailure { errorText = "Kunde inte hämta uppdateringen: ${it.message ?: "okänt fel"}" }
                                updating = false
                            }
                        }
                    }
                ) { Text(if (updating) "Laddar ner…" else "Uppdatera nu") }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) { Text("Senare") }
            }
        )
    }
}

@Composable
internal fun AppUpdateSettingsCard() {'''
if needle not in s:
    raise SystemExit('AppUpdateSettingsCard marker not found')
s = s.replace(needle, insert, 1)
p.write_text(s)

p = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
s = p.read_text()
needle = '''    if (showAddEvent) {
        AddEventDialog'''
replacement = '''    AutomaticUpdateNotice()

    if (showAddEvent) {
        AddEventDialog'''
if needle not in s:
    raise SystemExit('showAddEvent marker not found')
s = s.replace(needle, replacement, 1)
p.write_text(s)
