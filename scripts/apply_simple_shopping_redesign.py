from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
start = text.index('@Composable\nprivate fun ShoppingScreen(')
end = text.index('\nprivate fun shareFamilyInvite', start)

replacement = r'''@Composable
private fun ShoppingScreen(
    session: FamilySession,
    items: List<SyncShoppingItem>,
    mailOffers: List<MailOffer>,
    onAdd: (String) -> Unit,
    onToggle: (SyncShoppingItem) -> Unit,
    onClear: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val openItems = items.filterNot { it.checked }
    val checkedItems = items.filter { it.checked }
    val total = items.size
    val done = checkedItems.size

    fun categoryFor(name: String): String {
        val value = name.lowercase(Locale("sv", "SE"))
        return when {
            listOf("äpp", "banan", "päron", "apels", "citron", "gurk", "tomat", "sallad", "lök", "potatis", "morot", "paprika", "avokado", "frukt", "grönsak").any { it in value } -> "Frukt & grönt"
            listOf("mjölk", "fil", "yoghurt", "ost", "smör", "grädde", "ägg", "kvarg").any { it in value } -> "Mejeri & ägg"
            listOf("kött", "kyckling", "färs", "korv", "bacon", "fisk", "lax", "skinka").any { it in value } -> "Kött & fisk"
            listOf("fryst", "glass", "pizza", "pommes").any { it in value } -> "Frys"
            listOf("schampo", "tvål", "tand", "deo", "blöj", "toalett", "hygien").any { it in value } -> "Hygien"
            listOf("disk", "tvätt", "soppås", "hushåll", "folie", "bakplåt", "rengör").any { it in value } -> "Hushåll"
            listOf("bröd", "kaffe", "te", "pasta", "ris", "mjöl", "socker", "fling", "konserv", "sås", "krydd").any { it in value } -> "Skafferi"
            else -> "Övrigt"
        }
    }

    val categoryOrder = listOf("Frukt & grönt", "Mejeri & ägg", "Kött & fisk", "Skafferi", "Frys", "Hygien", "Hushåll", "Övrigt")
    val grouped = openItems.groupBy { categoryFor(it.name) }

    Text("Inköp", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("Familjens gemensamma inköpslista", color = Muted)
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = SoftPurple), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (total == 0) "Listan är tom" else "$done av $total klara", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(if (openItems.isEmpty() && total > 0) "Allt är fixat" else "${openItems.size} kvar att handla", color = Muted, fontSize = 13.sp)
                }
                if (total > 0) Text("${done * 100 / total}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Lägg till vara") }, placeholder = { Text("t.ex. mjölk, bananer, kaffe") }, singleLine = true, modifier = Modifier.weight(1f))
        FilledIconButton(onClick = {
            text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }.forEach(onAdd)
            text = ""
        }, enabled = text.isNotBlank(), modifier = Modifier.size(56.dp), shape = RoundedCornerShape(14.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.Default.Add, contentDescription = "Lägg till", tint = Color.Black)
        }
    }
    Text("Tips: skriv flera varor separerade med kommatecken", color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(14.dp))

    if (openItems.isEmpty() && checkedItems.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(8.dp))
                Text("Dags att fylla listan", fontWeight = FontWeight.SemiBold)
                Text("Lägg till det ni behöver ovan", color = Muted, fontSize = 13.sp)
            }
        }
    }

    categoryOrder.forEach { category ->
        val categoryItems = grouped[category].orEmpty()
        if (categoryItems.isNotEmpty()) {
            Text(category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 3.dp))
            categoryItems.forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = false, onCheckedChange = { onToggle(item) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, fontSize = 16.sp)
                            val offersForItem = mailOffers
                                .filter { mailOfferMatchesItem(it, item.name) }
                                .sortedBy { it.price }
                                .take(3)
                            offersForItem.forEach { offer ->
                                val unit = offer.unitText?.let { " / $it" }.orEmpty()
                                val formattedPrice = "%.2f".format(Locale.US, offer.price).replace('.', ',')
                                Text(
                                    "${offer.store}: $formattedPrice kr$unit",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (checkedItems.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Klara (${checkedItems.size})", color = Muted, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { showClearConfirmation = true }) { Text("Rensa avbockade") }
        }
        checkedItems.forEach { item ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = .62f)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = true, onCheckedChange = { onToggle(item) })
                    Text(item.name, color = Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(onDismissRequest = { showClearConfirmation = false }, title = { Text("Rensa avbockade?") }, text = { Text("${checkedItems.size} ${if (checkedItems.size == 1) "vara" else "varor"} tas bort från listan.") }, confirmButton = { TextButton(onClick = { showClearConfirmation = false; onClear() }) { Text("Ta bort") } }, dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Avbryt") } })
    }
}
'''

path.write_text(text[:start] + replacement + text[end:], encoding='utf-8')
print('Applied simple shopping redesign with mail offers preserved')
# workflow trigger
