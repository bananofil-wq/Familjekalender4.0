package se.familjekalender.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

private const val TODO_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val TODO_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

data class SyncTodoItem(val id: String, val title: String, val checked: Boolean)

private object TodoSync {
    fun load(session: FamilySession): List<SyncTodoItem> {
        val result = request(
            "GET",
            "/rest/v1/todo_items?select=id,title,checked&family_id=eq.${session.id}&order=updated_at.asc",
            familyCode = session.code
        )
        val array = JSONArray(result)
        return buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(SyncTodoItem(row.getString("id"), row.getString("title"), row.getBoolean("checked")))
            }
        }
    }

    fun add(session: FamilySession, title: String) {
        request(
            "POST",
            "/rest/v1/todo_items",
            JSONObject().put("family_id", session.id).put("title", title).put("checked", false),
            session.code,
            preferRepresentation = false
        )
    }

    fun toggle(session: FamilySession, item: SyncTodoItem) {
        request(
            "PATCH",
            "/rest/v1/todo_items?id=eq.${item.id}&family_id=eq.${session.id}",
            JSONObject().put("checked", !item.checked),
            session.code,
            preferRepresentation = false
        )
    }

    fun delete(session: FamilySession, item: SyncTodoItem) {
        request(
            "DELETE",
            "/rest/v1/todo_items?id=eq.${item.id}&family_id=eq.${session.id}",
            familyCode = session.code,
            preferRepresentation = false
        )
    }

    fun clearChecked(session: FamilySession) {
        request(
            "DELETE",
            "/rest/v1/todo_items?family_id=eq.${session.id}&checked=eq.true",
            familyCode = session.code,
            preferRepresentation = false
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        familyCode: String? = null,
        preferRepresentation: Boolean = true
    ): String {
        val connection = URL("$TODO_SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", TODO_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $TODO_SUPABASE_ANON_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        if (!familyCode.isNullOrBlank()) connection.setRequestProperty("x-family-code", familyCode.uppercase())
        connection.setRequestProperty("Prefer", if (preferRepresentation) "return=representation" else "return=minimal")
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Serverfel $code: $text")
        return text.ifBlank { "[]" }
    }
}

@Composable
internal fun ToDoScreen(session: FamilySession) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<SyncTodoItem>()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    suspend fun refresh() {
        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.load(session) } }
            .onSuccess { items = it; error = "" }
            .onFailure { error = it.message ?: "Kunde inte ladda To-Do" }
    }

    LaunchedEffect(session.id) {
        refresh()
        while (true) {
            delay(30L * 60L * 1000L)
            refresh()
        }
    }

    Text("To-Do", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Gemensam lista för familjen", color = Muted)
    Spacer(Modifier.height(18.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Ny uppgift") },
            singleLine = true,
            modifier = Modifier.weight(1f).height(56.dp)
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = {
                val title = text.trim()
                if (title.isNotEmpty()) {
                    text = ""
                    scope.launch {
                        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.add(session, title) } }
                            .onFailure { error = it.message ?: "Kunde inte lägga till" }
                        refresh()
                    }
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(56.dp).offset(y = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black.copy(alpha = .78f),
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .45f),
                disabledContentColor = Color.Black.copy(alpha = .55f)
            )
        ) { Icon(Icons.Default.Add, contentDescription = "Lägg till") }
    }

    if (error.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    Spacer(Modifier.height(12.dp))
    if (items.isEmpty()) {
        Text("Inga uppgifter ännu", color = Muted)
    }

    items.forEach { item ->
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = {
                        scope.launch {
                            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.toggle(session, item) } }
                                .onFailure { error = it.message ?: "Kunde inte uppdatera" }
                            refresh()
                        }
                    }
                )
                Text(
                    item.title,
                    color = if (item.checked) Muted else Color.White,
                    modifier = Modifier.weight(1f).clickable {
                        scope.launch {
                            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.toggle(session, item) } }
                                .onFailure { error = it.message ?: "Kunde inte uppdatera" }
                            refresh()
                        }
                    }
                )
                TextButton(onClick = {
                    scope.launch {
                        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.delete(session, item) } }
                            .onFailure { error = it.message ?: "Kunde inte ta bort" }
                        refresh()
                    }
                }) { Text("Ta bort") }
            }
        }
    }

    if (items.any { it.checked }) {
        TextButton(onClick = {
            scope.launch {
                runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TodoSync.clearChecked(session) } }
                    .onFailure { error = it.message ?: "Kunde inte rensa" }
                refresh()
            }
        }) { Text("Rensa klara") }
    }
}
