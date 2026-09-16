from pathlib import Path

ROOT = Path('.')
APP = ROOT / 'app/src/main/java/se/familjekalender/app'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'Missing patch marker: {label}')
    return text.replace(old, new, 1)

# Google authorization dependencies.
build_path = ROOT / 'app/build.gradle.kts'
build = build_path.read_text()
if 'com.google.android.gms:play-services-auth:21.6.0' not in build:
    build = replace_once(
        build,
        '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")\n',
        '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")\n'
        '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")\n'
        '    implementation("com.google.android.gms:play-services-auth:21.6.0")\n',
        'Google authorization dependencies',
    )
build_path.write_text(build)

# Make the IMAP engine obtain fresh Google OAuth tokens when a Gmail account
# is represented by an empty password. Existing password based accounts remain
# fully backwards compatible.
sync_path = APP / 'MailSync.kt'
sync = sync_path.read_text()

if 'import android.accounts.Account\n' not in sync:
    sync = replace_once(sync, 'import android.content.Context\n', 'import android.accounts.Account\nimport android.content.Context\n', 'Account import')
if 'import com.google.android.gms.auth.api.identity.AuthorizationRequest\n' not in sync:
    sync = replace_once(
        sync,
        'import androidx.work.WorkerParameters\n',
        'import androidx.work.WorkerParameters\n'
        'import com.google.android.gms.auth.api.identity.AuthorizationRequest\n'
        'import com.google.android.gms.auth.api.identity.AuthorizationResult\n'
        'import com.google.android.gms.auth.api.identity.Identity\n'
        'import com.google.android.gms.auth.api.identity.RevokeAccessRequest\n'
        'import com.google.android.gms.common.api.Scope\n',
        'Google auth imports',
    )
if 'import kotlinx.coroutines.tasks.await\n' not in sync:
    sync = replace_once(sync, 'import kotlinx.coroutines.withContext\n', 'import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.tasks.await\n', 'Task await import')

if 'internal const val GMAIL_AUTH_SCOPE' not in sync:
    sync = replace_once(
        sync,
        'private val MAIL_STOCKHOLM: ZoneId = ZoneId.of("Europe/Stockholm")\n',
        '''private val MAIL_STOCKHOLM: ZoneId = ZoneId.of("Europe/Stockholm")
internal const val GMAIL_AUTH_SCOPE = "https://mail.google.com/"
private const val GOOGLE_ACCOUNT_TYPE = "com.google"

internal fun gmailAuthorizationRequest(email: String? = null): AuthorizationRequest {
    val builder = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(GMAIL_AUTH_SCOPE), Scope("email")))
    if (!email.isNullOrBlank()) {
        builder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
    }
    return builder.build()
}

internal suspend fun requestGmailAuthorization(context: Context, email: String? = null): AuthorizationResult =
    Identity.getAuthorizationClient(context).authorize(gmailAuthorizationRequest(email)).await()

internal suspend fun revokeGmailAuthorization(context: Context, email: String) {
    val request = RevokeAccessRequest.builder()
        .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
        .setScopes(listOf(Scope(GMAIL_AUTH_SCOPE), Scope("email")))
        .build()
    Identity.getAuthorizationClient(context).revokeAccess(request).await()
}
''',
        'Gmail authorization helpers',
    )

sync = sync.replace(
    '    suspend fun testAccount(account: MailAccount) = withContext(Dispatchers.IO) {\n        openStore(account).use { connection ->',
    '    suspend fun testAccount(context: Context, account: MailAccount) = withContext(Dispatchers.IO) {\n        openStore(context, account).use { connection ->',
)
sync = sync.replace('        openStore(account).use { connection ->', '        openStore(context, account).use { connection ->')

