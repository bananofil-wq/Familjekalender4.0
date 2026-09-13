from pathlib import Path

sync = Path('app/src/main/java/se/familjekalender/app/SupabaseSync.kt')
text = sync.read_text()
anchor = '''    suspend fun importSportAdmin(session: FamilySession, webcalUrl: String, memberId: String?): Int = withContext(Dispatchers.IO) {\n'''
insert = '''    suspend fun updateEventSeriesId(\n        session: FamilySession,\n        eventId: String,\n        seriesId: String?\n    ) = withContext(Dispatchers.IO) {\n        val body = JSONObject().put(\"series_id\", seriesId ?: JSONObject.NULL)\n        request(\n            \"PATCH\",\n            \"/rest/v1/calendar_events?id=eq.$eventId&family_id=eq.${session.id}\",\n            body,\n            session.code,\n            preferRepresentation = false\n        )\n    }\n\n'''
if 'suspend fun updateEventSeriesId(' not in text:
    if anchor not in text:
        raise SystemExit('SupabaseSync anchor not found')
    text = text.replace(anchor, insert + anchor, 1)
    sync.write_text(text)

calendar = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = calendar.read_text()
old = '''                        runCatching {\n                            targets.forEach { target ->\n                                val targetDate = if (editScope == SeriesEditScope.THIS) date else target.date.plusDays(dayShift)\n                                SupabaseSync.updateEvent(session, target.id, title, targetDate, time, endTime, memberId)\n                            }\n                        }.onSuccess {\n'''
new = '''                        runCatching {\n                            val splitSeriesId = when {\n                                event.seriesId == null -> null\n                                editScope == SeriesEditScope.THIS_AND_FUTURE -> java.util.UUID.randomUUID().toString()\n                                else -> event.seriesId\n                            }\n                            targets.forEach { target ->\n                                val targetDate = if (editScope == SeriesEditScope.THIS) date else target.date.plusDays(dayShift)\n                                SupabaseSync.updateEvent(session, target.id, title, targetDate, time, endTime, memberId)\n                                when {\n                                    event.seriesId == null -> Unit\n                                    editScope == SeriesEditScope.THIS -> SupabaseSync.updateEventSeriesId(session, target.id, null)\n                                    editScope == SeriesEditScope.THIS_AND_FUTURE -> SupabaseSync.updateEventSeriesId(session, target.id, splitSeriesId)\n                                    editScope == SeriesEditScope.WHOLE_SERIES -> Unit\n                                }\n                            }\n                        }.onSuccess {\n'''
if old not in text:
    if 'splitSeriesId = when' not in text:
        raise SystemExit('Series edit anchor not found')
else:
    text = text.replace(old, new, 1)
    calendar.write_text(text)

print('Series split semantics applied')
