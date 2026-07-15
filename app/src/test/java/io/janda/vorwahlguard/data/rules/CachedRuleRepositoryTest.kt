package io.janda.vorwahlguard.data.rules

import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [CachedRuleRepository] — the in-memory snapshot the `onScreenCall()` hot path reads
 * from (CLAUDE.md §3 rule 1). Pins the fail-safe default (empty before the first [refresh], which
 * [io.janda.vorwahlguard.domain.service.RuleMatcher] treats as "no match → allow"), that [refresh]
 * publishes [RuleSnapshotSource.load]'s result, and that [observeAndCache] keeps the snapshot
 * current as the source Flow emits. [RuleSnapshotSource] is faked; this class holds no I/O of its
 * own.
 */
class CachedRuleRepositoryTest {

    private fun rule(pattern: String) = Rule(
        pattern,
        PatternSyntax.parse(pattern),
        RuleAction.BLOCK,
        true,
        null,
        Instant.EPOCH,
    )

    @Test
    fun `activeRules is empty before the first refresh`() {
        val source = mockk<RuleSnapshotSource>()
        val repository = CachedRuleRepository(source)

        assertEquals(emptyList<Rule>(), repository.activeRules())
    }

    @Test
    fun `refresh publishes the snapshot loaded from the source`() {
        val loaded = listOf(rule("+43*"), rule("+49*"))
        val source = mockk<RuleSnapshotSource>()
        every { source.load() } returns loaded
        val repository = CachedRuleRepository(source)

        repository.refresh()

        assertEquals(loaded, repository.activeRules())
    }

    @Test
    fun `observeAndCache updates activeRules on every source emission`() = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(listOf(rule("+43*")))
        val source = mockk<RuleSnapshotSource>()
        every { source.observe() } returns flow
        val repository = CachedRuleRepository(source)

        val job = launch { repository.observeAndCache() }
        assertEquals(listOf("+43*"), repository.activeRules().map { it.pattern().text() })

        flow.value = listOf(rule("+49*"), rule("+1*"))
        assertEquals(listOf("+49*", "+1*"), repository.activeRules().map { it.pattern().text() })

        job.cancel()
    }
}
