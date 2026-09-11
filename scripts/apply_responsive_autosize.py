from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f"Could not find expected source block: {label}")
    return text.replace(old, new, 1)


main_path = Path("app/src/main/java/se/familjekalender/app/MainActivity.kt")
main = main_path.read_text()
main = replace_once(
    main,
    "setContent { FamilyCalendarApp() }",
    "setContent { ResponsiveApp { FamilyCalendarApp() } }",
    "MainActivity responsive wrapper",
)

old_shopping = '''        FilledIconButton(
            onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } },
            modifier = Modifier.size(56.dp)
        ) { Icon(Icons.Default.Add, null) }'''
new_shopping = '''        FilledIconButton(
            onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Purple,
                contentColor = Color(0xFF2C1643),
                disabledContainerColor = Purple,
                disabledContentColor = Color(0xFF2C1643)
            )
        ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }'''
main = replace_once(main, old_shopping, new_shopping, "shopping add button")
main_path.write_text(main)


todo_path = Path("app/src/main/java/se/familjekalender/app/ToDoScreen.kt")
todo = todo_path.read_text()
if "import androidx.compose.material.icons.Icons" not in todo:
    todo = replace_once(
        todo,
        "import androidx.compose.foundation.layout.*\n",
        "import androidx.compose.foundation.layout.*\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Add\n",
        "ToDo icon imports",
    )

old_todo_button = '''        Button(
            onClick = {
                val title = text.trim()
                if (title.isNotEmpty()) {
                    text = ""
                    scope.launch {
                        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.add(session, title) } }
                            .onFailure { error = it.message ?: "Kunde inte lägga till" }
                        refresh()
                    }
                }
            },
            enabled = text.isNotBlank()
        ) { Text("+") }'''
new_todo_button = '''        FilledIconButton(
            onClick = {
                val title = text.trim()
                if (title.isNotEmpty()) {
                    text = ""
                    scope.launch {
                        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.add(session, title) } }
                            .onFailure { error = it.message ?: "Kunde inte lägga till" }
                        refresh()
                    }
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Purple,
                contentColor = Color(0xFF2C1643),
                disabledContainerColor = Purple,
                disabledContentColor = Color(0xFF2C1643)
            )
        ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }'''
todo = replace_once(todo, old_todo_button, new_todo_button, "ToDo add button")
todo_path.write_text(todo)


preview_path = Path("app/src/debug/java/se/familjekalender/app/DesignPreviewActivity.kt")
preview = preview_path.read_text()
preview = replace_once(
    preview,
    "setContent { PreviewScreen() }",
    "setContent { ResponsiveApp { PreviewScreen() } }",
    "preview responsive wrapper",
)
preview_path.write_text(preview)

print("Responsive autosize and matching add buttons applied")
