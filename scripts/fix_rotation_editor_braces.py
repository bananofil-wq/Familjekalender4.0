from pathlib import Path

path = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
lines = path.read_text().splitlines()

anchor_i = next((i for i, line in enumerate(lines) if 'Redigera vecka ${index + 1}' in line), None)
if anchor_i is None:
    raise SystemExit('Rotation editor anchor not found')

confirm_i = next((i for i in range(anchor_i, len(lines)) if 'confirmButton = { Button(onClick = { editingWeek = null }' in lines[i]), None)
if confirm_i is None:
    raise SystemExit('Rotation editor confirm button not found')

expected_bad = [
    '                    }',
    '                    }',
    '                }',
    '            },',
]
expected_good = [
    '                    }',
    '                }',
    '            },',
]

if lines[confirm_i - 4:confirm_i] == expected_bad:
    del lines[confirm_i - 4]
elif lines[confirm_i - 3:confirm_i] == expected_good:
    pass
else:
    raise SystemExit('Unexpected rotation editor closing structure: ' + repr(lines[confirm_i - 5:confirm_i]))

path.write_text('\n'.join(lines) + '\n')
