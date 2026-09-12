from pathlib import Path

main = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
calendar = Path("app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt")

m = main.read_text(encoding="utf-8")
c = calendar.read_text(encoding="utf-8")

# MainActivity: remember whether the add dialog is a normal activity or the laundry shortcut.
old = '    var showAddEvent by remember { mutableStateOf(false) }\n    var assistantAddRequest by remember { mutableIntStateOf(0) }'
new = '    var showAddEvent by remember { mutableStateOf(false) }\n    var addEventInitialTitle by remember { mutableStateOf("") }\n    var assistantAddRequest by remember { mutableIntStateOf(0) }'
assert old in m
m = m.replace(old, new, 1)

old = '''                            onAdd = { showAddEvent = true },\n                            addMenuRequest = assistantAddRequest'''
new = '''                            onAdd = {\n                                addEventInitialTitle = ""\n                                showAddEvent = true\n                            },\n                            onAddLaundry = {\n                                addEventInitialTitle = "🧺 Tvätt"\n                                showAddEvent = true\n                            },\n                            addMenuRequest = assistantAddRequest'''
assert old in m
m = m.replace(old, new, 1)

old = '        AddEventDialog(members, selectedDate, { showAddEvent = false }) { title, startTime, endTime, memberId, dates, birthday, recurrence ->'
new = '        AddEventDialog(members, selectedDate, addEventInitialTitle, { showAddEvent = false }) { title, startTime, endTime, memberId, dates, birthday, recurrence ->'
assert old in m
m = m.replace(old, new, 1)

old = '''private fun AddEventDialog(\n    members: List<SyncMember>,\n    selectedDate: LocalDate,\n    onDismiss: () -> Unit,'''
new = '''private fun AddEventDialog(\n    members: List<SyncMember>,\n    selectedDate: LocalDate,\n    initialTitle: String = "",\n    onDismiss: () -> Unit,'''
assert old in m
m = m.replace(old, new, 1)

old = '    var title by remember { mutableStateOf("") }'
new = '    var title by remember(initialTitle) { mutableStateOf(initialTitle) }'
assert old in m
m = m.replace(old, new, 1)

main.write_text(m, encoding="utf-8")

# ExactCalendarScreen: expose a laundry quick action and render its icon consistently.
old = '''    onAdd: () -> Unit,\n    addMenuRequest: Int = 0'''
new = '''    onAdd: () -> Unit,\n    onAddLaundry: () -> Unit,\n    addMenuRequest: Int = 0'''
assert old in c
c = c.replace(old, new, 1)

marker = '''private data class WorkRotationWeekDraft(\n    val weekdays: Set<Int>,\n    val startTime: String,\n    val endTime: String,\n    val dayTimes: Map<Int, Pair<String, String>> = weekdays.associateWith { startTime to endTime }\n)\n'''
insert = marker + '''\nprivate fun isLaundryEvent(event: SyncEvent): Boolean {\n    val title = event.title.trim()\n    return title.startsWith("🧺") || title.equals("Tvätt", ignoreCase = true)\n}\n\nprivate fun displayEventTitle(event: SyncEvent): String =\n    event.title.removePrefix("🌈").removePrefix("🧺").trim()\n'''
assert marker in c
c = c.replace(marker, insert, 1)

old = '''                    Button(\n                        onClick = {\n                            showAddMenu = false\n                            showSchoolSchedule = true\n                        },\n                        modifier = Modifier.fillMaxWidth()\n                    ) { Text("Förskola / skola") }\n                    Text('''
new = '''                    Button(\n                        onClick = {\n                            showAddMenu = false\n                            showSchoolSchedule = true\n                        },\n                        modifier = Modifier.fillMaxWidth()\n                    ) { Text("Förskola / skola") }\n                    Button(\n                        onClick = {\n                            showAddMenu = false\n                            onAddLaundry()\n                        },\n                        modifier = Modifier.fillMaxWidth()\n                    ) { Text("🧺 Tvätt") }\n                    Text('''
assert old in c
c = c.replace(old, new, 1)

# Day overview popup icon/title.
old = '''                        val birthday = isBirthdayEvent(event)\n                        val dotColor = if (allFamily)'''
new = '''                        val birthday = isBirthdayEvent(event)\n                        val laundry = isLaundryEvent(event)\n                        val dotColor = if (allFamily)'''
assert old in c
c = c.replace(old, new, 1)

