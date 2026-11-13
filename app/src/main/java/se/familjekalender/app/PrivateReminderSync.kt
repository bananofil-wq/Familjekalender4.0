package se.familjekalender.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A decrypted reminder exists only on the intended device, never in the shared calendar cache. */
internal data class PrivateReminder(
    val id: String,
    val text: String,
    val date: LocalDate,
    val time: String,
    val permanent: Boolean,
    val from: String = "",
)

internal object PrivateReminderSync {
    private const val PREFS = "family_private_reminders_v1"
    private const val KEY_ALIAS = "familjekalender_private_key_wrap_v1"
    private const val HEADER = "x-private-device-token"
    private const val TAG = "PrivateReminderSync"

    private data class Identity(
        val memberId: String,
        val deviceId: String,
        val token: String,
        val publicKey: PublicKey,
        val privateKey: PrivateKey,
    ) {
        val encodedPublic: String get() = PrivateReminderCrypto.encode(publicKey.encoded)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyForWrapping(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun wrapPrivateKey(session: FamilySession, memberId: String, privateKey: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyForWrapping())
        cipher.updateAAD((session.id + "|" + memberId).toByteArray(Charsets.UTF_8))
        return PrivateReminderCrypto.encode(cipher.iv) + "." +
            PrivateReminderCrypto.encode(cipher.doFinal(privateKey))
    }

    private fun unwrapPrivateKey(session: FamilySession, memberId: String, encoded: String): ByteArray {
        val parts = encoded.split('.', limit = 2)
        require(parts.size == 2) { "Ogiltig privat nyckel" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, keyForWrapping(),
            GCMParameterSpec(128, PrivateReminderCrypto.decode(parts[0])),
        )
        cipher.updateAAD((session.id + "|" + memberId).toByteArray(Charsets.UTF_8))
        return cipher.doFinal(PrivateReminderCrypto.decode(parts[1]))
    }

    @Synchronized
    private fun identity(context: Context, session: FamilySession): Identity? {
        val memberId = context.getSharedPreferences("family_calendar", Context.MODE_PRIVATE)
            .getString("device_member_id", null)?.takeIf { it.isNotBlank() } ?: return null
        if (memberId == ALL_FAMILY_MEMBER_ID) return null
        val store = prefs(context)
        val changed = store.getString("family_id", "") != session.id ||
            store.getString("member_id", "") != memberId
        if (changed) store.edit().clear().apply()
        val savedId = store.getString("device_id", null)
        val savedToken = store.getString("device_token", null)
        val savedPub = store.getString("public_key", null)
        val savedWrapped = store.getString("wrapped_private_key", null)
        if (savedId != null && savedToken != null && savedPub != null && savedWrapped != null) {
            // Never silently rotate keys on a decryption error: old messages would be lost.
            val privateBytes = unwrapPrivateKey(session, memberId, savedWrapped)
            return Identity(
                memberId, savedId, savedToken,
                PrivateReminderCrypto.decodePublic(savedPub),
                PrivateReminderCrypto.decodePrivate(privateBytes),
            )
        }
        val keys: KeyPair = PrivateReminderCrypto.generateKeyPair()
        val deviceId = UUID.randomUUID().toString()
        val token = PrivateReminderCrypto.randomSecret()
        val publicKey = PrivateReminderCrypto.encode(keys.public.encoded)
        val wrapped = wrapPrivateKey(session, memberId, keys.private.encoded)
        store.edit()
            .putString("family_id", session.id)
            .putString("member_id", memberId)
            .putString("device_id", deviceId)
            .putString("device_token", token)
            .putString("public_key", publicKey)
            .putString("wrapped_private_key", wrapped)
            .apply()
        return Identity(memberId, deviceId, token, keys.public, keys.private)
    }

    private fun headers(identity: Identity): Map<String, String> = mapOf(HEADER to identity.token)

    suspend fun ensureRegistered(context: Context, session: FamilySession): Boolean =
        withContext(Dispatchers.IO) {
            val identity = identity(context.applicationContext, session)
                ?: return@withContext false
            val response = SupabaseSync.request(
                "GET",
                "/rest/v1/private_reminder_devices?select=device_id,member_id,public_key" +
                    "&family_id=eq." + session.id + "&device_id=eq." + identity.deviceId,
                familyCode = session.code,
            )
            val registered = JSONArray(response).optJSONObject(0)
            when {
                registered == null -> {
                    val body = JSONObject()
                        .put("family_id", session.id)
                        .put("member_id", identity.memberId)
                        .put("device_id", identity.deviceId)
                        .put("public_key", identity.encodedPublic)
                        .put("access_token_hash", PrivateReminderCrypto.hashToken(identity.token))
                    SupabaseSync.request(
                        "POST", "/rest/v1/private_reminder_devices",
                        body, session.code, preferRepresentation = false,
                    )
                }
                registered.optString("public_key") != identity.encodedPublic ||
                    registered.optString("member_id") != identity.memberId -> {
                    val body = JSONObject()
                        .put("member_id", identity.memberId)
                        .put("public_key", identity.encodedPublic)
                        .put("updated_at", java.time.OffsetDateTime.now().toString())
                    SupabaseSync.request(
                        "PATCH", "/rest/v1/private_reminder_devices?family_id=eq." + session.id +
                            "&device_id=eq." + identity.deviceId,
                        body, session.code, preferRepresentation = false,
                        extraHeaders = headers(identity),
                    )
                }
            }
            true
        }

    /** Will throw rather than send a message to an unknown or unregistered recipient. */
    suspend fun send(
        context: Context,
        session: FamilySession,
        recipientMemberId: String,
        title: String,
        date: LocalDate,
        time: String,
        permanent: Boolean,
    ): Int = withContext(Dispatchers.IO) {
        require(recipientMemberId.isNotBlank() && recipientMemberId != ALL_FAMILY_MEMBER_ID) {
            "Välj en mottagare för en privat påminnelse"
        }
        require(title.isNotBlank() && title.length <= 600) {
            "Skriv en påminnelse på högst 600 tecken"
        }
        val allMembers = SupabaseSync.loadMembers(session)
        require(allMembers.any { it.id == recipientMemberId && it.id != ALL_FAMILY_MEMBER_ID }) {
            "Mottagaren tillhör inte familjen"
        }
        val recipient = allMembers.first { it.id == recipientMemberId }
        val devices = JSONArray(
            SupabaseSync.request(
                "GET",
                "/rest/v1/private_reminder_devices?select=device_id,public_key" +
                    "&family_id=eq." + session.id + "&member_id=eq." + recipientMemberId,
                familyCode = session.code,
            )
        )
        require(devices.length() > 0) {
            recipient.name + " behöver öppna sin kalender en gång innan privata påminnelser kan skickas."
        }
        val senderId = context.getSharedPreferences("family_calendar", 0)
            .getString("device_member_id", null)
        val sender = allMembers.firstOrNull { it.id == senderId }?.name.orEmpty()
        val plaintext = JSONObject()
            .put("version", 1)
            .put("title", title.trim())
            .put("date", date.toString())
            .put("time", time)
            .put("permanent", permanent)
            .put("from", sender)
            .toString()
        val envelopes = JSONArray()
        repeat(devices.length()) { index ->
            val row = devices.getJSONObject(index)
            val deviceId = row.getString("device_id")
            // Validate UUID and key before sending; a malformed key must never cause plaintext fallback.
            UUID.fromString(deviceId)
            val encrypted = PrivateReminderCrypto.encrypt(plaintext, row.getString("public_key"))
            envelopes.put(
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("family_id", session.id)
                    .put("recipient_device_id", deviceId)
                    .put("ephemeral_public_key", encrypted.ephemeralPublicKey)
                    .put("nonce", encrypted.nonce)
                    .put("ciphertext", encrypted.ciphertext)
            )
        }
        SupabaseSync.request(
            "POST", "/rest/v1/private_reminder_envelopes",
            envelopes, session.code, preferRepresentation = false,
        )
        devices.length()
    }

    private fun decodeInbox(raw: String, identity: Identity): List<PrivateReminder> {
        val json = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            repeat(json.length()) { index ->
                val row = json.optJSONObject(index) ?: return@repeat
                val item = runCatching {
                    val decrypted = PrivateReminderCrypto.decrypt(
                        PrivateReminderEnvelope(
                            row.getString("ephemeral_public_key"),
                            row.getString("nonce"),
                            row.getString("ciphertext"),
                        ), identity.privateKey, identity.publicKey,
                    )
                    val payload = JSONObject(decrypted)
                    require(payload.optInt("version") == 1)
                    PrivateReminder(
                        id = row.getString("id"),
                        text = payload.getString("title"),
                        date = LocalDate.parse(payload.getString("date")),
                        time = payload.optString("time"),
                        permanent = payload.optBoolean("permanent"),
                        from = payload.optString("from"),
                    )
                }.getOrNull()
                if (item != null) add(item)
            }
        }
    }

