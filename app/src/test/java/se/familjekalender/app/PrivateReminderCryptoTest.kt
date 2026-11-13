package se.familjekalender.app

import java.time.LocalDate
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateReminderCryptoTest {
    @Test fun `only recipient device private key can decrypt message`() {
        val bella = PrivateReminderCrypto.generateKeyPair()
        val hugo = PrivateReminderCrypto.generateKeyPair()
        val text = "Jag älskar dig ❤️"
        val envelope = PrivateReminderCrypto.encrypt(
            text, PrivateReminderCrypto.encode(bella.public.encoded),
        )
        assertEquals(
            text, PrivateReminderCrypto.decrypt(envelope, bella.private, bella.public),
        )
        assertThrows(Exception::class.java) {
            PrivateReminderCrypto.decrypt(envelope, hugo.private, hugo.public)
        }
        assertFalse(envelope.ciphertext.contains("älskar"))
        assertFalse(envelope.ciphertext.contains(text))
    }

    @Test fun `tampered ciphertext fails authenticated decryption`() {
        val key = PrivateReminderCrypto.generateKeyPair()
        val envelope = PrivateReminderCrypto.encrypt(
            "Privat", PrivateReminderCrypto.encode(key.public.encoded),
        )
        val encoded = PrivateReminderCrypto.decode(envelope.ciphertext)
        encoded[0] = (encoded[0].toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            PrivateReminderCrypto.decrypt(
                envelope.copy(ciphertext = PrivateReminderCrypto.encode(encoded)),
                key.private, key.public,
            )
        }
    }

    @Test fun `device tokens have sufficient entropy and deterministic fingerprints`() {
        val a = PrivateReminderCrypto.randomSecret()
        val b = PrivateReminderCrypto.randomSecret()
        assertTrue(a.length >= 40)
        assertFalse(a == b)
        assertEquals(64, PrivateReminderCrypto.hashToken(a).length)
        assertEquals(
            PrivateReminderCrypto.hashToken(a),
            PrivateReminderCrypto.hashToken(a),
        )
    }

    @Test fun `only own current week or still active permanent reminders appear`() {
        val today = LocalDate.of(2026, 11, 13)
        val rows = listOf(
            PrivateReminder("1", "Idag", today, "", false),
            PrivateReminder("2", "Alltid", today.minusMonths(3), "", true),
            PrivateReminder("3", "Igår veckan före", today.minusDays(7), "", false),
            PrivateReminder("4", "Nästa vecka", today.plusDays(4), "", false),
            PrivateReminder("5", "Permanent framåt", today.plusDays(1), "", true),
        )
        assertEquals(
            setOf("Idag", "Alltid"),
            PrivateReminderSync.visibleThisWeek(rows, today).map { it.text }.toSet(),
        )
    }
}
