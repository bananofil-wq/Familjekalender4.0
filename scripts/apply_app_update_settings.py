from pathlib import Path

path = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
text = path.read_text(encoding="utf-8")

if "AppUpdateSettingsCard()" in text:
    print("App update Settings card already present")
    raise SystemExit(0)

old = '''    Button(
        onClick = { onSaveUrl(url); onImport(url, memberId) },
        enabled = url.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Spara och importera") }
}'''

new = '''    Button(
        onClick = { onSaveUrl(url); onImport(url, memberId) },
        enabled = url.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Spara och importera") }

    Spacer(Modifier.height(20.dp))
    AppUpdateSettingsCard()
}'''

if old not in text:
    raise SystemExit("Could not find SettingsScreen SportAdmin anchor")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Added AppUpdateSettingsCard to SettingsScreen")
