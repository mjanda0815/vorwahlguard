package io.janda.vorwahlguard.ui.regeln

import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.data.rules.RuleWriter
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.PatternDescription
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.ui.regeln.addrule.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test

/**
 * Covers [RegelnViewModel]: partitioning [RuleSnapshotSource.observe] emissions into
 * [RegelnUiState.whitelist]/[RegelnUiState.blacklist] via the real [io.janda.vorwahlguard.domain.model.RuleSection.of]
 * (not mocked — a pure function, exercised end to end here rather than only in isolation), the
 * createdAt-descending sort within a section, and [RegelnViewModel.deleteRule] delegating to
 * [RuleWriter.delete]. [UnconfinedTestDispatcher] backs both `Dispatchers.Main` (via
 * [MainDispatcherRule]) and the injected `@DefaultDispatcher` argument, so every coroutine the
 * view model launches runs eagerly and synchronously on the test thread (same strategy as
 * `AddRuleViewModelTest`).
 */
class RegelnViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:JUnitRule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val austria = Country("AT", 43)
    private val unitedStates = Country("US", 1)
    private val canada = Country("CA", 1)

    private lateinit var rulesFlow: MutableStateFlow<List<Rule>>
    private lateinit var ruleSnapshotSource: RuleSnapshotSource
    private lateinit var ruleWriter: RuleWriter
    private lateinit var countryCatalog: CountryCatalog

    // Fixtures: one EXACT/ALLOW rule (whitelist-qualifying), one PREFIX/BLOCK rule on an
    // unambiguous calling code, and one PREFIX/SILENCE rule on an ambiguous calling code
    // (both blacklist-qualifying), each with a distinct createdAt so ordering is verifiable.
    private val allowExact = Rule(
        "allow-1",
        PatternSyntax.parse("+436631234567"),
        RuleAction.ALLOW,
        true,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val blockPrefixUnambiguous = Rule(
        "block-1",
        PatternSyntax.parse("+43*"),
        RuleAction.BLOCK,
        true,
        null,
        Instant.parse("2026-01-03T00:00:00Z"),
    )
    private val silencePrefixAmbiguous = Rule(
        "silence-1",
        PatternSyntax.parse("+1*"),
        RuleAction.SILENCE,
        true,
        null,
        Instant.parse("2026-01-02T00:00:00Z"),
    )

    @Before
    fun setUp() {
        rulesFlow = MutableStateFlow(emptyList())
        ruleSnapshotSource = mockk()
        every { ruleSnapshotSource.observe() } returns rulesFlow

        ruleWriter = mockk()
        coEvery { ruleWriter.delete(any()) } just Runs

        countryCatalog = mockk()
        every { countryCatalog.describe(PatternSyntax.parse("+43*")) } returns
            PatternDescription(PatternSyntax.parse("+43*"), listOf(austria), false)
        every { countryCatalog.describe(PatternSyntax.parse("+1*")) } returns
            PatternDescription(PatternSyntax.parse("+1*"), listOf(unitedStates, canada), true)
    }

    private fun createViewModel(): RegelnViewModel {
        return RegelnViewModel(ruleSnapshotSource, ruleWriter, countryCatalog, dispatcher)
    }

    @Test
    fun `a mixed emission splits into whitelist and blacklist and marks the state loaded`() = runTest(dispatcher) {
        rulesFlow.value = listOf(allowExact, blockPrefixUnambiguous, silencePrefixAmbiguous)

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.loaded)
        assertEquals(listOf("allow-1"), state.whitelist.map { it.id })
        assertEquals(listOf("block-1", "silence-1"), state.blacklist.map { it.id })
    }

    @Test
    fun `multiple rules in the same section sort by createdAt descending`() = runTest(dispatcher) {
        rulesFlow.value = listOf(blockPrefixUnambiguous, silencePrefixAmbiguous)

        val viewModel = createViewModel()
        val blacklist = viewModel.uiState.value.blacklist

        // block-1 (2026-01-03) is newer than silence-1 (2026-01-02).
        assertEquals(listOf("block-1", "silence-1"), blacklist.map { it.id })
    }

    @Test
    fun `an empty emission produces empty lists and a loaded state`() = runTest(dispatcher) {
        rulesFlow.value = emptyList()

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.loaded)
        assertTrue(state.whitelist.isEmpty())
        assertTrue(state.blacklist.isEmpty())
    }

    @Test
    fun `deleteRule delegates to the rule writer exactly once`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.deleteRule("block-1")

        coVerify(exactly = 1) { ruleWriter.delete("block-1") }
    }

    @Test
    fun `an ALLOW rule with a prefix pattern is classified as blacklist, not whitelist`() = runTest(dispatcher) {
        // Real RuleSection.of, exercised through the view model rather than in isolation:
        // whitelist requires an EXACT pattern, so a country-wide ALLOW rule must still land
        // in the blacklist section (CLAUDE.md §7 doc-comment on RuleSection).
        val allowPrefix = Rule(
            "allow-prefix-1",
            PatternSyntax.parse("+43*"),
            RuleAction.ALLOW,
            true,
            null,
            Instant.parse("2026-01-04T00:00:00Z"),
        )
        rulesFlow.value = listOf(allowPrefix)

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.whitelist.isEmpty())
        assertEquals(listOf("allow-prefix-1"), state.blacklist.map { it.id })
    }
}