old = '''                                when {\n                                    birthday -> BirthdayRainbowIcon(Modifier.size(width = 26.dp, height = 20.dp))\n                                    allFamily -> Text("★",'''
new = '''                                when {\n                                    birthday -> BirthdayRainbowIcon(Modifier.size(width = 26.dp, height = 20.dp))\n                                    laundry -> Text("🧺", fontSize = 20.sp, lineHeight = 22.sp)\n                                    allFamily -> Text("★",'''
assert old in c
c = c.replace(old, new, 1)

old = '                                        event.title.removePrefix("🌈").trim(),'
new = '                                        displayEventTitle(event),'
assert old in c
c = c.replace(old, new, 1)

# Month grid: keep one laundry icon even when the same person has another event.
old = '''                            val firstBirthday = allDayEvents.firstOrNull { isBirthdayEvent(it) }\n                            val firstAllFamily = allDayEvents.firstOrNull { !isBirthdayEvent(it) && it.memberId == ALL_FAMILY_MEMBER_ID }\n                            val uniqueMembers = allDayEvents\n                                .filter { !isBirthdayEvent(it) && it.memberId != ALL_FAMILY_MEMBER_ID }\n                                .distinctBy { it.memberId }\n                            buildList {\n                                if (firstBirthday != null) add(firstBirthday)\n                                if (firstAllFamily != null) add(firstAllFamily)\n                                addAll(uniqueMembers)\n                            }.take(3)'''
new = '''                            val firstBirthday = allDayEvents.firstOrNull { isBirthdayEvent(it) }\n                            val firstLaundry = allDayEvents.firstOrNull { isLaundryEvent(it) }\n                            val firstAllFamily = allDayEvents.firstOrNull { !isBirthdayEvent(it) && !isLaundryEvent(it) && it.memberId == ALL_FAMILY_MEMBER_ID }\n                            val uniqueMembers = allDayEvents\n                                .filter { !isBirthdayEvent(it) && !isLaundryEvent(it) && it.memberId != ALL_FAMILY_MEMBER_ID }\n                                .distinctBy { it.memberId }\n                            buildList {\n                                if (firstBirthday != null) add(firstBirthday)\n                                if (firstLaundry != null) add(firstLaundry)\n                                if (firstAllFamily != null) add(firstAllFamily)\n                                addAll(uniqueMembers)\n                            }.take(3)'''
assert old in c
c = c.replace(old, new, 1)

old = '''                                            when {\n                                                isBirthdayEvent(event) -> BirthdayRainbowIcon(Modifier.size(width = 20.dp, height = 16.dp))\n                                                event.memberId == ALL_FAMILY_MEMBER_ID -> Text('''
new = '''                                            when {\n                                                isBirthdayEvent(event) -> BirthdayRainbowIcon(Modifier.size(width = 20.dp, height = 16.dp))\n                                                isLaundryEvent(event) -> Text("🧺", fontSize = 14.sp, lineHeight = 16.sp)\n                                                event.memberId == ALL_FAMILY_MEMBER_ID -> Text('''
assert old in c
c = c.replace(old, new, 1)

# Day panel icon/title and popup title.
old = '''                    val birthday = isBirthdayEvent(event)\n                    val dotColor = if (allFamily)'''
new = '''                    val birthday = isBirthdayEvent(event)\n                    val laundry = isLaundryEvent(event)\n                    val dotColor = if (allFamily)'''
assert old in c
c = c.replace(old, new, 1)

old = '''                            if (birthday) {\n                                Text("🌈", fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.width(32.dp))\n                            } else if (allFamily) {'''
new = '''                            if (birthday) {\n                                Text("🌈", fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.width(32.dp))\n                            } else if (laundry) {\n                                Text("🧺", fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.width(32.dp))\n                            } else if (allFamily) {'''
assert old in c
c = c.replace(old, new, 1)

old = '                                    event.title.removePrefix("🌈").trim(),'
new = '                                    displayEventTitle(event),'
assert old in c
c = c.replace(old, new, 1)

old = '            title = { Text(event.title.removePrefix("🌈").trim()) },'
new = '            title = { Text(displayEventTitle(event)) },'
assert old in c
c = c.replace(old, new, 1)

calendar.write_text(c, encoding="utf-8")
print("Laundry calendar option applied")
