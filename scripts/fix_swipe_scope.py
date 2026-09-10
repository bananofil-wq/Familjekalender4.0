from pathlib import Path

p = Path("app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt")
s = p.read_text(encoding="utf-8")

s = s.replace("onDragStart = { launch { monthDrag.stop() } }", "onDragStart = { scope.launch { monthDrag.stop() } }")
s = s.replace("launch { monthDrag.snapTo(monthDrag.value + amount) }", "scope.launch { monthDrag.snapTo(monthDrag.value + amount) }")
s = s.replace("                            onDragEnd = {\n                                launch {", "                            onDragEnd = {\n                                scope.launch {")
s = s.replace("onDragCancel = { launch { monthDrag.animateTo(0f, tween(160)) } }", "onDragCancel = { scope.launch { monthDrag.animateTo(0f, tween(160)) } }")

p.write_text(s, encoding="utf-8")

final = p.read_text(encoding="utf-8")
assert "onDragStart = { scope.launch { monthDrag.stop() } }" in final
assert "scope.launch { monthDrag.snapTo(monthDrag.value + amount) }" in final
assert "onDragCancel = { scope.launch { monthDrag.animateTo(0f, tween(160)) } }" in final
