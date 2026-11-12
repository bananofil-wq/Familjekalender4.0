package se.familjekalender.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshStatusTest {
    @Test fun `normal calendar state stays readable`() {
        assertEquals(
            "2 aktiviteter idag",
            widgetRefreshStatus(2, true, true, true, true, true),
        )
    }

    @Test fun `event failure keeps explicit cached status`() {
        assertEquals(
            "Aktiviteter: visar sparade",
            widgetRefreshStatus(null, false, true, true, true, true),
        )
    }

    @Test fun `event failure without cache is not falsely reported as empty day`() {
        assertEquals(
            "Aktiviteter kunde inte hämtas",
            widgetRefreshStatus(null, false, true, true, true, false),
        )
    }

    @Test fun `shopping failure does not hide today's activities`() {
        assertEquals(
            "1 aktivitet idag · inköp ej uppdaterat",
            widgetRefreshStatus(1, true, true, true, false, true),
        )
    }

    @Test fun `all failed categories are named`() {
        assertEquals(
            "Aktiviteter: visar sparade · personer, To-Do, inköp ej uppdaterat",
            widgetRefreshStatus(null, false, false, false, false, true),
        )
    }
}
