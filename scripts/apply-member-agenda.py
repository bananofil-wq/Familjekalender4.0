from pathlib import Path

family = Path('app/src/main/java/se/familjekalender/app/FamilyColorEditor.kt')
text = family.read_text()
text = text.replace(
'''internal fun EditableFamilyScreen(
    members: List<SyncMember>,
    onAdd: (String, String) -> Unit,
    onColorChange: (SyncMember, Long) -> Unit
) {''',
'''internal fun EditableFamilyScreen(
    members: List<SyncMember>,
    events: List<SyncEvent>,
    onAdd: (String, String) -> Unit,
    onColorChange: (SyncMember, Long) -> Unit,
    onEventEdit: (SyncEvent, String, java.time.LocalDate, String) -> Unit,
    onEventDelete: (SyncEvent) -> Unit
) {'''
)
text = text.replace(
'''    var editingMember by remember { mutableStateOf<SyncMember?>(null) }

    Text("Familjen", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Tryck på en person för att byta färg", color = Muted, fontSize = 12.sp)''',
'''    var editingMember by remember { mutableStateOf<SyncMember?>(null) }
    var agendaMember by remember { mutableStateOf<SyncMember?>(null) }

    Text("Familjen", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Tryck på en person för att se planeringen framöver", color = Muted, fontSize = 12.sp)'''
)
text = text.replace(
'''            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editingMember = member }
''',
'''            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { agendaMember = member }
'''
)
text = text.replace(
'''                Text("Byt färg", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
''',
'''                TextButton(onClick = { editingMember = member }) { Text("Färg", fontSize = 11.sp) }
'''
)
needle = '''    editingMember?.let { member ->
        AlertDialog('''
insert = '''    agendaMember?.let { member ->
        MemberAgendaDialog(
            member = member,
            events = events,
            onDismiss = { agendaMember = null },
            onEdit = onEventEdit,
            onDelete = onEventDelete
        )
    }

    editingMember?.let { member ->
        AlertDialog('''
text = text.replace(needle, insert)
family.write_text(text)

main = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = main.read_text()
old = '''                        3 -> EditableFamilyScreen(
                            members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                            { name, role ->
                                scope.launch {
                                    val realMemberCount = members.count { it.id != ALL_FAMILY_MEMBER_ID }
                                    SupabaseSync.addMember(session, name, role, MemberColors[realMemberCount % MemberColors.size])
                                    refresh()
                                }
                            },
                            { member, color ->
                                scope.launch {
                                    SupabaseSync.updateMemberColor(session, member.id, color)
                                    refresh()
                                }
                            }
                        )'''
new = '''                        3 -> EditableFamilyScreen(
                            members.filter { it.id != ALL_FAMILY_MEMBER_ID },
                            events,
                            { name, role ->
                                scope.launch {
                                    val realMemberCount = members.count { it.id != ALL_FAMILY_MEMBER_ID }
                                    SupabaseSync.addMember(session, name, role, MemberColors[realMemberCount % MemberColors.size])
                                    refresh()
                                }
                            },
                            { member, color ->
                                scope.launch {
                                    SupabaseSync.updateMemberColor(session, member.id, color)
                                    refresh()
                                }
                            },
                            { event, title, date, time ->
                                scope.launch {
                                    SupabaseSync.updateEvent(session, event.id, title, date, time, event.memberId)
                                    refresh()
                                }
                            },
                            { event ->
                                scope.launch {
                                    deleteCalendarEventsDirect(session, listOf(event.id))
                                    refresh()
                                }
                            }
                        )'''
if old not in text:
    raise SystemExit('EditableFamilyScreen call not found')
main.write_text(text.replace(old, new))
