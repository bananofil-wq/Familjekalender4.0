from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = path.read_text(encoding='utf-8')
needle = '''        ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }\n    }\n    items.forEach { item ->\n'''
replacement = '''        ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }\n    }\n    Spacer(Modifier.height(8.dp))\n    items.forEach { item ->\n'''
if needle not in text:
    raise SystemExit('Shopping add-row anchor not found')
text = text.replace(needle, replacement, 1)
path.write_text(text, encoding='utf-8')
print('Added 8dp gap below shopping input row')
