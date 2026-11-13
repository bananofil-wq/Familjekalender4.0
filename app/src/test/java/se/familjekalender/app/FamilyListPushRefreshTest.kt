package se.familjekalender.app

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyListPushRefreshTest {
    private val family = "f001"

    @Test fun `new shopping and todo pushes refresh widget`() {
        assertTrue(FamilyListPushRefresh.shouldRefresh(
            JSONObject().put("family_id", family).put("dedupe_key", "shopping:abc"), family
        ))
        assertTrue(FamilyListPushRefresh.shouldRefresh(
            JSONObject().put("family_id", family).put("dedupe_key", "todo:def"), family
        ))
    }

    @Test fun `family list change data is recognized`() {
        assertTrue(FamilyListPushRefresh.shouldRefresh(
            JSONObject().put("family_id", family).put("type", "family_list_changed"), family
        ))
    }

    @Test fun `another family's data cannot trigger refresh`() {
        assertFalse(FamilyListPushRefresh.shouldRefresh(
            JSONObject().put("family_id", "some-other-family").put("dedupe_key", "todo:foo"), family
        ))
    }

    @Test fun `unrelated and malformed notifications are ignored`() {
        assertFalse(FamilyListPushRefresh.shouldRefresh(
            JSONObject().put("family_id", family).put("dedupe_key", "calendar:abc"), family
        ))
        assertFalse(FamilyListPushRefresh.shouldRefresh(JSONObject(), family))
        assertFalse(FamilyListPushRefresh.shouldRefresh(null, family))
        assertFalse(FamilyListPushRefresh.shouldRefresh(
            JSONObject().put("family_id", family).put("dedupe_key", "todo:foo"), null
        ))
    }
}
