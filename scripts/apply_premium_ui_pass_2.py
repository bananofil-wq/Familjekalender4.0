from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/se/familjekalender/app"


def must_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing expected block: {label}")
    return text.replace(old, new, 1)

# Main navigation: use one stable brand accent and quieter surfaces in every layout mode.
main_path = src / "MainActivity.kt"
main = main_path.read_text(encoding="utf-8")
main = main.replace('    val accent = Color(0xFFA66CFF)\n', '    val accent = MaterialTheme.colorScheme.primary\n')
main = main.replace('    val accent = Color(0xFF9C4DFF)\n', '    val accent = MaterialTheme.colorScheme.primary\n')
main = main.replace('NavigationBar(containerColor = Color(0xFA0B0B10), tonalElevation = 0.dp)', 'NavigationBar(containerColor = LuxuryBackground.copy(alpha = .985f), tonalElevation = 0.dp)')
main = main.replace('NavigationBar(containerColor = Color(0xF20F0E13))', 'NavigationBar(containerColor = LuxuryBackground.copy(alpha = .985f), tonalElevation = 0.dp)')
main = main.replace('                    unselectedIconColor = Color(0xFFAAA8B7),\n                    unselectedTextColor = Color(0xFFAAA8B7),\n                    indicatorColor = accent.copy(alpha = .12f)', '                    unselectedIconColor = LuxuryTextMuted.copy(alpha = .72f),\n                    unselectedTextColor = LuxuryTextMuted.copy(alpha = .72f),\n                    indicatorColor = LuxurySurfaceHigh')
main = main.replace('                    selectedIconColor = accent,\n                    selectedTextColor = accent,\n                    indicatorColor = accent.copy(alpha = .15f)', '                    selectedIconColor = accent,\n                    selectedTextColor = accent,\n                    unselectedIconColor = LuxuryTextMuted.copy(alpha = .72f),\n                    unselectedTextColor = LuxuryTextMuted.copy(alpha = .72f),\n                    indicatorColor = LuxurySurfaceHigh')
main = main.replace('        containerColor = Color(0xFF17131D),', '        containerColor = LuxurySurfaceElevated,')
main = main.replace('        shape = RoundedCornerShape(22.dp),\n        containerColor = LuxurySurfaceElevated,', '        shape = RoundedCornerShape(26.dp),\n        containerColor = LuxurySurfaceElevated,')
main_path.write_text(main, encoding="utf-8")

