package se.familjekalender.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Floating quick-add screen launched from the home-screen widget.
 * This is NOT MainActivity: the calendar is never opened underneath the menu.
 * Creation is performed here, so all four shortcuts can be completed from home.
 */
class WidgetQuickAddActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PRIVATE_INBOX = "open_private_inbox"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.4f }
        window.setGravity(Gravity.CENTER)
        setFinishOnTouchOutside(true)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB47CFF),
                    onPrimary = Color(0xFF150D21),
                    surface = Color(0xFF26252E),
                    onSurface = Color(0xFFF9F7FF),
                    surfaceVariant = Color(0xFF34313E),
                    onSurfaceVariant = Color(0xFFD5CDDF),
                ),
            ) {
                WidgetQuickAddSheet(
                    onClose = { finish() },
                    openPrivateInbox = intent.getBooleanExtra(EXTRA_PRIVATE_INBOX, false),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }
}

private enum class QuickKind(val label: String) {
    EVENT("Aktivitet"),
    REMINDER("Påminnelse"),
    TODO("To-do"),
    SHOPPING("Inköp"),
}

private val QuickAddText = Color(0xFFF8F7FD)
private val QuickAddMuted = Color(0xFFB4B4C8)

@Composable
private fun QuickAddChoiceCard(
    kind: QuickKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, accent, description) = when (kind) {
        QuickKind.EVENT -> Triple(Icons.Default.CalendarMonth, Color(0xFFB69AFF), "Planera en tid")
        QuickKind.REMINDER -> Triple(Icons.Default.Notifications, Color(0xFFF1BC86), "Kom ihåg något")
        QuickKind.TODO -> Triple(Icons.Default.CheckCircle, Color(0xFF83D6BD), "En uppgift")
        QuickKind.SHOPPING -> Triple(Icons.Default.ShoppingCart, Color(0xFF91BAFF), "Handla något")
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(106.dp),
        shape = RoundedCornerShape(19.dp),
        color = Color(0xFF2A2B38),
        contentColor = QuickAddText,
        border = BorderStroke(1.dp, accent.copy(alpha = .38f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(35.dp).background(
                    accent.copy(alpha = .17f),
                    RoundedCornerShape(11.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    kind.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = QuickAddText,
                )
                Text(description, fontSize = 10.sp, color = QuickAddMuted)
            }
        }
    }
}

