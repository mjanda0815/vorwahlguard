package io.janda.vorwahlguard.ui.regeln.addrule

import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.data.rules.RuleWriter
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.screening.SimRegionProvider
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps [Dispatchers.Main] for a [TestDispatcher] for the duration of each test, since
 * [AddRuleViewModel] uses `viewModelScope`, which resolves to `Dispatchers.Main.immediate`.
 * [UnconfinedTestDispatcher] is also handed in as the `defaultDispatcher` constructor argument
 * (see [AddRuleViewModelTest.dispatcher]) so every coroutine the view model launches — on Main
 * or on `defaultDispatcher` — runs eagerly, inline, on the test thread, instead of racing a real
 * dispatcher (app test convention, see `VorwahlGuardScreeningServiceTest`/
 * `CachedSettingsRepositoryTest`).
 */
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Covers `AddRuleViewModel` (issue #6): the country-pick vs. free-text pattern paths converging
 * on one draft, the CLAUDE.md §5 recommended-action rule and its manual override, duplicate
 * detection against persisted rules, and [AddRuleViewModel.save]/[AddRuleViewModel.reset].
 *
 * [countryCatalog] is a small, deliberately ambiguous fixture: `+1` (US, CA) covers the CLAUDE.md
 * §6 1:n case, `+43`/`+49` (AT, DE) are unambiguous. All four calling codes {@code {43, 1, 49}}
 * become [AddRuleViewModel]'s `knownCallingCodes`, which is what `+43*` vs. `+43663*` exercises.
 */
class AddRuleViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:JUnitRule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val fixedInstant: Instant = Instant.parse("2026-07-13T10:00:00Z")

    private lateinit var countryCatalog: CountryCatalog
    private lateinit var rulesFlow: MutableStateFlow<List<Rule>>
    private lateinit var ruleSnapshotSource: RuleSnapshotSource
    private lateinit var ruleWriter: RuleWriter
    private lateinit var numberNormalizer: NumberNormalizer
    private lateinit var simRegionProvider: SimRegionProvider
    private lateinit var clock: Clock
    private lateinit var viewModel: AddRuleViewModel

    private val savedRules = mutableListOf<Rule>()

    @Before
    fun setUp() {
        countryCatalog = mockk()
        every { countryCatalog.all() } returns listOf(
            Country("AT", 43),
            Country("US", 1),
            Country("CA", 1),
            Country("DE", 49),
        )
        every { countryCatalog.regionsFor(1) } returns listOf("US", "CA")
        every { countryCatalog.regionsFor(43) } returns emptyList()
        every { countryCatalog.regionsFor(49) } returns emptyList()

        rulesFlow = MutableStateFlow(emptyList())
        ruleSnapshotSource = mockk()
        every { ruleSnapshotSource.observe() } returns rulesFlow
        every { ruleSnapshotSource.load() } returns emptyList()

        ruleWriter = mockk()
        savedRules.clear()
        coEvery { ruleWriter.save(capture(savedRules)) } just Runs

        numberNormalizer = mockk(relaxed = true)

        simRegionProvider = mockk(relaxed = true)
        every { simRegionProvider.current() } returns "AT"

        clock = mockk()
        every { clock.now() } returns fixedInstant

        viewModel = AddRuleViewModel(
            countryCatalog,
            ruleSnapshotSource,
            ruleWriter,
            numberNormalizer,
            simRegionProvider,
            clock,
            dispatcher,
        )
    }

    @Test
    fun `selecting an unambiguous country recommends silence and produces no collateral`() = runTest(dispatcher) {
        val at = viewModel.uiState.value.countries.first { it.iso2 == "AT" }

        viewModel.selectCountry(at)

        val state = viewModel.uiState.value
        assertEquals("+43*", state.patternText)
        assertTrue(state.patternValid)
        assertEquals(RuleAction.SILENCE, state.recommendedAction)
        assertEquals(RuleAction.SILENCE, state.selectedAction)
        assertNull(state.collateral)
    }

    @Test
    fun `selecting a country whose calling code covers other regions returns collateral info`() = runTest(dispatcher) {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val us = viewModel.uiState.value.countries.first { it.iso2 == "US" }

            viewModel.selectCountry(us)

            val collateral = viewModel.uiState.value.collateral
            assertNotNull(collateral)
            assertEquals("Canada", collateral!!.firstOtherRegionName)
            assertEquals(0, collateral.remainingOtherCount)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `a prefix deeper than a known calling code recommends block`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("+43663*")

        val state = viewModel.uiState.value
        assertTrue(state.patternValid)
        assertEquals(RuleAction.BLOCK, state.recommendedAction)
        assertNull(state.selectedCountry)
    }

    @Test
    fun `a bare calling code prefix recommends silence`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("+43*")

        assertEquals(RuleAction.SILENCE, viewModel.uiState.value.recommendedAction)
    }

    @Test
    fun `a bare known calling code with no wildcard is flagged as missing the wildcard`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("+43")

        val state = viewModel.uiState.value
        assertTrue("+43 is syntactically valid (EXACT)", state.patternValid)
        assertTrue(state.patternMissingWildcard)
    }

    @Test
    fun `a bare calling code prefix with the wildcard is not flagged as missing it`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("+43*")

        assertFalse(viewModel.uiState.value.patternMissingWildcard)
    }

    @Test
    fun `a deeper prefix is not flagged as a missing wildcard`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("+43663*")

        assertFalse(viewModel.uiState.value.patternMissingWildcard)
    }

    @Test
    fun `a real exact number is not flagged as a missing wildcard`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("+436631234567")

        assertFalse(viewModel.uiState.value.patternMissingWildcard)
    }

    @Test
    fun `selecting a country never flags the missing-wildcard warning`() = runTest(dispatcher) {
        // If the free-text tab had already flagged the warning, selectCountry must clear it —
        // the country picker always appends the wildcard itself.
        viewModel.onPatternTextChanged("+43")
        assertTrue(viewModel.uiState.value.patternMissingWildcard)

        val at = viewModel.uiState.value.countries.first { it.iso2 == "AT" }
        viewModel.selectCountry(at)

        assertFalse(viewModel.uiState.value.patternMissingWildcard)
    }

    @Test
    fun `an invalid pattern text defaults the recommendation to block`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("not-a-pattern")

        val state = viewModel.uiState.value
        assertFalse(state.patternValid)
        assertEquals(RuleAction.BLOCK, state.recommendedAction)
    }

    @Test
    fun `a manual action selection survives a later pattern change`() = runTest(dispatcher) {
        viewModel.selectAction(RuleAction.ALLOW)

        // "+43*" alone would recommend SILENCE, but the explicit user choice must stick.
        viewModel.onPatternTextChanged("+43*")

        val state = viewModel.uiState.value
        assertEquals(RuleAction.SILENCE, state.recommendedAction)
        assertEquals(RuleAction.ALLOW, state.selectedAction)
    }

    @Test
    fun `a pattern matching a persisted rule is flagged as a duplicate and disables save`() = runTest(dispatcher) {
        val existing = Rule(
            "existing-1",
            PatternSyntax.parse("+436631234567"),
            RuleAction.BLOCK,
            true,
            null,
            Instant.EPOCH,
        )
        rulesFlow.value = listOf(existing)

        viewModel.onPatternTextChanged("+436631234567")

        val state = viewModel.uiState.value
        assertTrue(state.duplicate)
        assertFalse(state.saveEnabled)
    }

    @Test
    fun `a valid pattern with no matching persisted rule enables save`() = runTest(dispatcher) {
        viewModel.onPatternTextChanged("+43663*")

        val state = viewModel.uiState.value
        assertFalse(state.duplicate)
        assertTrue(state.saveEnabled)
    }

    @Test
    fun `save is a no-op while save is disabled`() = runTest(dispatcher) {
        assertFalse(viewModel.uiState.value.saveEnabled)

        viewModel.save()

        coVerify(exactly = 0) { ruleWriter.save(any()) }
        assertTrue(savedRules.isEmpty())
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun `save persists the current draft exactly once and marks the state saved`() = runTest(dispatcher) {
        val at = viewModel.uiState.value.countries.first { it.iso2 == "AT" }
        viewModel.selectCountry(at)
        assertTrue(viewModel.uiState.value.saveEnabled)

        viewModel.save()

        coVerify(exactly = 1) { ruleWriter.save(any()) }
        assertEquals(1, savedRules.size)
        val saved = savedRules.single()
        assertEquals(PatternSyntax.parse("+43*"), saved.pattern())
        assertEquals(RuleAction.SILENCE, saved.action())
        assertTrue(saved.enabled())
        assertNull(saved.label())
        assertEquals(fixedInstant, saved.createdAt())
        assertTrue(saved.id().isNotBlank())
        UUID.fromString(saved.id())
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `reset clears the draft but keeps the loaded country list`() = runTest(dispatcher) {
        val loadedCountries = viewModel.uiState.value.countries
        assertTrue(loadedCountries.isNotEmpty())
        viewModel.selectAction(RuleAction.ALLOW)
        viewModel.onPatternTextChanged("+43663*")
        assertEquals(RuleAction.ALLOW, viewModel.uiState.value.selectedAction)

        viewModel.reset()

        assertEquals(AddRuleUiState(countries = loadedCountries), viewModel.uiState.value)
    }

    @Test
    fun `reset re-enables auto-following the recommended action`() = runTest(dispatcher) {
        viewModel.selectAction(RuleAction.ALLOW)
        viewModel.onPatternTextChanged("+43663*")
        assertEquals(RuleAction.ALLOW, viewModel.uiState.value.selectedAction)

        viewModel.reset()
        viewModel.onPatternTextChanged("+43*")

        assertEquals(RuleAction.SILENCE, viewModel.uiState.value.selectedAction)
    }

    @Test
    fun `country query filters by matching calling code`() = runTest(dispatcher) {
        val fullList = viewModel.uiState.value.countries
        assertEquals(4, fullList.size)

        viewModel.onCountryQueryChanged("43")

        val filtered = viewModel.uiState.value.countries
        assertEquals(listOf("AT"), filtered.map { it.iso2 })
    }

    @Test
    fun `an empty country query restores the full country list`() = runTest(dispatcher) {
        val fullList = viewModel.uiState.value.countries

        viewModel.onCountryQueryChanged("43")
        viewModel.onCountryQueryChanged("")

        assertEquals(fullList, viewModel.uiState.value.countries)
    }
}
