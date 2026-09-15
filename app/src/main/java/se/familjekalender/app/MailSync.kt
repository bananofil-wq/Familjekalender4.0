package se.familjekalender.app

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