@Composable
private fun WidgetQuickAddSheet(
    onClose: () -> Unit,
    openPrivateInbox: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences("family_calendar", android.content.Context.MODE_PRIVATE)
    }
    val session = remember(prefs) {
        val id = prefs.getString("family_id", null)
        val code = prefs.getString("family_code", null)
        if (id.isNullOrBlank() || code.isNullOrBlank()) null
        else FamilySession(id, prefs.getString("family_name", "Min familj") ?: "Min familj", code)
    }

    var members by remember { mutableStateOf(emptyList<SyncMember>()) }
    LaunchedEffect(session?.id) {
        if (session != null) {
            members = try {
                SupabaseSync.loadMembers(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                withContext(Dispatchers.IO) {
                    FamilyOfflineCache.loadMembers(context, session).orEmpty()
                }
            }.filter { it.id != ALL_FAMILY_MEMBER_ID }
        }
    }

    var kind by remember { mutableStateOf<QuickKind?>(null) }
    var showPrivateInbox by remember { mutableStateOf(openPrivateInbox) }
    var privateOnly by remember { mutableStateOf(false) }
    var privateInbox by remember { mutableStateOf(emptyList<PrivateReminder>()) }
    var privateInboxLoading by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var hasTime by remember { mutableStateOf(true) }
    var keepUntilRemoved by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var memberMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("sv", "SE")) }
    LaunchedEffect(showPrivateInbox, session?.id) {
        if (showPrivateInbox && session != null) {
            privateInboxLoading = true
            error = ""
            try {
                privateInbox = PrivateReminderSync.refreshInbox(context, session)
            } catch (failure: Exception) {
                error = "Kunde inte hämta privata påminnelser: " +
                    (failure.message ?: "okänt fel")
                privateInbox = PrivateReminderSync.cached(context, session)
            } finally {
                privateInboxLoading = false
            }
        }
    }

    fun openMode(selected: QuickKind) {
        kind = selected
        title = ""
        date = LocalDate.now()
        hasTime = selected == QuickKind.EVENT
        keepUntilRemoved = false
        privateOnly = false
        startTime = LocalTime.of(9, 0)
        endTime = LocalTime.of(10, 0)
        selectedMemberId = null
        error = ""
    }

    BackHandler(enabled = (kind != null || showPrivateInbox) && !saving) {
        if (showPrivateInbox) showPrivateInbox = false else kind = null
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 660.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF20212D),
            contentColor = QuickAddText,
            border = BorderStroke(1.dp, Color(0xFF686078).copy(alpha = .72f)),
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A283A), Color(0xFF20212D)),
                        ),
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(43.dp).background(
                            Brush.linearGradient(
                                listOf(Color(0xFF9F67EF), Color(0xFF6338A7)),
                            ),
                            RoundedCornerShape(14.dp),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (showPrivateInbox) "Privata påminnelser"
                            else if (kind == null) "Lägg till" else kind!!.label,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = QuickAddText,
                        )
                        Text(
                            if (showPrivateInbox) "Endast på den här enheten"
                            else if (kind == null) "Vad vill du skapa?"
                            else "Direkt från hemskärmen",
                            fontSize = 11.sp,
                            color = QuickAddMuted,
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        enabled = !saving,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stäng",
                            tint = QuickAddMuted,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                if (session == null) {
                    Text(
                        "Anslut till en familj i appen innan du lägger till saker från widgeten.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (showPrivateInbox) {
                    if (privateInboxLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    val visible = PrivateReminderSync.visibleThisWeek(privateInbox)
                    if (!privateInboxLoading && visible.isEmpty()) {
                        Text("Inga privata påminnelser denna vecka.",
                            fontSize = 13.sp, color = QuickAddMuted)
                    }
                    visible.forEach { privateItem ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF302B43),
                            border = BorderStroke(1.dp, Color(0xFF755A9C)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(privateItem.text, fontWeight = FontWeight.SemiBold,
                                        color = QuickAddText)
                                    Text(
                                        (if (privateItem.from.isNotBlank())
                                            "Från " + privateItem.from + " · " else "") +
                                            (if (privateItem.permanent) "Utan slutdatum"
                                            else privateItem.date.toString()),
                                        fontSize = 11.sp, color = QuickAddMuted,
                                    )
                                }
                                TextButton(
                                    enabled = !saving,
                                    onClick = {
                                        if (session != null) {
                                            saving = true
                                            scope.launch {
                                                try {
                                                    PrivateReminderSync.delete(
                                                        context, session, privateItem.id,
                                                    )
                                                    privateInbox = privateInbox.filterNot {
                                                        it.id == privateItem.id
                                                    }
                                                    FamilyCalendarWidget.enqueueRefresh(context)
                                                } catch (failure: Exception) {
                                                    error = "Kunde inte ta bort: " +
                                                        (failure.message ?: "okänt fel")
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        }
                                    },
                                ) { Text("Ta bort") }
                            }
                        }
                    }
                    TextButton(onClick = { showPrivateInbox = false }, enabled = !saving) {
                        Text("Tillbaka")
                    }
                } else if (kind == null) {
                    listOf(
                        listOf(QuickKind.EVENT, QuickKind.REMINDER),
                        listOf(QuickKind.TODO, QuickKind.SHOPPING),
                    ).forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            group.forEach { option ->
                                QuickAddChoiceCard(
                                    kind = option,
                                    onClick = { openMode(option) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showPrivateInbox = true },
                        border = BorderStroke(1.dp, Color(0xFF8D77B5)),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Text("  Mina privata påminnelser", fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF84D1B5),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "  Synkas med familjen",
                            color = QuickAddMuted,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    val selectedKind = kind!!
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                when (selectedKind) {
                                    QuickKind.EVENT -> "Vad ska hända?"
                                    QuickKind.REMINDER -> "Vad vill du bli påmind om?"
                                    QuickKind.TODO -> "Vad behöver göras?"
                                    QuickKind.SHOPPING -> "Vad ska handlas?"
                                }
                            )
                        },
                        singleLine = true,
                        enabled = !saving,
                    )

                    if (selectedKind == QuickKind.EVENT || selectedKind == QuickKind.REMINDER) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving,
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                                    date.year, date.monthValue - 1, date.dayOfMonth,
                                ).show()
                            },
                        ) {
                            Text("Datum: ${date.format(dateFormatter)}")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = hasTime,
                                onCheckedChange = { hasTime = it },
                                enabled = !saving,
                            )
                            Text("Ange tid")
                        }
                        if (hasTime) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    enabled = !saving,
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, h, m -> startTime = LocalTime.of(h, m) },
                                            startTime.hour, startTime.minute, true,
                                        ).show()
                                    },
                                ) {
                                    Text(
                                        if (selectedKind == QuickKind.REMINDER) {
                                            "Tid ${startTime.format(timeFormatter)}"
                                        } else "Från ${startTime.format(timeFormatter)}",
                                        fontSize = 12.sp,
                                    )
                                }
                                if (selectedKind == QuickKind.EVENT) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        enabled = !saving,
                                        onClick = {
                                            TimePickerDialog(
                                                context,
                                                { _, h, m -> endTime = LocalTime.of(h, m) },
                                                endTime.hour, endTime.minute, true,
                                            ).show()
                                        },
                                    ) { Text("Till ${endTime.format(timeFormatter)}", fontSize = 12.sp) }
                                }
                            }
                        }
                        if (selectedKind == QuickKind.REMINDER) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = privateOnly,
                                    onCheckedChange = { privateOnly = it },
                                    enabled = !saving,
                                )
                                Column {
                                    Text("Privat – bara mottagaren", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Endast på den valda personens telefon",
                                        color = QuickAddMuted, fontSize = 11.sp,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = keepUntilRemoved,
                                    onCheckedChange = { keepUntilRemoved = it },
                                    enabled = !saving,
                                )
                                Column {
                                    Text("Utan slutdatum", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Visas tills du tar bort påminnelsen",
                                        color = QuickAddMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }

                    if (selectedKind != QuickKind.SHOPPING) {
                        Box {
                            OutlinedButton(
                                onClick = { memberMenuExpanded = true },
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Ansvarig: " + (
                                        members.firstOrNull { it.id == selectedMemberId }?.name
                                            ?: "Hela familjen"
                                    )
                                )
                            }
                            DropdownMenu(
                                expanded = memberMenuExpanded,
                                onDismissRequest = { memberMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Hela familjen") },
                                    onClick = {
                                        selectedMemberId = null
                                        memberMenuExpanded = false
                                    },
                                )
                                members.forEach { person ->
                                    DropdownMenuItem(
                                        text = { Text(person.name) },
                                        onClick = {
                                            selectedMemberId = person.id
                                            memberMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    val invalidRange = selectedKind == QuickKind.EVENT && hasTime &&
                        !endTime.isAfter(startTime)
                    val missingPrivateReceiver = selectedKind == QuickKind.REMINDER &&
                        privateOnly && selectedMemberId.isNullOrBlank()
                    if (missingPrivateReceiver) {
                        Text(
                            "Välj vem som ska få den privata påminnelsen.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                    if (invalidRange) {
                        Text(
                            "Sluttiden måste vara senare än starttiden.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                        )
                    }
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = !saving,
                            onClick = { kind = null },
                        ) { Text("Tillbaka") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !saving && title.isNotBlank() && !invalidRange &&
                                !missingPrivateReceiver,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9458E9),
                                contentColor = Color.White,
                            ),
                            onClick = {
                                val sessionToUse = session
                                val cleanTitle = title.trim()
                                if (sessionToUse == null || cleanTitle.isBlank()) return@Button
                                saving = true
                                error = ""
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            when (selectedKind) {
                                                QuickKind.EVENT -> SupabaseSync.addEvent(
                                                    sessionToUse,
                                                    cleanTitle,
                                                    date,
                                                    if (hasTime) startTime.format(timeFormatter) else "",
                                                    if (hasTime) endTime.format(timeFormatter) else null,
                                                    selectedMemberId,
                                                )
                                                QuickKind.REMINDER -> {
                                                    val selectedTime = if (hasTime)
                                                        startTime.format(timeFormatter) else ""
                                                    if (privateOnly) {
                                                        PrivateReminderSync.send(
                                                            context, sessionToUse,
                                                            selectedMemberId
                                                                ?: error("Välj mottagare"),
                                                            cleanTitle, date, selectedTime,
                                                            keepUntilRemoved,
                                                        )
                                                    } else {
                                                        SupabaseSync.addReminder(
                                                            sessionToUse, cleanTitle, date,
                                                            selectedTime, selectedMemberId,
                                                            keepUntilRemoved = keepUntilRemoved,
                                                        )
                                                    }
                                                }
                                                QuickKind.TODO -> {
                                                    val id = TodoSync.add(
                                                        sessionToUse, cleanTitle, selectedMemberId,
                                                    )
                                                    runCatching {
                                                        TodoSync.notifyAssigned(
                                                            sessionToUse, id, cleanTitle,
                                                            selectedMemberId,
                                                        )
                                                    }
                                                }
                                                QuickKind.SHOPPING -> SupabaseSync.addShopping(
                                                    sessionToUse,
                                                    cleanTitle,
                                                    prefs.getString("push_device_id", null),
                                                )
                                            }
                                        }
                                        FamilyCalendarWidget.enqueueRefresh(context)
                                        Toast.makeText(
                                            context, if (privateOnly &&
                                                selectedKind == QuickKind.REMINDER)
                                                "Privat påminnelse skickad"
                                            else selectedKind.label + " sparad",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        onClose()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        error = "Kunde inte spara: ${failure.message ?: "okänt fel"}"
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                        ) {
                            if (saving) CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(2.dp).heightIn(max = 18.dp),
                            )
                            else Text("Spara")
                        }
                    }
                }
            }
        }
    }
}
