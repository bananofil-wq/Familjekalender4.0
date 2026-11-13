package se.familjekalender.app

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A ciphertext envelope; never write the reminder's title to shared calendar storage. */
internal data class PrivateReminderEnvelope(
    val ephemeralPublicKey: String,
    val nonce: String,
    val ciphertext: String,
)

internal object PrivateReminderCrypto {
    private val random = SecureRandom()
    private val domain = "Familjekalender/private-reminder/envelope/v1".toByteArray(StandardCharsets.UTF_8)

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }.generateKeyPair()

    fun encode(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    fun decode(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)

    fun decodePublic(encoded: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decode(encoded)))

    fun decodePrivate(encoded: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encoded))

    private fun derivedKey(
        privateKey: PrivateKey,
        publicKey: PublicKey,
        ephemeralPublic: ByteArray,
        recipientPublic: ByteArray,
    ): SecretKeySpec {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        val sharedSecret = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(domain)
        digest.update(sharedSecret)
        digest.update(ephemeralPublic)
        digest.update(recipientPublic)
        sharedSecret.fill(0)
        return SecretKeySpec(digest.digest(), "AES")
    }

    fun encrypt(plaintext: String, recipientPublicBase64: String): PrivateReminderEnvelope {
        require(plaintext.length <= 3500) { "Påminnelsen är för lång" }
        val recipient = decodePublic(recipientPublicBase64)
        val ephemeral = generateKeyPair()
        val ephemeralBytes = ephemeral.public.encoded
        val recipientBytes = recipient.encoded
        val aesKey = derivedKey(ephemeral.private, recipient, ephemeralBytes, recipientBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(domain)
        cipher.updateAAD(ephemeralBytes)
        val bytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        return PrivateReminderEnvelope(
            ephemeralPublicKey = encode(ephemeralBytes),
            nonce = encode(iv),
            ciphertext = encode(cipher.doFinal(bytes)),
        )
    }

    fun decrypt(
        envelope: PrivateReminderEnvelope,
        recipientPrivate: PrivateKey,
        recipientPublic: PublicKey,
    ): String {
        val ephemeralBytes = decode(envelope.ephemeralPublicKey)
        val ephemeral = decodePublic(envelope.ephemeralPublicKey)
        val aesKey = derivedKey(
            recipientPrivate, ephemeral, ephemeralBytes, recipientPublic.encoded,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, decode(envelope.nonce)))
        cipher.updateAAD(domain)
        cipher.updateAAD(ephemeralBytes)
        return String(cipher.doFinal(decode(envelope.ciphertext)), StandardCharsets.UTF_8)
    }

    fun randomSecret(): String = encode(ByteArray(32).also(random::nextBytes))

    fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
