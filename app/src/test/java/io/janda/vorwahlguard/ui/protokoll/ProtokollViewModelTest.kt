package io.janda.vorwahlguard.ui.protokoll

import io.janda.vorwahlguard.data.contacts.ContactNameResolver
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.events.CallEventEntity
import io.janda.vorwahlguard.data.rules.CreateRuleResult
import io.janda.vorwahlguard.data.rules.RuleWriter
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.port.out.CountryCatalog
import io.janda.vorwahlguard.ui.regeln.addrule.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
    private lateinit var ruleWriter: RuleWriter
    private lateinit var contactNameResolver: ContactNameResolver
    private val createdPatterns = mutableListOf<String>()
    private val createdActions = mutableListOf<RuleAction>()

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
        DecisionReason.RULE_MATCH.name,
    )
    private val silenceRow = CallEventEntity(
        "silence-1",
        Instant.parse("2026-07-13T10:00:00Z"),
        "+436631234567",
        false,
        "AT",
        "rule-silence",
        RuleAction.SILENCE.name,
        DecisionReason.RULE_MATCH.name,
    )
    private val allowRow = CallEventEntity(
        "allow-1",
        Instant.parse("2026-07-13T08:00:00Z"),
        "hashed-value",
        true,
        "AT",
        "rule-allow",
        RuleAction.ALLOW.name,
        DecisionReason.RULE_MATCH.name,
    )

    // An allowed call from a known contact (issue #85): no matched rule, reason CONTACT_BYPASS,
    // a real (unhashed) number the view model resolves a display name for.
    private val contactBypassRow = CallEventEntity(
        "contact-1",
        Instant.parse("2026-07-13T11:00:00Z"),
        "+436649999999",
        false,
        "AT",
        null,
        RuleAction.ALLOW.name,
        DecisionReason.CONTACT_BYPASS.name,
    )

    @Before
    fun setUp() {
        eventsFlow = MutableStateFlow(emptyList())
        dao = mockk()
        every { dao.observeNewestFirst() } returns eventsFlow

        countryCatalog = mockk()
        every { countryCatalog.byIso2("AT") } returns Optional.of(austria)

        ruleWriter = mockk()
        createdPatterns.clear()
        createdActions.clear()
        coEvery {
            ruleWriter.createIfAbsent(capture(createdPatterns), capture(createdActions))
        } returns CreateRuleResult.CREATED

        // Fail-closed default: no contact resolves unless a test opts in. Matches
        // ContactNameResolver's own null-on-no-match/no-permission contract.
        contactNameResolver = mockk()
        every { contactNameResolver.nameFor(any()) } returns null
    }

    private fun createViewModel(): ProtokollViewModel {
        return ProtokollViewModel(dao, countryCatalog, ruleWriter, contactNameResolver, dispatcher)
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

    // --- issue #76: create a rule from a log entry ---

    @Test
    fun `tapping a number row opens the picker with the E164 as the pattern`() = runTest(dispatcher) {
        eventsFlow.value = listOf(blockRow)
        val viewModel = createViewModel()
        val row = viewModel.uiState.value.events.single()

        viewModel.onRowClicked(row)

        val pending = viewModel.uiState.value.pendingRule
        assertEquals("+4915112345678", pending?.patternText)
        assertEquals("+4915112345678", pending?.numberLabel)
    }

    @Test
    fun `tapping a hashed row is a no-op — no number to build a rule from`() = runTest(dispatcher) {
        eventsFlow.value = listOf(allowRow) // isHashed = true
        val viewModel = createViewModel()
        val row = viewModel.uiState.value.events.single()

        viewModel.onRowClicked(row)

        assertNull(viewModel.uiState.value.pendingRule)
    }

    @Test
    fun `tapping a withheld row opens the picker with the PRIVATE pattern and no number label`() = runTest(dispatcher) {
        val privateRow = blockRow.copy(id = "private-1", numberOrHash = "PRIVATE", isHashed = false)
        eventsFlow.value = listOf(privateRow)
        val viewModel = createViewModel()
        val row = viewModel.uiState.value.events.single()

        viewModel.onRowClicked(row)

        val pending = viewModel.uiState.value.pendingRule
        assertEquals("PRIVATE", pending?.patternText)
        assertNull(pending?.numberLabel)
    }

    @Test
    fun `creating a rule for a new pattern delegates to createIfAbsent and emits RuleCreated`() = runTest(dispatcher) {
        coEvery {
            ruleWriter.createIfAbsent(capture(createdPatterns), capture(createdActions))
        } returns CreateRuleResult.CREATED
        eventsFlow.value = listOf(blockRow)
        val viewModel = createViewModel()
        val effects = mutableListOf<ProtokollEffect>()
        val job = launch { viewModel.effects.toList(effects) }
        viewModel.onRowClicked(viewModel.uiState.value.events.single())

        viewModel.createRule(RuleAction.BLOCK)

        // Duplicate gating + rule construction are RuleWriter's job now (issue #78); the
        // ViewModel only forwards the tapped row's pattern + action and maps the result.
        coVerify(exactly = 1) { ruleWriter.createIfAbsent("+4915112345678", RuleAction.BLOCK) }
        assertEquals(listOf("+4915112345678"), createdPatterns)
        assertEquals(listOf(RuleAction.BLOCK), createdActions)
        assertEquals(listOf(ProtokollEffect.RuleCreated(RuleAction.BLOCK)), effects)
        assertNull(viewModel.uiState.value.pendingRule)
        job.cancel()
    }

    @Test
    fun `createIfAbsent reporting ALREADY_EXISTS surfaces RuleAlreadyExists`() = runTest(dispatcher) {
        coEvery { ruleWriter.createIfAbsent(any(), any()) } returns CreateRuleResult.ALREADY_EXISTS
        eventsFlow.value = listOf(blockRow)
        val viewModel = createViewModel()
        val effects = mutableListOf<ProtokollEffect>()
        val job = launch { viewModel.effects.toList(effects) }
        viewModel.onRowClicked(viewModel.uiState.value.events.single())

        viewModel.createRule(RuleAction.ALLOW)

        assertEquals(listOf(ProtokollEffect.RuleAlreadyExists), effects)
        assertNull(viewModel.uiState.value.pendingRule)
        job.cancel()
    }

    @Test
    fun `dismissing the picker clears the pending rule without creating anything`() = runTest(dispatcher) {
        eventsFlow.value = listOf(blockRow)
        val viewModel = createViewModel()
        viewModel.onRowClicked(viewModel.uiState.value.events.single())
        assertTrue(viewModel.uiState.value.pendingRule != null)

        viewModel.dismissPendingRule()

        assertNull(viewModel.uiState.value.pendingRule)
        coVerify(exactly = 0) { ruleWriter.createIfAbsent(any(), any()) }
    }

    // --- issue #85: contact name in the log for contact-bypass rows ---

    @Test
    fun `a contact-bypass row carries the resolved contact name`() = runTest(dispatcher) {
        every { contactNameResolver.nameFor("+436649999999") } returns "Alex Kontakt"
        eventsFlow.value = listOf(contactBypassRow)

        val viewModel = createViewModel()
        val row = viewModel.uiState.value.events.single()

        assertEquals("Alex Kontakt", row.contactName)
        assertEquals(AllowReasonUi.CONTACT_BYPASS, row.allowReason)
    }

    @Test
    fun `a contact-bypass row with no resolvable name keeps a null contact name`() = runTest(dispatcher) {
        every { contactNameResolver.nameFor(any()) } returns null
        eventsFlow.value = listOf(contactBypassRow)

        val viewModel = createViewModel()
        val row = viewModel.uiState.value.events.single()

        assertNull(row.contactName)
    }

    @Test
    fun `a rule-match number row is never looked up for a contact name`() = runTest(dispatcher) {
        // blockRow is a RULE_MATCH, not a contact bypass — resolving a name for it would be wrong
        // and a wasted ContactsContract query.
        eventsFlow.value = listOf(blockRow)

        val viewModel = createViewModel()

        assertNull(viewModel.uiState.value.events.single().contactName)
        verify(exactly = 0) { contactNameResolver.nameFor(any()) }
    }

    @Test
    fun `repeated emissions of the same contact number resolve the name only once`() = runTest(dispatcher) {
        every { contactNameResolver.nameFor("+436649999999") } returns "Alex Kontakt"
        eventsFlow.value = listOf(contactBypassRow)
        val viewModel = createViewModel()

        // A second emission re-maps the whole list; the per-number memo must serve the cached name
        // rather than re-querying.
        eventsFlow.value = listOf(contactBypassRow.copy(id = "contact-2"))

        assertEquals("Alex Kontakt", viewModel.uiState.value.events.single().contactName)
        verify(exactly = 1) { contactNameResolver.nameFor("+436649999999") }
    }
}
