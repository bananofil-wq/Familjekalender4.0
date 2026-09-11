from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / 'app/src/main/java/se/familjekalender/app/MainActivity.kt'
todo = root / 'app/src/main/java/se/familjekalender/app/ToDoScreen.kt'

m = main.read_text()
t = todo.read_text()

# Let the app MaterialTheme follow the currently resolved seasonal palette.
m = m.replace(
'''            primary = Purple,\n            secondary = Purple,\n            surfaceVariant = SoftPurple,\n            outline = Purple.copy(alpha = .55f),''',
'''            primary = palette.accent,\n            secondary = palette.accent,\n            surfaceVariant = palette.soft,\n            outline = palette.accent.copy(alpha = .55f),'''
)

# Shopping add button follows active theme.
m = m.replace(
'''                containerColor = Purple,\n                contentColor = Color(0xFF2C1643),\n                disabledContainerColor = Purple,\n                disabledContentColor = Color(0xFF2C1643)''',
'''                containerColor = MaterialTheme.colorScheme.primary,\n                contentColor = Color.Black.copy(alpha = .78f),\n                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .45f),\n                disabledContentColor = Color.Black.copy(alpha = .55f)'''
)

# To-Do add button follows active theme in exactly the same way.
t = t.replace(
'''                containerColor = Purple,\n                contentColor = Color(0xFF2C1643),\n                disabledContainerColor = Purple,\n                disabledContentColor = Color(0xFF2C1643)''',
'''                containerColor = MaterialTheme.colorScheme.primary,\n                contentColor = Color.Black.copy(alpha = .78f),\n                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .45f),\n                disabledContentColor = Color.Black.copy(alpha = .55f)'''
)

main.write_text(m)
todo.write_text(t)
