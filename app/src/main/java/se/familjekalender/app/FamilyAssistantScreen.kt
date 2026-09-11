package se.familjekalender.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

private const val ASSISTANT_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val ASSISTANT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

data class AssistantTodo(val title: String, val checked: Boolean)

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

private fun conflictLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> {
    return events
        .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
        .groupBy { Triple(it.date, it.time, it.memberId) }
        .filterValues { it.size > 1 }
        .values
        .map { group ->
            val first = group.first()
            val who = memberName(first.memberId, members)
            "$who har ${group.size} aktiviteter samtidigt ${first.time}."
        }
}

@Composable
internal fun FamilyAssistantCard(
    session: FamilySession,
    events: List<SyncEvent>,
    members: List<SyncMember>,
    shopping: List<SyncShoppingItem>
) {
    var todos by remember { mutableStateOf(emptyList<AssistantTodo>()) }
    var expanded by remember { mutableStateOf(false) }

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
    val openTodos = todos.count { !it.checked }
    val openShopping = shopping.count { !it.checked }
    val conflicts = conflictLines(todaysEvents, members)
    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "God morgon"
        in 11..16 -> "God dag"
        else -> "God kväll"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Familjeassistent", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("$greeting. Här är familjens läge just nu.", color = Color.White)
            Spacer(Modifier.height(10.dp))

            if (todaysEvents.isEmpty()) {
                Text("Idag: inga aktiviteter inlagda.", color = Muted)
            } else {
                Text("Idag", fontWeight = FontWeight.Bold)
                todaysEvents.take(if (expanded) 20 else 3).forEach {
                    Text("• ${eventLine(it, members)}", color = Color.White)
                }
                if (!expanded && todaysEvents.size > 3) {
                    Text("+ ${todaysEvents.size - 3} till", color = Muted)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("$openTodos saker kvar på To-Do • $openShopping varor kvar att handla", color = Muted)

            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Konfliktvarning", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                conflicts.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Text("Imorgon", fontWeight = FontWeight.Bold)
                if (tomorrowsEvents.isEmpty()) {
                    Text("Inga aktiviteter inlagda.", color = Muted)
                } else {
                    tomorrowsEvents.forEach { Text("• ${eventLine(it, members)}", color = Color.White) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "När hämtning, lämning, samlingstid och utrustning kopplas till aktiviteter visas de här i samma sammanfattning.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Visa mindre" else "Visa hela familjeöversikten")
            }
        }
    }
}
