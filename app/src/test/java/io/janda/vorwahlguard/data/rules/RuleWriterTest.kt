package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.Clock
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RuleWriter] itself does no mapping — [RuleEntityMappingTest] already covers `toEntity()` in
 * detail. This class pins that [RuleWriter.save] forwards the correctly mapped [RuleEntity] to
 * [RuleDao.upsert], and that [RuleWriter.createIfAbsent] builds a rule and delegates to the
 * atomic [RuleDao.insertIfPatternAbsent] (issue #78).
 */
class RuleWriterTest {

    private val dao = mockk<RuleDao>()
    private val clock = mockk<Clock>().also { every { it.now() } returns Instant.parse("2026-07-15T12:00:00Z") }
    private val writer = RuleWriter(dao, clock)

    @Test
    fun `save upserts the entity mapped from the given rule`() = runBlocking {
        coEvery { dao.upsert(any()) } just Runs
        val rule = Rule(
            "rule-1",
            PatternSyntax.parse("+43663*"),
            RuleAction.BLOCK,
            true,
            "Spam Anrufer",
            Instant.EPOCH,
        )

        writer.save(rule)

        val expected = RuleEntity(
            id = "rule-1",
            pattern = "+43663*",
            action = "BLOCK",
            enabled = true,
            label = "Spam Anrufer",
            createdAt = Instant.EPOCH,
        )
        coVerify(exactly = 1) { dao.upsert(expected) }
    }

    @Test
    fun `save round trips a disabled rule with a non-null label into the entity`() = runBlocking {
        coEvery { dao.upsert(any()) } just Runs
        val createdAt = Instant.parse("2026-07-13T10:15:30Z")
        val rule = Rule(
            "rule-2",
            PatternSyntax.parse("+436631234567"),
            RuleAction.SILENCE,
            false,
            "Werbeanrufe",
            createdAt,
        )

        writer.save(rule)

        val expected = RuleEntity(
            id = "rule-2",
            pattern = "+436631234567",
            action = "SILENCE",
            enabled = false,
            label = "Werbeanrufe",
            createdAt = createdAt,
        )
        coVerify(exactly = 1) { dao.upsert(expected) }
        // enabled=false and the label must not be dropped or defaulted on the way through.
        assertEquals(false, expected.enabled)
        assertEquals("Werbeanrufe", expected.label)
    }

    @Test
    fun `createIfAbsent builds an enabled rule with the clock timestamp and reports CREATED`() = runBlocking {
        val captured = slot<RuleEntity>()
        coEvery { dao.insertIfPatternAbsent(capture(captured)) } returns true

        val result = writer.createIfAbsent("+43*", RuleAction.SILENCE)

        assertEquals(CreateRuleResult.CREATED, result)
        assertEquals("+43*", captured.captured.pattern)
        assertEquals("SILENCE", captured.captured.action)
        assertTrue(captured.captured.enabled)
        assertEquals(null, captured.captured.label)
        assertEquals(Instant.parse("2026-07-15T12:00:00Z"), captured.captured.createdAt)
        assertTrue(captured.captured.id.isNotBlank())
    }

    @Test
    fun `createIfAbsent reports ALREADY_EXISTS and writes nothing when the DAO refuses`() = runBlocking {
        coEvery { dao.insertIfPatternAbsent(any()) } returns false

        val result = writer.createIfAbsent("+43*", RuleAction.BLOCK)

        assertEquals(CreateRuleResult.ALREADY_EXISTS, result)
    }
}
