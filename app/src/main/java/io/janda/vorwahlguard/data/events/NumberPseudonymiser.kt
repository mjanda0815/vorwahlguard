package io.janda.vorwahlguard.data.events

import java.security.MessageDigest

/**
 * Pure `sha256(e164 + salt)` hashing (CLAUDE.md §12). No Android imports — deliberately a plain
 * JVM function so it is trivially unit-testable. Must never log [e164] (CLAUDE.md §1) — callers
 * must not either.
 */
object NumberPseudonymiser {

    fun hash(e164: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((e164 + salt).toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
