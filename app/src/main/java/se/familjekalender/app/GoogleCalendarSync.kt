package se.familjekalender.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val GOOGLE_CAL_PREFS = "google_calendar_sync"
private const val GOOGLE_CAL_ID = "selected_calendar_id"
private const val FAMILY_MARKER = "Familjekalender-ID:"
private val CAL_ZONE: ZoneId = ZoneId.of("Europe/Stockholm")

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int
)

data class GoogleCalendarSyncResult(
    val imported: Int,
    val exported: Int,
    val updated: Int
)

object GoogleCalendarSync {
    fun hasPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(context: Context): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermissions(context)) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )
        val result = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            while (cursor.moveToNext()) {
                val accountType = cursor.getString(typeCol).orEmpty()
                if (accountType.contains("google", ignoreCase = true)) {
                    result += DeviceCalendar(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol).orEmpty().ifBlank { "Google Kalender" },
                        accountName = cursor.getString(accountCol).orEmpty(),
                        accountType = accountType,
                        color = cursor.getInt(colorCol)
                    )
                }
            }
        }
        result
    }

    suspend fun sync(context: Context, session: FamilySession, calendarId: Long): GoogleCalendarSyncResult = withContext(Dispatchers.IO) {
        require(hasPermissions(context)) { "Kalenderbehörighet saknas" }
        var imported = 0
        var exported = 0
        var updated = 0

        val from = LocalDate.now().minusYears(1).atStartOfDay(CAL_ZONE).toInstant().toEpochMilli()
        val until = LocalDate.now().plusYears(2).plusDays(1).atStartOfDay(CAL_ZONE).toInstant().toEpochMilli()
        val importedRows = mutableListOf<Triple<Long, ContentValues, String>>()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DELETED
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<?"
        val args = arrayOf(calendarId.toString(), from.toString(), until.toString())
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val descCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val deletedCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DELETED)
            while (cursor.moveToNext()) {
                if (cursor.getInt(deletedCol) != 0) continue
                val description = cursor.getString(descCol).orEmpty()
                if (description.contains(FAMILY_MARKER)) continue
                val eventId = cursor.getLong(idCol)
                val startMs = cursor.getLong(startCol)
                val endMs = if (cursor.isNull(endCol)) null else cursor.getLong(endCol)
                val allDay = cursor.getInt(allDayCol) == 1
                val start = Instant.ofEpochMilli(startMs).atZone(if (allDay) ZoneId.of("UTC") else CAL_ZONE)
                val end = endMs?.let { Instant.ofEpochMilli(it).atZone(if (allDay) ZoneId.of("UTC") else CAL_ZONE) }
                SupabaseSync.upsertExternalEvent(
                    session = session,
                    source = "google_calendar",
                    externalId = "android:$calendarId:$eventId",
                    title = cursor.getString(titleCol).orEmpty().ifBlank { "Google Kalender" },
                    startsAt = start.withZoneSameInstant(CAL_ZONE),
                    endsAt = end?.withZoneSameInstant(CAL_ZONE),
                    memberId = ALL_FAMILY_MEMBER_ID
                )
                imported++
            }
        }

        val familyEvents = SupabaseSync.loadEvents(session)
            .filter { it.source != "google_calendar" }
            .filter { !it.date.isBefore(LocalDate.now().minusYears(1)) && !it.date.isAfter(LocalDate.now().plusYears(2)) }

        familyEvents.forEach { event ->
            val marker = "$FAMILY_MARKER${event.id}"
            val existingId = findByMarker(context, calendarId, marker)
            val startTime = runCatching { LocalTime.parse(event.time) }.getOrElse { LocalTime.of(9, 0) }
            val start = ZonedDateTime.of(event.date, startTime, CAL_ZONE)
            val end = event.endTime?.let { raw ->
                runCatching { LocalTime.parse(raw) }.getOrNull()?.let { endTime ->
                    val endDate = event.endDate ?: if (endTime.isAfter(startTime)) event.date else event.date.plusDays(1)
                    ZonedDateTime.of(endDate, endTime, CAL_ZONE)
                }
            } ?: start.plusHours(1)

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DTSTART, start.toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, end.toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, CAL_ZONE.id)
                put(CalendarContract.Events.DESCRIPTION, marker)
            }
            if (existingId == null) {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                exported++
            } else {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
                context.contentResolver.update(uri, values, null, null)
                updated++
            }
        }

        GoogleCalendarSyncResult(imported, exported, updated)
    }

    private fun findByMarker(context: Context, calendarId: Long, marker: String): Long? {
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION)
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?",
            arrayOf(calendarId.toString(), "%$marker%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID))
        }
        return null
    }
}

@Composable
fun GoogleCalendarSettingsCard(session: FamilySession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(GOOGLE_CAL_PREFS, Context.MODE_PRIVATE) }
    var permissionGranted by remember { mutableStateOf(GoogleCalendarSync.hasPermissions(context)) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    var selectedId by remember { mutableStateOf(prefs.getLong(GOOGLE_CAL_ID, -1L).takeIf { it >= 0 }) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result[Manifest.permission.READ_CALENDAR] == true && result[Manifest.permission.WRITE_CALENDAR] == true
        if (permissionGranted) {
            scope.launch { calendars = GoogleCalendarSync.calendars(context) }
        } else {
            message = "Kalenderbehörighet krävs för att synka Google Kalender."
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) calendars = GoogleCalendarSync.calendars(context)
    }

    SettingsSectionCardForIntegration(
        title = "Google Kalender",
        subtitle = "Tvåvägssynk av nya och ändrade aktiviteter med en Google-kalender på telefonen."
    ) {
        if (!permissionGranted) {
            Button(
                onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Ge kalenderåtkomst") }
        } else if (calendars.isEmpty()) {
            Text("Ingen Google-kalender hittades på telefonen.", color = Muted, fontSize = 12.sp)
            Text("Kontrollera att Google Kalender är installerad och att kontots kalendrar är synkade i Android.", color = Muted, fontSize = 11.sp)
        } else {
            calendars.forEach { calendar ->
                val selected = selectedId == calendar.id
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f)) else null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp).clickable {
                        selectedId = calendar.id
                        prefs.edit().putLong(GOOGLE_CAL_ID, calendar.id).apply()
                    }
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = {
                            selectedId = calendar.id
                            prefs.edit().putLong(GOOGLE_CAL_ID, calendar.id).apply()
                        })
                        Column(Modifier.weight(1f)) {
                            Text(calendar.name, fontWeight = FontWeight.SemiBold)
                            Text(calendar.accountName, color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val calendarId = selectedId ?: return@Button
                    scope.launch {
                        syncing = true
                        message = "Synkar…"
                        runCatching { GoogleCalendarSync.sync(context, session, calendarId) }
                            .onSuccess { result ->
                                message = "Klart: ${result.imported} från Google, ${result.exported} nya till Google, ${result.updated} uppdaterade."
                            }
                            .onFailure { message = "Google Kalender-fel: ${it.message}" }
                        syncing = false
                    }
                },
                enabled = selectedId != null && !syncing,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (syncing) "Synkar…" else "Synka nu") }
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = if (message.startsWith("Google Kalender-fel")) MaterialTheme.colorScheme.error else Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsSectionCardForIntegration(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = LuxuryText)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = LuxuryTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
