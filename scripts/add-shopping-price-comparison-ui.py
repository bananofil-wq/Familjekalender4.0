from pathlib import Path

# Applies the shopping comparison UI to the Android source on the roadmap branch.
path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')

old_call = '''                        1 -> ShoppingScreen(
                            shopping,
                            { name -> scope.launch { SupabaseSync.addShopping(session, name); refresh() } },
                            { item -> scope.launch { SupabaseSync.toggleShopping(session, item); refresh() } },
                            { scope.launch { SupabaseSync.clearChecked(session); refresh() } }
                        )'''
new_call = '''                        1 -> ShoppingScreen(
                            session,
                            shopping,
                            { name -> scope.launch { SupabaseSync.addShopping(session, name); refresh() } },
                            { item -> scope.launch { SupabaseSync.toggleShopping(session, item); refresh() } },
                            { scope.launch { SupabaseSync.clearChecked(session); refresh() } }
                        )'''
if old_call in text:
    text = text.replace(old_call, new_call, 1)
elif new_call not in text:
    raise SystemExit('ShoppingScreen call anchor missing')

start = text.find('@Composable\nprivate fun ShoppingScreen(')
end = text.find('\nprivate fun shareFamilyInvite', start)
if start < 0 or end < 0:
    raise SystemExit('ShoppingScreen block anchors missing')

replacement = '''@Composable
private fun ShoppingScreen(
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

    Text("Inköpslista", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Synkas mellan era telefoner", color = Muted)
    Spacer(Modifier.height(18.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, modifier = Modifier.weight(1f).height(56.dp))
        FilledIconButton(
            onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = ""; comparison = null } },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(56.dp).offset(y = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black.copy(alpha = .78f),
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .45f),
                disabledContentColor = Color.Black.copy(alpha = .55f)
            )
        ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }
    }
    Spacer(Modifier.height(8.dp))

    if (openItems.isNotEmpty()) {
        Button(
            onClick = {
                scope.launch {
                    comparing = true
                    comparisonError = ""
                    runCatching { ShoppingPriceService.compare(openItems.map { it.name }) }
                        .onSuccess { comparison = it }
                        .onFailure { comparisonError = it.message ?: "Kunde inte jämföra priser" }
                    comparing = false
                }
            },
            enabled = !comparing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (comparing) "Jämför priser…" else "Jämför priser")
        }
        Text(
            "Visar bara verifierade priser från ansluten prisdata. Inga uppskattade priser.",
            color = Muted,
            fontSize = 12.sp
        )
        if (comparisonError.isNotBlank()) {
            Text(comparisonError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }

    comparison?.let { result ->
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prisjämförelse", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                result.items.forEach { pricedItem ->
                    val cheapest = pricedItem.cheapest
                    if (cheapest == null) {
                        Text("${pricedItem.query}: inget verifierat pris hittades", color = Muted, fontSize = 13.sp)
                    } else {
                        Text("${pricedItem.query}: ${cheapest.store} ${"%.2f".format(Locale("sv", "SE"), cheapest.price)} kr", fontWeight = FontWeight.SemiBold)
                        val detail = listOfNotNull(cheapest.brand, cheapest.packageText).joinToString(" · ")
                        if (detail.isNotBlank()) Text(detail, color = Muted, fontSize = 12.sp)
                        pricedItem.matches.drop(1).take(3).forEach { match ->
                            Text("  ${match.store}: ${"%.2f".format(Locale("sv", "SE"), match.price)} kr", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                if (result.pendingStores.isNotEmpty()) {
                    Text(
                        "Saknar ännu stabil prisdatakälla: ${result.pendingStores.joinToString()}",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Text(result.attributionText, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    items.forEach { item ->
        Card(
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
text = text[:start] + replacement + text[end:]
path.write_text(text, encoding='utf-8')
print('Added real-data shopping price comparison UI')
