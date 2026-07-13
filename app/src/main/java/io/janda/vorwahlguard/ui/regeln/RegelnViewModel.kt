package io.janda.vorwahlguard.ui.regeln

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.data.rules.RuleWriter
import io.janda.vorwahlguard.di.DefaultDispatcher
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleSection
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs `RegelnScreen`'s list body (issue #23). Reuses the same [RuleSnapshotSource] the
 * screening hot path warms from (CLAUDE.md §3 rule 1 does not apply here — this class only ever
 * reads off the already-observing Flow, never touches disk on a UI-triggered call), partitions
 * the rules into whitelist/blacklist via [RuleSection.of], and maps each [Rule] to a [RuleRowUi]
 * via [RuleRowUiMapper] built internally — the same "ViewModel owns its own small mapper"
 * idiom `AddRuleViewModel` uses for `CountryUiMapper`/`DraftRuleTester`.
 */
@HiltViewModel
class RegelnViewModel @Inject constructor(
    ruleSnapshotSource: RuleSnapshotSource,
    private val ruleWriter: RuleWriter,
    countryCatalog: CountryCatalog,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val ruleRowUiMapper = RuleRowUiMapper(countryCatalog)

    private val _uiState = MutableStateFlow(RegelnUiState())
    val uiState: StateFlow<RegelnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ruleSnapshotSource.observe().collect { rules ->
                val (whitelist, blacklist) = withContext(defaultDispatcher) {
                    mapAndPartition(rules)
                }
                _uiState.update { it.copy(whitelist = whitelist, blacklist = blacklist, loaded = true) }
            }
        }
    }

    /** Removes a rule; the list updates itself once [RuleSnapshotSource]'s Flow re-emits. */
    fun deleteRule(id: String) {
        viewModelScope.launch {
            ruleWriter.delete(id)
        }
    }

    private fun mapAndPartition(rules: List<Rule>): Pair<List<RuleRowUi>, List<RuleRowUi>> {
        val locale = Locale.getDefault()
        val comparator = compareByDescending<Rule> { it.createdAt() }.thenBy { it.pattern().text() }
        val (whitelistRules, blacklistRules) = rules.partition { RuleSection.of(it) == RuleSection.WHITELIST }
        val whitelist = whitelistRules.sortedWith(comparator).map { ruleRowUiMapper.toRow(it, locale) }
        val blacklist = blacklistRules.sortedWith(comparator).map { ruleRowUiMapper.toRow(it, locale) }
        return whitelist to blacklist
    }
}
