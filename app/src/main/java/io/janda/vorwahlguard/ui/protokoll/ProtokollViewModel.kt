package io.janda.vorwahlguard.ui.protokoll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.di.DefaultDispatcher
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import java.time.ZoneId
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
 * Backs `ProtokollScreen`'s list body (issue #25). This class only ever reads off [CallEventDao]'s
 * already-observing Flow, never touches disk on a UI-triggered call — CLAUDE.md §3 rule 1's "no
 * disk I/O on the hot path" concerns `onScreenCall()` itself, not this screen. Maps each
 * [io.janda.vorwahlguard.data.events.CallEventEntity] to a [CallEventRowUi] via
 * [CallEventRowUiMapper] built internally — the same "ViewModel owns its own small mapper" idiom
 * `AddRuleViewModel`/`RegelnViewModel` use.
 */
@HiltViewModel
class ProtokollViewModel @Inject constructor(
    private val dao: CallEventDao,
    countryCatalog: CountryCatalog,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mapper = CallEventRowUiMapper(countryCatalog)

    private var allRows: List<CallEventRowUi> = emptyList()
    private var selectedFilter: RuleAction? = null
    private var loaded = false

    private val _uiState = MutableStateFlow(ProtokollUiState())
    val uiState: StateFlow<ProtokollUiState> = _uiState.asStateFlow()

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

    private fun recompute() {
        val filtered = allRows.filter { selectedFilter == null || it.action == selectedFilter }
        _uiState.update {
            it.copy(
                events = filtered,
                selectedFilter = selectedFilter,
                hasAnyEvents = allRows.isNotEmpty(),
                loaded = loaded,
            )
        }
    }
}
