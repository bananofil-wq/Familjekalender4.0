package se.familjekalender.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val MAIN_PREFS = "family_calendar"
private const val LOCATION_PREFS_IDENTITY = "family_calendar_location"
private const val DEVICE_MEMBER_KEY = "device_member_id"
private const val IDENTITY_CONFIRMATION_VERSION = "20260916_v2"

private fun identityConfirmationKey(sessionId: String) =
    "identity_prompt_confirmed_${IDENTITY_CONFIRMATION_VERSION}_$sessionId"

@Composable
fun FirstRunIdentityGate(
    session: FamilySession,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainPrefs = remember { context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE) }
    val locationPrefs = remember {
        context.getSharedPreferences(LOCATION_PREFS_IDENTITY, Context.MODE_PRIVATE)
    }
    val confirmationKey = remember(session.id) { identityConfirmationKey(session.id) }

    var members by remember(session.id) { mutableStateOf<List<SyncMember>>(emptyList()) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var error by remember(session.id) { mutableStateOf("") }
    var selectedMemberId by
    remember(session.id) {
        mutableStateOf(
            mainPrefs.getString(DEVICE_MEMBER_KEY, null)
                ?: locationPrefs.getString(DEVICE_MEMBER_KEY, null)
        )
    }
    var identityConfirmed by
    remember(session.id) {
        mutableStateOf(mainPrefs.getBoolean(confirmationKey, false))
    }

    fun persistIdentity(memberId: String, confirmedByUser: Boolean = false) {
        selectedMemberId = memberId
        mainPrefs.edit().putString(DEVICE_MEMBER_KEY, memberId).apply()
        locationPrefs.edit().putString(DEVICE_MEMBER_KEY, memberId).apply()
        if (confirmedByUser) {
            identityConfirmed = true
            mainPrefs.edit().putBoolean(confirmationKey, true).apply()
        }
    }

    suspend fun reloadMembers() {
        loading = true
        runCatching { SupabaseSync.loadMembers(session).filter { it.id != ALL_FAMILY_MEMBER_ID } }
            .onSuccess { loaded ->
                members = loaded
                val saved = selectedMemberId
                if (saved != null && loaded.any { it.id == saved }) {
                    // Migrate an identity that may previously only have been saved by GPS.
                    persistIdentity(saved)
                } else if (saved != null) {
                    selectedMemberId = null
                    mainPrefs.edit().remove(DEVICE_MEMBER_KEY).apply()
                    locationPrefs.edit().remove(DEVICE_MEMBER_KEY).apply()
                }
                error = ""
            }
            .onFailure { error = it.message ?: "Kunde inte hämta familjemedlemmar" }
        loading = false
    }

    LaunchedEffect(session.id) { reloadMembers() }

    // Never render the identity prompt while the saved identity is still being validated.
    // Otherwise "Vem är du?" flashes briefly on every app launch before members finish loading.
    if (loading) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        return
    }

    val chosenIsValid =
        identityConfirmed && selectedMemberId != null && members.any { it.id == selectedMemberId }
    if (chosenIsValid) {
        content()
        return
    }

    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(22.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Vem är du?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Välj vem som använder den här telefonen. Valet sparas för hela appen, även platsdelning.",
                        color = Muted,
                    )

                    when {
                        loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Hämtar familjen…", color = Muted)
                            }
                        }

                        members.isNotEmpty() -> {
                            members.forEach { member ->
                                Card(
                                    modifier =
                                        Modifier.fillMaxWidth().clickable {
                                            persistIdentity(member.id, confirmedByUser = true)
                                        },
                                    shape = RoundedCornerShape(18.dp),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            Color(member.colorArgb).copy(alpha = .75f),
                                        ),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                Color(member.colorArgb).copy(alpha = .12f)
                                        ),
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            member.name,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (member.role.isNotBlank()) {
                                            Text(member.role, color = Muted, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        OutlinedButton(onClick = { scope.launch { reloadMembers() } }) {
                            Text("Försök igen")
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = .10f))

                    TextButton(onClick = { showCreate = !showCreate }) {
                        Text(if (showCreate) "Avbryt ny person" else "Jag finns inte i listan")
                    }

                    if (showCreate || (!loading && members.isEmpty())) {
                        Text(
                            if (members.isEmpty()) "Skapa den första personen"
                            else "Skapa min person",
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Namn") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newRole,
                            onValueChange = { newRole = it },
                            label = { Text("Roll, t.ex. mamma, pappa, barn") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    error = ""
                                    runCatching {
                                        SupabaseSync.addMember(
                                            session,
                                            newName.trim(),
                                            newRole.trim(),
                                            0xFFB47CFF,
                                        )
                                        val loaded =
                                            SupabaseSync.loadMembers(session).filter {
                                                it.id != ALL_FAMILY_MEMBER_ID
                                            }
                                        members = loaded
                                        val created =
                                            loaded.lastOrNull {
                                                it.name.equals(newName.trim(), ignoreCase = true) &&
                                                        it.role.equals(
                                                            newRole.trim(),
                                                            ignoreCase = true,
                                                        )
                                            }
                                                ?: loaded.lastOrNull {
                                                    it.name.equals(
                                                        newName.trim(),
                                                        ignoreCase = true,
                                                    )
                                                }
                                                ?: error("Kunde inte hitta den skapade personen")
                                        persistIdentity(created.id, confirmedByUser = true)
                                    }
                                        .onFailure {
                                            error = it.message ?: "Kunde inte skapa personen"
                                        }
                                    saving = false
                                }
                            },
                            enabled = !saving && newName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (saving) "Sparar…" else "Skapa och välj mig")
                        }
                    }
                }
            }
        }
    }
}