    /** Local cache contains ciphertext only and is scoped to this phone's current member identity. */
    fun cached(context: Context, session: FamilySession): List<PrivateReminder> {
        val identity = runCatching { identity(context.applicationContext, session) }.getOrNull()
            ?: return emptyList()
        val store = prefs(context)
        if (store.getString("inbox_for_device", null) != identity.deviceId) return emptyList()
        return decodeInbox(store.getString("encrypted_inbox", "[]") ?: "[]", identity)
    }

    suspend fun refreshInbox(context: Context, session: FamilySession): List<PrivateReminder> =
        withContext(Dispatchers.IO) {
            if (!ensureRegistered(context, session)) return@withContext emptyList()
            val identity = identity(context.applicationContext, session)
                ?: return@withContext emptyList()
            val raw = SupabaseSync.request(
                "GET",
                "/rest/v1/private_reminder_envelopes?select=id,ephemeral_public_key,nonce,ciphertext" +
                    "&family_id=eq." + session.id +
                    "&recipient_device_id=eq." + identity.deviceId +
                    "&order=created_at.desc&limit=100",
                familyCode = session.code,
                extraHeaders = headers(identity),
            )
            prefs(context).edit()
                .putString("inbox_for_device", identity.deviceId)
                .putString("encrypted_inbox", raw)
                .apply()
            decodeInbox(raw, identity)
        }

