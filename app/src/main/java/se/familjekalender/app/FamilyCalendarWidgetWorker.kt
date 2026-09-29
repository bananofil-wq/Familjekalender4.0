package se.familjekalender.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val WIDGET_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val WIDGET_SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

class FamilyCalendarWidgetWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    private data class WidgetRow(val who: String, val activity: String, val time: String)

    private data class DisplayRow(
        val row: WidgetRow,
        val groupKey: String? = null,
        val canToggle: Boolean = false,
    )

    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, FamilyCalendarWidget::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isEmpty()) return Result.success()
        return runCatching {
            val prefs = applicationContext.getSharedPreferences("family_calendar", 0)
            val id = prefs.getString("family_id", null)
            val name = prefs.getString("family_name", null)
            val code = prefs.getString("family_code", null)
            widgetIds.forEach { widgetId ->
                val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                if (id.isNullOrBlank() || code.isNullOrBlank()) {
                    views.setTextViewText(
                        R.id.widget_status,
                        "Öppna appen och anslut till familjen",
                    )
                    views.setTextViewText(R.id.widget_todo, "✓  To-Do    ›")
                    views.setTextViewText(R.id.widget_shopping, "🛒  Inköp    ›")
                    views.setTextViewText(R.id.widget_updated, "")
                    manager.updateAppWidget(widgetId, views)
                    return@forEach
                }
                val session = FamilySession(id, name ?: "Min familj", code)
                val today = LocalDate.now()
                val now = LocalTime.now()
                val allEvents = SupabaseSync.loadEvents(session)
                val todaysEvents =
                    allEvents
                        .filter { it.date == today && isStillRelevantToday(it, now) }
                        .sortedWith(
                            compareBy<SyncEvent> { parseMinute(it.time) ?: Int.MAX_VALUE }
                                .thenBy { it.title }
                        )
                val tomorrowsEvents =
                    allEvents
                        .filter { it.date == today.plusDays(1) }
                        .sortedWith(
                            compareBy<SyncEvent> { parseMinute(it.time) ?: Int.MAX_VALUE }
                                .thenBy { it.title }
                        )
                val members = runCatching {
                    SupabaseSync.loadMembers(session)
                }.getOrDefault(emptyList()).associateBy { it.id }
                val openShopping = runCatching {
                    SupabaseSync.loadShopping(session).count { !it.checked }
                }.getOrDefault(0)
                val openTodos = runCatching { loadOpenTodoCount(session) }.getOrDefault(0)
                val widgetPrefs =
                    applicationContext.getSharedPreferences(
                        FamilyCalendarWidget.WIDGET_PREFS,
                        Context.MODE_PRIVATE,
                    )
                val expandedGroup =
                    widgetPrefs.getString(
                        FamilyCalendarWidget.expandedGroupPrefKey(widgetId),
                        null,
                    )
                val todayRows = buildDayRows(todaysEvents, today, members, expandedGroup)
                val tomorrowRows =
                    buildDayRows(tomorrowsEvents, today.plusDays(1), members, expandedGroup)
                val totalVisibleSlots = 8
                val expandedToday = expandedGroup?.startsWith("$today|") == true
                val expandedTomorrow = expandedGroup?.startsWith("${today.plusDays(1)}|") == true
                var todaySlots =
                    when {
                        expandedToday -> minOf(todayRows.size, 6)
                        expandedTomorrow -> minOf(todayRows.size, 2)
                        else -> minOf(todayRows.size, 4)
                    }
                var tomorrowSlots =
                    when {
                        expandedTomorrow -> minOf(tomorrowRows.size, 6)
                        expandedToday -> minOf(tomorrowRows.size, 2)
                        else -> minOf(tomorrowRows.size, 4)
                    }
                var remainingSlots = totalVisibleSlots - todaySlots - tomorrowSlots
                if (remainingSlots > 0) {
                    val extraToday = minOf(remainingSlots, todayRows.size - todaySlots)
                    todaySlots += extraToday
                    remainingSlots -= extraToday
                }
                if (remainingSlots > 0) {
                    tomorrowSlots += minOf(remainingSlots, tomorrowRows.size - tomorrowSlots)
                }
                val visibleToday = todayRows.take(todaySlots)
                val visibleTomorrow = tomorrowRows.take(tomorrowSlots)
                val todayCount = todaysEvents.size
                views.setTextViewText(
                    R.id.widget_status,
                    when (todayCount) {
                        0 -> "Inga aktiviteter kvar idag"
                        1 -> "✓ 1 aktivitet kvar idag"
                        else -> "✓ $todayCount aktiviteter kvar idag"
                    },
                )
                views.setTextViewText(R.id.widget_todo, "✓  $openTodos To-Do    ›")
                views.setTextViewText(R.id.widget_shopping, "🛒  $openShopping inköp    ›")
                views.setTextViewText(R.id.widget_updated, "")
                val todayContainers =
                    intArrayOf(
                        R.id.widget_event_1,
                        R.id.widget_event_2,
                        R.id.widget_event_3,
                        R.id.widget_event_4,
                        R.id.widget_event_5,
                        R.id.widget_event_6,
                        R.id.widget_event_7,
                        R.id.widget_event_8,
                    )
                val todayWho =
                    intArrayOf(
                        R.id.widget_event_1_who,
                        R.id.widget_event_2_who,
                        R.id.widget_event_3_who,
                        R.id.widget_event_4_who,
                        R.id.widget_event_5_who,
                        R.id.widget_event_6_who,
                        R.id.widget_event_7_who,
                        R.id.widget_event_8_who,
                    )
                val todayActivity =
                    intArrayOf(
                        R.id.widget_event_1_activity,
                        R.id.widget_event_2_activity,
                        R.id.widget_event_3_activity,
                        R.id.widget_event_4_activity,
                        R.id.widget_event_5_activity,
                        R.id.widget_event_6_activity,
                        R.id.widget_event_7_activity,
                        R.id.widget_event_8_activity,
                    )
                val todayTime =
                    intArrayOf(
                        R.id.widget_event_1_time,
                        R.id.widget_event_2_time,
                        R.id.widget_event_3_time,
                        R.id.widget_event_4_time,
                        R.id.widget_event_5_time,
                        R.id.widget_event_6_time,
                        R.id.widget_event_7_time,
                        R.id.widget_event_8_time,
                    )
                visibleToday.forEachIndexed { index, displayRow ->
                    bindRow(
                        views,
                        todayContainers[index],
                        todayWho[index],
                        todayActivity[index],
                        todayTime[index],
                        displayRow,
                        widgetId,
                        60_000 + index * 10,
                    )
                }
                if (visibleToday.isEmpty() && visibleTomorrow.isEmpty())
                    bindRow(
                        views,
                        R.id.widget_event_1,
                        R.id.widget_event_1_who,
                        R.id.widget_event_1_activity,
                        R.id.widget_event_1_time,
                        DisplayRow(WidgetRow("Idag", "Lugnt i kalendern", "")),
                        widgetId,
                        60_000,
                    )
                if (visibleTomorrow.isNotEmpty()) {
                    views.setViewVisibility(R.id.widget_tomorrow_header, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_tomorrow_header,
                        if (tomorrowsEvents.size > visibleTomorrow.size) {
                            "Imorgon • visar ${visibleTomorrow.size} av ${tomorrowsEvents.size}"
                        } else {
                            when (tomorrowsEvents.size) {
                                1 -> "Imorgon • 1 aktivitet"
                                else -> "Imorgon • ${tomorrowsEvents.size} aktiviteter"
                            }
                        },
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_tomorrow_header,
                        FamilyCalendarWidget.openCalendarPendingIntent(
                            applicationContext,
                            widgetId,
                            70_900,
                        ),
                    )
                    val tomorrowContainers =
                        intArrayOf(
                            R.id.widget_tomorrow_1,
                            R.id.widget_tomorrow_2,
                            R.id.widget_tomorrow_3,
                            R.id.widget_tomorrow_4,
                            R.id.widget_tomorrow_5,
                            R.id.widget_tomorrow_6,
                            R.id.widget_tomorrow_7,
                            R.id.widget_tomorrow_8,
                        )
                    val tomorrowWho =
                        intArrayOf(
                            R.id.widget_tomorrow_1_who,
                            R.id.widget_tomorrow_2_who,
                            R.id.widget_tomorrow_3_who,
                            R.id.widget_tomorrow_4_who,
                            R.id.widget_tomorrow_5_who,
                            R.id.widget_tomorrow_6_who,
                            R.id.widget_tomorrow_7_who,
                            R.id.widget_tomorrow_8_who,
                        )
                    val tomorrowActivity =
                        intArrayOf(
                            R.id.widget_tomorrow_1_activity,
                            R.id.widget_tomorrow_2_activity,
                            R.id.widget_tomorrow_3_activity,
                            R.id.widget_tomorrow_4_activity,
                            R.id.widget_tomorrow_5_activity,
                            R.id.widget_tomorrow_6_activity,
                            R.id.widget_tomorrow_7_activity,
                            R.id.widget_tomorrow_8_activity,
                        )
                    val tomorrowTime =
                        intArrayOf(
                            R.id.widget_tomorrow_1_time,
                            R.id.widget_tomorrow_2_time,
                            R.id.widget_tomorrow_3_time,
                            R.id.widget_tomorrow_4_time,
                            R.id.widget_tomorrow_5_time,
                            R.id.widget_tomorrow_6_time,
                            R.id.widget_tomorrow_7_time,
                            R.id.widget_tomorrow_8_time,
                        )
                    visibleTomorrow.forEachIndexed { index, displayRow ->
                        bindRow(
                            views,
                            tomorrowContainers[index],
                            tomorrowWho[index],
                            tomorrowActivity[index],
                            tomorrowTime[index],
                            displayRow,
                            widgetId,
                            70_000 + index * 10,
                        )
                    }
                }
                manager.updateAppWidget(widgetId, views)
            }
            Result.success()
        }
            .getOrElse {
                widgetIds.forEach { widgetId ->
                    val views = FamilyCalendarWidget.baseViews(applicationContext, widgetId)
                    views.setTextViewText(R.id.widget_status, "Kunde inte uppdatera just nu")
                    views.setTextViewText(R.id.widget_todo, "✓  To-Do    ›")
                    views.setTextViewText(R.id.widget_shopping, "🛒  Inköp    ›")
                    views.setTextViewText(R.id.widget_updated, "")
                    manager.updateAppWidget(widgetId, views)
                }
                Result.retry()
            }
    }

    private fun bindRow(
        views: RemoteViews,
        containerId: Int,
        whoId: Int,
        activityId: Int,
        timeId: Int,
        displayRow: DisplayRow,
        appWidgetId: Int,
        requestOffset: Int,
    ) {
        val row = displayRow.row
        views.setViewVisibility(containerId, View.VISIBLE)
        views.setTextViewText(whoId, row.who)
        views.setTextViewText(activityId, row.activity)
        views.setTextViewText(timeId, row.time)

        val openCalendar =
            FamilyCalendarWidget.openCalendarPendingIntent(
                applicationContext,
                appWidgetId,
                requestOffset,
            )

        if (displayRow.canToggle && !displayRow.groupKey.isNullOrBlank()) {
            val groupKey = displayRow.groupKey
            val hashOffset = groupKey.hashCode().and(0x3fff)
            val toggle =
                FamilyCalendarWidget.toggleGroupPendingIntent(
                    applicationContext,
                    appWidgetId,
                    groupKey,
                    50_000 + hashOffset,
                )
            views.setOnClickPendingIntent(containerId, toggle)
            views.setOnClickPendingIntent(whoId, toggle)
            views.setOnClickPendingIntent(activityId, toggle)
            views.setOnClickPendingIntent(timeId, toggle)
        } else {
            views.setOnClickPendingIntent(containerId, openCalendar)
            views.setOnClickPendingIntent(whoId, openCalendar)
            views.setOnClickPendingIntent(activityId, openCalendar)
            views.setOnClickPendingIntent(timeId, openCalendar)
        }
    }
    private fun buildDayRows(
        events: List<SyncEvent>,
        date: LocalDate,
        members: Map<String, SyncMember>,
        expandedGroup: String?,
    ): List<DisplayRow> {
        if (events.isEmpty()) return emptyList()

        val groups =
            events
                .groupBy { it.memberId ?: ALL_FAMILY_MEMBER_ID }
                .entries
                .sortedWith(
                    compareBy<Map.Entry<String, List<SyncEvent>>> { entry ->
                        entry.value.minOfOrNull { parseMinute(it.time) ?: Int.MAX_VALUE }
                            ?: Int.MAX_VALUE
                    }.thenBy { entry -> memberName(entry.key, members) },
                )

        return buildList {
            groups.forEach { entry ->
                val memberKey = entry.key
                val groupEvents = entry.value.sortedWith(eventComparator())
                val who = memberName(memberKey, members)
                val groupKey = "$date|$memberKey"

                if (groupEvents.size == 1) {
                    add(DisplayRow(toWidgetRow(groupEvents.first(), members)))
                } else {
                    val expanded = expandedGroup == groupKey
                    val firstTime =
                        groupEvents.mapNotNull { timeTextOrNull(it) }.firstOrNull().orEmpty()
                    add(
                        DisplayRow(
                            row =
                                WidgetRow(
                                    who = who,
                                    activity = "${groupEvents.size} aktiviteter",
                                    time = if (expanded) "" else firstTime,
                                ),
                            groupKey = groupKey,
                            canToggle = true,
                        ),
                    )
                    if (expanded) {
                        groupEvents.forEach { event ->
                            val row = toWidgetRow(event, members)
                            add(
                                DisplayRow(
                                    WidgetRow(
                                        who = "",
                                        activity = row.activity,
                                        time = row.time,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isStillRelevantToday(event: SyncEvent, now: LocalTime): Boolean {
        if (event.time.isBlank()) return true
        val end = parseLocalTime(event.endTime)
        if (end != null) return !now.isAfter(end)
        val start = parseLocalTime(event.time) ?: return true
        return !now.isAfter(start.plusMinutes(15))
    }

    private fun eventComparator(): Comparator<SyncEvent> =
        compareBy<SyncEvent> { parseMinute(it.time) ?: Int.MAX_VALUE }
            .thenBy { it.title }

    private fun memberName(memberKey: String, members: Map<String, SyncMember>): String =
        if (memberKey == ALL_FAMILY_MEMBER_ID) {
            "Alla"
        } else {
            members[memberKey]?.name?.takeIf { it.isNotBlank() } ?: "Familj"
        }

    private fun timeTextOrNull(event: SyncEvent): String? {
        if (event.time.isBlank()) return null
        return event.endTime
            ?.takeIf { it.isNotBlank() }
            ?.let { "${event.time}–$it" }
            ?: event.time
    }
    private fun toWidgetRow(event: SyncEvent, members: Map<String, SyncMember>): WidgetRow {
        val who =
            when (event.memberId) {
                null,
                ALL_FAMILY_MEMBER_ID -> "Alla"

                else -> members[event.memberId]?.name?.takeIf { it.isNotBlank() } ?: "Familj"
            }
        val timeText = timeTextOrNull(event) ?: "Ingen tid"
        val activity = cleanActivityTitle(event.title, event.time, event.endTime, who)
        return WidgetRow(
            who,
            widgetActivityIcon(activity) + "  " + activity,
            timeText,
        )
    }

    private fun cleanActivityTitle(
        title: String,
        startTime: String,
        endTime: String?,
        who: String,
    ): String {
        var cleaned = title.removePrefix("🌈").trim()
        if (cleaned.startsWith(who, ignoreCase = true)) {
            cleaned = cleaned.drop(who.length).trimStart()
            cleaned = cleaned.trimStart('•', '·', '|', ':', '-', '–').trimStart()
        }
        val ranges = buildList {
            endTime
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    add("$startTime–$it")
                    add("$startTime-$it")
                    add("$startTime – $it")
                    add("$startTime - $it")
                }
        }
        ranges.forEach { cleaned = cleaned.replace(it, " ", ignoreCase = true) }
        if (startTime.isNotBlank()) {
            cleaned = cleaned.replace(startTime, " ", ignoreCase = true)
        }
        cleaned =
            cleaned
                .replace("  ", " ")
                .replace("  ", " ")
                .trim()
                .trim('•', '·', '-', '–', '|', ':')
                .trim()
        return cleaned.ifBlank { "Aktivitet" }
    }

    private fun widgetActivityIcon(title: String): String {
        val value = title.lowercase()
        return when {
            "tvätt" in value -> "🧺"
            "jobb" in value || "arbete" in value -> "💼"
            "skola" in value || "förskola" in value -> "🎓"
            "träning" in value || "innebandy" in value || "handboll" in value -> "🏃"
            "födelsedag" in value || "kalas" in value -> "🎂"
            else -> "•"
        }
    }

    private fun parseLocalTime(value: String?): LocalTime? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun parseMinute(value: String?): Int? =
        parseLocalTime(value)?.let { it.hour * 60 + it.minute }

    private suspend fun loadOpenTodoCount(session: FamilySession): Int =
        withContext(Dispatchers.IO) {
            val path = "/rest/v1/todo_items?select=checked&family_id=eq.${session.id}"
            val connection = URL("$WIDGET_SUPABASE_URL$path").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("apikey", WIDGET_SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $WIDGET_SUPABASE_ANON_KEY")
            connection.setRequestProperty("x-family-code", session.code.uppercase())
            try {
                if (connection.responseCode !in 200..299) return@withContext 0
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(text.ifBlank { "[]" })
                var count = 0
                repeat(array.length()) { index ->
                    if (!array.getJSONObject(index).optBoolean("checked")) count++
                }
                count
            } finally {
                connection.disconnect()
            }
        }
}
