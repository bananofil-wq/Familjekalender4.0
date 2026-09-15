from pathlib import Path

ROOT = Path('.')
APP = ROOT / 'app/src/main/java/se/familjekalender/app'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'Missing patch marker: {label}')
    return text.replace(old, new, 1)

# 1) Dependencies
build_path = ROOT / 'app/build.gradle.kts'
build = build_path.read_text()
mail_deps = '''    implementation("com.sun.mail:android-mail:1.6.8")\n    implementation("com.sun.mail:android-activation:1.6.8")\n'''
if 'com.sun.mail:android-mail' not in build:
    build = replace_once(
        build,
        '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")\n',
        '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")\n' + mail_deps,
        'mail dependencies',
    )
build_path.write_text(build)

# 2) Supabase REST helpers
sync_path = APP / 'SupabaseSync.kt'
sync = sync_path.read_text()
if 'data class MailOffer(' not in sync:
    sync = replace_once(
        sync,
        'data class SyncShoppingItem(val id: String, val name: String, val checked: Boolean)\n',
        '''data class SyncShoppingItem(val id: String, val name: String, val checked: Boolean)\n\ndata class MailOffer(\n    val id: String,\n    val store: String,\n    val productName: String,\n    val normalizedProduct: String,\n    val price: Double,\n    val unitText: String?,\n    val validUntil: LocalDate?,\n    val sourceSubject: String?,\n    val createdAt: String?\n)\n''',
        'MailOffer data class',
    )

old_toggle = '''    suspend fun toggleShopping(session: FamilySession, item: SyncShoppingItem) = withContext(Dispatchers.IO) {\n        val body = JSONObject().put("checked", !item.checked).put("updated_at", OffsetDateTime.now().toString())\n        request("PATCH", "/rest/v1/shopping_items?id=eq.${item.id}", body, session.code, preferRepresentation = false)\n    }\n'''
new_toggle = '''    suspend fun toggleShopping(session: FamilySession, item: SyncShoppingItem) = withContext(Dispatchers.IO) {\n        val body = JSONObject().put("checked", !item.checked).put("updated_at", OffsetDateTime.now().toString())\n        request("PATCH", "/rest/v1/shopping_items?id=eq.${item.id}", body, session.code, preferRepresentation = false)\n        if (!item.checked) {\n            val normalized = normalizeShoppingName(item.name)\n            if (normalized.isNotBlank()) {\n                val historyBody = JSONObject()\n                    .put("p_family_id", session.id)\n                    .put("p_name", item.name.trim())\n                    .put("p_normalized", normalized)\n                request("POST", "/rest/v1/rpc/record_shopping_purchase", historyBody, session.code, preferRepresentation = false)\n            }\n        }\n    }\n'''
if 'record_shopping_purchase' not in sync:
    sync = replace_once(sync, old_toggle, new_toggle, 'shopping history hook')

