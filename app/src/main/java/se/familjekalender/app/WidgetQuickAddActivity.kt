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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
                WidgetQuickAddSheet(onClose = { finish() })
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

@Composable
private fun WidgetQuickAddSheet(onClose: () -> Unit) {
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
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var hasTime by remember { mutableStateOf(true) }
    var startTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var endTime by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var memberMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("sv", "SE")) }

    fun openMode(selected: QuickKind) {
        kind = selected
        title = ""
        date = LocalDate.now()
        hasTime = selected == QuickKind.EVENT
        startTime = LocalTime.of(9, 0)
        endTime = LocalTime.of(10, 0)
        selectedMemberId = null
        error = ""
    }

    BackHandler(enabled = kind != null && !saving) { kind = null }

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 660.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF282630),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (kind == null) "Lägg till" else kind!!.label,
                        modifier = Modifier.weight(1f),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onClose, enabled = !saving) { Text("Stäng") }
                }

                if (session == null) {
                    Text(
                        "Anslut till en familj i appen innan du lägger till saker från widgeten.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (kind == null) {
                    Text(
                        "Skapa direkt från hemskärmen utan att öppna kalendern.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        listOf(QuickKind.EVENT, QuickKind.REMINDER),
                        listOf(QuickKind.TODO, QuickKind.SHOPPING),
                    ).forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            group.forEach { option ->
                                OutlinedButton(
                                    onClick = { openMode(option) },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, Color(0xFF78658F)),
                                ) {
                                    Text(option.label, fontSize = 13.sp)
                                }
                            }
                        }
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
                            enabled = !saving && title.isNotBlank() && !invalidRange,
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
                                                QuickKind.REMINDER -> SupabaseSync.addReminder(
                                                    sessionToUse,
                                                    cleanTitle,
                                                    date,
                                                    if (hasTime) startTime.format(timeFormatter) else "",
                                                    selectedMemberId,
                                                )
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
                                            context, "${selectedKind.label} sparad",
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
