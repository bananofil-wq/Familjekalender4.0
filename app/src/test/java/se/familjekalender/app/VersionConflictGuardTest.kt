package se.familjekalender.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionConflictGuardTest {
    @Test fun sameValues() {
        assertTrue(revisionFieldMatches("checked", "true", "true"))
        assertFalse(revisionFieldMatches("checked", "true", "false"))
        assertTrue(revisionFieldMatches("member_id", null, null))
        assertFalse(revisionFieldMatches("member_id", null, "example"))
    }

    @Test fun equalTimestampsWithDifferentOffsets() {
        assertTrue(revisionFieldMatches(
            "starts_at", "2026-11-11T09:00:00+01:00", "2026-11-11T08:00:00+00:00"
        ))
        assertTrue(revisionFieldMatches(
            "ends_at", "2026-11-11T12:30:00+01:00", "2026-11-11T11:30:00Z"
        ))
        assertFalse(revisionFieldMatches(
            "starts_at", "2026-11-11T09:00:00+01:00", "2026-11-11T09:00:00Z"
        ))
    }

    @Test fun textualFieldsRemainExact() {
        assertTrue(revisionFieldMatches("title", "Fotboll", "Fotboll"))
        assertFalse(revisionFieldMatches("title", "Fotboll", "Fotbollen"))
        assertFalse(revisionFieldMatches("starts_at", "missing", "missing"))
    }
}
