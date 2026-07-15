package io.janda.vorwahlguard.ui.regeln.addrule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.data.rules.RuleWriter
import io.janda.vorwahlguard.di.DefaultDispatcher
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.Pattern
import io.janda.vorwahlguard.domain.model.PatternKind
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.screening.SimRegionProvider
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs `AddRuleSheet` (issue #6). Owns the single [AddRuleUiState] draft that both the
 * "Land wählen" and "Vorwahl eingeben" tabs feed. All pattern validation goes through
 * [PatternSyntax] and all "Nummer testen" matching goes through the real domain
 * [io.janda.vorwahlguard.domain.service.RuleMatcher] via [DraftRuleTester] — never a hand-rolled
 * comparison in this class (CLAUDE.md §4).
 */
@HiltViewModel
class AddRuleViewModel @Inject constructor(
    private val countryCatalog: CountryCatalog,
    private val ruleSnapshotSource: RuleSnapshotSource,
    private val ruleWriter: RuleWriter,
    numberNormalizer: NumberNormalizer,
    private val simRegionProvider: SimRegionProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val countryUiMapper = CountryUiMapper(countryCatalog)
    private val draftRuleTester = DraftRuleTester(numberNormalizer)

    private val _uiState = MutableStateFlow(AddRuleUiState())
    val uiState: StateFlow<AddRuleUiState> = _uiState.asStateFlow()

    private val allCountriesFlow = MutableStateFlow<List<CountryUi>>(emptyList())
    private val countryQueryFlow = MutableStateFlow("")
    private val persistedRulesFlow = MutableStateFlow<List<Rule>>(emptyList())

    /** Every known E.164 calling code, used by [isBareCountryPrefix]. Empty until the first load. */
    @Volatile
    private var knownCallingCodes: Set<Int> = emptySet()

    /** `true` once the user has explicitly picked an action — stops auto-following [recommendedActionFor]. */
    private var manualActionOverride = false

    init {
        viewModelScope.launch(defaultDispatcher) {
            simRegionProvider.refresh()
            val countries = countryUiMapper.countriesFor(Locale.getDefault())
            knownCallingCodes = countries.map { it.callingCode }.toSet()
            allCountriesFlow.value = countries
        }

        viewModelScope.launch {
            ruleSnapshotSource.observe().collect { persistedRulesFlow.value = it }
        }

        viewModelScope.launch {
            combine(allCountriesFlow, countryQueryFlow) { countries, query -> countries to query }
                .collectLatest { (countries, query) ->
                    val filtered = withContext(defaultDispatcher) {
                        countries.filter { countryUiMapper.matches(it, query) }
                    }
                    _uiState.update { it.copy(countries = filtered) }
                }
        }

        viewModelScope.launch {
            val patternFlow = uiState.map { it.patternText to it.patternValid }.distinctUntilChanged()
            combine(patternFlow, persistedRulesFlow) { (patternText, patternValid), rules ->
                val duplicate = patternValid && rules.any { rule -> rule.pattern().text() == patternText }
                val saveEnabled = patternValid && !duplicate
                duplicate to saveEnabled
            }.collectLatest { (duplicate, saveEnabled) ->
                _uiState.update { it.copy(duplicate = duplicate, saveEnabled = saveEnabled) }
            }
        }

        viewModelScope.launch {
            val triggerFlow = uiState
                .map { TestTrigger(it.testInput, it.patternText, it.patternValid, it.selectedAction) }
                .distinctUntilChanged()
            combine(triggerFlow, persistedRulesFlow) { trigger, rules -> trigger to rules }
                .collectLatest { (trigger, rules) ->
                    val outcome = computeTestOutcome(trigger, rules)
                    _uiState.update { it.copy(testOutcome = outcome) }
                }
        }
    }

    fun selectTab(tab: AddRuleTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun onCountryQueryChanged(query: String) {
        _uiState.update { it.copy(countryQuery = query) }
        countryQueryFlow.value = query
    }

    fun selectCountry(country: CountryUi) {
        viewModelScope.launch(defaultDispatcher) {
            val patternText = "+${country.callingCode}*"
            val patternValid = PatternSyntax.isValid(patternText)
            val collateral = computeCollateral(country)
            val recommended = RuleAction.SILENCE
            _uiState.update { state ->
                state.copy(
                    selectedCountry = country,
                    collateral = collateral,
                    patternText = patternText,
                    patternValid = patternValid,
                    patternMissingWildcard = false,
                    recommendedAction = recommended,
                    selectedAction = if (manualActionOverride) state.selectedAction else recommended,
                )
            }
        }
    }

    /**
     * The pinned "Unterdrückte Nummer" entry in the country picker (issue #60): selects the
     * reserved [PatternSyntax.PRIVATE_TOKEN] pattern the same way tapping a country selects its
     * calling-code prefix. Recommended action is SILENCE — the same safe-default reasoning as
     * for country-wide rules (CLAUDE.md §5).
     */
    fun selectPrivateRule() {
        val recommended = RuleAction.SILENCE
        _uiState.update { state ->
            state.copy(
                selectedCountry = null,
                collateral = null,
                patternText = PatternSyntax.PRIVATE_TOKEN,
                patternValid = true,
                patternMissingWildcard = false,
                recommendedAction = recommended,
                selectedAction = if (manualActionOverride) state.selectedAction else recommended,
            )
        }
    }

    fun onPatternTextChanged(text: String) {
        val valid = PatternSyntax.isValid(text)
        val parsed = if (valid) PatternSyntax.parse(text) else null
        val recommended = if (parsed != null) recommendedActionFor(parsed) else RuleAction.BLOCK
        val missingWildcard = parsed != null && isBareCallingCodeWithoutWildcard(parsed)
        _uiState.update { state ->
            state.copy(
                patternText = text,
                patternValid = valid,
                patternMissingWildcard = missingWildcard,
                selectedCountry = null,
                collateral = null,
                recommendedAction = recommended,
                selectedAction = if (manualActionOverride) state.selectedAction else recommended,
            )
        }
    }

    fun selectAction(action: RuleAction) {
        manualActionOverride = true
        _uiState.update { it.copy(selectedAction = action) }
    }

    fun onTestInputChanged(text: String) {
        _uiState.update { it.copy(testInput = text) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.saveEnabled || !PatternSyntax.isValid(state.patternText)) {
            return
        }
        viewModelScope.launch {
            // saveEnabled already gates on the live duplicate check for UI feedback;
            // createIfAbsent is the atomic backstop (issue #78) — if a rule for this pattern was
            // created elsewhere in the meantime it simply no-ops, never a second identical rule.
            ruleWriter.createIfAbsent(state.patternText, state.selectedAction)
            _uiState.update { it.copy(saved = true) }
        }
    }

    /** Clears the draft, e.g. when the sheet is dismissed. Keeps the already-loaded country list. */
    fun reset() {
        manualActionOverride = false
        countryQueryFlow.value = ""
        _uiState.value = AddRuleUiState(countries = allCountriesFlow.value)
    }

    private fun computeCollateral(country: CountryUi): CollateralInfo? {
        val otherRegions = countryCatalog.regionsFor(country.callingCode).filter { it != country.iso2 }
        if (otherRegions.isEmpty()) {
            return null
        }
        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale)
        val sortedNames = otherRegions
            .map { iso2 -> Country(iso2, country.callingCode).displayName(locale) }
            .sortedWith(collator)
        return CollateralInfo(
            firstOtherRegionName = sortedNames.first(),
            remainingOtherCount = sortedNames.size - 1,
        )
    }

    /**
     * CLAUDE.md §5: a bare calling-code pattern (the whole [PatternKind.PREFIX] digit string is
     * exactly a known calling code, not just prefixed by one) recommends [RuleAction.SILENCE] —
     * it is the safe default for a country-wide rule. A [PatternKind.PRIVATE] pattern gets the
     * same recommendation (issue #60), and via the same path whether it came from the pinned
     * picker entry or was typed literally into the free-text tab.
     */
    private fun recommendedActionFor(pattern: Pattern): RuleAction =
        if (pattern.kind() == PatternKind.PRIVATE || isBareCountryPrefix(pattern)) {
            RuleAction.SILENCE
        } else {
            RuleAction.BLOCK
        }

    private fun isBareCountryPrefix(pattern: Pattern): Boolean {
        if (pattern.kind() != PatternKind.PREFIX) {
            return false
        }
        val callingCode = pattern.digits()?.toIntOrNull() ?: return false
        return knownCallingCodes.contains(callingCode)
    }

    /**
     * A free-text pattern with no trailing `*` parses as [PatternKind.EXACT] — a real, complete
     * phone number, matched literally. If its digits are exactly a known calling code (e.g. the
     * user typed `+43` instead of `+43*`), it can never match any real incoming call: no genuine
     * E.164 number is just a bare calling code with nothing after it. This is almost always a
     * missing `*`, not a deliberate rule — see [AddRuleUiState.patternMissingWildcard].
     */
    private fun isBareCallingCodeWithoutWildcard(pattern: Pattern): Boolean {
        if (pattern.kind() != PatternKind.EXACT) {
            return false
        }
        val callingCode = pattern.digits()?.toIntOrNull() ?: return false
        return knownCallingCodes.contains(callingCode)
    }

    private suspend fun computeTestOutcome(trigger: TestTrigger, persistedRules: List<Rule>): TestOutcome? {
        if (trigger.input.isBlank() || !trigger.patternValid) {
            return null
        }
        return withContext(defaultDispatcher) {
            val pattern = PatternSyntax.parse(trigger.patternText)
            draftRuleTester.test(
                persistedRules,
                pattern,
                trigger.action,
                trigger.input,
                simRegionProvider.current(),
            )
        }
    }

    private data class TestTrigger(
        val input: String,
        val patternText: String,
        val patternValid: Boolean,
        val action: RuleAction,
    )
}
