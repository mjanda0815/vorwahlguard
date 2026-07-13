package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RuleWriter] itself does no mapping — [RuleEntityMappingTest] already covers `toEntity()` in
 * detail. This class only pins that [RuleWriter.save] forwards the correctly mapped
 * [RuleEntity] to [RuleDao.upsert] exactly once.
 */
class RuleWriterTest {

    private val dao = mockk<RuleDao>()
    private val writer = RuleWriter(dao)

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
}
