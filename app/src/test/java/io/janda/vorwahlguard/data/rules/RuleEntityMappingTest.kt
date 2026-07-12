package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.RuleAction
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RuleEntity.toDomain] re-validates the stored pattern text through `PatternSyntax` on every
 * read (CLAUDE.md §4) — these tests cover both directions of the mapping and the fail-safe
 * "corrupt row degrades to null, never throws" contract (CLAUDE.md §3 rule 1).
 */
class RuleEntityMappingTest {

    @Test
    fun `a prefix pattern round trips from entity to domain and back`() {
        assertRoundTrips("+43663*")
    }

    @Test
    fun `an exact pattern round trips from entity to domain and back`() {
        assertRoundTrips("+436631234567")
    }

    @Test
    fun `the bare wildcard pattern round trips from entity to domain and back`() {
        assertRoundTrips("*")
    }

    @Test
    fun `the PRIVATE token round trips from entity to domain and back`() {
        assertRoundTrips("PRIVATE")
    }

    @Test
    fun `a pattern with an infix wildcard maps to null instead of throwing`() {
        assertMapsToNull("+43**")
    }

    @Test
    fun `a non-numeric pattern maps to null instead of throwing`() {
        assertMapsToNull("abc")
    }

    @Test
    fun `an empty pattern maps to null instead of throwing`() {
        assertMapsToNull("")
    }

    @Test
    fun `a corrupt action string maps to null instead of throwing`() {
        assertNull(entityFor("+43*", action = "SHOUT").toDomain())
    }

    @Test
    fun `the stored action names are pinned so an enum rename cannot silently corrupt rows`() {
        // These exact strings are what existing installs already have on disk. Renaming a
        // RuleAction constant must fail here, not silently drop every stored rule.
        for (name in listOf("BLOCK", "SILENCE", "ALLOW")) {
            val domain = entityFor("+43*", action = name).toDomain()

            assertEquals(RuleAction.valueOf(name), domain?.action())
            assertEquals(name, domain?.toEntity()?.action)
        }
    }

    private fun assertRoundTrips(pattern: String) {
        val entity = entityFor(pattern)

        val domain = entity.toDomain()

        assertEquals(pattern, domain?.pattern()?.text())
        assertEquals(entity, domain?.toEntity())
    }

    private fun assertMapsToNull(pattern: String) {
        assertNull(entityFor(pattern).toDomain())
    }

    private fun entityFor(pattern: String, action: String = "BLOCK"): RuleEntity = RuleEntity(
        id = "rule-1",
        pattern = pattern,
        action = action,
        enabled = true,
        label = "label",
        createdAt = Instant.EPOCH,
    )
}
