from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/SchoolScheduleDialog.kt')
text = path.read_text(encoding='utf-8')

old_members = '''    val session = currentFamilySession(context)
    val scheduleTypes = listOf("Förskola", "Skola", "Fritids", "Dagis")
    var type by remember { mutableStateOf("Förskola") }
    var rotationWeeks by remember { mutableIntStateOf(1) }
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
'''
new_members = '''    val session = currentFamilySession(context)
    val eligibleMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
    val scheduleTypes = listOf("Förskola", "Skola", "Fritids", "Dagis")
    var type by remember { mutableStateOf("Förskola") }
    var rotationWeeks by remember { mutableIntStateOf(1) }
    var selectedMemberId by remember {
        mutableStateOf(
            eligibleMembers.firstOrNull { member ->
                member.role.contains("barn", ignoreCase = true) || member.role.contains("child", ignoreCase = true)
            }?.id ?: eligibleMembers.firstOrNull()?.id.orEmpty()
        )
    }
'''
if old_members not in text:
    raise SystemExit('School schedule member initialization block not found')
text = text.replace(old_members, new_members, 1)

old_ui_members = '''                members.forEach { member ->
                    Row(
'''
new_ui_members = '''                eligibleMembers.forEach { member ->
                    Row(
'''
if old_ui_members not in text:
    raise SystemExit('School member list not found')
text = text.replace(old_ui_members, new_ui_members, 1)

old_before_dialog = '''    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
'''
new_before_dialog = '''    val invalidTimeRange = weeks.take(rotationWeeks).any { week ->
        week.weekdays.any { day ->
            val times = week.dayTimes[day] ?: ("07:30" to "16:00")
            val start = runCatching { LocalTime.parse(times.first) }.getOrNull()
            val end = runCatching { LocalTime.parse(times.second) }.getOrNull()
            start == null || end == null || !end.isAfter(start)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
'''
if old_before_dialog not in text:
    raise SystemExit('School dialog insertion point not found')
text = text.replace(old_before_dialog, new_before_dialog, 1)

old_error = '''                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && session != null && selectedMemberId.isNotBlank() && weeks.take(rotationWeeks).any { it.weekdays.isNotEmpty() },
'''
new_error = '''                if (invalidTimeRange) {
                    Text("Sluttiden måste vara senare än starttiden för varje vald dag.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && session != null && selectedMemberId.isNotBlank() && !invalidTimeRange && weeks.take(rotationWeeks).any { it.weekdays.isNotEmpty() },
'''
if old_error not in text:
    raise SystemExit('School schedule validation insertion point not found')
text = text.replace(old_error, new_error, 1)

path.write_text(text, encoding='utf-8')
print('School schedule hardened for family member selection and time validation')
