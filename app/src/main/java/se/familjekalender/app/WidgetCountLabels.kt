package se.familjekalender.app

/** The widget and the corresponding app screens count only unchecked items. */
internal fun widgetOpenTodoCount(items: List<SyncTodoItem>): Int =
    items.count { !it.checked }

internal fun widgetOpenShoppingCount(items: List<SyncShoppingItem>): Int =
    items.count { !it.checked }

internal fun widgetTodoLabel(count: Int): String = "✓  $count To-Do    ›"

internal fun widgetShoppingLabel(count: Int): String = "🛒  $count inköp    ›"
