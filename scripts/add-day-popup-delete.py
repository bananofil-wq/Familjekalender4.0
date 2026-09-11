from pathlib import Path
p=Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
s=p.read_text(encoding='utf-8')
old='''            onAdd = {\n                dayPopupDate = null\n                onSelect(popupDate)\n                onAdd()\n            }\n        )\n'''
new='''            onAdd = {\n                dayPopupDate = null\n                onSelect(popupDate)\n                onAdd()\n            },\n            onDelete = { event ->\n                val session = currentFamilySession(context)\n                if (session != null) {\n                    scope.launch {\n                        runCatching { deleteCalendarEventsDirect(session, listOf(event.id)) }\n                            .onSuccess {\n                                dayPopupDate = null\n                                refreshActivity()\n                            }\n                    }\n                }\n            }\n        )\n'''
if old not in s: raise SystemExit('popup call anchor not found')
s=s.replace(old,new,1)
old='''    members: List<SyncMember>,\n    onDismiss: () -> Unit,\n    onAdd: () -> Unit\n) {\n'''
new='''    members: List<SyncMember>,\n    onDismiss: () -> Unit,\n    onAdd: () -> Unit,\n    onDelete: (SyncEvent) -> Unit\n) {\n'''
if old not in s: raise SystemExit('signature anchor not found')
s=s.replace(old,new,1)
old='''                                Text(\n                                    if (allFamily) \"Hela familjen\" else member?.name ?: \"Familjen\",\n                                    color = Color.White.copy(alpha = .64f),\n                                    fontSize = 11.sp\n                                )\n'''
new='''                                Column(horizontalAlignment = Alignment.End) {\n                                    Text(\n                                        if (allFamily) \"Hela familjen\" else member?.name ?: \"Familjen\",\n                                        color = Color.White.copy(alpha = .64f),\n                                        fontSize = 11.sp\n                                    )\n                                    if (event.source != \"sportadmin\") {\n                                        TextButton(\n                                            onClick = { onDelete(event) },\n                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)\n                                        ) {\n                                            Text(\"Ta bort\", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)\n                                        }\n                                    }\n                                }\n'''
if old not in s: raise SystemExit('event action anchor not found')
s=s.replace(old,new,1)
p.write_text(s,encoding='utf-8')
