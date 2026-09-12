from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = path.read_text()

state_old = '''    var showWorkMonth by remember { mutableStateOf(false) }
    var showWorkRotation by remember { mutableStateOf(false) }
    var showManageMonth by remember { mutableStateOf(false) }
'''
state_new = '''    var showWorkMonth by remember { mutableStateOf(false) }
    var showWorkRotation by remember { mutableStateOf(false) }
    var showSchoolSchedule by remember { mutableStateOf(false) }
    var showManageMonth by remember { mutableStateOf(false) }
'''
if state_old not in text:
    raise SystemExit('state anchor not found')
text = text.replace(state_old, state_new, 1)

button_old = '''                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkRotation = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lägg till arbetsvecka") }
                    Text(
                        "Arbetsmånad låter dig ange olika tider för olika veckodagar och fyller hela månaden åt dig.",
'''
button_new = '''                    Button(
                        onClick = {
                            showAddMenu = false
                            showWorkRotation = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lägg till arbetsvecka") }
                    Button(
                        onClick = {
                            showAddMenu = false
                            showSchoolSchedule = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Förskola / skola") }
                    Text(
                        "Arbetsmånad och förskola/skola låter dig lägga återkommande tider utan att mata in varje dag för hand.",
'''
if button_old not in text:
    raise SystemExit('button anchor not found')
text = text.replace(button_old, button_new, 1)

call_old = '''    if (showWorkRotation) {
        WorkRotationDialog(
            members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
            selectedDate = selectedDate,
            onDismiss = { showWorkRotation = false },
            onChanged = {
                showWorkRotation = false
                refreshActivity()
            }
        )
    }

    dayPopupDate?.let { popupDate ->
'''
call_new = '''    if (showWorkRotation) {
        WorkRotationDialog(
            members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
            selectedDate = selectedDate,
            onDismiss = { showWorkRotation = false },
            onChanged = {
                showWorkRotation = false
                refreshActivity()
            }
        )
    }

    if (showSchoolSchedule) {
        SchoolScheduleDialog(
            members = members.filter { it.id != ALL_FAMILY_MEMBER_ID },
            selectedDate = selectedDate,
            onDismiss = { showSchoolSchedule = false },
            onChanged = {
                showSchoolSchedule = false
                refreshActivity()
            }
        )
    }

    dayPopupDate?.let { popupDate ->
'''
if call_old not in text:
    raise SystemExit('dialog anchor not found')
text = text.replace(call_old, call_new, 1)

path.write_text(text)
print('School schedule UI wired into ExactCalendarScreen.kt')
