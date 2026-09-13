from pathlib import Path

supabase = Path('app/src/main/java/se/familjekalender/app/SupabaseSync.kt')
text = supabase.read_text()

old = '''data class SyncEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: String,
    val memberId: String?,
    val source: String,
    val endDate: LocalDate? = null,
    val endTime: String? = null
)'''
new = '''data class SyncEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: String,
    val memberId: String?,
    val source: String,
    val endDate: LocalDate? = null,
    val endTime: String? = null,
    val seriesId: String? = null
)'''
if old in text:
    text = text.replace(old, new, 1)

text = text.replace(
    '/rest/v1/calendar_events?select=id,title,starts_at,ends_at,member_id,source&family_id=eq.${session.id}&order=starts_at.asc',
    '/rest/v1/calendar_events?select=id,title,starts_at,ends_at,member_id,source,series_id&family_id=eq.${session.id}&order=starts_at.asc',
    1
)

old_parse = '''                        source = row.optString("source", "manual"),
                        endDate = endZoned?.toLocalDate(),
                        endTime = endZoned?.let { "%02d:%02d".format(it.hour, it.minute) }
'''
new_parse = '''                        source = row.optString("source", "manual"),
                        endDate = endZoned?.toLocalDate(),
                        endTime = endZoned?.let { "%02d:%02d".format(it.hour, it.minute) },
                        seriesId = if (row.isNull("series_id")) null else row.getString("series_id")
'''
if old_parse in text:
    text = text.replace(old_parse, new_parse, 1)

old_sig = '''    suspend fun addEvent(
        session: FamilySession,
        title: String,
        date: LocalDate,
        startTime: String,
        endTime: String?,
        memberId: String?
    ) = withContext(Dispatchers.IO) {'''
new_sig = '''    suspend fun addEvent(
        session: FamilySession,
        title: String,
        date: LocalDate,
        startTime: String,
        endTime: String?,
        memberId: String?,
        seriesId: String? = null
    ) = withContext(Dispatchers.IO) {'''
if old_sig in text:
    text = text.replace(old_sig, new_sig, 1)

anchor = '''        if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
        request("POST", "/rest/v1/calendar_events", body, session.code, preferRepresentation = false)
    }

    suspend fun updateEvent('''
replacement = '''        if (memberId == null || memberId == ALL_FAMILY_MEMBER_ID) body.put("member_id", JSONObject.NULL) else body.put("member_id", memberId)
        if (seriesId != null) body.put("series_id", seriesId)
        request("POST", "/rest/v1/calendar_events", body, session.code, preferRepresentation = false)
    }

    suspend fun updateEvent('''
if anchor in text:
    text = text.replace(anchor, replacement, 1)

supabase.write_text(text)

main = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = main.read_text()
old_block = '''                    val targetDates = if (recurrence == RecurrenceMode.NONE) {
                        dates.sorted()
                    } else {
                        dates.sorted().flatMap { recurringDates(it, recurrence) }.distinct().sorted()
                    }
                    targetDates.forEach { date ->
                        SupabaseSync.addEvent(session, title, date, startTime, endTime, memberId)
                    }
'''
new_block = '''                    val targetDates = if (recurrence == RecurrenceMode.NONE) {
                        dates.sorted()
                    } else {
                        dates.sorted().flatMap { recurringDates(it, recurrence) }.distinct().sorted()
                    }
                    val seriesId = if (recurrence == RecurrenceMode.NONE) null else java.util.UUID.randomUUID().toString()
                    targetDates.forEach { date ->
                        SupabaseSync.addEvent(session, title, date, startTime, endTime, memberId, seriesId)
                    }
'''
if old_block not in text:
    raise SystemExit('MainActivity recurrence block not found')
text = text.replace(old_block, new_block, 1)
main.write_text(text)

calendar = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = calendar.read_text()
old_match = '''        val matchingSeries = events
            .filter { candidate ->
                candidate.source != "sportadmin" &&
                    candidate.source == event.source &&
                    candidate.memberId == event.memberId &&
                    candidate.title == event.title &&
                    candidate.time == event.time &&
                    candidate.endTime == event.endTime
            }
            .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
'''
new_match = '''        val matchingSeries = events
            .filter { candidate ->
                candidate.source != "sportadmin" &&
                    if (event.seriesId != null) {
                        candidate.seriesId == event.seriesId
                    } else {
                        candidate.seriesId == null &&
                            candidate.source == event.source &&
                            candidate.memberId == event.memberId &&
                            candidate.title == event.title &&
                            candidate.time == event.time &&
                            candidate.endTime == event.endTime
                    }
            }
            .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
'''
if old_match not in text:
    raise SystemExit('ExactCalendar series matching block not found')
text = text.replace(old_match, new_match, 1)
calendar.write_text(text)

print('Series IDs wired into recurring events and series editing')
