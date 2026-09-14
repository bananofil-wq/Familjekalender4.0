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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

private const val ASSISTANT_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val ASSISTANT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJIUzI1NiIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

data class AssistantTodo(val title: String, val checked: Boolean)

private enum class AssistantPopup { TODO, SHOPPING, TODAY, TOMORROW }

private suspend fun loadAssistantTodos(session: FamilySession): List<AssistantTodo> = withContext(Dispatchers.IO) {
    val path = "/rest/v1/todo_items?select=title,checked&family_id=eq.${session.id}&order=updated_at.asc"
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

private fun memberName(memberId: String?, members: List<SyncMember>): String = when (memberId) {
    null, ALL_FAMILY_MEMBER_ID -> "Hela familjen"
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
}.getOrNull()

private fun assistantTimeRange(event: SyncEvent): AssistantTimeRange? {
    val start = minutesOfDay(event.time) ?: return null

    val storedEnd = event.endTime?.let(::minutesOfDay)?.let { endMinutes ->
        val dayOffset = when {
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
    val fallbackEnd = when {
        titleEnd == null -> start + 60
        titleEnd > start -> titleEnd
        else -> titleEnd + 24 * 60
    }
    return AssistantTimeRange(start, fallbackEnd)
}

private fun isChildMember(member: SyncMember): Boolean {
    val role = member.role.lowercase(Locale("sv", "SE"))
    return role.contains("barn") || role.contains("child") || role.contains("son") || role.contains("dotter")
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

private fun conflictLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val warnings = mutableListOf<String>()
    events
        .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
        .groupBy { it.memberId }
        .forEach { (memberId, memberEvents) ->
            val timed = memberEvents.mapNotNull { event -> assistantTimeRange(event)?.let { event to it } }
                .sortedBy { it.second.start }
            for (index in 0 until timed.lastIndex) {
                val (firstEvent, firstRange) = timed[index]
                for (nextIndex in index + 1..timed.lastIndex) {
                    val (secondEvent, secondRange) = timed[nextIndex]
                    if (secondRange.start >= firstRange.end) break
                    warnings += "${memberName(memberId, members)} har överlappning: ${shortEventTitle(firstEvent)} och ${shortEventTitle(secondEvent)}."
                }
            }
        }
    return warnings.distinct()
}

private fun coordinationLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val timed = events
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
                warnings += "Kort byte i familjens schema: ${memberName(first.memberId, members)}s ${shortEventTitle(first)} slutar ${clockText(firstRange.end)} och ${memberName(second.memberId, members)}s ${shortEventTitle(second)} börjar ${clockText(secondRange.start)}, bara $gap min emellan."
            }
        }
    }
    return warnings.distinct()
}

private data class AdultAvailability(val member: SyncMember, val freeMinutesBeforeNext: Int?)

private fun overlapsWindow(range: AssistantTimeRange, start: Int, end: Int): Boolean =
    range.start < end && range.end > start

private fun suggestedAdultAt(events: List<SyncEvent>, adults: List<SyncMember>, minute: Int): AdultAvailability? {
    val pickupWindowStart = minute - 15
    val pickupWindowEnd = minute + 30
    return adults.mapNotNull { adult ->
        val ranges = events
            .filter { it.memberId == adult.id }
            .mapNotNull(::assistantTimeRange)
            .sortedBy { it.start }
        if (ranges.any { overlapsWindow(it, pickupWindowStart, pickupWindowEnd) }) return@mapNotNull null
        val nextStart = ranges.firstOrNull { it.start >= pickupWindowEnd }?.start
        AdultAvailability(adult, nextStart?.minus(minute))
    }.sortedWith(
        compareByDescending<AdultAvailability> { it.freeMinutesBeforeNext ?: Int.MAX_VALUE }
            .thenBy { it.member.name.lowercase(Locale("sv", "SE")) }
    ).firstOrNull()
}

