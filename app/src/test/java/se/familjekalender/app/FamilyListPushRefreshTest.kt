package se.familjekalender.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyListPushRefreshTest {
    private val family = "f001"

    @Test fun `new shopping and todo pushes refresh widget`() {
        assertTrue(FamilyListPushRefresh.shouldRefresh(family, family, "shopping:abc", null))
        assertTrue(FamilyListPushRefresh.shouldRefresh(family, family, "todo:def", null))
    }

    @Test fun `recipient private reminder push refreshes widget`() {
        assertTrue(FamilyListPushRefresh.shouldRefresh(
            family, family, "private-reminder:abc", null,
        ))
        assertFalse(FamilyListPushRefresh.shouldRefresh(
            family, "other", "private-reminder:abc", null,
        ))
    }

    @Test fun `family list change data is recognized`() {
        assertTrue(FamilyListPushRefresh.shouldRefresh(
            family, family, null, "family_list_changed",
        ))
    }

    @Test fun `another family's data cannot trigger refresh`() {
        assertFalse(FamilyListPushRefresh.shouldRefresh(family, "other", "todo:abc", null))
    }

    @Test fun `unrelated and malformed notifications are ignored`() {
        assertFalse(FamilyListPushRefresh.shouldRefresh(
            family, family, "calendar:abc", "calendar_event_created",
        ))
        assertFalse(FamilyListPushRefresh.shouldRefresh(family, null, null, null))
        assertFalse(FamilyListPushRefresh.shouldRefresh(null, family, "shopping:abc", null))
        assertFalse(FamilyListPushRefresh.shouldRefresh(family, family, null, null))
    }
}
