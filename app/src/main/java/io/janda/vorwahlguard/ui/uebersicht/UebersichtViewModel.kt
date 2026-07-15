package io.janda.vorwahlguard.ui.uebersicht

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.events.ActionReasonCount
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.events.RegionCount
import io.janda.vorwahlguard.data.events.RuleIdCount
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.di.DefaultDispatcher
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.screening.CallScreeningRoleProvider
import io.janda.vorwahlguard.ui.regeln.RuleRowUiMapper
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs `UebersichtScreen`'s dashboard body (issue #27): the hero counter, 30-day sparkline,
 * top-countries/top-rules lists, and the action breakdown (issue #59) are all derived from
 * [CallEventDao]'s aggregate Flows plus [RuleSnapshotSource.observe] (the same source
 * `RegelnViewModel` reads) — combined in two steps, since [combine] only has a typed overload up
 * to five Flows and there are six sources in total. Mapped via [DashboardUiMapper] and
 * [RuleRowUiMapper] — both built internally, the same "ViewModel owns its own small mapper" idiom
 * `RegelnViewModel`/`ProtokollViewModel` use. Role status ([CallScreeningRoleProvider]) is read
 * separately: it does not come from Room, and [refreshRoleStatus] lets `UebersichtScreen`
 * re-check it on `ON_RESUME` without touching the aggregate Flows at all.
 */
@HiltViewModel
class UebersichtViewModel @Inject constructor(
    private val dao: CallEventDao,
    private val ruleSnapshotSource: RuleSnapshotSource,
    countryCatalog: CountryCatalog,
    private val roleProvider: CallScreeningRoleProvider,
    private val clock: Clock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mapper = DashboardUiMapper(countryCatalog)
    private val ruleRowMapper = RuleRowUiMapper(countryCatalog)

    /**
     * Lower bound for the DAO query only, computed once at construction rather than per emission.
     * [DashboardUiMapper.toSparkline] re-derives the actual 30-day bucket window from a fresh
     * [Clock.now] on every emission and discards anything outside it, so the *rendered*
     * sparkline does slide day to day across a long-lived ViewModel — only the query's lower
     * bound stays fixed, which just means a harmless, shrinking over-fetch (never a stale render)
     * the longer the ViewModel lives past construction.
     */
    private val thirtyDaysAgoMillis = clock.now().minus(30, ChronoUnit.DAYS).toEpochMilli()

    private val _uiState = MutableStateFlow(UebersichtUiState())
    val uiState: StateFlow<UebersichtUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(roleAvailable = roleProvider.isRoleAvailable(), roleHeld = roleProvider.isRoleHeld())
        }

        viewModelScope.launch {
            // Six sources exceed combine()'s typed 5-arg overload, so the five CallEventDao
            // aggregate Flows are combined first, then combined again with the rule snapshot.
            val aggregates = combine(
                dao.observeTotalCount(),
                dao.observeOccurredAtSince(thirtyDaysAgoMillis),
                dao.observeTopRegions(),
                dao.observeTopRules(),
                dao.observeActionBreakdown(),
                ::Aggregates,
            )
            combine(aggregates, ruleSnapshotSource.observe()) { agg, rules ->
                withContext(defaultDispatcher) {
                    val locale = Locale.getDefault()
                    val zone = ZoneId.systemDefault()
                    val sparkline = mapper.toSparkline(agg.occurredAtMillis, clock.now(), zone)
                    val topCountries = agg.topRegions.map { mapper.toTopCountry(it, locale) }
                    val rulesById = rules.associateBy { it.id() }
                    val topRuleRows = mapper.toTopRules(agg.topRules, rulesById, ruleRowMapper, locale)
                    val breakdown = mapper.toBreakdown(agg.actionBreakdown)
                    DashboardData(agg.totalCount, sparkline, topCountries, topRuleRows, breakdown)
                }
            }.collect { data ->
                _uiState.update { current ->
                    current.copy(
                        totalScreened = data.totalScreened,
                        sparkline = data.sparkline,
                        topCountries = data.topCountries,
                        topRules = data.topRules,
                        actionBreakdown = data.actionBreakdown,
                        hasAnyEvents = data.totalScreened > 0,
                        loaded = true,
                    )
                }
            }
        }
    }

    /** Re-checks role status without touching the aggregate Flows — call on `ON_RESUME`. */
    fun refreshRoleStatus() {
        _uiState.update {
            it.copy(roleAvailable = roleProvider.isRoleAvailable(), roleHeld = roleProvider.isRoleHeld())
        }
    }

    fun createRoleRequestIntent(): Intent? = roleProvider.createRoleRequestIntent()

    /** Intermediate combine() step — see the comment in [init] for why this exists. */
    private data class Aggregates(
        val totalCount: Int,
        val occurredAtMillis: List<Long>,
        val topRegions: List<RegionCount>,
        val topRules: List<RuleIdCount>,
        val actionBreakdown: List<ActionReasonCount>,
    )

    private data class DashboardData(
        val totalScreened: Int,
        val sparkline: List<DaySparkPoint>,
        val topCountries: List<TopCountryUi>,
        val topRules: List<TopRuleUi>,
        val actionBreakdown: List<BreakdownEntry>,
    )
}
