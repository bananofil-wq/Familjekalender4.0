package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val ASSISTANT_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val ASSISTANT_SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJIUzI1NiIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

data class AssistantTodo(val title: String, val checked: Boolean)

private enum class AssistantPopup {
    TODO,
    SHOPPING,
    TODAY,
    TOMORROW,
}

private suspend fun loadAssistantTodos(session: FamilySession): List<AssistantTodo> =
    withContext(Dispatchers.IO) {
        val path =
            "/rest/v1/todo_items?select=title,checked&family_id=eq.${session.id}&order=updated_at.asc"
        val connection = URL("$ASSISTANT_SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", ASSISTANT_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $ASSISTANT_SUPABASE_ANON_KEY")
        connection.setRequestProperty("x-family-code", session.code.uppercase())
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) return@withContext emptyList()
        val array = JSONArray(text.ifBlank { "[]" })
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(AssistantTodo(row.optString("title"), row.optBoolean("checked")))
            }
        }
    }

private fun memberName(memberId: String?, members: List<SyncMember>): String =
    when (memberId) {
        null,
        ALL_FAMILY_MEMBER_ID -> "Hela familjen"

        else -> members.firstOrNull { it.id == memberId }?.name ?: "Familjen"
    }

private fun eventLine(event: SyncEvent, members: List<SyncMember>): String {
    val who = memberName(event.memberId, members)
    val time = event.time.takeIf { it.isNotBlank() } ?: "hela dagen"
    return "$who: ${event.title} $time"
}

private data class AssistantTimeRange(val start: Int, val end: Int)

private fun minutesOfDay(value: String): Int? = runCatching {
    val parsed = LocalTime.parse(value)
    parsed.hour * 60 + parsed.minute
}
    .getOrNull()

private fun assistantTimeRange(event: SyncEvent): AssistantTimeRange? {
    val start = minutesOfDay(event.time) ?: return null

    val storedEnd =
        event.endTime?.let(::minutesOfDay)?.let { endMinutes ->
            val dayOffset =
                when {
                    event.endDate == null -> if (endMinutes > start) 0 else 1
                    event.endDate.isAfter(event.date) -> 1
                    else -> 0
                }
            endMinutes + dayOffset * 24 * 60
        }
    if (storedEnd != null && storedEnd > start) {
        return AssistantTimeRange(start, storedEnd)
    }

    // Older rows and generated school schedules may not have ends_at yet.
    // Keep title parsing as a compatibility fallback, then finally assume one hour.
    val range = Regex("(\\d{2}:\\d{2})[–-](\\d{2}:\\d{2})").find(event.title)
    val titleEnd = range?.groupValues?.getOrNull(2)?.let(::minutesOfDay)
    val fallbackEnd =
        when {
            titleEnd == null -> start + 60
            titleEnd > start -> titleEnd
            else -> titleEnd + 24 * 60
        }
    return AssistantTimeRange(start, fallbackEnd)
}

private fun isChildMember(member: SyncMember): Boolean {
    val role = member.role.lowercase(Locale("sv", "SE"))
    return role.contains("barn") ||
            role.contains("child") ||
            role.contains("son") ||
            role.contains("dotter")
}

private fun isCareOrSchoolEvent(event: SyncEvent): Boolean {
    val title = event.title.lowercase(Locale("sv", "SE"))
    return listOf("förskola", "skola", "fritids", "dagis").any(title::contains)
}

private fun shortEventTitle(event: SyncEvent): String = event.title.substringBefore(" · ").trim()

private fun clockText(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}

private fun conflictLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> =
    analyzeCalendarConflicts(events, members).map { it.message }.distinct()

private fun coordinationLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val timed =
        events
            .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
            .mapNotNull { event -> assistantTimeRange(event)?.let { event to it } }
            .sortedBy { it.second.start }
    val warnings = mutableListOf<String>()
    for (index in timed.indices) {
        val (first, firstRange) = timed[index]
        for (nextIndex in timed.indices) {
            if (index == nextIndex) continue
            val (second, secondRange) = timed[nextIndex]
            if (first.memberId == second.memberId) continue
            val gap = secondRange.start - firstRange.end
            if (gap in 0..45) {
                warnings +=
                    "Kort byte i familjens schema: ${
                        memberName(
                            first.memberId,
                            members
                        )
                    }s ${shortEventTitle(first)} slutar ${clockText(firstRange.end)} och ${
                        memberName(
                            second.memberId,
                            members
                        )
                    }s ${shortEventTitle(second)} börjar ${clockText(secondRange.start)}, bara $gap min emellan."
            }
        }
    }
    return warnings.distinct()
}

