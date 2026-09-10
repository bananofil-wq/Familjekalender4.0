from pathlib import Path

main_path = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
main = main_path.read_text()

# Keep the app's seasonal calendar palette, but use the approved purple/black style
# for Material controls and forms instead of autumn orange.
main = main.replace(
    '            primary = palette.accent,\n            background = Bg,\n            surface = CardBg,',
    '            primary = Purple,\n            secondary = Purple,\n            surfaceVariant = SoftPurple,\n            outline = Purple.copy(alpha = .55f),\n            background = Bg,\n            surface = CardBg,'
)

# Birthday / event form: same dark-purple visual language as the approved work-week mockup.
main = main.replace(
'''    AlertDialog(\n        onDismissRequest = onDismiss,\n        title = { Text(if (isBirthday) "Ny födelsedag" else "Ny aktivitet") },''',
'''    AlertDialog(\n        onDismissRequest = onDismiss,\n        modifier = Modifier.fillMaxWidth(0.94f),\n        shape = RoundedCornerShape(22.dp),\n        containerColor = Color(0xFF17131D),\n        titleContentColor = Color.White,\n        textContentColor = Color.White,\n        title = { Text(if (isBirthday) "Ny födelsedag" else "Ny aktivitet", fontWeight = FontWeight.Bold) },'''
)

# Make the primary add action unmistakably purple even in seasonal themes.
main = main.replace(
'''            Button(\n                onClick = { onAdd(title.trim(), startTime, endTime, memberId, dates.toList(), isBirthday, recurrence) },\n                enabled = title.isNotBlank() && dates.isNotEmpty()\n            ) {''',
'''            Button(\n                onClick = { onAdd(title.trim(), startTime, endTime, memberId, dates.toList(), isBirthday, recurrence) },\n                enabled = title.isNotBlank() && dates.isNotEmpty(),\n                colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White)\n            ) {'''
)

main_path.write_text(main)

cal_path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
cal = cal_path.read_text()

# Work-week dialog: keep it as a true modal overlay, but match the approved compact
# black/purple design instead of inheriting orange/grey seasonal controls.
cal = cal.replace(
'''    AlertDialog(\n        onDismissRequest = { if (!saving) onDismiss() },\n        title = {''',
'''    AlertDialog(\n        onDismissRequest = { if (!saving) onDismiss() },\n        modifier = Modifier.fillMaxWidth(0.94f),\n        shape = RoundedCornerShape(22.dp),\n        containerColor = Color(0xFF17131D),\n        titleContentColor = Color.White,\n        textContentColor = Color.White,\n        title = {''',
1
)

cal = cal.replace(
    '.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))',
    '.background(Color(0xFF21182B))',
    1
)
cal = cal.replace(
    '.background(if (!rotating) MaterialTheme.colorScheme.primary else Color.Transparent)',
    '.background(if (!rotating) Color(0xFF9C4DFF) else Color.Transparent)',
    1
)
cal = cal.replace(
    '.background(if (rotating) MaterialTheme.colorScheme.primary else Color.Transparent)',
    '.background(if (rotating) Color(0xFF9C4DFF) else Color.Transparent)',
    1
)
cal = cal.replace(
    'color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),',
    'color = Color(0xFF201925),',
    1
)
cal = cal.replace(
    'border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))',
    'border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .38f))',
    1
)
cal = cal.replace(
    'color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f),',
    'color = Color(0xFF21182B),',
    1
)
cal = cal.replace(
    'border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),',
    'border = BorderStroke(1.dp, Color(0xFF9C4DFF).copy(alpha = .38f)),',
    1
)

# Purple action buttons in week cards and save action.
cal = cal.replace(
'''                                Button(\n                                    onClick = { editingWeek = index },\n                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),\n                                    modifier = Modifier.fillMaxWidth().height(32.dp)\n                                ) {''',
'''                                Button(\n                                    onClick = { editingWeek = index },\n                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),\n                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White),\n                                    modifier = Modifier.fillMaxWidth().height(32.dp)\n                                ) {'''
)
cal = cal.replace(
'''            Button(\n                enabled = !saving && session != null && selectedMemberId.isNotBlank() && weeks.take(rotationWeeks).any { it.weekdays.isNotEmpty() },''',
'''            Button(\n                enabled = !saving && session != null && selectedMemberId.isNotBlank() && weeks.take(rotationWeeks).any { it.weekdays.isNotEmpty() },\n                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White),''',
1
)

# The nested week editor should use the same styling too.
cal = cal.replace(
'''        AlertDialog(\n            onDismissRequest = { editingWeek = null },\n            title = { Text("Redigera vecka ${index + 1}") },''',
'''        AlertDialog(\n            onDismissRequest = { editingWeek = null },\n            modifier = Modifier.fillMaxWidth(0.92f),\n            shape = RoundedCornerShape(22.dp),\n            containerColor = Color(0xFF17131D),\n            titleContentColor = Color.White,\n            textContentColor = Color.White,\n            title = { Text("Redigera vecka ${index + 1}", fontWeight = FontWeight.Bold) },'''
)
cal = cal.replace(
    'confirmButton = { Button(onClick = { editingWeek = null }) { Text("Klar") } },',
    'confirmButton = { Button(onClick = { editingWeek = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C4DFF), contentColor = Color.White)) { Text("Klar") } },'
)

cal_path.write_text(cal)

# Guardrails: fail the workflow instead of silently shipping the wrong design again.
main_check = main_path.read_text()
cal_check = cal_path.read_text()
assert 'primary = Purple' in main_check
assert 'containerColor = Color(0xFF17131D)' in main_check
assert 'Ny födelsedag' in main_check and 'Ny aktivitet' in main_check
assert 'Modifier.fillMaxWidth(0.94f)' in cal_check
assert 'Color(0xFF9C4DFF)' in cal_check
assert 'Text("Fast schema"' in cal_check
assert 'Text("Roterande schema"' in cal_check
