package io.janda.vorwahlguard.ui.protokoll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.rules.CreateRuleResult
import io.janda.vorwahlguard.data.rules.RuleWriter
import io.janda.vorwahlguard.di.DefaultDispatcher
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import java.time.ZoneId
import java.util.Locale
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
    private val ruleWriter: RuleWriter,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mapper = CallEventRowUiMapper(countryCatalog)

    private var allRows: List<CallEventRowUi> = emptyList()
    private var selectedFilter: RuleAction? = null
    private var loaded = false
    private var pendingRule: PendingRule? = null

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
     * Persists a rule with [action] for the pending row's pattern via the shared, atomic
     * [RuleWriter.createIfAbsent] (issue #76/#78): it creates the rule only if none with that
     * exact pattern exists, and reports which happened. Closes the dialog either way.
     */
    fun createRule(action: RuleAction) {
        val pending = pendingRule ?: return
        pendingRule = null
        recompute()

        viewModelScope.launch {
            val effect = when (ruleWriter.createIfAbsent(pending.patternText, action)) {
                CreateRuleResult.CREATED -> ProtokollEffect.RuleCreated(action)
                CreateRuleResult.ALREADY_EXISTS -> ProtokollEffect.RuleAlreadyExists
            }
            _effects.send(effect)
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
