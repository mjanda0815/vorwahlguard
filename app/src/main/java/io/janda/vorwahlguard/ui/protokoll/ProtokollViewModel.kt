package io.janda.vorwahlguard.ui.protokoll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.data.rules.RuleWriter
import io.janda.vorwahlguard.di.DefaultDispatcher
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs `ProtokollScreen`'s list body (issue #25). Reads off [CallEventDao]'s already-observing
 * Flow and — for the "create rule from a log entry" action (issue #76) — writes a [Rule] through
 * [RuleWriter], the same "ViewModel talks to the adapter directly" idiom `AddRuleViewModel` uses.
 * Neither path touches disk on the `onScreenCall()` hot path (CLAUDE.md §3 rule 1 concerns the
 * screening service, not this screen). Maps each
 * [io.janda.vorwahlguard.data.events.CallEventEntity] to a [CallEventRowUi] via
 * [CallEventRowUiMapper] built internally.
 */
@HiltViewModel
class ProtokollViewModel @Inject constructor(
    private val dao: CallEventDao,
    countryCatalog: CountryCatalog,
    private val ruleSnapshotSource: RuleSnapshotSource,
    private val ruleWriter: RuleWriter,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mapper = CallEventRowUiMapper(countryCatalog)

    private var allRows: List<CallEventRowUi> = emptyList()
    private var selectedFilter: RuleAction? = null
    private var loaded = false
    private var pendingRule: PendingRule? = null

    /** The pattern text of every persisted rule, kept current to gate duplicate creation (issue #76). */
    private var existingPatternTexts: Set<String> = emptySet()

    private val _uiState = MutableStateFlow(ProtokollUiState())
    val uiState: StateFlow<ProtokollUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ProtokollEffect>(Channel.BUFFERED)
    val effects: Flow<ProtokollEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            dao.observeNewestFirst().collect { entities ->
                // observeNewestFirst() already orders by occurred_at DESC — trust that order,
                // do not re-sort here.
                allRows = withContext(defaultDispatcher) {
                    entities.mapNotNull { mapper.toRow(it, Locale.getDefault(), ZoneId.systemDefault()) }
                }
                loaded = true
                recompute()
            }
        }
        viewModelScope.launch {
            ruleSnapshotSource.observe().collect { rules ->
                existingPatternTexts = rules.map { it.pattern().text() }.toSet()
            }
        }
    }

    /** `null` clears the filter ("All"). */
    fun setFilter(action: RuleAction?) {
        selectedFilter = action
        recompute()
    }

    /**
     * A tap on a log row (issue #76). Only rows with a recoverable [CallEventRowUi.rulePattern]
     * open the action picker; a pseudonymised row (no number) is a no-op.
     */
    fun onRowClicked(row: CallEventRowUi) {
        val pattern = row.rulePattern ?: return
        val numberLabel = when (val display = row.display) {
            is CallEventDisplay.Number -> display.e164
            CallEventDisplay.Private -> null
            is CallEventDisplay.Region, is CallEventDisplay.UnknownRegion -> return
        }
        pendingRule = PendingRule(pattern, numberLabel)
        recompute()
    }

    fun dismissPendingRule() {
        pendingRule = null
        recompute()
    }

    /**
     * Persists a rule with [action] for the pending row's pattern, unless a rule with that exact
     * pattern text already exists — in which case nothing is written and
     * [ProtokollEffect.RuleAlreadyExists] is emitted (issue #76). Closes the dialog either way.
     */
    fun createRule(action: RuleAction) {
        val pending = pendingRule ?: return
        pendingRule = null
        recompute()

        if (existingPatternTexts.contains(pending.patternText)) {
            viewModelScope.launch { _effects.send(ProtokollEffect.RuleAlreadyExists) }
            return
        }
        val pattern = runCatching { PatternSyntax.parse(pending.patternText) }.getOrNull() ?: return
        viewModelScope.launch {
            ruleWriter.save(Rule(UUID.randomUUID().toString(), pattern, action, true, null, clock.now()))
            _effects.send(ProtokollEffect.RuleCreated(action))
        }
    }

    private fun recompute() {
        val filtered = allRows.filter { selectedFilter == null || it.action == selectedFilter }
        _uiState.update {
            it.copy(
                events = filtered,
                selectedFilter = selectedFilter,
                hasAnyEvents = allRows.isNotEmpty(),
                loaded = loaded,
                pendingRule = pendingRule,
            )
        }
    }
}

/** One-shot outcomes of the "create rule from a log entry" action (issue #76). */
sealed interface ProtokollEffect {
    data class RuleCreated(val action: RuleAction) : ProtokollEffect
    data object RuleAlreadyExists : ProtokollEffect
}
