package io.janda.vorwahlguard.ui.uebersicht

import android.content.Intent
import io.janda.vorwahlguard.data.events.ActionReasonCount
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.events.RegionCount
import io.janda.vorwahlguard.data.events.RuleIdCount
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.screening.CallScreeningRoleProvider
import io.janda.vorwahlguard.ui.regeln.addrule.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test

/**
 * Covers [UebersichtViewModel]: the combined [CallEventDao] aggregate Flows (including
 * [CallEventDao.observeActionBreakdown], issue #59) plus [RuleSnapshotSource.observe] producing a
 * fully populated, `loaded=true` [UebersichtUiState]; [UebersichtUiState.hasAnyEvents] tracking
 * `totalScreened > 0`; role status being read once at construction (before any
 * [UebersichtViewModel.refreshRoleStatus] call) and re-read live on each subsequent call — proving
 * it is not cached — via [CallScreeningRoleProvider]; and
 * [UebersichtViewModel.createRoleRequestIntent] delegating straight through.
 * [UnconfinedTestDispatcher] backs both `Dispatchers.Main` (via [MainDispatcherRule]) and the
 * injected `@DefaultDispatcher` argument, so `init`'s collection runs eagerly and synchronously
 * (same strategy as `ProtokollViewModelTest`/`RegelnViewModelTest`).
 */
class UebersichtViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:JUnitRule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val austria = Country("AT", 43)

    private lateinit var totalCountFlow: MutableStateFlow<Int>
    private lateinit var occurredAtSinceFlow: MutableStateFlow<List<Long>>
    private lateinit var topRegionsFlow: MutableStateFlow<List<RegionCount>>
    private lateinit var topRulesFlow: MutableStateFlow<List<RuleIdCount>>
    private lateinit var actionBreakdownFlow: MutableStateFlow<List<ActionReasonCount>>
    private lateinit var rulesFlow: MutableStateFlow<List<Rule>>

    private lateinit var dao: CallEventDao
    private lateinit var ruleSnapshotSource: RuleSnapshotSource
    private lateinit var countryCatalog: CountryCatalog
    private lateinit var roleProvider: CallScreeningRoleProvider
    private lateinit var clock: Clock

    private val liveRule = Rule(
        "rule-live",
        PatternSyntax.parse("+436631234567"),
        RuleAction.BLOCK,
        true,
        null,
        Instant.EPOCH,
    )

    @Before
    fun setUp() {
        totalCountFlow = MutableStateFlow(0)
        occurredAtSinceFlow = MutableStateFlow(emptyList())
        topRegionsFlow = MutableStateFlow(emptyList())
        topRulesFlow = MutableStateFlow(emptyList())
        actionBreakdownFlow = MutableStateFlow(emptyList())
        rulesFlow = MutableStateFlow(emptyList())

        dao = mockk()
        every { dao.observeTotalCount() } returns totalCountFlow
        every { dao.observeOccurredAtSince(any()) } returns occurredAtSinceFlow
        every { dao.observeTopRegions() } returns topRegionsFlow
        every { dao.observeTopRules() } returns topRulesFlow
        every { dao.observeActionBreakdown() } returns actionBreakdownFlow

        ruleSnapshotSource = mockk()
        every { ruleSnapshotSource.observe() } returns rulesFlow

        countryCatalog = mockk()
        every { countryCatalog.byIso2("AT") } returns Optional.of(austria)

        roleProvider = mockk()
        every { roleProvider.isRoleAvailable() } returns false
        every { roleProvider.isRoleHeld() } returns false
        every { roleProvider.createRoleRequestIntent() } returns null

        clock = mockk()
        every { clock.now() } returns Instant.parse("2026-07-15T12:00:00Z")
    }

    private fun createViewModel(): UebersichtViewModel =
        UebersichtViewModel(dao, ruleSnapshotSource, countryCatalog, roleProvider, clock, dispatcher)

    @Test
    fun `a combined emission across all flows produces a loaded state`() = runTest(dispatcher) {
        totalCountFlow.value = 3
        topRegionsFlow.value = listOf(RegionCount("AT", 3))
        topRulesFlow.value = listOf(RuleIdCount("rule-live", 3))
        rulesFlow.value = listOf(liveRule)

        val state = createViewModel().uiState.value

        assertTrue(state.loaded)
    }

    @Test
    fun `a combined emission carries the total screened count through`() = runTest(dispatcher) {
        totalCountFlow.value = 3
        rulesFlow.value = listOf(liveRule)

        val state = createViewModel().uiState.value

        assertEquals(3, state.totalScreened)
    }

    @Test
    fun `a combined emission produces a non-empty top countries list from observeTopRegions`() = runTest(dispatcher) {
        totalCountFlow.value = 1
        topRegionsFlow.value = listOf(RegionCount("AT", 1))
        rulesFlow.value = listOf(liveRule)

        val state = createViewModel().uiState.value

        assertEquals(listOf(TopCountryUi.Known(austria.flagEmoji(), austria.displayName(java.util.Locale.getDefault()), 1)), state.topCountries)
    }

    @Test
    fun `a combined emission produces a non-empty top rules list resolved against the rule snapshot`() = runTest(dispatcher) {
        totalCountFlow.value = 1
        topRulesFlow.value = listOf(RuleIdCount("rule-live", 1))
        rulesFlow.value = listOf(liveRule)

        val state = createViewModel().uiState.value

        assertEquals(1, state.topRules.size)
        assertTrue(state.topRules.single() is TopRuleUi.Known)
    }

    @Test
    fun `hasAnyEvents is false when totalScreened is zero`() = runTest(dispatcher) {
        totalCountFlow.value = 0
        rulesFlow.value = emptyList()

        val state = createViewModel().uiState.value

        assertFalse(state.hasAnyEvents)
    }

    @Test
    fun `hasAnyEvents is true when totalScreened is non-zero`() = runTest(dispatcher) {
        totalCountFlow.value = 1
        rulesFlow.value = emptyList()

        val state = createViewModel().uiState.value

        assertTrue(state.hasAnyEvents)
    }

    @Test
    fun `role status is read from the provider once at construction`() = runTest(dispatcher) {
        every { roleProvider.isRoleAvailable() } returns true
        every { roleProvider.isRoleHeld() } returns false

        val state = createViewModel().uiState.value

        assertTrue(state.roleAvailable)
        assertFalse(state.roleHeld)
    }

    @Test
    fun `role status reflects roleHeld true at construction when the provider reports it held`() = runTest(dispatcher) {
        every { roleProvider.isRoleAvailable() } returns true
        every { roleProvider.isRoleHeld() } returns true

        val state = createViewModel().uiState.value

        assertTrue(state.roleHeld)
    }

    @Test
    fun `refreshRoleStatus re-reads the provider and updates the state to newly stubbed values`() = runTest(dispatcher) {
        every { roleProvider.isRoleAvailable() } returns true
        every { roleProvider.isRoleHeld() } returns false
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.roleHeld)

        // Re-stub after construction: proves the live read is not a value cached at init time.
        every { roleProvider.isRoleHeld() } returns true
        viewModel.refreshRoleStatus()

        assertTrue(viewModel.uiState.value.roleHeld)
    }

    @Test
    fun `refreshRoleStatus updates roleAvailable to a newly stubbed value too`() = runTest(dispatcher) {
        every { roleProvider.isRoleAvailable() } returns false
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.roleAvailable)

        every { roleProvider.isRoleAvailable() } returns true
        viewModel.refreshRoleStatus()

        assertTrue(viewModel.uiState.value.roleAvailable)
    }

    @Test
    fun `a combined emission maps observeActionBreakdown through to actionBreakdown`() = runTest(dispatcher) {
        totalCountFlow.value = 3
        actionBreakdownFlow.value = listOf(ActionReasonCount(RuleAction.BLOCK.name, DecisionReason.RULE_MATCH.name, 3))
        rulesFlow.value = listOf(liveRule)

        val state = createViewModel().uiState.value

        assertEquals(3, state.actionBreakdown.single { it.category == BreakdownCategory.BLOCK }.count)
    }

    @Test
    fun `createRoleRequestIntent delegates to the role provider exactly once`() = runTest(dispatcher) {
        val intent = mockk<Intent>()
        every { roleProvider.createRoleRequestIntent() } returns intent
        val viewModel = createViewModel()

        val result = viewModel.createRoleRequestIntent()

        verify(exactly = 1) { roleProvider.createRoleRequestIntent() }
        assertSame(intent, result)
    }
}
