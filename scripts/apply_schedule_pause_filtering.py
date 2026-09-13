from pathlib import Path

recurring = Path('app/src/main/java/se/familjekalender/app/RecurringScheduleSync.kt')
text = recurring.read_text()

old_pause = '''data class SchedulePause(
    val id: String,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val scope: String,
    val memberId: String?,
    val titlePrefix: String?
)
'''
new_pause = '''data class SchedulePause(
    val id: String,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val scope: String,
    val seriesId: String?,
    val memberId: String?,
    val titlePrefix: String?
)
'''
if old_pause in text:
    text = text.replace(old_pause, new_pause, 1)
elif 'val seriesId: String?,' not in text:
    raise SystemExit('SchedulePause anchor not found')

old_select = '"/rest/v1/family_schedule_pauses?select=id,starts_on,ends_on,scope,member_id,title_prefix&family_id=eq.${session.id}&order=starts_on.asc",'
new_select = '"/rest/v1/family_schedule_pauses?select=id,starts_on,ends_on,scope,series_id,member_id,title_prefix&family_id=eq.${session.id}&order=starts_on.asc",'
if old_select in text:
    text = text.replace(old_select, new_select, 1)
elif 'select=id,starts_on,ends_on,scope,series_id,member_id,title_prefix' not in text:
    raise SystemExit('Pause select anchor not found')

old_parse = '''                        scope = row.getString("scope"),
                        memberId = if (row.isNull("member_id")) null else row.getString("member_id"),
                        titlePrefix = if (row.isNull("title_prefix")) null else row.getString("title_prefix")
'''
new_parse = '''                        scope = row.getString("scope"),
                        seriesId = if (row.isNull("series_id")) null else row.getString("series_id"),
                        memberId = if (row.isNull("member_id")) null else row.getString("member_id"),
                        titlePrefix = if (row.isNull("title_prefix")) null else row.getString("title_prefix")
'''
if old_parse in text:
    text = text.replace(old_parse, new_parse, 1)
elif 'seriesId = if (row.isNull("series_id"))' not in text:
    raise SystemExit('Pause parse anchor not found')

filter_block = '''
    private fun pauseMatchesEvent(pause: SchedulePause, event: SyncEvent): Boolean {
        if (event.seriesId.isNullOrBlank()) return false
        if (event.date.isBefore(pause.startsOn) || event.date.isAfter(pause.endsOn)) return false
        if (pause.scope == "all_schedules") return true
        if (pause.scope == "series" && !pause.seriesId.isNullOrBlank()) return pause.seriesId == event.seriesId
        val memberMatches = pause.memberId == null || pause.memberId == event.memberId
        val titleMatches = pause.titlePrefix.isNullOrBlank() || event.title.startsWith(pause.titlePrefix, ignoreCase = true)
        return memberMatches && titleMatches
    }

    suspend fun filterPausedScheduleEvents(
        session: FamilySession,
        events: List<SyncEvent>
    ): List<SyncEvent> {
        val pauses = loadPauses(session)
        if (pauses.isEmpty()) return events
        return events.filterNot { event -> pauses.any { pause -> pauseMatchesEvent(pause, event) } }
    }
'''
anchor = '''    suspend fun addGlobalPause(session: FamilySession, startsOn: LocalDate, endsOn: LocalDate) = withContext(Dispatchers.IO) {'''
if 'suspend fun filterPausedScheduleEvents(' not in text:
    if anchor not in text:
        raise SystemExit('addGlobalPause anchor not found')
    text = text.replace(anchor, filter_block + '\n' + anchor, 1)

recurring.write_text(text)

main = Path('app/src/main/java/se/familjekalender/app/MainActivity.kt')
text = main.read_text()
old_refresh = '''            members = SupabaseSync.loadMembers(session)
            shopping = SupabaseSync.loadShopping(session)
            events = SupabaseSync.loadEvents(session)
'''
new_refresh = '''            members = SupabaseSync.loadMembers(session)
            shopping = SupabaseSync.loadShopping(session)
            val loadedEvents = SupabaseSync.loadEvents(session)
            events = RecurringScheduleSync.filterPausedScheduleEvents(session, loadedEvents)
'''
if old_refresh in text:
    text = text.replace(old_refresh, new_refresh, 1)
elif 'RecurringScheduleSync.filterPausedScheduleEvents(session, loadedEvents)' not in text:
    raise SystemExit('MainActivity refresh anchor not found')
main.write_text(text)

print('Reversible pause filtering applied to recurring schedule series')
