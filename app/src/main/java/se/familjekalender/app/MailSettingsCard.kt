package se.familjekalender.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class MailProviderPreset(val name: String, val host: String, val hint: String)

private val MAIL_PRESETS = listOf(
    MailProviderPreset("Gmail", "imap.gmail.com", "Använd ett app-lösenord från Google-kontot."),
    MailProviderPreset("iCloud", "imap.mail.me.com", "Använd ett appspecifikt lösenord från Apple-ID."),
    MailProviderPreset("Yahoo", "imap.mail.yahoo.com", "Använd ett app-lösenord från Yahoo."),
    MailProviderPreset("Annan IMAP", "", "Ange IMAP-server och lösenord/app-lösenord.")
)

@Composable
internal fun MailSettingsCard(session: FamilySession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(MailAccountStore.load(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Mailkoppling", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Kalenderinbjudningar från mail läggs in automatiskt. Reklam- och erbjudandemail matchas mot varor ni brukar köpa.",
                color = Muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            if (accounts.isEmpty()) {
                Text("Inga mailkonton kopplade ännu.", color = Muted, fontSize = 13.sp)
            } else {
                accounts.forEach { account ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(account.label.ifBlank { account.email }, fontWeight = FontWeight.SemiBold)
                            Text(account.email, color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked = account.enabled,
                            onCheckedChange = { enabled ->
                                accounts = accounts.map { if (it.id == account.id) it.copy(enabled = enabled) else it }
                                MailAccountStore.save(context, accounts)
                                MailSyncScheduler.schedule(context)
                            }
                        )
                        TextButton(onClick = {
                            accounts = accounts.filterNot { it.id == account.id }
                            MailAccountStore.save(context, accounts)
                        }) { Text("Ta bort") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Koppla mailkonto") }
            if (accounts.any { it.enabled }) {
                OutlinedButton(
                    enabled = !syncing,
                    onClick = {
                        scope.launch {
                            syncing = true
                            status = "Synkar mail…"
                            val summary = MailSyncEngine.syncAll(context, session)
                            status = buildString {
                                append("${summary.events} kalenderinbjudningar och ${summary.offers} erbjudanden hittades")
                                if (summary.errors.isNotEmpty()) append(". ${summary.errors.joinToString(" · ")}")
                            }
                            syncing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (syncing) "Synkar…" else "Synka mail nu") }
            }
            if (status.isNotBlank()) Text(status, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Kontouppgifterna krypteras med Android Keystore och sparas bara på den här telefonen. Automatisk kontroll körs var 30:e minut.",
                color = Muted,
                fontSize = 11.sp
            )
            Text(
                "Microsoft/Outlook kräver OAuth och stöds därför inte med vanligt IMAP-lösenord i denna första version.",
                color = Muted,
                fontSize = 11.sp
            )
        }
    }

    if (showAdd) {
        AddMailAccountDialog(
            onDismiss = { showAdd = false },
            onSaved = { account ->
                scope.launch {
                    syncing = true
                    status = "Kontrollerar ${account.email}…"
                    runCatching { MailSyncEngine.testAccount(account) }
                        .onSuccess {
                            accounts = accounts + account
                            MailAccountStore.save(context, accounts)
                            MailSyncScheduler.schedule(context)
                            val summary = MailSyncEngine.syncAll(context, session)
                            status = "Kopplad. ${summary.events} kalenderinbjudningar och ${summary.offers} erbjudanden hittades."
                            showAdd = false
                        }
                        .onFailure { status = "Kunde inte ansluta: ${it.message ?: "kontrollera IMAP och app-lösenord"}" }
                    syncing = false
                }
            }
        )
    }
}

@Composable
private fun AddMailAccountDialog(onDismiss: () -> Unit, onSaved: (MailAccount) -> Unit) {
    var preset by remember { mutableStateOf(MAIL_PRESETS.first()) }
    var label by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(preset.host) }
    var port by remember { mutableStateOf("993") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Koppla mailkonto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MAIL_PRESETS.take(2).forEach { item ->
                        FilterChip(
                            selected = preset == item,
                            onClick = { preset = item; host = item.host },
                            label = { Text(item.name) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MAIL_PRESETS.drop(2).forEach { item ->
                        FilterChip(
                            selected = preset == item,
                            onClick = { preset = item; host = item.host },
                            label = { Text(item.name) }
                        )
                    }
                }
                Text(preset.hint, color = Muted, fontSize = 11.sp)
                OutlinedTextField(label, { label = it }, label = { Text("Namn, t.ex. Privat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it; if (username.isBlank()) username = it }, label = { Text("Mailadress") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (preset.name == "Annan IMAP") {
                    OutlinedTextField(host, { host = it }, label = { Text("IMAP-server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(username, { username = it }, label = { Text("Användarnamn") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("App-lösenord / IMAP-lösenord") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaved(
                        MailAccount(
                            label = label.trim().ifBlank { email.trim() },
                            email = email.trim(),
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 993,
                            username = username.trim().ifBlank { email.trim() },
                            password = password
                        )
                    )
                },
                enabled = email.isNotBlank() && host.isNotBlank() && password.isNotBlank()
            ) { Text("Testa och koppla") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Avbryt") } }
    )
}
