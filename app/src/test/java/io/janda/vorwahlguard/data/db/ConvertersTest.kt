package io.janda.vorwahlguard.data.db

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Converters] is the only place that decides how domain types are represented as SQLite
 * primitives. `RuleAction` deliberately has no converter (a converter cannot skip a corrupt row,
 * only throw) — its stored-name pinning lives with the entity mapping in
 * [io.janda.vorwahlguard.data.rules.RuleEntityMappingTest].
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `an instant round trips through epoch millis`() {
        val instant = Instant.parse("2026-07-12T10:15:30.123Z")

        val roundTripped = converters.epochMillisToInstant(converters.instantToEpochMillis(instant))

        assertEquals(instant, roundTripped)
    }

    @Test
    fun `a null instant converts to a null epoch millis value`() {
        assertNull(converters.instantToEpochMillis(null))
    }

    @Test
    fun `a null epoch millis value converts to a null instant`() {
        assertNull(converters.epochMillisToInstant(null))
    }
}
