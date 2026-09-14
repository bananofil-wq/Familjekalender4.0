from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/FamilyAssistantScreen.kt')
text = path.read_text(encoding='utf-8')

old_subtitle = '                    Text("Här är familjens läge just nu.", fontSize = 14.sp, color = Muted)'
new_subtitle = '                    Text("Dagens plan, krockar och det som behöver lösas.", fontSize = 14.sp, color = Muted)'
if new_subtitle not in text:
    if old_subtitle not in text:
        raise SystemExit('Subtitle anchor not found')
    text = text.replace(old_subtitle, new_subtitle, 1)

stats_anchor = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AssistantStat("✓", "${openTodoItems.size} kvar", "To-Do", Modifier.weight(1f)) { popup = AssistantPopup.TODO }
                AssistantStat("🛒", "${openShoppingItems.size} kvar", "Inköp", Modifier.weight(1f)) { popup = AssistantPopup.SHOPPING }
                AssistantStat("●", "${todaysEvents.size}", "idag", Modifier.weight(1f)) { popup = AssistantPopup.TODAY }
                AssistantStat("▣", "${tomorrowsEvents.size}", "imorgon", Modifier.weight(1f)) { popup = AssistantPopup.TOMORROW }
            }
'''
agenda_block = stats_anchor + '''
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dagens plan", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Text("${todaysEvents.size} aktiviteter", fontSize = 11.sp, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            if (todaysEvents.isEmpty()) {
                Surface(
                    color = Color.White.copy(alpha = .04f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Inget tidsatt idag. En sällsynt seger över logistiken.", modifier = Modifier.padding(12.dp), color = Muted, fontSize = 12.sp)
                }
            } else {
                todaysEvents.take(5).forEach { event ->
                    val who = memberName(event.memberId, members)
                    val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                    Surface(
                        color = Color.White.copy(alpha = .04f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shortEventTitle(event), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                Text(who, fontSize = 11.sp, color = Muted, maxLines = 1)
                            }
                        }
                    }
                }
                if (todaysEvents.size > 5) {
                    TextButton(onClick = { popup = AssistantPopup.TODAY }, modifier = Modifier.align(Alignment.End)) {
                        Text("Visa alla ${todaysEvents.size}")
                    }
                }
            }
'''
if agenda_block not in text:
    if stats_anchor not in text:
        raise SystemExit('Stats anchor not found')
    text = text.replace(stats_anchor, agenda_block, 1)

old_attention = '''            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Konfliktvarning", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
            if (planning.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Behöver planeras", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                planning.take(3).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Förslag", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                actions.take(2).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
'''
new_attention = '''            val attentionCount = conflicts.size + planning.size + actions.size
            if (attentionCount > 0) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .08f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Behöver din uppmärksamhet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                            Text("$attentionCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                        conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                        planning.take(2).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .88f)) }
                        actions.take(1).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text("✓ Inga krockar eller olösta hämtningar idag", fontSize = 12.sp, color = Color(0xFF6DD6A7), fontWeight = FontWeight.SemiBold)
            }
'''
if new_attention not in text:
    if old_attention not in text:
        raise SystemExit('Attention anchor not found')
    text = text.replace(old_attention, new_attention, 1)

path.write_text(text, encoding='utf-8')
print('Applied world-class Today dashboard')
