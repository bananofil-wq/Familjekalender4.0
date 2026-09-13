from pathlib import Path

# Triggered after workflow creation so GitHub applies this patch on the feature branch.
screen = Path('app/src/main/java/se/familjekalender/app/ExactCalendarScreen.kt')
text = screen.read_text(encoding='utf-8')

old = '''            onSave = { title, date, time, memberId ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        runCatching { SupabaseSync.updateEvent(session, event.id, title, date, time, memberId) }
'''
new = '''            onSave = { title, date, time, endTime, memberId ->
                val session = currentFamilySession(context)
                if (session != null) {
                    scope.launch {
                        runCatching { SupabaseSync.updateEvent(session, event.id, title, date, time, endTime, memberId) }
'''
if old not in text:
    raise SystemExit('Edit callback callsite not found')
text = text.replace(old, new, 1)

old = '''    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, String?) -> Unit
) {
'''
new = '''    onDismiss: () -> Unit,
    onSave: (String, LocalDate, String, String?, String?) -> Unit
) {
'''
if old not in text:
    raise SystemExit('Edit dialog signature not found')
text = text.replace(old, new, 1)

old = '''    var date by remember(event.id) { mutableStateOf(event.date) }
    var time by remember(event.id) { mutableStateOf(event.time.ifBlank { "18:00" }) }
    var memberId by remember(event.id) { mutableStateOf(event.memberId ?: ALL_FAMILY_MEMBER_ID) }
'''
new = '''    var date by remember(event.id) { mutableStateOf(event.date) }
    var time by remember(event.id) { mutableStateOf(event.time.ifBlank { "18:00" }) }
    var endTime by remember(event.id) { mutableStateOf(event.endTime ?: "") }
    var memberId by remember(event.id) { mutableStateOf(event.memberId ?: ALL_FAMILY_MEMBER_ID) }
'''
if old not in text:
    raise SystemExit('Edit dialog state not found')
text = text.replace(old, new, 1)

old = '''    fun chooseTime() {
        val parsed = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        TimePickerDialog(context, { _, hour, minute ->
            time = "%02d:%02d".format(hour, minute)
        }, parsed.hour, parsed.minute, true).show()
    }
'''
new = '''    fun chooseTime() {
        val parsed = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        TimePickerDialog(context, { _, hour, minute ->
            time = "%02d:%02d".format(hour, minute)
        }, parsed.hour, parsed.minute, true).show()
    }

    fun chooseEndTime() {
        val fallback = runCatching { LocalTime.parse(time).plusHours(1) }.getOrElse { LocalTime.of(19, 0) }
        val parsed = runCatching { LocalTime.parse(endTime) }.getOrDefault(fallback)
        TimePickerDialog(context, { _, hour, minute ->
            endTime = "%02d:%02d".format(hour, minute)
        }, parsed.hour, parsed.minute, true).show()
    }
'''
if old not in text:
    raise SystemExit('chooseTime block not found')
text = text.replace(old, new, 1)

old = '''                OutlinedButton(onClick = ::chooseTime, modifier = Modifier.fillMaxWidth()) { Text("Tid: $time") }
                Text("Gäller", fontWeight = FontWeight.Bold)
'''
new = '''                OutlinedButton(onClick = ::chooseTime, modifier = Modifier.fillMaxWidth()) { Text("Starttid: $time") }
                OutlinedButton(onClick = ::chooseEndTime, modifier = Modifier.fillMaxWidth()) {
                    Text(if (endTime.isBlank()) "Sluttid: inte angiven" else "Sluttid: $endTime")
                }
                if (endTime.isNotBlank()) {
                    TextButton(onClick = { endTime = "" }) { Text("Ta bort sluttid") }
                }
                Text("Gäller", fontWeight = FontWeight.Bold)
'''
if old not in text:
    raise SystemExit('Edit time UI not found')
text = text.replace(old, new, 1)

old = '''                onClick = { onSave((if (birthday) "🌈 " else "") + title.trim(), date, time, memberId) }
'''
new = '''                onClick = { onSave((if (birthday) "🌈 " else "") + title.trim(), date, time, endTime.ifBlank { null }, memberId) }
'''
if old not in text:
    raise SystemExit('Edit save action not found')
text = text.replace(old, new, 1)

screen.write_text(text, encoding='utf-8')

sync = Path('app/src/main/java/se/familjekalender/app/SupabaseSync.kt')
s = sync.read_text(encoding='utf-8')
old = '''    suspend fun updateEvent(
        session: FamilySession,
        eventId: String,
        title: String,
        date: LocalDate,
        time: String,
        memberId: String?
    ) = withContext(Dispatchers.IO) {
        val parsedTime = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        val startsAt = ZonedDateTime.of(date, parsedTime, STOCKHOLM).toOffsetDateTime().toString()
        val body = JSONObject()
            .put("title", title)
            .put("starts_at", startsAt)
            .put("updated_at", OffsetDateTime.now().toString())
'''
new = '''    suspend fun updateEvent(
        session: FamilySession,
        eventId: String,
        title: String,
        date: LocalDate,
        time: String,
        endTime: String?,
        memberId: String?
    ) = withContext(Dispatchers.IO) {
        val parsedTime = runCatching { LocalTime.parse(time) }.getOrElse { LocalTime.of(18, 0) }
        val startsAt = ZonedDateTime.of(date, parsedTime, STOCKHOLM)
        val parsedEnd = endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val endsAt = parsedEnd?.let { value ->
            ZonedDateTime.of(if (value.isAfter(parsedTime)) date else date.plusDays(1), value, STOCKHOLM)
        }
        val body = JSONObject()
            .put("title", title)
            .put("starts_at", startsAt.toOffsetDateTime().toString())
            .put("updated_at", OffsetDateTime.now().toString())
        if (endsAt == null) body.put("ends_at", JSONObject.NULL) else body.put("ends_at", endsAt.toOffsetDateTime().toString())
'''
if old not in s:
    raise SystemExit('Supabase updateEvent block not found')
s = s.replace(old, new, 1)
sync.write_text(s, encoding='utf-8')

print('Event editing now supports persisted end times')
