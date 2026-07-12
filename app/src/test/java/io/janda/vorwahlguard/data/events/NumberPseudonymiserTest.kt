package io.janda.vorwahlguard.data.events

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NumberPseudonymiser] is the pure `sha256(number + salt)` step behind CLAUDE.md §12's
 * pseudonymisation option. It must never log the raw number (CLAUDE.md §1) — these tests only
 * ever assert on the returned hash, never print an input.
 */
class NumberPseudonymiserTest {

    @Test
    fun `the same number and salt hash to the same value`() {
        val first = NumberPseudonymiser.hash("+431234567", "salt")
        val second = NumberPseudonymiser.hash("+431234567", "salt")

        assertEquals(first, second)
    }

    @Test
    fun `a different salt hashes the same number to a different value`() {
        val withSaltA = NumberPseudonymiser.hash("+431234567", "salt-a")
        val withSaltB = NumberPseudonymiser.hash("+431234567", "salt-b")

        assertNotEquals(withSaltA, withSaltB)
    }

    @Test
    fun `a different number hashes to a different value under the same salt`() {
        val first = NumberPseudonymiser.hash("+431234567", "salt")
        val second = NumberPseudonymiser.hash("+4915112345678", "salt")

        assertNotEquals(first, second)
    }

    @Test
    fun `the hash is a 64 character lowercase hex string`() {
        val hash = NumberPseudonymiser.hash("+431234567", "salt")

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `the hash matches an independently computed sha256 digest`() {
        val expected = MessageDigest.getInstance("SHA-256")
            .digest("+431234567salt".toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

        val actual = NumberPseudonymiser.hash("+431234567", "salt")

        assertEquals(expected, actual)
    }
}
