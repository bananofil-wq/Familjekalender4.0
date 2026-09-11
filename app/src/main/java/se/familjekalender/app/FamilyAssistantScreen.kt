package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

private fun conflictLines(events: List<SyncEvent>, members: List<SyncMember>): List<String> = events
    .filter { it.memberId != null && it.memberId != ALL_FAMILY_MEMBER_ID }
    .groupBy { Triple(it.date, it.time, it.memberId) }
    .filterValues { it.size > 1 }
    .values
    .map { group ->
        val first = group.first()
        "${memberName(first.memberId, members)} har ${group.size} aktiviteter samtidigt ${first.time}."
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
        in 5..10 -> "God morgon!"
        in 11..16 -> "God dag!"
        else -> "God kväll!"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
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
            Text("Idag", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (todaysEvents.isEmpty()) {
                Text("Inga aktiviteter inlagda.", fontSize = 14.sp, color = Muted)
            } else {
                todaysEvents.take(3).forEach { event ->
                    Text("• ${eventLine(event, members)}", fontSize = 14.sp, color = Color.White)
                }
                if (todaysEvents.size > 3) Text("+ ${todaysEvents.size - 3} till", fontSize = 12.sp, color = Muted)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AssistantStat("✓", "$openTodos kvar", "To-Do", Modifier.weight(1f))
                AssistantStat("🛒", "$openShopping kvar", "Inköp", Modifier.weight(1f))
                AssistantStat("●", "${todaysEvents.size}", "idag", Modifier.weight(1f))
                AssistantStat("▣", "${tomorrowsEvents.size}", "imorgon", Modifier.weight(1f))
            }

            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Konfliktvarning", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun AssistantStat(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
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