# Dashboard: reduce visual noise, remove emoji UI, unify spacing and brand color.
assistant_path = src / "FamilyAssistantScreen.kt"
assistant = assistant_path.read_text(encoding="utf-8")
assistant = must_replace(
    assistant,
    'import androidx.compose.material.icons.filled.Add\n',
    'import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.filled.CheckCircle\nimport androidx.compose.material.icons.filled.Event\nimport androidx.compose.material.icons.filled.ShoppingCart\nimport androidx.compose.material.icons.filled.Today\n',
    'assistant icon imports'
)
assistant = must_replace(
    assistant,
    'import androidx.compose.ui.graphics.Color\n',
    'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.vector.ImageVector\n',
    'ImageVector import'
)
assistant = assistant.replace('        colors = CardDefaults.cardColors(containerColor = CardBg),\n        shape = RoundedCornerShape(22.dp),', '        colors = CardDefaults.cardColors(containerColor = LuxurySurface),\n        shape = RoundedCornerShape(28.dp),', 1)
assistant = assistant.replace('        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()', '        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()', 1)
assistant = assistant.replace('        Column(Modifier.padding(18.dp)) {', '        Column(Modifier.padding(22.dp)) {', 1)
assistant = assistant.replace('                    Text(greeting, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)\n                    Text("Dagens plan, krockar och det som behöver lösas.", fontSize = 14.sp, color = Muted)', '                    Text(greeting, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText)\n                    Spacer(Modifier.height(4.dp))\n                    Text("Det viktigaste för familjen just nu.", fontSize = 14.sp, color = LuxuryTextMuted)')
assistant = assistant.replace('                    modifier = Modifier.size(52.dp),\n                    shape = RoundedCornerShape(16.dp),\n                    colors = IconButtonDefaults.filledIconButtonColors(\n                        containerColor = Color(0xFF8F22FF),\n                        contentColor = Color.White\n                    )', '                    modifier = Modifier.size(48.dp),\n                    shape = RoundedCornerShape(18.dp),\n                    colors = IconButtonDefaults.filledIconButtonColors(\n                        containerColor = MaterialTheme.colorScheme.primary,\n                        contentColor = MaterialTheme.colorScheme.onPrimary\n                    )')
assistant = assistant.replace('                    Icon(Icons.Default.Add, contentDescription = "Lägg till", modifier = Modifier.size(30.dp))', '                    Icon(Icons.Default.Add, contentDescription = "Lägg till", modifier = Modifier.size(25.dp))')
assistant = assistant.replace('            Spacer(Modifier.height(14.dp))\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {\n                AssistantStat("✓", "${openTodoItems.size} kvar", "To-Do", Modifier.weight(1f)) { popup = AssistantPopup.TODO }\n                AssistantStat("🛒", "${openShoppingItems.size} kvar", "Inköp", Modifier.weight(1f)) { popup = AssistantPopup.SHOPPING }\n                AssistantStat("●", "${todaysEvents.size}", "idag", Modifier.weight(1f)) { popup = AssistantPopup.TODAY }\n                AssistantStat("▣", "${tomorrowsEvents.size}", "imorgon", Modifier.weight(1f)) { popup = AssistantPopup.TOMORROW }\n            }', '            Spacer(Modifier.height(18.dp))\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                AssistantStat(Icons.Default.CheckCircle, "${openTodoItems.size}", "To-do kvar", Modifier.weight(1f)) { popup = AssistantPopup.TODO }\n                AssistantStat(Icons.Default.ShoppingCart, "${openShoppingItems.size}", "Inköp kvar", Modifier.weight(1f)) { popup = AssistantPopup.SHOPPING }\n                AssistantStat(Icons.Default.Today, "${todaysEvents.size}", "Idag", Modifier.weight(1f)) { popup = AssistantPopup.TODAY }\n                AssistantStat(Icons.Default.Event, "${tomorrowsEvents.size}", "Imorgon", Modifier.weight(1f)) { popup = AssistantPopup.TOMORROW }\n            }')
assistant = assistant.replace('                Text("Dagens plan", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))', '                Text("Dagens plan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText, modifier = Modifier.weight(1f))')
assistant = assistant.replace('                Text("Familjen", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)', '                Text("FAMILJEN", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LuxuryTextMuted, letterSpacing = 1.1.sp)')
assistant = assistant.replace('                            .padding(vertical = 7.dp),', '                            .padding(vertical = 9.dp),', 1)
assistant = assistant.replace('            val attentionCount = conflicts.size + planning.size + actions.size\n            if (attentionCount > 0) {', '            val attentionCount = conflicts.size + planning.size + actions.size\n            val attentionPreview = conflicts.firstOrNull() ?: planning.firstOrNull() ?: actions.firstOrNull()\n            if (attentionCount > 0) {')
assistant = assistant.replace('                    color = MaterialTheme.colorScheme.primary.copy(alpha = .08f),\n                    shape = RoundedCornerShape(16.dp),\n                    modifier = Modifier.fillMaxWidth().clickable { showAttentionDetails = true }', '                    color = LuxurySurfaceHigh,\n                    shape = RoundedCornerShape(20.dp),\n                    border = BorderStroke(1.dp, LuxuryOutlineSoft),\n                    modifier = Modifier.fillMaxWidth().clickable { showAttentionDetails = true }')
assistant = assistant.replace('                            Text("Behöver din uppmärksamhet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))\n                            Text("$attentionCount  ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)', '                            Text("Behöver din uppmärksamhet", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText, modifier = Modifier.weight(1f))\n                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), shape = RoundedCornerShape(99.dp)) {\n                                Text("$attentionCount  ›", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)\n                            }')
assistant = assistant.replace('                        conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }\n                        planning.take(2).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .88f)) }\n                        actions.take(1).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }', '                        attentionPreview?.let {\n                            Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = LuxuryTextMuted, maxLines = 2)\n                        }')

old_stat = '''@Composable
private fun AssistantStat(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .13f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, fontSize = 10.sp, color = Muted, maxLines = 1)
        }
    }
}
'''
new_stat = '''@Composable
private fun AssistantStat(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = LuxurySurfaceElevated
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                shape = CircleShape,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText, maxLines = 1)
            Text(label, fontSize = 10.sp, color = LuxuryTextMuted, maxLines = 1)
        }
    }
}
'''
assistant = must_replace(assistant, old_stat, new_stat, 'AssistantStat')
assistant = assistant.replace('containerColor = Color(0xFF171A20)', 'containerColor = LuxurySurfaceElevated')
assistant = assistant.replace('color = Color(0xFF20242B)', 'color = LuxurySurfaceHigh')
assistant_path.write_text(assistant, encoding="utf-8")

print("Premium UI pass 2 applied")
