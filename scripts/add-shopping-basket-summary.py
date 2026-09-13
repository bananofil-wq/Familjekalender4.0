from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
needle = '''                Text("Prisjämförelse", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                result.items.forEach { pricedItem ->
'''
replacement = '''                Text("Prisjämförelse", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                result.splitBasketTotal?.let { total ->
                    Text("Billigast om ni delar upp köpet: ${"%.2f".format(Locale("sv", "SE"), total)} kr", fontWeight = FontWeight.SemiBold)
                }
                result.bestCompleteStore?.let { basket ->
                    Text("Billigaste kompletta butik: ${basket.store} · ${"%.2f".format(Locale("sv", "SE"), basket.total)} kr", fontSize = 13.sp)
                }
                result.splitSavingsAgainstBestCompleteStore?.let { saving ->
                    Text("Möjlig besparing genom att dela upp: ${"%.2f".format(Locale("sv", "SE"), saving)} kr", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
                result.storeSummaries().filterNot { it.complete }.take(4).forEach { basket ->
                    Text("${basket.store}: ${basket.matchedItems}/${basket.totalItems} varor hittade", color = Muted, fontSize = 11.sp)
                }
                result.items.forEach { pricedItem ->
'''
if replacement in text:
    print('Basket summary already present')
elif needle not in text:
    raise SystemExit('Price comparison title anchor missing')
else:
    path.write_text(text.replace(needle, replacement, 1), encoding='utf-8')
    print('Added basket totals, coverage and savings UI')
