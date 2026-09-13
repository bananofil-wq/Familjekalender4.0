from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = path.read_text()

anchor = '''private data class WorkRotationWeekDraft(
    val weekdays: Set<Int>,
    val startTime: String,
    val endTime: String,
    val dayTimes: Map<Int, Pair<String, String>> = weekdays.associateWith { startTime to endTime }
)
'''
insert = anchor + '''
private enum class SeriesEditScope {
    THIS,
    THIS_AND_FUTURE,
    WHOLE_SERIES
}
'''
if 'private enum class SeriesEditScope' not in text:
    if anchor not in text:
        raise SystemExit('WorkRotationWeekDraft anchor not found')
    text = text.replace(anchor, insert, 1)

old_call = '''    editEvent?.let { event ->
        EditEventDialog(
            event = event,
            members = members,
            onDismiss = { editEvent = null },
            onSave = { title, date, time, endTime, memberId ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        runCatching { SupabaseSync.updateEvent(session, event.id, title, date, time, endTime, memberId) }
                            .onSuccess {
                                editEvent = null
                                onSelect(date)
                                refreshActivity()
                            }
                    }
                }
            }
        )
    }
'''
new_call = '''    editEvent?.let { event ->
        val matchingSeries = events
            .filter { candidate ->
                candidate.source != "sportadmin" &&
                    candidate.source == event.source &&
                    candidate.memberId == event.memberId &&
                    candidate.title == event.title &&
                    candidate.time == event.time &&
                    candidate.endTime == event.endTime
            }
            .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
        EditEventDialog(
            event = event,
            members = members,
            hasSeries = matchingSeries.size > 1,
            onDismiss = { editEvent = null },
            onSave = { title, date, time, endTime, memberId, editScope ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        val dayShift = java.time.temporal.ChronoUnit.DAYS.between(event.date, date)
                        val targets = when (editScope) {
                            SeriesEditScope.THIS -> listOf(event)
                            SeriesEditScope.THIS_AND_FUTURE -> matchingSeries.filter { !it.date.isBefore(event.date) }
                            SeriesEditScope.WHOLE_SERIES -> matchingSeries
                        }
                        runCatching {
                            targets.forEach { target ->
                                val targetDate = if (editScope == SeriesEditScope.THIS) date else target.date.plusDays(dayShift)
                                SupabaseSync.updateEvent(session, target.id, title, targetDate, time, endTime, memberId)
                            }
                        }.onSuccess {
                            editEvent = null
                            onSelect(date)
                            refreshActivity()
                        }
                    }
                }
            }
        )
    }
'''
if old_call not in text:
    raise SystemExit('EditEventDialog call anchor not found')
text = text.replace(old_call, new_call, 1)

old_sig = '''private fun EditEventDialog(
    event: SyncEvent,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, String?, String?) -> Unit
) {'''
new_sig = '''private fun EditEventDialog(
    event: SyncEvent,
    members: List<SyncMember>,
    hasSeries: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, String?, String?, SeriesEditScope) -> Unit
) {'''
if old_sig not in text:
    raise SystemExit('EditEventDialog signature anchor not found')
text = text.replace(old_sig, new_sig, 1)

state_anchor = '''    var endTime by remember(event.id) { mutableStateOf(event.endTime ?: "") }
    var memberId by remember(event.id) { mutableStateOf(event.memberId ?: ALL_FAMILY_MEMBER_ID) }
'''
state_insert = state_anchor + '''    var editScope by remember(event.id) { mutableStateOf(SeriesEditScope.THIS) }
'''
if state_anchor not in text:
    raise SystemExit('Edit state anchor not found')
text = text.replace(state_anchor, state_insert, 1)

member_block = '''                Text("Gäller", fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().clickable { memberId = member.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = memberId == member.id, onClick = { memberId = member.id })
                        Text(if (member.id == ALL_FAMILY_MEMBER_ID) "Hela familjen" else member.name)
                    }
                }
'''
member_new = member_block + '''                if (hasSeries) {
                    HorizontalDivider()
                    Text("Ändra återkommande aktivitet", fontWeight = FontWeight.Bold)
                    listOf(
                        SeriesEditScope.THIS to "Bara denna",
                        SeriesEditScope.THIS_AND_FUTURE to "Denna och framåt",
                        SeriesEditScope.WHOLE_SERIES to "Hela serien"
                    ).forEach { (scopeOption, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editScope = scopeOption },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = editScope == scopeOption,
                                onClick = { editScope = scopeOption }
                            )
                            Text(label)
                        }
                    }
                    Text(
                        "Datumändringar flyttar motsvarande förekomster lika många dagar. Övriga ändringar används på vald del av serien.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                        fontSize = 11.sp
                    )
                }
'''
if member_block not in text:
    raise SystemExit('Member block anchor not found')
text = text.replace(member_block, member_new, 1)

old_save = '''                onClick = { onSave((if (birthday) "🌈 " else "") + title.trim(), date, time, endTime.ifBlank { null }, memberId) }
'''
new_save = '''                onClick = { onSave((if (birthday) "🌈 " else "") + title.trim(), date, time, endTime.ifBlank { null }, memberId, editScope) }
'''
if old_save not in text:
    raise SystemExit('Save button anchor not found')
text = text.replace(old_save, new_save, 1)

path.write_text(text)
print('Series editing patch applied')