mail_methods = r'''
    suspend fun loadMailOffers(session: FamilySession): List<MailOffer> = withContext(Dispatchers.IO) {
        val result = request(
            "GET",
            "/rest/v1/mail_offers?select=id,store,product_name,normalized_product,price,unit_text,valid_until,source_subject,created_at&family_id=eq.${session.id}&order=created_at.desc&limit=200",
            familyCode = session.code
        )
        val array = JSONArray(result)
        buildList {
            repeat(array.length()) {
                val row = array.getJSONObject(it)
                val validUntil = if (row.isNull("valid_until")) null else runCatching { LocalDate.parse(row.getString("valid_until")) }.getOrNull()
                if (validUntil == null || !validUntil.isBefore(LocalDate.now())) {
                    add(
                        MailOffer(
                            id = row.getString("id"),
                            store = row.optString("store", "Butik"),
                            productName = row.optString("product_name"),
                            normalizedProduct = row.optString("normalized_product"),
                            price = row.optDouble("price"),
                            unitText = row.optString("unit_text").takeIf { it.isNotBlank() && it != "null" },
                            validUntil = validUntil,
                            sourceSubject = row.optString("source_subject").takeIf { it.isNotBlank() && it != "null" },
                            createdAt = row.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
        }
    }

    suspend fun loadShoppingInterestTerms(session: FamilySession): List<String> = withContext(Dispatchers.IO) {
        val names = linkedSetOf<String>()
        runCatching {
            val shopping = JSONArray(
                request(
                    "GET",
                    "/rest/v1/shopping_items?select=name&family_id=eq.${session.id}&order=updated_at.desc&limit=100",
                    familyCode = session.code
                )
            )
            repeat(shopping.length()) { index ->
                shopping.getJSONObject(index).optString("name").trim().takeIf(String::isNotBlank)?.let(names::add)
            }
        }
        runCatching {
            val history = JSONArray(
                request(
                    "GET",
                    "/rest/v1/shopping_history?select=display_name,purchase_count,last_purchased_at&family_id=eq.${session.id}&order=purchase_count.desc,last_purchased_at.desc&limit=100",
                    familyCode = session.code
                )
            )
            repeat(history.length()) { index ->
                history.getJSONObject(index).optString("display_name").trim().takeIf(String::isNotBlank)?.let(names::add)
            }
        }
        names.toList()
    }

    suspend fun upsertMailOffer(
        session: FamilySession,
        store: String,
        productName: String,
        normalizedProduct: String,
        price: Double,
        unitText: String?,
        validUntil: LocalDate?,
        sourceSubject: String?,
        sourceMessageId: String
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("family_id", session.id)
            .put("store", store.take(120))
            .put("product_name", productName.take(200))
            .put("normalized_product", normalizedProduct.take(200))
            .put("price", price)
            .put("source_message_id", sourceMessageId.take(200))
        if (!unitText.isNullOrBlank()) body.put("unit_text", unitText.take(40))
        if (validUntil != null) body.put("valid_until", validUntil.toString())
        if (!sourceSubject.isNullOrBlank()) body.put("source_subject", sourceSubject.take(300))
        request(
            "POST",
            "/rest/v1/mail_offers?on_conflict=family_id,source_message_id,normalized_product,price",
            body,
            session.code,
            preferRepresentation = false,
            preferExtra = "resolution=merge-duplicates"
        )
    }

    suspend fun upsertMailEvent(
        session: FamilySession,
        externalId: String,
        title: String,
        startsAt: OffsetDateTime,
        endsAt: OffsetDateTime?,
        location: String?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("family_id", session.id)
            .put("title", title.ifBlank { "Mailinbjudan" }.take(300))
            .put("starts_at", startsAt.toString())
            .put("source", "mail")
            .put("external_id", externalId.take(200))
            .put("member_id", JSONObject.NULL)
        if (endsAt != null && endsAt.isAfter(startsAt)) body.put("ends_at", endsAt.toString())
        if (!location.isNullOrBlank()) body.put("location", location.take(400))
        request(
            "POST",
            "/rest/v1/calendar_events?on_conflict=family_id,source,external_id",
            body,
            session.code,
            preferRepresentation = false,
            preferExtra = "resolution=merge-duplicates"
        )
    }

    private fun normalizeShoppingName(value: String): String = value
        .lowercase(java.util.Locale("sv", "SE"))
        .replace(Regex("[^a-z0-9åäö]+"), " ")
        .trim()
'''
if 'suspend fun loadMailOffers' not in sync:
    marker = '    private fun valueFor(lines: List<String>, key: String): String? =\n'
    sync = replace_once(sync, marker, mail_methods + '\n' + marker, 'mail REST methods')
sync_path.write_text(sync)

