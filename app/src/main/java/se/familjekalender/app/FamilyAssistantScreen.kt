package se.familjekalender.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    var popup by remember { mutableStateOf<AssistantPopup?>(null) }

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
                AssistantStat("✓", "${openTodoItems.size} kvar", "To-Do", Modifier.weight(1f)) { popup = AssistantPopup.TODO }
                AssistantStat("🛒", "${openShoppingItems.size} kvar", "Inköp", Modifier.weight(1f)) { popup = AssistantPopup.SHOPPING }
                AssistantStat("●", "${todaysEvents.size}", "idag", Modifier.weight(1f)) { popup = AssistantPopup.TODAY }
                AssistantStat("▣", "${tomorrowsEvents.size}", "imorgon", Modifier.weight(1f)) { popup = AssistantPopup.TOMORROW }
            }

            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Konfliktvarning", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                conflicts.take(2).forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
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
