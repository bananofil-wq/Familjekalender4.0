package se.familjekalender.app

import androidx.compose.material3.MaterialTheme
import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import kotlinx.coroutines.launch

private data class MailProviderPreset(val name: String, val host: String, val hint: String)

private val MAIL_PRESETS = listOf(
    MailProviderPreset("iCloud", "imap.mail.me.com", "Använd ett appspecifikt lösenord från Apple-ID."),
    MailProviderPreset("Yahoo", "imap.mail.yahoo.com", "Använd ett app-lösenord från Yahoo."),
    MailProviderPreset("Annan IMAP", "", "Ange IMAP-server och lösenord/app-lösenord.")
)

@Composable
internal fun MailSettingsCard(session: FamilySession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(MailAccountStore.load(context)) }
    var showAddOther by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var pendingGmailEmail by remember { mutableStateOf<String?>(null) }

    fun finishGmailAuthorization(result: AuthorizationResult, preferredEmail: String? = pendingGmailEmail) {
        val email = preferredEmail?.trim().orEmpty()
            .ifBlank { result.toGoogleSignInAccount()?.email.orEmpty().trim() }
        val accessToken = result.accessToken.orEmpty()
        val gmailGranted = result.grantedScopes.any { it == GMAIL_AUTH_SCOPE }
        if (email.isBlank()) {
            syncing = false
            pendingGmailEmail = null
            status = "Google-kontot saknar en tillgänglig mailadress."
            return
        }
        if (!gmailGranted || accessToken.isBlank()) {
            syncing = false
            pendingGmailEmail = null
            status = "Gmail-behörigheten blev inte godkänd. Tryck på Koppla Gmail och godkänn åtkomst till Gmail."
            return
        }
        val account = MailAccount(
            label = "Gmail",
            email = email,
            host = "imap.gmail.com",
            port = 993,
            username = email,
            password = ""
        )
        scope.launch {
            syncing = true
            status = "Kontrollerar Gmail…"
            runCatching { MailSyncEngine.testAccount(context, account, accessToken) }
                .onSuccess {
                    accounts = accounts.filterNot {
                        it.host.equals("imap.gmail.com", ignoreCase = true) && it.email.equals(email, ignoreCase = true)
                    } + account
                    MailAccountStore.save(context, accounts)
                    MailSyncScheduler.schedule(context)
                    val summary = MailSyncEngine.syncAll(context, session)
                    status = "Gmail är kopplad. ${summary.events} kalenderinbjudningar och ${summary.offers} erbjudanden hittades."
                }
                .onFailure {
                    status = "Kunde inte koppla Gmail: ${it.message ?: "Google-behörigheten misslyckades"}"
                }
            pendingGmailEmail = null
            syncing = false
        }
    }

    val gmailAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val selectedEmail = pendingGmailEmail
        val data = activityResult.data
        if (activityResult.resultCode != Activity.RESULT_OK || data == null) {
            syncing = false
            pendingGmailEmail = null
            status = "Google-behörigheten stängdes innan Gmail kopplades. Försök igen och välj Tillåt."
        } else {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
                .onSuccess { finishGmailAuthorization(it, selectedEmail) }
                .onFailure {
                    syncing = false
                    pendingGmailEmail = null
                    status = "Kunde inte slutföra Google-behörigheten: ${it.message ?: "okänt fel"}"
                }
        }
    }

    fun authorizeSelectedGmail(email: String) {
        scope.launch {
            syncing = true
            pendingGmailEmail = email
            status = "Begär Gmail-behörighet för $email…"
            runCatching { requestGmailAuthorization(context, email) }
                .onSuccess { authorization ->
                    if (authorization.hasResolution()) {
                        val pendingIntent = authorization.pendingIntent
                        if (pendingIntent == null) {
                            syncing = false
                            pendingGmailEmail = null
                            status = "Google kunde inte öppna behörighetsdialogen."
                        } else {
                            syncing = false
                            gmailAuthorizationLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        }
                    } else {
                        finishGmailAuthorization(authorization, email)
                    }
                }
                .onFailure {
                    syncing = false
                    pendingGmailEmail = null
                    status = "Kunde inte begära Gmail-behörighet: ${it.message ?: "okänt fel"}"
                }
        }
    }

    val gmailAccountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK || activityResult.data == null) {
            syncing = false
            pendingGmailEmail = null
            status = "Inget Google-konto valdes."
        } else {
            val email = activityResult.data
                ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?.trim()
                .orEmpty()
            if (email.isBlank()) {
                syncing = false
                pendingGmailEmail = null
                status = "Google returnerade inget konto. Försök igen."
            } else {
                authorizeSelectedGmail(email)
            }
        }
    }

    fun startGmailAuthorization() {
        syncing = true
        status = "Välj Google-konto…"
        val options = AccountPicker.AccountChooserOptions.Builder()
            .setAllowableAccountsTypes(listOf(GOOGLE_ACCOUNT_TYPE))
            .setAlwaysShowAccountPicker(true)
            .setTitleOverrideText("Välj Gmail-konto")
            .build()
        runCatching { AccountPicker.newChooseAccountIntent(options) }
            .onSuccess { gmailAccountPickerLauncher.launch(it) }
            .onFailure {
                syncing = false
                status = "Kunde inte öppna Google-kontovalet: ${it.message ?: "okänt fel"}"
            }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = LuxurySurface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("E-post", fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = LuxuryText)
                    Text("Kalenderinbjudningar och erbjudanden kan läggas in automatiskt.", color = LuxuryTextMuted, fontSize = 12.sp)
                }
                Text("AUTO", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            if (accounts.isEmpty()) {
                Text("Ingen e-post är ansluten ännu.", color = LuxuryTextMuted, fontSize = 12.sp)
            } else {
                accounts.forEach { account ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.fillMaxWidth(0.58f)) {
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
                            scope.launch {
                                val googleOauth = account.host.equals("imap.gmail.com", ignoreCase = true) && account.password.isBlank()
                                if (googleOauth) runCatching { revokeGmailAuthorization(context, account.email) }
                                accounts = accounts.filterNot { it.id == account.id }
                                MailAccountStore.save(context, accounts)
                                status = "Mailkontot är bortkopplat."
                            }
                        }) { Text("Ta bort") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = !syncing,
                onClick = ::startGmailAuthorization,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text(if (syncing) "Arbetar…" else "Koppla Gmail") }

            OutlinedButton(
                enabled = !syncing,
                onClick = { showAddOther = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Annan e-post (IMAP)") }

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

            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = LuxurySurfaceElevated),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(status, modifier = Modifier.padding(12.dp), color = LuxuryTextMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Privat och säkert", color = LuxuryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                "Google-inloggning används för Gmail och inget Gmail-lösenord sparas. Övriga IMAP-konton skyddas med Android Keystore på den här telefonen.",
                color = LuxuryTextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }

    if (showAddOther) {
        AddMailAccountDialog(
            onDismiss = { showAddOther = false },
            onSaved = { account ->
                scope.launch {
                    syncing = true
                    status = "Kontrollerar ${account.email}…"
                    runCatching { MailSyncEngine.testAccount(context, account) }
                        .onSuccess {
                            accounts = accounts + account
                            MailAccountStore.save(context, accounts)
                            MailSyncScheduler.schedule(context)
                            val summary = MailSyncEngine.syncAll(context, session)
                            status = "Kopplad. ${summary.events} kalenderinbjudningar och ${summary.offers} erbjudanden hittades."
                            showAddOther = false
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
        title = { Text("Koppla annan e-post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MAIL_PRESETS.forEach { item ->
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