# 3) Android IMAP engine + encrypted local account store
mail_sync = r'''package se.familjekalender.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.Html
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder

private val MAIL_STOCKHOLM: ZoneId = ZoneId.of("Europe/Stockholm")

data class MailAccount(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val email: String,
    val host: String,
    val port: Int = 993,
    val username: String = email,
    val password: String,
    val enabled: Boolean = true
)

data class MailSyncSummary(
    val accounts: Int,
    val events: Int,
    val offers: Int,
    val errors: List<String>
)

internal object MailAccountStore {
    private const val PREFS = "familjekalender_mail_accounts"
    private const val KEY_ALIAS = "familjekalender_mail_accounts_v1"
    private const val KEY_DATA = "accounts_ciphertext"
    private const val KEY_IV = "accounts_iv"

    fun load(context: Context): List<MailAccount> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encodedData = prefs.getString(KEY_DATA, null) ?: return emptyList()
        val encodedIv = prefs.getString(KEY_IV, null) ?: return emptyList()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP))
        )
        val json = String(cipher.doFinal(Base64.decode(encodedData, Base64.NO_WRAP)), Charsets.UTF_8)
        val array = JSONArray(json)
        buildList {
            repeat(array.length()) { index ->
                val row = array.getJSONObject(index)
                add(
                    MailAccount(
                        id = row.getString("id"),
                        label = row.optString("label").ifBlank { row.getString("email") },
                        email = row.getString("email"),
                        host = row.getString("host"),
                        port = row.optInt("port", 993),
                        username = row.optString("username").ifBlank { row.getString("email") },
                        password = row.getString("password"),
                        enabled = row.optBoolean("enabled", true)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, accounts: List<MailAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("label", account.label)
                    .put("email", account.email)
                    .put("host", account.host)
                    .put("port", account.port)
                    .put("username", account.username)
                    .put("password", account.password)
                    .put("enabled", account.enabled)
            )
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}

internal object MailSyncScheduler {
    private const val PERIODIC_NAME = "familjekalender_mail_sync"
    private const val NOW_NAME = "familjekalender_mail_sync_now"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<MailSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun syncNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<MailSyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class MailSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
        val familyId = prefs.getString("family_id", null) ?: return Result.success()
        val familyCode = prefs.getString("family_code", null) ?: return Result.success()
        val familyName = prefs.getString("family_name", "Min familj") ?: "Min familj"
        if (MailAccountStore.load(applicationContext).none { it.enabled }) return Result.success()
        val session = FamilySession(familyId, familyName, familyCode)
        val summary = runCatching { MailSyncEngine.syncAll(applicationContext, session) }
            .getOrElse { return Result.retry() }
        return if (summary.errors.isEmpty()) Result.success() else Result.success()
    }
}

internal object MailSyncEngine {
    suspend fun testAccount(account: MailAccount) = withContext(Dispatchers.IO) {
        openStore(account).use { connection ->
            val folder = connection.store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            folder.close(false)
        }
    }

    suspend fun syncAll(context: Context, family: FamilySession): MailSyncSummary = withContext(Dispatchers.IO) {
        val accounts = MailAccountStore.load(context).filter { it.enabled }
        val interests = SupabaseSync.loadShoppingInterestTerms(family)
        var events = 0
        var offers = 0
        val errors = mutableListOf<String>()
        for (account in accounts) {
            runCatching { syncAccount(context, family, account, interests) }
                .onSuccess {
                    events += it.first
                    offers += it.second
                }
                .onFailure { errors += "${account.label}: ${it.message ?: "synkfel"}" }
        }
        MailSyncSummary(accounts.size, events, offers, errors)
    }

    private suspend fun syncAccount(
        context: Context,
        family: FamilySession,
        account: MailAccount,
        interests: List<String>
    ): Pair<Int, Int> {
        var eventCount = 0
        var offerCount = 0
        val statePrefs = context.getSharedPreferences("familjekalender_mail_sync_state", Context.MODE_PRIVATE)
        val lastUid = statePrefs.getLong("uid_${account.id}", 0L)
        var maxUid = lastUid

        openStore(account).use { connection ->
            val folder = connection.store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)
            try {
                val uidFolder = folder as? UIDFolder
                val messageCount = folder.messageCount
                if (messageCount <= 0) return Pair(0, 0)
                val fromIndex = maxOf(1, messageCount - 59)
                val messages = folder.getMessages(fromIndex, messageCount)
                for (message in messages) {
                    val uid = uidFolder?.getUID(message) ?: message.messageNumber.toLong()
                    if (uid <= lastUid) continue
                    maxUid = maxOf(maxUid, uid)
                    val extracted = extractMessage(message)
                    val messageId = message.getHeader("Message-ID")?.firstOrNull()?.trim().orEmpty()
                    val sourceKey = hash("${account.id}|${messageId.ifBlank { uid.toString() }}")

                    extracted.calendars.forEachIndexed { index, calendarText ->
                        parseCalendar(calendarText).forEachIndexed { eventIndex, event ->
                            val external = hash("$sourceKey|$index|$eventIndex|${event.uid}")
                            SupabaseSync.upsertMailEvent(
                                family,
                                external,
                                event.title,
                                event.startsAt,
                                event.endsAt,
                                event.location
                            )
                            eventCount++
                        }
                    }

                    val subject = message.subject.orEmpty()
                    val sender = message.from?.joinToString(", ") { it.toString() }.orEmpty()
                    if (looksLikePromotion(sender, subject, extracted.text)) {
                        val parsedOffers = parseOffers(sender, subject, extracted.text, interests)
                        parsedOffers.forEach { offer ->
                            SupabaseSync.upsertMailOffer(
                                family,
                                offer.store,
                                offer.productName,
                                offer.normalizedProduct,
                                offer.price,
                                offer.unitText,
                                offer.validUntil,
                                subject,
                                "$sourceKey:${offer.normalizedProduct}:${offer.price}"
                            )
                            offerCount++
                        }
                    }
                }
            } finally {
                folder.close(false)
            }
        }
        if (maxUid > lastUid) statePrefs.edit().putLong("uid_${account.id}", maxUid).apply()
        return Pair(eventCount, offerCount)
    }

    private fun openStore(account: MailAccount): StoreConnection {
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

    private data class StoreConnection(val store: javax.mail.Store) : AutoCloseable {
        override fun close() {
            runCatching { if (store.isConnected) store.close() }
        }
    }

    private data class ExtractedMessage(val text: String, val calendars: List<String>)

    private fun extractMessage(message: Message): ExtractedMessage {
        val texts = mutableListOf<String>()
        val calendars = mutableListOf<String>()
        extractPart(message, texts, calendars)
        return ExtractedMessage(texts.joinToString("\n").take(250_000), calendars)
    }

    private fun extractPart(part: Part, texts: MutableList<String>, calendars: MutableList<String>) {
        val fileName = part.fileName.orEmpty().lowercase(Locale.ROOT)
        val contentType = part.contentType.orEmpty().lowercase(Locale.ROOT)
        if (part.isMimeType("text/calendar") || fileName.endsWith(".ics") || "calendar" in contentType) {
            val value = when (val content = runCatching { part.content }.getOrNull()) {
                is String -> content
                is InputStream -> content.bufferedReader().use { it.readText() }
                else -> runCatching { part.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            }
            if (value.isNotBlank()) calendars += value.take(200_000)
            return
        }
        when {
            part.isMimeType("text/plain") -> {
                val content = runCatching { part.content?.toString() }.getOrNull().orEmpty()
                if (content.isNotBlank()) texts += content
            }
            part.isMimeType("text/html") -> {
                val html = runCatching { part.content?.toString() }.getOrNull().orEmpty()
                if (html.isNotBlank()) texts += Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            }
            part.isMimeType("multipart/*") -> {
                val multipart = runCatching { part.content as? Multipart }.getOrNull() ?: return
                for (index in 0 until multipart.count) extractPart(multipart.getBodyPart(index), texts, calendars)
            }
            part.isMimeType("message/rfc822") -> {
                (runCatching { part.content }.getOrNull() as? Part)?.let { extractPart(it, texts, calendars) }
            }
        }
    }

    private data class ParsedCalendarEvent(
        val uid: String,
        val title: String,
        val startsAt: OffsetDateTime,
        val endsAt: OffsetDateTime?,
        val location: String?
    )

    private fun parseCalendar(text: String): List<ParsedCalendarEvent> {
        val unfolded = text.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "").replace("\n\t", "")
        return unfolded.split("BEGIN:VEVENT").drop(1).mapNotNull { raw ->
            val block = raw.substringBefore("END:VEVENT", "")
            val lines = block.lines().map(String::trim)
            val startRaw = valueFor(lines, "DTSTART") ?: return@mapNotNull null
            val start = parseIcsTime(startRaw) ?: return@mapNotNull null
            val end = valueFor(lines, "DTEND")?.let(::parseIcsTime)
            ParsedCalendarEvent(
                uid = valueFor(lines, "UID") ?: hash(block),
                title = unescapeIcs(valueFor(lines, "SUMMARY") ?: "Mailinbjudan"),
                startsAt = start,
                endsAt = end,
                location = valueFor(lines, "LOCATION")?.let(::unescapeIcs)?.takeIf(String::isNotBlank)
            )
        }
    }

    private fun valueFor(lines: List<String>, key: String): String? =
        lines.firstOrNull { it.startsWith("$key:") || it.startsWith("$key;") }?.substringAfter(':')

    private fun parseIcsTime(raw: String): OffsetDateTime? = runCatching {
        when {
            raw.endsWith("Z") && raw.length >= 16 -> {
                val formatter = if (raw.length >= 16) DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX") else DateTimeFormatter.BASIC_ISO_DATE
                ZonedDateTime.parse(raw, formatter).withZoneSameInstant(MAIL_STOCKHOLM).toOffsetDateTime()
            }
            raw.length >= 15 -> ZonedDateTime.of(
                LocalDateTime.parse(raw.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")),
                MAIL_STOCKHOLM
            ).toOffsetDateTime()
            raw.length == 8 -> ZonedDateTime.of(LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE), LocalTime.NOON, MAIL_STOCKHOLM).toOffsetDateTime()
            else -> null
        }
    }.getOrNull()

    private fun unescapeIcs(value: String): String = value
        .replace("\\n", " ")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    private data class ParsedOffer(
        val store: String,
        val productName: String,
        val normalizedProduct: String,
        val price: Double,
        val unitText: String?,
        val validUntil: LocalDate?
    )

    private fun parseOffers(sender: String, subject: String, text: String, interests: List<String>): List<ParsedOffer> {
        if (interests.isEmpty()) return emptyList()
        val store = detectStore(sender, subject)
        val normalizedInterests = interests
            .map { it.trim() to normalize(it) }
            .filter { it.second.length >= 3 }
            .distinctBy { it.second }
        val priceRegex = Regex("(?i)(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(?:kr|:-)(?:\\s*(?:/|per)\\s*([a-zåäö0-9]+))?")
        val result = linkedMapOf<String, ParsedOffer>()
        val segments = text.replace('\u00a0', ' ')
            .lines()
            .flatMap { line -> line.split(" • ", " | ", "  ") }
            .map { it.trim() }
            .filter { it.length in 3..500 }
        for (segment in segments) {
            val normalizedLine = normalize(segment)
            val matchingInterest = normalizedInterests.firstOrNull { (_, needle) ->
                normalizedLine.contains(needle) || needle.split(' ').filter { it.length >= 3 }.any { token -> normalizedLine.contains(token) }
            } ?: continue
            for (match in priceRegex.findAll(segment)) {
                val price = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
                if (price <= 0.0 || price > 100_000.0) continue
                val unit = match.groupValues.getOrNull(2)?.takeIf(String::isNotBlank)
                val key = "${matchingInterest.second}|$price|${unit.orEmpty()}"
                result[key] = ParsedOffer(
                    store = store,
                    productName = matchingInterest.first,
                    normalizedProduct = matchingInterest.second,
                    price = price,
                    unitText = unit,
                    validUntil = detectValidUntil(subject + " " + segment)
                )
            }
        }
        return result.values.take(40)
    }

    private fun looksLikePromotion(sender: String, subject: String, text: String): Boolean {
        val haystack = normalize("$sender $subject ${text.take(5000)}")
        val knownStores = listOf("ica", "willys", "coop", "city gross", "citygross", "jula", "rusta", "dollarstore")
        val promoWords = listOf("erbjud", "kampanj", "medlemspris", "veckans", "reklam", "rabatt", "rea", "kundklubb")
        return knownStores.any(haystack::contains) || promoWords.any(haystack::contains)
    }

    private fun detectStore(sender: String, subject: String): String {
        val haystack = normalize("$sender $subject")
        return when {
            "city gross" in haystack || "citygross" in haystack -> "City Gross"
            "willys" in haystack -> "Willys"
            Regex("(^| )ica( |$)").containsMatchIn(haystack) -> "ICA"
            "coop" in haystack -> "Coop"
            "dollarstore" in haystack -> "Dollarstore"
            "jula" in haystack -> "Jula"
            "rusta" in haystack -> "Rusta"
            else -> sender.substringAfter('@', sender).substringBefore('>').substringBefore(' ').take(80).ifBlank { "Mailerbjudande" }
        }
    }

    private fun detectValidUntil(value: String): LocalDate? {
        val lower = value.lowercase(Locale("sv", "SE"))
        if (!("t.o.m" in lower || "tom " in lower || "gäller till" in lower || "giltig till" in lower)) return null
        val match = Regex("(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{2,4}))?").find(lower) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val yearRaw = match.groupValues.getOrNull(3).orEmpty()
        val now = LocalDate.now(MAIL_STOCKHOLM)
        var year = when {
            yearRaw.length == 4 -> yearRaw.toIntOrNull() ?: now.year
            yearRaw.length == 2 -> 2000 + (yearRaw.toIntOrNull() ?: (now.year % 100))
            else -> now.year
        }
        var date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (yearRaw.isBlank() && date.isBefore(now.minusMonths(2))) date = date.plusYears(1)
        return date
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale("sv", "SE"))
        .replace(Regex("[^a-z0-9åäö]+"), " ")
        .trim()

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal fun mailOfferMatchesItem(offer: MailOffer, itemName: String): Boolean {
    fun normalize(value: String) = value.lowercase(Locale("sv", "SE")).replace(Regex("[^a-z0-9åäö]+"), " ").trim()
    val item = normalize(itemName)
    val product = offer.normalizedProduct.ifBlank { normalize(offer.productName) }
    if (item.isBlank() || product.isBlank()) return false
    if (item.contains(product) || product.contains(item)) return true
    val itemTokens = item.split(' ').filter { it.length >= 3 }.toSet()
    val productTokens = product.split(' ').filter { it.length >= 3 }.toSet()
    return itemTokens.intersect(productTokens).isNotEmpty()
}
'''
(APP / 'MailSync.kt').write_text(mail_sync)

