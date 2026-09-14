from pathlib import Path

main_path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
manifest_path = Path('app/src/main/AndroidManifest.xml')

main = main_path.read_text(encoding='utf-8')
manifest = manifest_path.read_text(encoding='utf-8')

old_signature = '''private fun ShoppingScreen(
    session: FamilySession,
    items: List<SyncShoppingItem>,
    onAdd: (String) -> Unit,
    onToggle: (SyncShoppingItem) -> Unit,
    onClear: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var comparison by remember { mutableStateOf<ShoppingPriceComparison?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var comparisonError by remember { mutableStateOf("") }
    val openItems = items.filterNot { it.checked }
'''
new_signature = '''private fun ShoppingScreen(
    session: FamilySession,
    items: List<SyncShoppingItem>,
    onAdd: (String) -> Unit,
    onToggle: (SyncShoppingItem) -> Unit,
    onClear: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var comparison by remember { mutableStateOf<ShoppingPriceComparison?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var comparisonError by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val openItems = items.filterNot { it.checked }
'''
if old_signature not in main:
    raise SystemExit('ShoppingScreen state anchor not found')
main = main.replace(old_signature, new_signature, 1)

old_card = '''        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onToggle(item) }
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.checked, { onToggle(item) })
                Text(item.name, color = if (item.checked) Muted else Color.White)
            }
        }
    }
    if (items.any { it.checked }) TextButton(onClick = onClear) { Text("Rensa avbockade") }
}
'''
new_card = '''        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.checked, { onToggle(item) })
                Text(item.name, color = if (item.checked) Muted else Color.White)
            }
        }
    }
    if (items.any { it.checked }) {
        TextButton(onClick = { showClearConfirmation = true }) { Text("Rensa avbockade") }
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Ta bort varor?") },
            text = { Text("Är du säker på att du vill ta bort de avbockade varorna?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    }
                ) { Text("Ta bort") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Avbryt") }
            }
        )
    }
}
'''
if old_card not in main:
    raise SystemExit('Shopping item card anchor not found')
main = main.replace(old_card, new_card, 1)

main_path.write_text(main, encoding='utf-8')

main_activity = '''        <activity
            android:name=".MainActivity"
            android:exported="true">'''
main_activity_portrait = '''        <activity
            android:name=".MainActivity"
            android:screenOrientation="portrait"
            android:exported="true">'''
if main_activity not in manifest:
    if 'android:name=".MainActivity"' not in manifest or 'android:screenOrientation="portrait"' not in manifest:
        raise SystemExit('MainActivity manifest anchor not found')
else:
    manifest = manifest.replace(main_activity, main_activity_portrait, 1)

join_activity = '''        <activity
            android:name=".JoinFamilyActivity"
            android:exported="true">'''
join_activity_portrait = '''        <activity
            android:name=".JoinFamilyActivity"
            android:screenOrientation="portrait"
            android:exported="true">'''
if join_activity in manifest:
    manifest = manifest.replace(join_activity, join_activity_portrait, 1)

manifest_path.write_text(manifest, encoding='utf-8')

print('Applied portrait lock and safer shopping interactions')
# Triggered after workflow installation.
