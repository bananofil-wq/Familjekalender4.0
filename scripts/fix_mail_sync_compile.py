from pathlib import Path

APP = Path('app/src/main/java/se/familjekalender/app')

settings_path = APP / 'MailSettingsCard.kt'
settings = settings_path.read_text()
settings = settings.replace('import androidx.compose.foundation.layout.weight\n', '')
settings = settings.replace('Column(Modifier.weight(1f)) {', 'Column(Modifier.fillMaxWidth(0.58f)) {')
settings_path.write_text(settings)

main_path = APP / 'MainActivity.kt'
main = main_path.read_text()
old_sig = '''private fun ShoppingScreen(
    session: FamilySession,
    items: List<SyncShoppingItem>,
    onAdd: (String) -> Unit,
'''
new_sig = '''private fun ShoppingScreen(
    session: FamilySession,
    items: List<SyncShoppingItem>,
    mailOffers: List<MailOffer>,
    onAdd: (String) -> Unit,
'''
if old_sig in main:
    main = main.replace(old_sig, new_sig, 1)
elif new_sig not in main:
    raise SystemExit('ShoppingScreen signature marker not found')

old_row = '''                        Checkbox(checked = false, onCheckedChange = { onToggle(item) })
                        Text(item.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
'''
new_row = '''                        Checkbox(checked = false, onCheckedChange = { onToggle(item) })
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
'''
if old_row in main:
    main = main.replace(old_row, new_row, 1)
elif 'val offersForItem = mailOffers' not in main:
    raise SystemExit('Shopping item row marker not found')

main_path.write_text(main)
print('Mail sync compile fixes applied.')
