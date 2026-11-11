package se.familjekalender.app

import java.time.OffsetDateTime

/**
 * Checks whether a server row already contains exactly the intended field value.
 * Needed when an HTTP response is lost after a successful optimistic update.
 */
internal fun revisionFieldMatches(
    column: String,
    wanted: String?,
    actual: String?,
): Boolean {
    if (wanted == null || actual == null) return wanted == actual
    if (column in setOf("starts_at", "ends_at")) {
        return runCatching {
            OffsetDateTime.parse(wanted).toInstant() ==
                OffsetDateTime.parse(actual).toInstant()
        }.getOrDefault(false)
    }
    return wanted == actual
}
