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
import androidx.compose.material.icons.filled.Person
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

data class SyncTodoItem(
    val id: String,
    val title: String,
    val checked: Boolean,
    val memberId: String?,
)

private object TodoSync {
    fun load(session: FamilySession): List<SyncTodoItem> {
        val result =
            request(
                "GET",
                "/rest/v1/todo_items?select=id,title,checked,member_id&family_id=eq.${session.id}&order=updated_at.asc",
                familyCode = session.code,
            )
        val array = JSONArray(result)
        return buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                add(
                    SyncTodoItem(
                        id = row.getString("id"),
                        title = row.getString("title"),
                        checked = row.getBoolean("checked"),
                        memberId =
                            if (row.isNull("member_id")) null
                            else row.getString("member_id"),
                    )
                )
            }
        }
    }

    fun add(session: FamilySession, title: String, memberId: String?) {
        val body =
            JSONObject()
                .put("family_id", session.id)
                .put("title", title)
                .put("checked", false)
        if (memberId.isNullOrBlank() || memberId == ALL_FAMILY_MEMBER_ID) {
            body.put("member_id", JSONObject.NULL)
        } else {
            body.put("member_id", memberId)
        }
        request(
            "POST",
            "/rest/v1/todo_items",
            body,
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

    fun updateAssignee(session: FamilySession, item: SyncTodoItem, memberId: String?) {
        val body = JSONObject()
        if (memberId.isNullOrBlank() || memberId == ALL_FAMILY_MEMBER_ID) {
            body.put("member_id", JSONObject.NULL)
        } else {
            body.put("member_id", memberId)
        }
        request(
            "PATCH",
            "/rest/v1/todo_items?id=eq.${item.id}&family_id=eq.${session.id}",
            body,
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
        if (!familyCode.isNullOrBlank()) {
            connection.setRequestProperty("x-family-code", familyCode.uppercase())
        }
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
internal fun ToDoScreen(
    session: FamilySession,
    members: List<SyncMember>,
) {
    val scope = rememberCoroutineScope()
    val motionEnabled = appMotionEnabled()
    val realMembers = remember(members) { members.filter { it.id != ALL_FAMILY_MEMBER_ID } }
    val memberById = remember(realMembers) { realMembers.associateBy { it.id } }

    var items by remember { mutableStateOf(emptyList<SyncTodoItem>()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var newAssigneeId by remember { mutableStateOf<String?>(null) }
    var showNewAssigneeDialog by remember { mutableStateOf(false) }
    var editAssigneeItem by remember { mutableStateOf<SyncTodoItem?>(null) }

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
        val assignee = newAssigneeId
        text = ""
        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    TodoSync.add(session, title, assignee)
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

    fun updateAssignee(item: SyncTodoItem, memberId: String?) {
        editAssigneeItem = null
        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    TodoSync.updateAssignee(session, item, memberId)
                }
            }.onFailure { error = it.message ?: "Kunde inte byta ansvarig" }
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
    val newAssignee = newAssigneeId?.let(memberById::get)

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
                border =
                    BorderStroke(
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

                Spacer(Modifier.height(10.dp))
                Text(
                    "ANSVARIG",
                    color = LuxuryTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .8.sp,
                )
                Spacer(Modifier.height(6.dp))
                TodoAssigneeChip(
                    member = newAssignee,
                    onClick = { showNewAssigneeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (error.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = .10f),
                shape = RoundedCornerShape(16.dp),
                border =
                    BorderStroke(
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
                        val assignee = item.memberId?.let(memberById::get)
                        Surface(
                            color = LuxurySurfaceElevated,
                            shape = RoundedCornerShape(20.dp),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (index == 0)
                                        MaterialTheme.colorScheme.primary.copy(alpha = .24f)
                                    else Color.White.copy(alpha = .08f),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = false,
                                        onCheckedChange = { toggleItem(item) },
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        item.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { deleteItem(item) }) {
                                        Text(
                                            "Ta bort",
                                            color = LuxuryTextMuted,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 48.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TodoAssigneeChip(
                                        member = assignee,
                                        onClick = { editAssigneeItem = item },
                                        compact = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (index == 0) {
                                        Text(
                                            "NÄSTA",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = .8.sp,
                                        )
                                    }
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
                                val assignee = item.memberId?.let(memberById::get)
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = .05f),
                                        thickness = .5.dp,
                                    )
                                }
                                Column(
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 11.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
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
                                    TodoAssigneeChip(
                                        member = assignee,
                                        onClick = { editAssigneeItem = item },
                                        compact = true,
                                        muted = true,
                                        modifier = Modifier.padding(start = 48.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewAssigneeDialog) {
        TodoAssigneeDialog(
            members = realMembers,
            selectedMemberId = newAssigneeId,
            title = "Tilldela uppgiften",
            onDismiss = { showNewAssigneeDialog = false },
            onSelect = { memberId ->
                newAssigneeId = memberId
                showNewAssigneeDialog = false
            },
        )
    }

    editAssigneeItem?.let { item ->
        TodoAssigneeDialog(
            members = realMembers,
            selectedMemberId = item.memberId,
            title = "Byt ansvarig",
            onDismiss = { editAssigneeItem = null },
            onSelect = { memberId -> updateAssignee(item, memberId) },
        )
    }
}

@Composable
private fun TodoAssigneeChip(
    member: SyncMember?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    muted: Boolean = false,
) {
    val accent =
        if (member == null) Color(0xFFFFD75E)
        else runCatching { Color(member.colorArgb.toInt()) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val label = member?.name ?: "Hela familjen"

    Surface(
        color = if (muted) Color.White.copy(alpha = .025f) else accent.copy(alpha = .10f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = if (muted) .15f else .28f)),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = if (compact) 6.dp else 9.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(if (compact) 8.dp else 10.dp)
                    .then(
                        if (member == null) Modifier
                        else Modifier
                    )
                    .let { base -> base },
            ) {
                if (member == null) {
                    Text(
                        "★",
                        color = accent,
                        fontSize = if (compact) 9.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize()
                            .then(Modifier)
                            .let { it }
                            .run { this }
                    )
                }
            }
            if (member != null) {
                Box(
                    Modifier.size(if (compact) 8.dp else 10.dp)
                        .padding(0.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (compact) 14.dp else 17.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = if (muted) LuxuryTextMuted else Color.White,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!compact) {
                Spacer(Modifier.weight(1f))
                Text("›", color = accent, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun TodoAssigneeDialog(
    members: List<SyncMember>,
    selectedMemberId: String?,
    title: String,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LuxurySurfaceElevated,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TodoAssigneeOption(
                    label = "Hela familjen",
                    accent = Color(0xFFFFD75E),
                    selected = selectedMemberId == null,
                    onClick = { onSelect(null) },
                )
                members.forEach { member ->
                    TodoAssigneeOption(
                        label = member.name,
                        accent =
                            runCatching { Color(member.colorArgb.toInt()) }
                                .getOrDefault(MaterialTheme.colorScheme.primary),
                        selected = selectedMemberId == member.id,
                        onClick = { onSelect(member.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        },
    )
}

@Composable
private fun TodoAssigneeOption(
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) accent.copy(alpha = .14f) else Color.White.copy(alpha = .035f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = .55f) else Color.White.copy(alpha = .08f),
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(11.dp)
                    .then(Modifier)
            ) {
                Surface(
                    color = accent,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize(),
                ) {}
            }
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text("✓", color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}
