package se.familjekalender.app

/**
 * Compatibility overload for the restored, previously working Supabase sync layer.
 * Multi-date selection remains in MainActivity. The restored backend path stores
 * the selected start time exactly as before.
 */
suspend fun SupabaseSync.addEvent(
    session: FamilySession,
    title: String,
    date: java.time.LocalDate,
    startTime: String,
    endTime: String?,
    memberId: String?
) {
    addEvent(session, title, date, startTime, memberId)
}
