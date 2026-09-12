from pathlib import Path

p = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
s = p.read_text()
old = '''    LaunchedEffect(addMenuRequest) {
        if (addMenuRequest > 0) showAddMenu = true
    }
'''
new = '''    // Only react to a NEW + request. ExactCalendarScreen is recreated when the
    // user leaves and returns to the calendar tab; replaying an old non-zero
    // request made the add dialog reopen every time.
    var lastHandledAddMenuRequest by rememberSaveable { mutableIntStateOf(addMenuRequest) }
    LaunchedEffect(addMenuRequest) {
        if (addMenuRequest > lastHandledAddMenuRequest) {
            lastHandledAddMenuRequest = addMenuRequest
            showAddMenu = true
        }
    }
'''
if old not in s:
    raise SystemExit('addMenuRequest block not found')
s = s.replace(old, new, 1)
p.write_text(s)