private fun actionPlanningLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
    val children = realMembers.filter(::isChildMember)
    val adults = realMembers.filterNot(::isChildMember)
    if (children.isEmpty() || adults.isEmpty()) return emptyList()

    val eventsByMember = events
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
                val suffix = when {
                    margin == null -> " och har inget senare tidsatt åtagande."
                    margin >= 120 -> " och har minst ${margin / 60} timmar till nästa tidsatta åtagande."
                    else -> " och har cirka $margin minuter till nästa tidsatta åtagande."
                }
                actions += "Enligt kalendern har ${suggestion.member.name} bäst lucka runt ${clockText(pickup)} för ${child.name}s hämtning$suffix"
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

    val eventsByMember = events
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
                val adultRanges = eventsByMember[adult.id].orEmpty().mapNotNull(::assistantTimeRange)
                adultRanges.none { range -> overlapsWindow(range, pickupWindowStart, pickupWindowEnd) }
            }
            if (availableAdults.isEmpty()) {
                warnings += "Hämtning för ${child.name} runt ${clockText(pickupMinute)} behöver planeras: ingen vuxen har en fri kalenderlucka från 15 min före till 30 min efter hämtningen."
            }

            val nextActivity = childEvents
                .asSequence()
                .filter { it.id != care.id && !isCareOrSchoolEvent(it) }
                .mapNotNull { event -> minutesOfDay(event.time)?.let { event to it } }
                .filter { (_, start) -> start >= pickupMinute }
                .minByOrNull { it.second }
            if (nextActivity != null) {
                val (activity, activityStart) = nextActivity
                val gap = activityStart - pickupMinute
                if (gap in 0..60) {
                    warnings += "${child.name} har ${shortEventTitle(activity)} ${clockText(activityStart)}, bara $gap min efter ${shortEventTitle(care)} slutar. Planera hämtning och transport."
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
    onAdd: () -> Unit
) {
    var todos by remember { mutableStateOf(emptyList<AssistantTodo>()) }
    var popup by remember { mutableStateOf<AssistantPopup?>(null) }
    var selectedMember by remember { mutableStateOf<SyncMember?>(null) }

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
    val planning = (familyPlanningLines(todaysEvents, members) + coordinationLines(todaysEvents, members)).distinct()
    val actions = actionPlanningLines(todaysEvents, members)
    val tomorrowConflicts = conflictLines(tomorrowsEvents, members)
    val tomorrowPlanning = (familyPlanningLines(tomorrowsEvents, members) + coordinationLines(tomorrowsEvents, members)).distinct()
    val tomorrowActions = actionPlanningLines(tomorrowsEvents, members)
    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "God morgon!"
        in 11..16 -> "God dag!"
        else -> "God kväll!"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Här är familjens läge just nu.", fontSize = 14.sp, color = Muted)
                }
                FilledIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF8F22FF),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Lägg till", modifier = Modifier.size(30.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AssistantStat("✓", "${openTodoItems.size} kvar", "To-Do", Modifier.weight(1f)) { popup = AssistantPopup.TODO }
                AssistantStat("🛒", "${openShoppingItems.size} kvar", "Inköp", Modifier.weight(1f)) { popup = AssistantPopup.SHOPPING }
                AssistantStat("●", "${todaysEvents.size}", "idag", Modifier.weight(1f)) { popup = AssistantPopup.TODAY }
                AssistantStat("▣", "${tomorrowsEvents.size}", "imorgon", Modifier.weight(1f)) { popup = AssistantPopup.TOMORROW }
            }

            val realMembers = members.filter { it.id != ALL_FAMILY_MEMBER_ID }
            if (realMembers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Familjen", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                realMembers.forEach { member ->
                    val todayCount = todaysEvents.count { it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID }
                    val tomorrowCount = tomorrowsEvents.count { it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedMember = member }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(member.colorArgb.toInt()))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(member.name, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$todayCount idag · $tomorrowCount imorgon",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Konfliktvarning", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
            if (planning.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Behöver planeras", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                planning.take(3).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Förslag", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                actions.take(2).forEach { Text("• $it", fontSize = 12.sp, color = Color.White.copy(alpha = .84f)) }
            }
            if (tomorrowConflicts.isNotEmpty() || tomorrowPlanning.isNotEmpty() || tomorrowActions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Inför imorgon", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

    popup?.let { selected ->
        AssistantDetailPopup(
            popup = selected,
            todos = openTodoItems,
            shopping = openShoppingItems,
            events = if (selected == AssistantPopup.TOMORROW) tomorrowsEvents else todaysEvents,
            members = members,
            date = if (selected == AssistantPopup.TOMORROW) tomorrow else today,
            onDismiss = { popup = null }
        )
    }

    selectedMember?.let { member ->
        MemberTwoDayPopup(
            member = member,
            events = events,
            today = today,
            tomorrow = tomorrow,
            onDismiss = { selectedMember = null }
        )
    }
}

@Composable
private fun MemberTwoDayPopup(
    member: SyncMember,
    events: List<SyncEvent>,
    today: LocalDate,
    tomorrow: LocalDate,
    onDismiss: () -> Unit
) {
    val todayEvents = events.filter { it.date == today && (it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID) }.sortedBy { it.time }
    val tomorrowEvents = events.filter { it.date == tomorrow && (it.memberId == member.id || it.memberId == ALL_FAMILY_MEMBER_ID) }.sortedBy { it.time }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF171A20),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(member.colorArgb.toInt())))
                Spacer(Modifier.width(8.dp))
                Text(member.name, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Idag", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (todayEvents.isEmpty()) Text("Inga aktiviteter idag.", color = Muted, fontSize = 12.sp)
                todayEvents.forEach { event ->
                    val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                    DetailRow("•", "$time  ${event.title}", if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else null)
                }

                Spacer(Modifier.height(6.dp))
                Text("Imorgon", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (tomorrowEvents.isEmpty()) Text("Inga aktiviteter imorgon.", color = Muted, fontSize = 12.sp)
                tomorrowEvents.forEach { event ->
                    val time = event.time.takeIf { it.isNotBlank() } ?: "Hela dagen"
                    DetailRow("•", "$time  ${event.title}", if (event.memberId == ALL_FAMILY_MEMBER_ID) "Hela familjen" else null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}

@Composable
private fun AssistantStat(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .13f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, fontSize = 10.sp, color = Muted, maxLines = 1)
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
    onDismiss: () -> Unit
) {
    val title = when (popup) {
        AssistantPopup.TODO -> "To-Do"
        AssistantPopup.SHOPPING -> "Inköp"
        AssistantPopup.TODAY -> "Idag"
        AssistantPopup.TOMORROW -> "Imorgon"
    }
    val dateText = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("sv", "SE")).replaceFirstChar { it.uppercase() }} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("sv", "SE"))}"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF171A20),
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
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    AssistantPopup.TODAY, AssistantPopup.TOMORROW -> {
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Stäng") } }
    )
}

@Composable
private fun DetailRow(icon: String, text: String, secondary: String? = null) {
    Surface(
        color = Color(0xFF20242B),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
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
