from pathlib import Path

root = Path(__file__).resolve().parents[1]
cal = root / "app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt"
todo = root / "app/src/main/java/se/familjekalender/app/ToDoScreen.kt"
main = root / "app/src/main/java/se/familjekalender/app/MainActivity.kt"

# --- Calendar: smooth month transition + move activity count left ---
s = cal.read_text()

s = s.replace(
    "import androidx.compose.animation.core.Animatable\nimport androidx.compose.animation.core.tween\n",
    "import androidx.compose.animation.AnimatedContent\nimport androidx.compose.animation.core.tween\nimport androidx.compose.animation.slideInHorizontally\nimport androidx.compose.animation.slideOutHorizontally\nimport androidx.compose.animation.togetherWith\n"
)

s = s.replace(
    "    val mode = palette.mode\n    val monthDrag = remember { Animatable(0f) }\n    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)\n",
    "    val mode = palette.mode\n    var slideDirection by remember { mutableIntStateOf(1) }\n    val date = if (YearMonth.from(selectedDate) == month) selectedDate else month.atDay(1)\n"
)

s = s.replace(
    "        fun moveMonth(delta: Long) {\n            val next = month.plusMonths(delta)\n            month = next\n            onSelect(next.atDay(1))\n        }\n",
    "        fun moveMonth(delta: Long) {\n            slideDirection = if (delta > 0) 1 else -1\n            val next = month.plusMonths(delta)\n            month = next\n            onSelect(next.atDay(1))\n        }\n"
)

old_panel = '''            MonthPanel(\n                month = month,\n                selected = date,\n                onSelect = onSelect,\n                events = events,\n                members = members,\n                accent = palette.accent,\n                onPreviousMonth = { moveMonth(-1) },\n                onNextMonth = { moveMonth(1) },\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .weight(1.72f)\n          .graphicsLayer { translationX = monthDrag.value }\n          .pointerInput(month) {\n              detectHorizontalDragGestures(\n                  onDragStart = { scope.launch { monthDrag.stop() } },\n                  onHorizontalDrag = { change, amount ->\n                      change.consume()\n                      scope.launch { monthDrag.snapTo(monthDrag.value + amount) }\n                  },\n                  onDragEnd = {\n                      scope.launch {\n                          val width = size.width.toFloat().coerceAtLeast(1f)\n                          if (abs(monthDrag.value) >= width * 0.18f) {\n                              val direction = if (monthDrag.value < 0f) -1f else 1f\n                              monthDrag.animateTo(direction * width, tween(130))\n                              moveMonth(if (direction < 0f) 1 else -1)\n                              monthDrag.snapTo(-direction * width)\n                              monthDrag.animateTo(0f, tween(190))\n                          } else {\n                              monthDrag.animateTo(0f, tween(160))\n                          }\n                      }\n                  },\n                  onDragCancel = { scope.launch { monthDrag.animateTo(0f, tween(160)) } }\n              )\n          }\n            )\n'''

new_panel = '''            AnimatedContent(\n                targetState = month,\n                transitionSpec = {\n                    if (slideDirection > 0) {\n                        slideInHorizontally(animationSpec = tween(220)) { it } togetherWith\n                            slideOutHorizontally(animationSpec = tween(220)) { -it }\n                    } else {\n                        slideInHorizontally(animationSpec = tween(220)) { -it } togetherWith\n                            slideOutHorizontally(animationSpec = tween(220)) { it }\n                    }\n                },\n                label = "month-slide",\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .weight(1.72f)\n                    .pointerInput(month) {\n                        var dragTotal = 0f\n                        detectHorizontalDragGestures(\n                            onDragStart = { dragTotal = 0f },\n                            onHorizontalDrag = { change, amount ->\n                                change.consume()\n                                dragTotal += amount\n                            },\n                            onDragEnd = {\n                                val threshold = size.width.toFloat().coerceAtLeast(1f) * 0.14f\n                                if (abs(dragTotal) >= threshold) {\n                                    moveMonth(if (dragTotal < 0f) 1 else -1)\n                                }\n                                dragTotal = 0f\n                            },\n                            onDragCancel = { dragTotal = 0f }\n                        )\n                    }\n            ) { shownMonth ->\n                MonthPanel(\n                    month = shownMonth,\n                    selected = if (YearMonth.from(selectedDate) == shownMonth) selectedDate else shownMonth.atDay(1),\n                    onSelect = onSelect,\n                    events = events,\n                    members = members,\n                    accent = palette.accent,\n                    onPreviousMonth = { moveMonth(-1) },\n                    onNextMonth = { moveMonth(1) },\n                    modifier = Modifier.fillMaxSize()\n                )\n            }\n'''

if old_panel not in s:
    raise SystemExit("Calendar swipe block not found")
s = s.replace(old_panel, new_panel)

old_count = '''                Text(\n                    "${events.size} ${if (events.size == 1) "aktivitet" else "aktiviteter"}",\n                    color = Color.White.copy(alpha = .68f),\n                    fontSize = 11.sp\n                )\n'''
new_count = '''                Text(\n                    "${events.size} ${if (events.size == 1) "aktivitet" else "aktiviteter"}",\n                    color = Color.White.copy(alpha = .68f),\n                    fontSize = 11.sp,\n                    modifier = Modifier.padding(end = 12.dp)\n                )\n'''
if old_count not in s:
    raise SystemExit("Activity count block not found")
s = s.replace(old_count, new_count)
cal.write_text(s)

# --- To-Do: make input exactly the same height as + button ---
s = todo.read_text()
old = '            modifier = Modifier.weight(1f)\n        )\n        Spacer(Modifier.width(8.dp))\n        FilledIconButton('
new = '            modifier = Modifier.weight(1f).height(56.dp)\n        )\n        Spacer(Modifier.width(8.dp))\n        FilledIconButton('
if old not in s:
    raise SystemExit("ToDo input block not found")
s = s.replace(old, new, 1)
todo.write_text(s)

# --- Shopping: same 56dp input/button alignment ---
s = main.read_text()
old = '        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, modifier = Modifier.weight(1f))\n        FilledIconButton('
new = '        OutlinedTextField(text, { text = it }, label = { Text("Lägg till vara") }, modifier = Modifier.weight(1f).height(56.dp))\n        FilledIconButton('
if old not in s:
    raise SystemExit("Shopping input block not found")
s = s.replace(old, new, 1)
main.write_text(s)

print("UI polish applied")
