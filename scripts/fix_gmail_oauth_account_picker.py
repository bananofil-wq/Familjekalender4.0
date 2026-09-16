from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/se/familjekalender/app"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new in text:
            return text
        raise SystemExit(f"Missing patch marker: {label}")
    return text.replace(old, new, 1)

# MailSync: request only the Gmail scope, expose the Google account type to the
# account picker, and allow the just-issued access token to be used directly
# for the connection test instead of immediately starting a second auth call.
sync_path = src / "MailSync.kt"
sync = sync_path.read_text(encoding="utf-8")
sync = replace_once(
    sync,
    'private const val GOOGLE_ACCOUNT_TYPE = "com.google"',
    'internal const val GOOGLE_ACCOUNT_TYPE = "com.google"',
    "Google account type visibility",
)
sync = sync.replace(
    '.setRequestedScopes(listOf(Scope(GMAIL_AUTH_SCOPE), Scope("email")))',
    '.setRequestedScopes(listOf(Scope(GMAIL_AUTH_SCOPE)))',
)
sync = sync.replace(
    '.setScopes(listOf(Scope(GMAIL_AUTH_SCOPE), Scope("email")))',
    '.setScopes(listOf(Scope(GMAIL_AUTH_SCOPE)))',
)
sync = replace_once(
    sync,
    '''    suspend fun testAccount(context: Context, account: MailAccount) = withContext(Dispatchers.IO) {
        openStore(context, account).use { connection ->''',
    '''    suspend fun testAccount(
        context: Context,
        account: MailAccount,
        accessTokenOverride: String? = null
    ) = withContext(Dispatchers.IO) {
        openStore(context, account, accessTokenOverride).use { connection ->''',
    "testAccount token override",
)
sync = replace_once(
    sync,
    '    private suspend fun openStore(context: Context, account: MailAccount): StoreConnection {',
    '    private suspend fun openStore(context: Context, account: MailAccount, accessTokenOverride: String? = null): StoreConnection {',
    "openStore token override",
)
sync = replace_once(
    sync,
    '''        val credential = if (googleOauth) {
            val authorization = requestGmailAuthorization(context, account.email)
            if (authorization.hasResolution()) {
                error("Gmail-behörigheten behöver förnyas under Inställningar > Mailkoppling")
            }
            authorization.accessToken ?: error("Google returnerade ingen åtkomsttoken")
        } else {
            account.password
        }''',
    '''        val credential = if (googleOauth) {
            accessTokenOverride?.takeIf { it.isNotBlank() } ?: run {
                val authorization = requestGmailAuthorization(context, account.email)
                if (authorization.hasResolution()) {
                    error("Gmail-behörigheten behöver förnyas under Inställningar > E-post")
                }
                authorization.accessToken ?: error("Google returnerade ingen åtkomsttoken")
            }
        } else {
            account.password
        }''',
    "openStore credential",
)
sync_path.write_text(sync, encoding="utf-8")

# MailSettingsCard: pick the Google account first. This gives us the selected
# address independently of deprecated GoogleSignInAccount, then authorize the
# Gmail scope for exactly that account.
settings_path = src / "MailSettingsCard.kt"
settings = settings_path.read_text(encoding="utf-8")
if "import android.accounts.AccountManager\n" not in settings:
    settings = settings.replace(
        "import androidx.compose.material3.MaterialTheme\n",
        "import androidx.compose.material3.MaterialTheme\nimport android.accounts.AccountManager\n",
        1,
    )
if "import com.google.android.gms.common.AccountPicker\n" not in settings:
    settings = settings.replace(
        "import com.google.android.gms.auth.api.identity.Identity\n",
        "import com.google.android.gms.auth.api.identity.Identity\nimport com.google.android.gms.common.AccountPicker\n",
        1,
    )
settings = replace_once(
    settings,
    '    var syncing by remember { mutableStateOf(false) }\n',
    '    var syncing by remember { mutableStateOf(false) }\n    var pendingGmailEmail by remember { mutableStateOf<String?>(null) }\n',
    "pending Gmail account",
)
start = settings.index("    fun finishGmailAuthorization(result: AuthorizationResult) {")
end = settings.index("\n    Card(\n", start)
new_auth = r'''    fun finishGmailAuthorization(result: AuthorizationResult, preferredEmail: String? = pendingGmailEmail) {
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
'''
settings = settings[:start] + new_auth + settings[end:]
settings_path.write_text(settings, encoding="utf-8")

print("Gmail OAuth account picker fix applied")