    fun visibleThisWeek(
        items: List<PrivateReminder>,
        today: LocalDate = LocalDate.now(),
    ): List<PrivateReminder> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return items.filter {
            if (it.permanent) !it.date.isAfter(today)
            else !it.date.isBefore(monday) && !it.date.isAfter(sunday)
        }.sortedWith(compareBy<PrivateReminder> { it.date }.thenBy { it.text })
    }

    suspend fun delete(context: Context, session: FamilySession, messageId: String) =
        withContext(Dispatchers.IO) {
            UUID.fromString(messageId)
            val identity = identity(context.applicationContext, session)
                ?: error("Ingen privat brevlåda registrerad")
            SupabaseSync.request(
                "DELETE",
                "/rest/v1/private_reminder_envelopes?family_id=eq." + session.id +
                    "&recipient_device_id=eq." + identity.deviceId + "&id=eq." + messageId,
                familyCode = session.code, preferRepresentation = false,
                extraHeaders = headers(identity),
            )
            val cachedRows = prefs(context).getString("encrypted_inbox", "[]") ?: "[]"
            val old = JSONArray(cachedRows)
            val updated = JSONArray()
            repeat(old.length()) { index ->
                val obj = old.optJSONObject(index) ?: return@repeat
                if (obj.optString("id") != messageId) updated.put(obj)
            }
            prefs(context).edit().putString("encrypted_inbox", updated.toString()).apply()
        }
}