private data class AdultAvailability(val member: SyncMember, val freeMinutesBeforeNext: Int?)

private fun overlapsWindow(range: AssistantTimeRange, start: Int, end: Int): Boolean =
    range.start < end && range.end > start

private fun suggestedAdultAt(
    events: List<SyncEvent>,
    adults: List<SyncMember>,
    minute: Int,
): AdultAvailability? {
    val pickupWindowStart = minute - 15
    val pickupWindowEnd = minute + 30
    return adults
        .mapNotNull { adult ->
            val ranges =
                events
                    .filter { it.memberId == adult.id }
                    .mapNotNull(::assistantTimeRange)
                    .sortedBy { it.start }
            if (ranges.any { overlapsWindow(it, pickupWindowStart, pickupWindowEnd) })
                return@mapNotNull null
            val nextStart = ranges.firstOrNull { it.start >= pickupWindowEnd }?.start
            AdultAvailability(adult, nextStart?.minus(minute))
        }
        .sortedWith(
            compareByDescending<AdultAvailability> { it.freeMinutesBeforeNext ?: Int.MAX_VALUE }
                .thenBy { it.member.name.lowercase(Locale("sv", "SE")) }
        )
        .firstOrNull()
}

private fun actionPlanningLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
    val children = realMembers.filter(::isChildMember)
    val adults = realMembers.filterNot(::isChildMember)
    if (children.isEmpty() || adults.isEmpty()) return emptyList()

    val eventsByMember =
        events
            .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
            .groupBy { it.memberId }
    val actions = mutableListOf<String>()

    children.forEach { child ->
        val childEvents = eventsByMember[child.id].orEmpty()
        childEvents.filter(::isCareOrSchoolEvent).forEach { care ->
            val pickup = assistantTimeRange(care)?.end ?: return@forEach
            val suggestion = suggestedAdultAt(events, adults, pickup)
            if (suggestion != null) {
                val margin = suggestion.freeMinutesBeforeNext
                val suffix =
                    when {
                        margin == null -> " och har inget senare tidsatt åtagande."
                        margin >= 120 ->
                            " och har minst ${margin / 60} timmar till nästa tidsatta åtagande."

                        else -> " och har cirka $margin minuter till nästa tidsatta åtagande."
                    }
                actions +=
                    "Enligt kalendern har ${suggestion.member.name} bäst lucka runt ${
                        clockText(
                            pickup
                        )
                    } för ${child.name}s hämtning$suffix"
            }
        }
    }
    return actions.distinct()
}

private fun familyPlanningLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
    val children = realMembers.filter(::isChildMember)
    val adults = realMembers.filterNot(::isChildMember)
    if (children.isEmpty() || adults.isEmpty()) return emptyList()

    val eventsByMember =
        events
            .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
            .groupBy { it.memberId }
    val warnings = mutableListOf<String>()

    for (child in children) {
        val childEvents = eventsByMember[child.id].orEmpty()
        val careEvents = childEvents.filter(::isCareOrSchoolEvent)
        for (care in careEvents) {
            val careRange = assistantTimeRange(care) ?: continue
            val pickupMinute = careRange.end

            val pickupWindowStart = pickupMinute - 15
            val pickupWindowEnd = pickupMinute + 30
            val availableAdults = adults.filter { adult ->
                val adultRanges =
                    eventsByMember[adult.id].orEmpty().mapNotNull(::assistantTimeRange)
                adultRanges.none { range ->
                    overlapsWindow(range, pickupWindowStart, pickupWindowEnd)
                }
            }
            if (availableAdults.isEmpty()) {
                warnings +=
                    "Hämtning för ${child.name} runt ${clockText(pickupMinute)} behöver planeras: ingen vuxen har en fri kalenderlucka från 15 min före till 30 min efter hämtningen."
            }

            val nextActivity =
                childEvents
                    .asSequence()
                    .filter { it.id != care.id && !isCareOrSchoolEvent(it) }
                    .mapNotNull { event -> minutesOfDay(event.time)?.let { event to it } }
                    .filter { (_, start) -> start >= pickupMinute }
                    .minByOrNull { it.second }
            if (nextActivity != null) {
                val (activity, activityStart) = nextActivity
                val gap = activityStart - pickupMinute
                if (gap in 0..60) {
                    warnings +=
                        "${child.name} har ${shortEventTitle(activity)} ${clockText(activityStart)}, bara $gap min efter ${
                            shortEventTitle(
                                care
                            )
                        } slutar. Planera hämtning och transport."
                }
            }
        }
    }
    return warnings.distinct()
}

