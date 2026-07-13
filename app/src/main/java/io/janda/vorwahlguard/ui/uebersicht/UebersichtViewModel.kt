package io.janda.vorwahlguard.ui.uebersicht

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.rules.RuleSnapshotSource
import io.janda.vorwahlguard.di.DefaultDispatcher
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.screening.CallScreeningRoleProvider
import io.janda.vorwahlguard.ui.regeln.RuleRowUiMapper
import java.time.Instant
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
 * Backs `UebersichtScreen`'s dashboard body (issue #27): the hero counter, 30-day sparkline, and
 * top-countries/top-rules lists are all derived from a 5-way [combine] of [CallEventDao]'s
 * aggregate Flows plus [RuleSnapshotSource.observe] (the same source `RegelnViewModel` reads),
 * mapped via [DashboardUiMapper] and [RuleRowUiMapper] — both built internally, the same
 * "ViewModel owns its own small mapper" idiom `RegelnViewModel`/`ProtokollViewModel` use. Role
 * status ([CallScreeningRoleProvider]) is read separately: it does not come from Room, and
 * [refreshRoleStatus] lets `UebersichtScreen` re-check it on `ON_RESUME` without touching the
 * aggregate Flows at all.
 */
@HiltViewModel
class UebersichtViewModel @Inject constructor(
    private val dao: CallEventDao,
    private val ruleSnapshotSource: RuleSnapshotSource,
    countryCatalog: CountryCatalog,
    private val roleProvider: CallScreeningRoleProvider,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mapper = DashboardUiMapper(countryCatalog)
    private val ruleRowMapper = RuleRowUiMapper(countryCatalog)

    /**
     * Lower bound for the DAO query only, computed once at construction rather than per emission.
     * [DashboardUiMapper.toSparkline] re-derives the actual 30-day bucket window from a fresh
     * `Instant.now()` on every emission and discards anything outside it, so the *rendered*
     * sparkline does slide day to day across a long-lived ViewModel — only the query's lower
     * bound stays fixed, which just means a harmless, shrinking over-fetch (never a stale render)
     * the longer the ViewModel lives past construction.
     */
    private val thirtyDaysAgoMillis = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()

    private val _uiState = MutableStateFlow(UebersichtUiState())
    val uiState: StateFlow<UebersichtUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(roleAvailable = roleProvider.isRoleAvailable(), roleHeld = roleProvider.isRoleHeld())
        }

        viewModelScope.launch {
            combine(
                dao.observeTotalCount(),
                dao.observeOccurredAtSince(thirtyDaysAgoMillis),
                dao.observeTopRegions(),
                dao.observeTopRules(),
                ruleSnapshotSource.observe(),
            ) { totalCount, occurredAtMillis, topRegions, topRules, rules ->
                withContext(defaultDispatcher) {
                    val locale = Locale.getDefault()
                    val zone = ZoneId.systemDefault()
                    val sparkline = mapper.toSparkline(occurredAtMillis, Instant.now(), zone)
                    val topCountries = topRegions.map { mapper.toTopCountry(it, locale) }
                    val rulesById = rules.associateBy { it.id() }
                    val topRuleRows = mapper.toTopRules(topRules, rulesById, ruleRowMapper, locale)
                    DashboardData(totalCount, sparkline, topCountries, topRuleRows)
                }
            }.collect { data ->
                _uiState.update { current ->
                    current.copy(
                        totalScreened = data.totalScreened,
                        sparkline = data.sparkline,
                        topCountries = data.topCountries,
                        topRules = data.topRules,
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

    private data class DashboardData(
        val totalScreened: Int,
        val sparkline: List<DaySparkPoint>,
        val topCountries: List<TopCountryUi>,
        val topRules: List<TopRuleUi>,
    )
}