# 4) Settings UI
mail_settings = r'''package se.familjekalender.app

import androidx.compose.foundation.clickable
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
'''
(APP / 'MailSettingsCard.kt').write_text(mail_settings)

# 5) Main UI wiring
main_path = APP / 'MainActivity.kt'
main = main_path.read_text()
if 'var mailOffers by remember' not in main:
    main = replace_once(
        main,
        '    var shopping by remember { mutableStateOf(emptyList<SyncShoppingItem>()) }\n',
        '    var shopping by remember { mutableStateOf(emptyList<SyncShoppingItem>()) }\n    var mailOffers by remember { mutableStateOf(emptyList<MailOffer>()) }\n',
        'mail offer state',
    )
    main = replace_once(
        main,
        '            shopping = SupabaseSync.loadShopping(session)\n',
        '            shopping = SupabaseSync.loadShopping(session)\n            mailOffers = SupabaseSync.loadMailOffers(session)\n',
        'mail offer refresh',
    )
    main = replace_once(
        main,
        '    LaunchedEffect(session.id, sportUrl, sportMemberId) {\n        suspend fun syncExternalCalendars() {\n',
        '    LaunchedEffect(session.id, sportUrl, sportMemberId) {\n        MailSyncScheduler.schedule(context)\n        suspend fun syncExternalCalendars() {\n',
        'mail schedule',
    )
    main = replace_once(
        main,
        '                        1 -> ShoppingScreen(\n                            session,\n                            shopping,\n',
        '                        1 -> ShoppingScreen(\n                            session,\n                            shopping,\n                            mailOffers,\n',
        'shopping offer argument',
    )
    main = replace_once(
        main,
        'private fun ShoppingScreen(\n    session: FamilySession,\n    items: List<SyncShoppingItem>,\n',
        'private fun ShoppingScreen(\n    session: FamilySession,\n    items: List<SyncShoppingItem>,\n    mailOffers: List<MailOffer>,\n',
        'shopping signature',
    )
    main = replace_once(
        main,
        '                        Text(item.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))\n                    }\n                }\n',
        '''                        Column(modifier = Modifier.weight(1f)) {\n                            Text(item.name, color = Color.White, fontSize = 16.sp)\n                            val offersForItem = mailOffers.filter { mailOfferMatchesItem(it, item.name) }.sortedBy { it.price }.take(3)\n                            offersForItem.forEach { offer ->\n                                val unit = offer.unitText?.let { " / $it" }.orEmpty()\n                                Text("${offer.store}: ${"%.2f".format(Locale.US, offer.price).replace('.', ',')} kr$unit", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)\n                            }\n                        }\n                    }\n                }\n''',
        'shopping offer rows',
    )
    main = replace_once(
        main,
        '    Spacer(Modifier.height(20.dp))\n    AppUpdateSettingsCard()\n',
        '    Spacer(Modifier.height(20.dp))\n    MailSettingsCard(session)\n    Spacer(Modifier.height(20.dp))\n    AppUpdateSettingsCard()\n',
        'mail settings card',
    )
main_path.write_text(main)

print('Mail sync integration applied.')
