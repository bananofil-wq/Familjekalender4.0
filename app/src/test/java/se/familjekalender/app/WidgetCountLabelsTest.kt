package se.familjekalender.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCountLabelsTest {
    @Test fun `a successfully loaded empty todo list resets the badge to zero`() {
        val items: List<SyncTodoItem> = emptyList()
        assertEquals(0, widgetOpenTodoCount(items))
        assertEquals("✓  0 To-Do    ›", widgetTodoLabel(widgetOpenTodoCount(items)))
    }

    @Test fun `completed tasks do not appear in the count`() {
        val items = listOf(
            SyncTodoItem("a", "Klar", true, null),
            SyncTodoItem("b", "Pågående", false, null),
            SyncTodoItem("c", "Också klar", true, null),
        )
        assertEquals(1, widgetOpenTodoCount(items))
    }

    @Test fun `premium shortcut labels keep live counts but not platform emoji`() {
        assertEquals("4 inköp    ›", widgetPremiumShortcutLabel(widgetShoppingLabel(4)))
        assertEquals("1 To-Do    ›", widgetPremiumShortcutLabel(widgetTodoLabel(1)))
        assertEquals("0 inköp    ›", widgetPremiumShortcutLabel(widgetShoppingLabel(0)))
        assertEquals("Redan formaterad", widgetPremiumShortcutLabel("Redan formaterad"))
    }

    @Test fun `shopping count also excludes checked items`() {
        val items = listOf(
            SyncShoppingItem("a", "Mjölk", true),
            SyncShoppingItem("b", "Ägg", false),
        )
        assertEquals(1, widgetOpenShoppingCount(items))
        assertEquals("🛒  1 inköp    ›", widgetShoppingLabel(widgetOpenShoppingCount(items)))
    }
}
