package se.familjekalender.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetThemeSwitchTest {
    @Test
    fun `switching between existing themes changes list adapter identity`() {
        val original = widgetListIdentity(42, WidgetThemeColors.ORIGINAL, 3)
        val sunset = widgetListIdentity(42, WidgetThemeColors.SUNSET, 4)
        assertNotEquals(original, sunset)
        assertEquals(
            "familjekalender://widget/list/42?theme=PREMIUM_SUNSET&revision=4",
            sunset,
        )
    }

    @Test
    fun `reselecting same theme also forces a fresh adapter`() {
        assertNotEquals(
            widgetListIdentity(42, WidgetThemeColors.ORIGINAL, 3),
            widgetListIdentity(42, WidgetThemeColors.ORIGINAL, 4),
        )
    }

    @Test
    fun `separate widgets cannot share a stale adapter`() {
        assertNotEquals(
            widgetListIdentity(42, WidgetThemeColors.SUNSET, 8),
            widgetListIdentity(43, WidgetThemeColors.SUNSET, 8),
        )
    }

    @Test
    fun `original and sunset still use their existing distinct styling`() {
        assertNull(WidgetThemeColors.premiumLook(WidgetThemeColors.ORIGINAL))
        assertNotNull(WidgetThemeColors.premiumLook(WidgetThemeColors.SUNSET))
    }
}