@Composable
internal fun FamilyAssistantCard(
    session: FamilySession,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    shopping: List<SyncShoppingItem>,
    onAdd: () -> Unit,
) {
    var todos by remember { mutableStateOf(emptyList<AssistantTodo>()) }
    var popup by remember { mutableStateOf<AssistantPopup?>(null) }
    var selectedMember by remember { mutableStateOf<SyncMember?>(null) }
    var todayPlanExpanded by remember { mutableStateOf(false) }
    var showAttentionDetails by remember { mutableStateOf(false) }

    LaunchedEffect(session.id) {
        while (true) {
            todos = runCatching { loadAssistantTodos(session) }.getOrDefault(emptyList())
            delay(30L * 60L * 1000L)
        }
    }

    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val todaysEvents = events.filter { it.date == today }.sortedBy { it.time }
    val tomorrowsEvents = events.filter { it.date == tomorrow }.sortedBy { it.time }
    val openTodoItems = todos.filter { !it.checked }
    val openShoppingItems = shopping.filter { !it.checked }
    val conflicts = conflictLines(todaysEvents, members)
    val planning =
        (familyPlanningLines(todaysEvents, members) + coordinationLines(todaysEvents, members))
            .distinct()
    val actions = actionPlanningLines(todaysEvents, members)
    val tomorrowConflicts = conflictLines(tomorrowsEvents, members)
    val tomorrowPlanning =
        (familyPlanningLines(tomorrowsEvents, members) +
                coordinationLines(tomorrowsEvents, members))
            .distinct()
    val tomorrowActions = actionPlanningLines(tomorrowsEvents, members)
    val greeting =
        when (LocalTime.now().hour) {
            in 5..10 -> "God morgon!"
            in 11..16 -> "God dag!"
            else -> "God kväll!"
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurface),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LuxuryText,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Det viktigaste för familjen just nu.",
                        fontSize = 14.sp,
                        color = LuxuryTextMuted,
                    )
                }
                FilledIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Lägg till",
                        modifier = Modifier.size(25.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistantStat(
                    Icons.Default.CheckCircle,
                    "${openTodoItems.size}",
                    "To-do kvar",
                    Modifier.weight(1f),
                ) {
                    popup = AssistantPopup.TODO
                }
                AssistantStat(
                    Icons.Default.ShoppingCart,
                    "${openShoppingItems.size}",
                    "Inköp kvar",
                    Modifier.weight(1f),
                ) {
                    popup = AssistantPopup.SHOPPING
                }
                AssistantStat(
                    Icons.Default.Today,
                    "${todaysEvents.size}",
                    "Idag",
                    Modifier.weight(1f),
                ) {
                    popup = AssistantPopup.TODAY
                }
                AssistantStat(
                    Icons.Default.Event,
                    "${tomorrowsEvents.size}",
                    "Imorgon",
                    Modifier.weight(1f),
                ) {
                    popup = AssistantPopup.TOMORROW
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dagens plan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LuxuryText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { todayPlanExpanded = !todayPlanExpanded },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(
                        "${todaysEvents.size} aktiviteter  ${if (todayPlanExpanded) "▲" else "▼"}",
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
            }
            if (todayPlanExpanded) {
                Spacer(Modifier.height(8.dp))
                if (todaysEvents.isEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = .04f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Inget tidsatt idag. En sällsynt seger över logistiken.",
                            modifier = Modifier.padding(12.dp),
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    todaysEvents.take(5).forEach { event ->
                        val who = memberName(event.memberId, members)
                        val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                        Surface(
                            color = Color.White.copy(alpha = .04f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    time,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(64.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        shortEventTitle(event),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                    )
                                    Text(who, fontSize = 11.sp, color = Muted, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (todaysEvents.size > 5) {
                        TextButton(
                            onClick = { popup = AssistantPopup.TODAY },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Visa alla ${todaysEvents.size}")
                        }
                    }
                }
            }

            val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
            if (realMembers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "FAMILJEN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LuxuryTextMuted,
                    letterSpacing = 1.1.sp,
                )
                realMembers.forEach { member ->
                    val todayCount = todaysEvents.count {
                        it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID
                    }
                    val tomorrowCount = tomorrowsEvents.count {
                        it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { selectedMember = member }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(10.dp)
                                .clip(CircleShape)
                                .background(Color(member.colorArgb.toInt()))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            member.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "$todayCount idag · $tomorrowCount imorgon",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            val attentionCount = conflicts.size + planning.size + actions.size
            val attentionPreview =
                conflicts.firstOrNull() ?: planning.firstOrNull() ?: actions.firstOrNull()
            if (attentionCount > 0) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = LuxurySurfaceHigh,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, LuxuryOutlineSoft),
                    modifier = Modifier.fillMaxWidth().clickable { showAttentionDetails = true },
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Behöver din uppmärksamhet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LuxuryText,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                                shape = RoundedCornerShape(99.dp),
                            ) {
                                Text(
                                    "$attentionCount  ›",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        attentionPreview?.let {
                            Text(
                                it,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = LuxuryTextMuted,
                                maxLines = 2,
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "✓ Inga krockar eller olösta hämtningar idag",
                    fontSize = 12.sp,
                    color = Color(0xFF6DD6A7),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (
                tomorrowConflicts.isNotEmpty() ||
                tomorrowPlanning.isNotEmpty() ||
                tomorrowActions.isNotEmpty()
            ) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Inför imorgon",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                tomorrowConflicts.take(1).forEach {
                    Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                tomorrowPlanning.take(2).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                }
                tomorrowActions.take(1).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f))
                }
            }
        }
    }

    if (showAttentionDetails) {
        val attentionItems = buildList {
            conflicts.forEach { add("Krock" to it) }
            planning.forEach { add("Planering" to it) }
            actions.forEach { add("Förslag" to it) }
        }
        AlertDialog(
            onDismissRequest = { showAttentionDetails = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = LuxurySurfaceElevated,
            title = {
                Column {
                    Text("Behöver din uppmärksamhet", fontWeight = FontWeight.Bold)
                    Text(
                        "${attentionItems.size} saker att gå igenom",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
            },
            text = {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    attentionItems.forEachIndexed { index, (kind, detail) ->
                        Surface(
                            color =
                                when (kind) {
                                    "Krock" -> MaterialTheme.colorScheme.error.copy(alpha = .09f)
                                    "Förslag" ->
                                        MaterialTheme.colorScheme.primary.copy(alpha = .09f)

                                    else -> Color.White.copy(alpha = .045f)
                                },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "${index + 1}. $kind",
                                    color =
                                        if (kind == "Krock") MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(detail, color = Color.White, fontSize = 13.sp)
                                if (kind != "Förslag") {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        "Åtgärd: ${weekActionSuggestion(detail)}",
                                        color = Muted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttentionDetails = false }) { Text("Stäng") }
            },
        )
    }

    popup?.let { selected ->
        AssistantDetailPopup(
            popup = selected,
            todos = openTodoItems,
            shopping = openShoppingItems,
            events = if (selected == AssistantPopup.TOMORROW) tomorrowsEvents else todaysEvents,
            members = members,
            date = if (selected == AssistantPopup.TOMORROW) tomorrow else today,
            onDismiss = { popup = null },
        )
    }

    selectedMember?.let { member ->
        MemberTwoDayPopup(
            member = member,
            events = events,
            today = today,
            tomorrow = tomorrow,
            onDismiss = { selectedMember = null },
        )
    }
}

private data class WeekDaySummary(
    val date: LocalDate,
    val events: List<SyncEvent>,
    val warnings: List<String>,
)

@Composable
internal fun WeekOverviewCard(
    events: List<SyncEvent>,
    members: List<SyncMember>,
) {
    val today = LocalDate.now()
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val locale = Locale("sv", "SE")
    val days =
        (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            val dayEvents = events.filter { it.date == date }.sortedBy { it.time }
            val warnings =
                (conflictLines(dayEvents, members) +
                        familyPlanningLines(dayEvents, members) +
                        coordinationLines(dayEvents, members))
                    .distinct()
            WeekDaySummary(date = date, events = dayEvents, warnings = warnings)
        }
    var selectedWeekDay by remember { mutableStateOf<WeekDaySummary?>(null) }
    var weekExpanded by remember { mutableStateOf(false) }

    val totalEvents = days.sumOf { it.events.size }
    val totalWarnings = days.sumOf { it.warnings.size }
    val intenseDays = days.count { it.events.size >= 5 || it.warnings.isNotEmpty() }
    val calmDays = days.count { it.events.size <= 1 && it.warnings.isEmpty() }
    val busiest = days.maxByOrNull { it.events.size }
    val calmest =
        days.minWithOrNull(compareBy<WeekDaySummary> { it.events.size }.thenBy { it.warnings.size })
    val firstProblemDay = days.firstOrNull { !it.date.isBefore(today) && it.warnings.isNotEmpty() }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Veckan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "$totalEvents aktiviteter · $totalWarnings planeringspunkter",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
                val weekState =
                    when {
                        totalWarnings > 0 -> "ATT PLANERA"
                        intenseDays >= 3 -> "INTENSIV"
                        else -> "BALANS"
                    }
                val stateColor =
                    when {
                        totalWarnings > 0 -> MaterialTheme.colorScheme.error
                        intenseDays >= 3 -> Color(0xFFFFB86B)
                        else -> Color(0xFF6DD6A7)
                    }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        weekState,
                        fontSize = 10.sp,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { weekExpanded = !weekExpanded },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text(
                            if (weekExpanded) "Minimera ▲" else "Visa ▼",
                            fontSize = 10.sp,
                            color = Muted,
                        )
                    }
                }
            }

            if (weekExpanded) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color.White.copy(alpha = .04f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text("Intensiva", fontSize = 10.sp, color = Muted)
                            Text(
                                "$intenseDays dagar",
                                fontSize = 15.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = .04f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text("Lugna", fontSize = 10.sp, color = Muted)
                            Text(
                                "$calmDays dagar",
                                fontSize = 15.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = .04f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text("Att lösa", fontSize = 10.sp, color = Muted)
                            Text(
                                "$totalWarnings",
                                fontSize = 15.sp,
                                color =
                                    if (totalWarnings > 0) MaterialTheme.colorScheme.error
                                    else Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (firstProblemDay != null) {
                    val focusDay =
                        firstProblemDay.date.dayOfWeek
                            .getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar { it.uppercase() }
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = .08f),
                        shape = RoundedCornerShape(14.dp),
                        modifier =
                            Modifier.fillMaxWidth().clickable { selectedWeekDay = firstProblemDay },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Veckans fokus · $focusDay",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                firstProblemDay.warnings.first(),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = .88f),
                            )
                            if (firstProblemDay.warnings.size > 1) {
                                Text(
                                    "+ ${firstProblemDay.warnings.size - 1} till på samma dag",
                                    fontSize = 10.sp,
                                    color = Muted,
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF6DD6A7).copy(alpha = .07f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "✓ Veckan har inga upptäckta krockar eller olösta hämtningar.",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 11.sp,
                            color = Color(0xFF6DD6A7),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                days.forEach { day ->
                    val dayName =
                        day.date.dayOfWeek
                            .getDisplayName(TextStyle.SHORT, locale)
                            .replaceFirstChar { it.uppercase() }
                    val warningCount = day.warnings.size
                    val loadLabel =
                        when {
                            warningCount > 0 -> "Behöver planeras"
                            day.events.size >= 5 -> "Intensiv"
                            day.events.size >= 3 -> "Normal"
                            day.events.isEmpty() -> "Lugn"
                            else -> "Lätt"
                        }
                    val loadColor =
                        when {
                            warningCount > 0 -> MaterialTheme.colorScheme.error
                            day.events.size >= 5 -> Color(0xFFFFB86B)
                            day.events.isEmpty() -> Color(0xFF6DD6A7)
                            else -> Muted
                        }
                    val loadFraction =
                        (day.events.size.coerceAtMost(6) / 6f).coerceAtLeast(
                            if (day.events.isEmpty()) 0f else .10f
                        )

                    Surface(
                        color =
                            if (day.date == today)
                                MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                            else Color.White.copy(alpha = .035f),
                        shape = RoundedCornerShape(14.dp),
                        modifier =
                            Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable {
                                selectedWeekDay = day
                            },
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.width(58.dp)) {
                                    Text(
                                        dayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    Text(
                                        "${day.date.dayOfMonth}/${day.date.monthValue}",
                                        fontSize = 10.sp,
                                        color = Muted,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${day.events.size} aktiviteter",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(loadLabel, fontSize = 10.sp, color = loadColor)
                                    Text("Tryck för detaljer", fontSize = 9.sp, color = Muted)
                                }
                                if (warningCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = .14f),
                                        shape = CircleShape,
                                    ) {
                                        Text(
                                            "$warningCount",
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = 9.dp,
                                                    vertical = 4.dp,
                                                ),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Color.White.copy(alpha = .06f))
                            ) {
                                if (loadFraction > 0f) {
                                    Box(
                                        Modifier.fillMaxWidth(loadFraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(loadColor.copy(alpha = .85f))
                                    )
                                }
                            }
                            if (day.warnings.isNotEmpty()) {
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    day.warnings.first(),
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = .78f),
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }

                if (busiest != null && busiest.events.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    val busiestName =
                        busiest.date.dayOfWeek
                            .getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar { it.uppercase() }
                    val calmestName =
                        calmest
                            ?.date
                            ?.dayOfWeek
                            ?.getDisplayName(TextStyle.FULL, locale)
                            ?.replaceFirstChar { it.uppercase() }
                    Text(
                        buildString {
                            append(
                                "Mest belastad: $busiestName · ${busiest.events.size} aktiviteter"
                            )
                            if (calmestName != null && calmest?.date != busiest.date)
                                append("  •  Lugnast: $calmestName")
                        },
                        fontSize = 10.sp,
                        color = Muted,
                    )
                }
            }
        }
    }

    selectedWeekDay?.let { day ->
        WeekDayDetailPopup(
            day = day,
            members = members,
            onDismiss = { selectedWeekDay = null },
        )
    }
}

private fun weekActionSuggestion(warning: String): String =
    when {
        warning.contains("Kort byte", ignoreCase = true) ->
            "Lägg in restid/överlämning eller flytta en av aktiviteterna så att det finns marginal."

        warning.contains("överlappning", ignoreCase = true) ->
            "Flytta en av tiderna eller bestäm vem som tar respektive aktivitet."

        warning.contains("Hämtning", ignoreCase = true) ->
            "Bestäm vem som hämtar och lägg in ansvaret i kalendern."

        warning.contains("transport", ignoreCase = true) ->
            "Bestäm hämtning och transport mellan aktiviteterna och lägg in marginal."

        else -> "Justera tid, ansvar eller transport för de berörda aktiviteterna."
    }

@Composable
private fun WeekDayDetailPopup(
    day: WeekDaySummary,
    members: List<SyncMember>,
    onDismiss: () -> Unit,
) {
    val locale = Locale("sv", "SE")
    val dayName =
        day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar {
            it.uppercase()
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$dayName ${day.date.dayOfMonth}/${day.date.monthValue}") },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (day.warnings.isNotEmpty()) {
                    Text(
                        "Behöver åtgärdas",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    day.warnings.forEachIndexed { index, warning ->
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = .08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Text(
                                    "${index + 1}. $warning",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Förslag: ${weekActionSuggestion(warning)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "✓ Inget särskilt behöver åtgärdas den här dagen.",
                        color = Color(0xFF6DD6A7),
                        fontSize = 12.sp,
                    )
                }

                Text(
                    "Aktiviteter (${day.events.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (day.events.isEmpty()) {
                    Text("Inga aktiviteter.", color = Muted, fontSize = 12.sp)
                } else {
                    day.events.forEach { event ->
                        Surface(
                            color = Color.White.copy(alpha = .04f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    event.time.takeIf { it.isNotBlank() } ?: "Hela dagen",
                                    modifier = Modifier.width(72.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(event.title, fontSize = 12.sp, color = Color.White)
                                    Text(
                                        memberName(event.memberId, members),
                                        fontSize = 10.sp,
                                        color = Muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } },
    )
}

@Composable
internal fun FamilyAutopilotCard(
    events: List<SyncEvent>,
    members: List<SyncMember>,
) {
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val weekEnd = today.plusDays(6)
    val locale = Locale("sv", "SE")
    val suggestions = mutableListOf<Pair<String, String>>()
    var autopilotExpanded by remember { mutableStateOf(false) }

    val tomorrowEvents = events.filter { it.date == tomorrow }.sortedBy { it.time }
    val tomorrowIssues =
        (conflictLines(tomorrowEvents, members) +
                familyPlanningLines(tomorrowEvents, members) +
                coordinationLines(tomorrowEvents, members))
            .distinct()
    if (tomorrowIssues.isNotEmpty()) {
        suggestions +=
            "Planera imorgon" to
                    "${tomorrowIssues.size} sak${if (tomorrowIssues.size == 1) "" else "er"} behöver lösas innan morgondagen."
    }

    val comingDays = (1L..6L).map { today.plusDays(it) }
    val busiest = comingDays.maxByOrNull { date -> events.count { it.date == date } }
    val busiestCount = busiest?.let { date -> events.count { it.date == date } } ?: 0
    if (busiest != null && busiestCount >= 5) {
        val dayName =
            busiest.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar {
                it.uppercase()
            }
        suggestions +=
            "Förbered $dayName" to
                    "$busiestCount aktiviteter gör dagen intensiv. Fördela ansvar och transporter i förväg."
    }

    val recentStart = today.minusDays(42)
    val recentTitles =
        events
            .filter { !it.date.isBefore(recentStart) && it.date.isBefore(today) }
            .map { it.title.trim() }
            .filter { it.length >= 3 }
            .groupingBy { it.lowercase(locale) }
            .eachCount()
            .filterValues { it >= 3 }
            .toList()
            .sortedByDescending { it.second }

    val recurring = recentTitles.firstOrNull { (normalized, _) ->
        events.none { event ->
            !event.date.isBefore(today) &&
                    !event.date.isAfter(today.plusDays(14)) &&
                    event.title.trim().lowercase(locale) == normalized
        }
    }
    if (recurring != null) {
        val (normalized, count) = recurring
        val displayTitle =
            events.lastOrNull { it.title.trim().lowercase(locale) == normalized }?.title
                ?: normalized
        suggestions +=
            "Återkommande mönster" to
                    "\"$displayTitle\" har lagts in $count gånger senaste 6 veckorna. Ett återkommande schema kan spara tid."
    }

    val weekEvents = events.count { !it.date.isBefore(today) && !it.date.isAfter(weekEnd) }
    if (weekEvents == 0 && suggestions.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Familjeautopilot",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "Förslag baserade på familjens egen kalender.",
                        fontSize = 12.sp,
                        color = Muted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "AUTO",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { autopilotExpanded = !autopilotExpanded },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text(
                            if (autopilotExpanded) "Minimera ▲" else "Visa ▼",
                            fontSize = 10.sp,
                            color = Muted,
                        )
                    }
                }
            }

            if (autopilotExpanded) {
                Spacer(Modifier.height(12.dp))
                if (suggestions.isEmpty()) {
                    Surface(
                        color = Color(0xFF6DD6A7).copy(alpha = .08f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "✓ Inget särskilt behöver förebyggas just nu.",
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFF6DD6A7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    suggestions.take(3).forEachIndexed { index, suggestion ->
                        Surface(
                            color =
                                if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                                else Color.White.copy(alpha = .035f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    suggestion.first,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(suggestion.second, fontSize = 11.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberTwoDayPopup(
    member: SyncMember,
    events: List<SyncEvent>,
    today: LocalDate,
    tomorrow: LocalDate,
    onDismiss: () -> Unit,
) {
    val weekEnd = today.plusDays(6)
    val memberEvents =
        events
            .filter {
                (it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID) &&
                        !it.date.isBefore(today) &&
                        !it.date.isAfter(weekEnd)
            }
            .sortedWith(compareBy<SyncEvent> { it.date }.thenBy { it.time })
    val todayEvents = memberEvents.filter { it.date == today }
    val tomorrowEvents = memberEvents.filter { it.date == tomorrow }
    val weekDays = (0L..6L).map { today.plusDays(it) }
    val busiestDay = weekDays.maxByOrNull { date -> memberEvents.count { it.date == date } }
    val busyCount = busiestDay?.let { date -> memberEvents.count { it.date == date } } ?: 0
    val locale = Locale("sv", "SE")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = LuxurySurfaceElevated,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(14.dp)
                            .clip(CircleShape)
                            .background(Color(member.colorArgb.toInt()))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(member.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text(
                    "${memberEvents.size} aktiviteter kommande 7 dagar",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Idag", color = Muted, fontSize = 10.sp)
                            Text(
                                "${todayEvents.size}",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("aktiviteter", color = Muted, fontSize = 10.sp)
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = .05f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Imorgon", color = Muted, fontSize = 10.sp)
                            Text(
                                "${tomorrowEvents.size}",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("aktiviteter", color = Muted, fontSize = 10.sp)
                        }
                    }
                }

                if (busiestDay != null && busyCount > 0) {
                    val dayName =
                        busiestDay.dayOfWeek
                            .getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar { it.uppercase() }
                    Text(
                        "Mest belastad: $dayName · $busyCount aktiviteter",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    "Idag",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (todayEvents.isEmpty()) {
                    Text("Inga aktiviteter idag.", color = Muted, fontSize = 12.sp)
                } else {
                    todayEvents.forEach { event ->
                        val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                        DetailRow(
                            "•",
                            "$time  ${event.title}",
                            if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else null,
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    "Kommande 7 dagar",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                weekDays.forEach { date ->
                    val dayEvents = memberEvents.filter { it.date == date }
                    val dayName =
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar {
                            it.uppercase()
                        }
                    Surface(
                        color =
                            if (date == today) MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                            else Color.White.copy(alpha = .035f),
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.width(54.dp)) {
                                Text(
                                    dayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    "${date.dayOfMonth}/${date.monthValue}",
                                    fontSize = 9.sp,
                                    color = Muted,
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (dayEvents.isEmpty()) {
                                    Text("Ledig", fontSize = 11.sp, color = Color(0xFF6DD6A7))
                                } else {
                                    dayEvents.take(3).forEach { event ->
                                        val time =
                                            event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                                        Text(
                                            "$time · ${event.title}",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = .9f),
                                            maxLines = 1,
                                        )
                                    }
                                    if (dayEvents.size > 3) {
                                        Text(
                                            "+${dayEvents.size - 3} till",
                                            fontSize = 10.sp,
                                            color = Muted,
                                        )
                                    }
                                }
                            }
                            if (dayEvents.size >= 4) {
                                Text(
                                    "Intensiv",
                                    fontSize = 9.sp,
                                    color = Color(0xFFFFB86B),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } },
    )
}

@Composable
private fun AssistantStat(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = LuxurySurfaceElevated,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                shape = CircleShape,
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = LuxuryText,
                maxLines = 1,
            )
            Text(label, fontSize = 10.sp, color = LuxuryTextMuted, maxLines = 1)
        }
    }
}

@Composable
private fun AssistantDetailPopup(
    popup: AssistantPopup,
    todos: List<AssistantTodo>,
    shopping: List<SyncShoppingItem>,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    date: LocalDate,
    onDismiss: () -> Unit,
) {
    val title =
        when (popup) {
            AssistantPopup.TODO -> "To-Do"
            AssistantPopup.SHOPPING -> "Inköp"
            AssistantPopup.TODAY -> "Idag"
            AssistantPopup.TOMORROW -> "Imorgon"
        }
    val dateText =
        "${
            date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))
                .replaceFirstChar { it.uppercase() }
        } ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = LuxurySurfaceElevated,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                if (popup == AssistantPopup.TODAY || popup == AssistantPopup.TOMORROW) {
                    Text(dateText, fontSize = 12.sp, color = Muted)
                }
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (popup) {
                    AssistantPopup.TODO -> {
                        if (todos.isEmpty()) Text("Inga kvarvarande uppgifter.", color = Muted)
                        todos.forEach { item -> DetailRow("✓", item.title) }
                    }

                    AssistantPopup.SHOPPING -> {
                        if (shopping.isEmpty()) Text("Inga varor kvar att handla.", color = Muted)
                        shopping.forEach { item -> DetailRow("🛒", item.name) }
                    }

                    AssistantPopup.TODAY,
                    AssistantPopup.TOMORROW -> {
                        if (events.isEmpty()) Text("Inga aktiviteter inlagda.", color = Muted)
                        events.forEach { event ->
                            val who = memberName(event.memberId, members)
                            val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                            DetailRow("•", "$time  ${event.title}", who)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } },
    )
}

@Composable
private fun DetailRow(icon: String, text: String, secondary: String? = null) {
    Surface(
        color = LuxurySurfaceHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (!secondary.isNullOrBlank()) Text(secondary, fontSize = 11.sp, color = Muted)
            }
        }
    }
}
