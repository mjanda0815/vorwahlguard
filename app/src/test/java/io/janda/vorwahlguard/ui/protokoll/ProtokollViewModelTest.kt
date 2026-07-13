package io.janda.vorwahlguard.ui.protokoll

import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.events.CallEventEntity
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.ui.regeln.addrule.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test

/**
 * Covers [ProtokollViewModel]: that it trusts [CallEventDao.observeNewestFirst]'s ordering rather
 * than re-sorting, the `loaded`/`hasAnyEvents`/`events` state-shape distinctions
 * [ProtokollUiState]'s KDoc documents, [ProtokollViewModel.setFilter] filtering by
 * [RuleAction] without perturbing `hasAnyEvents`, and that a row whose action no longer parses
 * is silently dropped end to end through the `mapNotNull` in `init`. [UnconfinedTestDispatcher]
 * backs both `Dispatchers.Main` (via [MainDispatcherRule]) and the injected `@DefaultDispatcher`
 * argument, so `init`'s collection runs eagerly and synchronously (same strategy as
 * `RegelnViewModelTest`).
 */
class ProtokollViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:JUnitRule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val austria = Country("AT", 43)

    private lateinit var eventsFlow: MutableStateFlow<List<CallEventEntity>>
    private lateinit var dao: CallEventDao
    private lateinit var countryCatalog: CountryCatalog

    // Fixtures deliberately constructed in an order that would look "wrong" if the view model
    // re-sorted by occurredAt: silenceRow's occurredAt is *newer* than blockRow's, so a
    // re-sort-by-createdAt-descending would move it to the front — but the DAO already returned
    // it second, and the view model must preserve that.
    private val blockRow = CallEventEntity(
        "block-1",
        Instant.parse("2026-07-13T09:00:00Z"),
        "+4915112345678",
        false,
        "DE",
        "rule-block",
        RuleAction.BLOCK.name,
    )
    private val silenceRow = CallEventEntity(
        "silence-1",
        Instant.parse("2026-07-13T10:00:00Z"),
        "+436631234567",
        false,
        "AT",
        "rule-silence",
        RuleAction.SILENCE.name,
    )
    private val allowRow = CallEventEntity(
        "allow-1",
        Instant.parse("2026-07-13T08:00:00Z"),
        "hashed-value",
        true,
        "AT",
        "rule-allow",
        RuleAction.ALLOW.name,
    )

    @Before
    fun setUp() {
        eventsFlow = MutableStateFlow(emptyList())
        dao = mockk()
        every { dao.observeNewestFirst() } returns eventsFlow

        countryCatalog = mockk()
        every { countryCatalog.byIso2("AT") } returns Optional.of(austria)
    }

    private fun createViewModel(): ProtokollViewModel {
        return ProtokollViewModel(dao, countryCatalog, dispatcher)
    }

    @Test
    fun `a mixed emission is reflected in DB order without re-sorting`() = runTest(dispatcher) {
        // DAO order: block-1, silence-1, allow-1 — silence-1 has the newest occurredAt, so this
        // order is only reproducible if the view model does not re-sort.
        eventsFlow.value = listOf(blockRow, silenceRow, allowRow)

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(listOf("block-1", "silence-1", "allow-1"), state.events.map { it.id })
    }

    @Test
    fun `a mixed emission marks the state loaded with events present`() = runTest(dispatcher) {
        eventsFlow.value = listOf(blockRow, silenceRow, allowRow)

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.loaded)
        assertTrue(state.hasAnyEvents)
    }

    @Test
    fun `an empty emission produces a loaded state with no events and hasAnyEvents false`() = runTest(dispatcher) {
        eventsFlow.value = emptyList()

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.loaded)
        assertTrue(state.events.isEmpty())
        assertFalse(state.hasAnyEvents)
    }

    @Test
    fun `setFilter to BLOCK restricts events to BLOCK-action rows only`() = runTest(dispatcher) {
        eventsFlow.value = listOf(blockRow, silenceRow, allowRow)
        val viewModel = createViewModel()

        viewModel.setFilter(RuleAction.BLOCK)

        assertEquals(listOf("block-1"), viewModel.uiState.value.events.map { it.id })
    }

    @Test
    fun `setFilter updates selectedFilter on the state`() = runTest(dispatcher) {
        eventsFlow.value = listOf(blockRow, silenceRow, allowRow)
        val viewModel = createViewModel()

        viewModel.setFilter(RuleAction.BLOCK)

        assertEquals(RuleAction.BLOCK, viewModel.uiState.value.selectedFilter)
    }

    @Test
    fun `filtering to an action with zero matching rows still leaves hasAnyEvents true`() = runTest(dispatcher) {
        // Only BLOCK and SILENCE rows exist; filtering to ALLOW yields an empty events list, but
        // hasAnyEvents must still reflect the unfiltered set (distinguishes "no events at all"
        // from "filter matched nothing" per ProtokollUiState's KDoc).
        eventsFlow.value = listOf(blockRow, silenceRow)
        val viewModel = createViewModel()

        viewModel.setFilter(RuleAction.ALLOW)
        val state = viewModel.uiState.value

        assertTrue(state.events.isEmpty())
        assertTrue(state.hasAnyEvents)
    }

    @Test
    fun `setFilter to null after a prior filter restores the full unfiltered list`() = runTest(dispatcher) {
        eventsFlow.value = listOf(blockRow, silenceRow, allowRow)
        val viewModel = createViewModel()
        viewModel.setFilter(RuleAction.BLOCK)

        viewModel.setFilter(null)
        val state = viewModel.uiState.value

        assertNull(state.selectedFilter)
        assertEquals(listOf("block-1", "silence-1", "allow-1"), state.events.map { it.id })
    }

    @Test
    fun `an entity with an unparseable action is silently dropped from events`() = runTest(dispatcher) {
        val garbageRow = blockRow.copy(id = "garbage-1", action = "GARBAGE")
        eventsFlow.value = listOf(garbageRow, silenceRow)

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(listOf("silence-1"), state.events.map { it.id })
    }

    @Test
    fun `an entity with an unparseable action does not crash the collection`() = runTest(dispatcher) {
        val garbageRow = blockRow.copy(id = "garbage-1", action = "GARBAGE")
        eventsFlow.value = listOf(garbageRow, silenceRow)

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loaded)
    }
}
