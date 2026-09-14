from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/RunningProgressCard.kt')
text = path.read_text(encoding='utf-8')

old = '''    val monthRuns = runs.filter { it.event.date.year == today.year && it.event.date.month == today.month }\n    val monthKm = monthRuns.sumOf { it.distanceKm }\n    val activeWeeks = runs\n'''
new = '''    val monthRuns = runs.filter { it.event.date.year == today.year && it.event.date.month == today.month }\n    val monthKm = monthRuns.sumOf { it.distanceKm }\n    val goalPrefs = remember { context.getSharedPreferences("running_month_goals", 0) }\n    val goalKey = "${selectedMemberId ?: "none"}_${today.year}_${today.monthValue}"\n    var monthlyGoalText by remember(goalKey) {\n        mutableStateOf(goalPrefs.getFloat(goalKey, 0f).takeIf { it > 0f }?.let { "%.0f".format(Locale.US, it) } ?: "")\n    }\n    val monthlyGoalKm = monthlyGoalText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }\n    val monthlyGoalProgress = monthlyGoalKm?.let { (monthKm / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f\n    val activeWeeks = runs\n'''
if old not in text:
    raise SystemExit('stats anchor not found')
text = text.replace(old, new, 1)

old_ui = '''                Spacer(Modifier.height(8.dp))\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    RunStat(\n                        "Längsta pass",\n'''
new_ui = '''                Spacer(Modifier.height(10.dp))\n                Text("Månadsmål", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)\n                Spacer(Modifier.height(6.dp))\n                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    OutlinedTextField(\n                        value = monthlyGoalText,\n                        onValueChange = { value -> monthlyGoalText = value.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },\n                        label = { Text("Mål km") },\n                        singleLine = true,\n                        modifier = Modifier.weight(0.42f)\n                    )\n                    Button(\n                        onClick = {\n                            val goal = monthlyGoalText.replace(',', '.').toFloatOrNull()\n                            if (goal != null && goal > 0f) goalPrefs.edit().putFloat(goalKey, goal).apply()\n                            else goalPrefs.edit().remove(goalKey).apply()\n                        },\n                        modifier = Modifier.weight(0.58f)\n                    ) { Text("Spara mål") }\n                }\n                if (monthlyGoalKm != null) {\n                    Spacer(Modifier.height(8.dp))\n                    LinearProgressIndicator(\n                        progress = { monthlyGoalProgress },\n                        modifier = Modifier.fillMaxWidth()\n                    )\n                    Spacer(Modifier.height(4.dp))\n                    Text(\n                        "${"%.1f".format(Locale.US, monthKm)} av ${"%.1f".format(Locale.US, monthlyGoalKm)} km · ${(monthlyGoalProgress * 100).toInt()}%",\n                        color = Muted,\n                        fontSize = 12.sp\n                    )\n                }\n\n                Spacer(Modifier.height(8.dp))\n                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    RunStat(\n                        "Längsta pass",\n'''
if old_ui not in text:
    raise SystemExit('ui anchor not found')
text = text.replace(old_ui, new_ui, 1)

path.write_text(text, encoding='utf-8')
print('Running monthly goal patch applied')
