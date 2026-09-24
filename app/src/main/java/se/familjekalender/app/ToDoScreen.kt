package se.familjekalender.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TODO_SUPABASE_URL = "https://zigychfkpgypjuovgyqq.supabase.co"
private const val TODO_SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppZ3ljaGZrcGd5cGp1b3ZneXFxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg2NTI2NzQsImV4cCI6MjEwNDIyODY3NH0.dN4zZ78EDYjPOpQ4-nj21tnFOJG21Hj7dXpm69AuEQc"

data class SyncTodoItem(val id: String, val title: String, val checked: Boolean)

private object TodoSync {
    fun load(session: FamilySession): List<SyncTodoItem> {
        val result =
            request(
                "GET",
                "/rest/v1/todo_items?select=id,title,checked&family_id=eq.${session.id}&order=updated_at.asc",
                familyCode = session.code,
            )
        val array = JSONArray(result)
        return buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(
                    SyncTodoItem(
                        row.getString("id"),
                        row.getString("title"),
                        row.getBoolean("checked"),
                    )
                )
            }
        }
    }

    fun add(session: FamilySession, title: String) {
        request(
            "POST",
            "/rest/v1/todo_items",
            JSONObject().put("family_id", session.id).put("title", title).put("checked", false),
            session.code,
            preferRepresentation = false,
        )
    }

    fun toggle(session: FamilySession, item: SyncTodoItem) {
        request(
            "PATCH",
            "/rest/v1/todo_items?id=eq.${item.id}&family_id=eq.${session.id}",
            JSONObject().put("checked", !item.checked),
            session.code,
            preferRepresentation = false,
        )
    }

    fun delete(session: FamilySession, item: SyncTodoItem) {
        request(
            "DELETE",
            "/rest/v1/todo_items?id=eq.${item.id}&family_id=eq.${session.id}",
            familyCode = session.code,
            preferRepresentation = false,
        )
    }

    fun clearChecked(session: FamilySession) {
        request(
            "DELETE",
            "/rest/v1/todo_items?family_id=eq.${session.id}&checked=eq.true",
            familyCode = session.code,
            preferRepresentation = false,
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        familyCode: String? = null,
        preferRepresentation: Boolean = true,
    ): String {
        val connection = URL("$TODO_SUPABASE_URL$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("apikey", TODO_SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $TODO_SUPABASE_ANON_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        if (!familyCode.isNullOrBlank())
            connection.setRequestProperty("x-family-code", familyCode.uppercase())
        connection.setRequestProperty(
            "Prefer",
            if (preferRepresentation) "return=representation" else "return=minimal",
        )
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use {
                it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
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
    val motionEnabled = appMotionEnabled()
    var items by remember { mutableStateOf(emptyList<SyncTodoItem>()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    suspend fun refresh() {
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                TodoSync.load(session)
            }
        }
            .onSuccess {
                items = it
                error = ""
            }
            .onFailure { error = it.message ?: "Kunde inte ladda To-Do" }
    }

    fun addItem() {
        val title = text.trim()
        if (title.isEmpty()) return
        text = ""
        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    TodoSync.add(session, title)
                }
            }.onFailure { error = it.message ?: "Kunde inte lägga till" }
            refresh()
        }
    }

    fun toggleItem(item: SyncTodoItem) {
        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    TodoSync.toggle(session, item)
                }
            }.onFailure { error = it.message ?: "Kunde inte uppdatera" }
            refresh()
        }
    }

    fun deleteItem(item: SyncTodoItem) {
        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    TodoSync.delete(session, item)
                }
            }.onFailure { error = it.message ?: "Kunde inte ta bort" }
            refresh()
        }
    }

    LaunchedEffect(session.id) {
        refresh()
        while (true) {
            delay(30L * 60L * 1000L)
            refresh()
        }
    }

    val openItems = items.filterNot { it.checked }
    val completedItems = items.filter { it.checked }
    val total = items.size
    val completed = completedItems.size
    val progress = if (total == 0) 0f else completed.toFloat() / total.toFloat()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "To-Do",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Familjens gemensamma uppgifter",
                    color = LuxuryTextMuted,
                    fontSize = 12.sp,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                shape = RoundedCornerShape(99.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = .30f),
                ),
            ) {
                Text(
                    "FAMILJ",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
        }

        Surface(
            color = LuxurySurfaceElevated,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (openItems.isEmpty() && total > 0) "Allt klart"
                            else "${openItems.size} kvar",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when {
                                total == 0 -> "Lägg till första uppgiften"
                                completed == 0 -> "$total uppgifter totalt"
                                else -> "$completed av $total avklarade"
                            },
                            color = LuxuryTextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .15f),
                        shape = CircleShape,
                        modifier = Modifier.size(58.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                if (total > 0) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = .08f),
                    )
                }
            }
        }

        Surface(
            color = LuxurySurfaceElevated,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "NY UPPGIFT",
                    color = LuxuryTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Vad behöver göras?") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = ::addItem,
                        enabled = text.isNotBlank(),
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black.copy(alpha = .82f),
                                disabledContainerColor =
                                    MaterialTheme.colorScheme.primary.copy(alpha = .28f),
                            ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Lägg till")
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = .10f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = .22f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        AnimatedContent(
            targetState = items,
            transitionSpec = {
                (fadeIn(tween(motionDuration(170, motionEnabled))) +
                    slideInVertically(tween(motionDuration(210, motionEnabled))) { it / 14 }) togetherWith
                    (fadeOut(tween(motionDuration(120, motionEnabled))) +
                        slideOutVertically(tween(motionDuration(170, motionEnabled))) { -it / 18 })
            },
            label = "todo-premium-list",
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .animateContentSize(tween(motionDuration(220, motionEnabled))),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (openItems.isEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = .035f),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (total == 0) "Inga uppgifter ännu" else "Allt är avklarat",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (total == 0) "Lägg till något ovanför."
                                else "Familjens lista är tom på måsten.",
                                color = LuxuryTextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "ATT GÖRA",
                            color = LuxuryTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            openItems.size.toString(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    openItems.forEachIndexed { index, item ->
                        Surface(
                            color = LuxurySurfaceElevated,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (index == 0)
                                    MaterialTheme.colorScheme.primary.copy(alpha = .24f)
                                else Color.White.copy(alpha = .08f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { toggleItem(item) },
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(
                                    Modifier.weight(1f)
                                        .clickable { toggleItem(item) }
                                        .padding(vertical = 5.dp),
                                ) {
                                    Text(
                                        item.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        if (index == 0) "Nästa uppgift" else "Familjens To-Do",
                                        color = LuxuryTextMuted,
                                        fontSize = 9.sp,
                                    )
                                }
                                TextButton(onClick = { deleteItem(item) }) {
                                    Text(
                                        "Ta bort",
                                        color = LuxuryTextMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (completedItems.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "KLARA",
                            color = LuxuryTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.IO
                                        ) {
                                            TodoSync.clearChecked(session)
                                        }
                                    }.onFailure {
                                        error = it.message ?: "Kunde inte rensa"
                                    }
                                    refresh()
                                }
                            },
                        ) {
                            Text("Rensa klara", fontSize = 10.sp)
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = .025f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = .06f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            completedItems.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = .05f),
                                        thickness = .5.dp,
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { toggleItem(item) }
                                        .padding(horizontal = 11.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = { toggleItem(item) },
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        item.title,
                                        color = LuxuryTextMuted,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { deleteItem(item) }) {
                                        Text(
                                            "Ta bort",
                                            color = LuxuryTextMuted.copy(alpha = .75f),
                                            fontSize = 9.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
