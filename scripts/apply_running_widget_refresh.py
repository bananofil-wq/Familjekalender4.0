from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
text = path.read_text()

if 'import androidx.compose.ui.platform.LocalContext' not in text:
    text = text.replace('import androidx.compose.ui.graphics.Path\n', 'import androidx.compose.ui.graphics.Path\nimport androidx.compose.ui.platform.LocalContext\n')

if 'val context = LocalContext.current' not in text:
    text = text.replace('    val scope = rememberCoroutineScope()\n', '    val scope = rememberCoroutineScope()\n    val context = LocalContext.current\n', 1)

old = '''                    onChanged()\n                    showAdd = false\n'''
new = '''                    onChanged()\n                    FamilyCalendarWidget.enqueueRefresh(context)\n                    showAdd = false\n'''
if old in text:
    text = text.replace(old, new, 1)

old2 = '''                    onChanged()\n                    showPlan = false\n'''
new2 = '''                    onChanged()\n                    FamilyCalendarWidget.enqueueRefresh(context)\n                    showPlan = false\n'''
if old2 in text:
    text = text.replace(old2, new2, 1)

path.write_text(text)
print('Running widget refresh applied')