old_open = '''    private fun openStore(account: MailAccount): StoreConnection {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", account.host)
            put("mail.imaps.port", account.port.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "25000")
            put("mail.imaps.writetimeout", "25000")
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(account.host, account.port, account.username.ifBlank { account.email }, account.password)
        return StoreConnection(store)
    }
'''
new_open = '''    private suspend fun openStore(context: Context, account: MailAccount): StoreConnection {
        val googleOauth = account.host.equals("imap.gmail.com", ignoreCase = true) && account.password.isBlank()
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", account.host)
            put("mail.imaps.port", account.port.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "25000")
            put("mail.imaps.writetimeout", "25000")
            if (googleOauth) {
                put("mail.imaps.auth.mechanisms", "XOAUTH2")
                put("mail.imaps.auth.login.disable", "true")
                put("mail.imaps.auth.plain.disable", "true")
            }
        }
        val credential = if (googleOauth) {
            val authorization = requestGmailAuthorization(context, account.email)
            if (authorization.hasResolution()) {
                error("Gmail-behörigheten behöver förnyas under Inställningar > Mailkoppling")
            }
            authorization.accessToken ?: error("Google returnerade ingen åtkomsttoken")
        } else {
            account.password
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(account.host, account.port, account.username.ifBlank { account.email }, credential)
        return StoreConnection(store)
    }
'''
if old_open in sync:
    sync = sync.replace(old_open, new_open, 1)
elif 'private suspend fun openStore(context: Context, account: MailAccount)' not in sync:
    raise SystemExit('Missing patch marker: openStore')

sync_path.write_text(sync)

# Replace the settings card with a direct Google authorization button. Manual
# IMAP remains available for iCloud/Yahoo/other providers, but Gmail no longer
# asks the user for an app password.
settings_path = APP / 'MailSettingsCard.kt'
settings_path.write_text(r'''package se.familjekalender.app

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

    fun finishGmailAuthorization(result: AuthorizationResult) {
        val email = result.toGoogleSignInAccount()?.email.orEmpty().trim()
        if (email.isBlank()) {
            syncing = false
            status = "Google-kontot saknar en tillgänglig mailadress."
            return
        }
        if (result.accessToken.isNullOrBlank()) {
            syncing = false
            status = "Google gav ingen Gmail-behörighet. Försök igen."
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
            runCatching { MailSyncEngine.testAccount(context, account) }
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
            syncing = false
        }
    }

    val gmailAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val data = activityResult.data
        if (activityResult.resultCode != Activity.RESULT_OK || data == null) {
            syncing = false
            status = "Gmail-kopplingen avbröts."
        } else {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }
                .onSuccess(::finishGmailAuthorization)
                .onFailure {
                    syncing = false
                    status = "Kunde inte slutföra Google-inloggningen: ${it.message ?: "okänt fel"}"
                }
        }
    }

    fun startGmailAuthorization() {
        scope.launch {
            syncing = true
            status = "Öppnar Google…"
            runCatching { requestGmailAuthorization(context) }
                .onSuccess { authorization ->
                    if (authorization.hasResolution()) {
                        val pendingIntent = authorization.pendingIntent
                        if (pendingIntent == null) {
                            syncing = false
                            status = "Google kunde inte öppna kontoväljaren."
                        } else {
                            syncing = false
                            gmailAuthorizationLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        }
                    } else {
                        finishGmailAuthorization(authorization)
                    }
                }
                .onFailure {
                    syncing = false
                    status = "Kunde inte öppna Google-inloggningen: ${it.message ?: "okänt fel"}"
                }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = CardBg), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Mailkoppling", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Koppla Gmail med Google. Kalenderinbjudningar från mail läggs in automatiskt och erbjudanden kan matchas mot inköpslistan.",
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
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (syncing) "Arbetar…" else "Koppla Gmail") }

            OutlinedButton(
                enabled = !syncing,
                onClick = { showAddOther = true },
                modifier = Modifier.fillMaxWidth()
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

            if (status.isNotBlank()) Text(status, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Gmail använder Googles behörighetsflöde och inget Gmail-lösenord sparas i appen. Automatisk kontroll körs var 30:e minut.",
                color = Muted,
                fontSize = 11.sp
            )
            Text(
                "För iCloud, Yahoo och annan IMAP krypteras kontouppgifterna med Android Keystore och sparas bara på den här telefonen.",
                color = Muted,
                fontSize = 11.sp
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
''')

print('Gmail one-tap authorization patch applied.')
