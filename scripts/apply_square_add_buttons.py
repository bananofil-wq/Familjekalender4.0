from pathlib import Path

# Trigger: square themed add buttons aligned to their 56dp text fields.
files = [
    Path('app/src/main/java/se/familjekalender/app/ToDoScreen.kt'),
    Path('app/src/main/java/se/familjekalender/app/MainActivity.kt'),
]

for path in files:
    text = path.read_text(encoding='utf-8')
    old = '''            modifier = Modifier.size(56.dp),\n            colors = IconButtonDefaults.filledIconButtonColors('''
    new = '''            modifier = Modifier.size(56.dp),\n            shape = RoundedCornerShape(8.dp),\n            colors = IconButtonDefaults.filledIconButtonColors('''
    if old not in text:
        raise SystemExit(f'Expected add-button block not found in {path}')
    text = text.replace(old, new, 1)
    path.write_text(text, encoding='utf-8')

print('Square themed add buttons applied to To-Do and Shopping')
