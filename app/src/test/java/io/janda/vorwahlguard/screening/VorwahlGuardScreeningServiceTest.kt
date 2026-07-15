package io.janda.vorwahlguard.screening

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService.CallResponse
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.events.RetentionPurger
import io.janda.vorwahlguard.data.rules.CachedRuleRepository
import io.janda.vorwahlguard.data.settings.CachedSettingsRepository
import io.janda.vorwahlguard.domain.model.CallEvent
import io.janda.vorwahlguard.domain.model.DecisionReason
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.model.ScreeningDecision
import io.janda.vorwahlguard.domain.model.Settings
import io.janda.vorwahlguard.domain.port.`in`.ScreenIncomingCall
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.ContactsLookup
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.mockk.Runs
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the hot-path invariants from CLAUDE.md §3 / issue #5: exactly one [respondToCall] per
 * call, the null-handle guard, fail-open on any exception, the BLOCK/SILENCE/ALLOW mapping,
 * cache warming in [onCreate], and the "only matched decisions are recorded" gate (ADR 0006).
 *
 * The service is a [spyk] so the framework-final [respondToCall] can be stubbed and captured
 * without a bound `CallScreeningAdapter`; [backgroundDispatcher] is swapped for
 * [Dispatchers.Unconfined] so cache warming and event recording run synchronously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class VorwahlGuardScreeningServiceTest {

    private lateinit var service: VorwahlGuardScreeningService
    private lateinit var screenIncomingCall: ScreenIncomingCall
    private lateinit var normalizer: NumberNormalizer
    private lateinit var contactsLookup: ContactsLookup
    private lateinit var contactsCache: CachedContactsLookup
    private lateinit var ruleCache: CachedRuleRepository
    private lateinit var simRegionProvider: SimRegionProvider
    private lateinit var settingsCache: CachedSettingsRepository
    private lateinit var recorder: CallEventRecorder
    private lateinit var purger: RetentionPurger
    private lateinit var clock: Clock

    /** Every [respondToCall] the service issues, in order, for count and flag assertions. */
    private val responses = mutableListOf<CallResponse>()

    private val defaultSettings = Settings(false, 90, false, false, false)

    @Before
    fun setUp() {
        screenIncomingCall = mockk()
        normalizer = mockk()
        contactsLookup = mockk()
        contactsCache = mockk(relaxed = true)
        ruleCache = mockk(relaxed = true)
        simRegionProvider = mockk(relaxed = true)
        settingsCache = mockk(relaxed = true)
        recorder = mockk()
        purger = mockk(relaxed = true)
        clock = mockk()

        every { simRegionProvider.current() } returns "AT"
        every { settingsCache.current() } returns defaultSettings
        every { clock.now() } returns Instant.EPOCH
        every { recorder.record(any()) } just Runs

        // Unconfined makes serviceScope.launch {} run synchronously on the test thread, so cache
        // warming (onCreate) and event recording become deterministic instead of racing.
        service = wiredService(Dispatchers.Unconfined, responses)

        // Drive warming directly rather than through onCreate(): the Hilt-generated onCreate()
        // performs field injection that needs a bound Application, absent in a plain JVM test.
        service.startCacheWarming()
    }

    private fun wiredService(
        dispatcher: CoroutineDispatcher,
        captured: MutableList<CallResponse>,
    ): VorwahlGuardScreeningService {
        val svc = spyk(VorwahlGuardScreeningService())
        every { svc.respondToCall(any(), capture(captured)) } just Runs

        svc.screenIncomingCall = screenIncomingCall
        svc.normalizer = normalizer
        svc.contactsLookup = contactsLookup
        svc.contactsCache = contactsCache
        svc.ruleCache = ruleCache
        svc.simRegionProvider = simRegionProvider
        svc.settingsCache = settingsCache
        svc.recorder = recorder
        svc.purger = purger
        svc.clock = clock
        svc.mapper = CallResponseMapper()
        svc.backgroundDispatcher = dispatcher
        return svc
    }

    private fun callWithHandle(number: String): Call.Details {
        val details = mockk<Call.Details>()
        every { details.callDirection } returns Call.Details.DIRECTION_INCOMING
        every { details.handle } returns Uri.fromParts("tel", number, null)
        return details
    }

    private fun callWithNoHandle(): Call.Details {
        val details = mockk<Call.Details>()
        every { details.callDirection } returns Call.Details.DIRECTION_INCOMING
        every { details.handle } returns null
        return details
    }

    private fun outgoingCall(number: String): Call.Details {
        val details = mockk<Call.Details>()
        every { details.callDirection } returns Call.Details.DIRECTION_OUTGOING
        every { details.handle } returns Uri.fromParts("tel", number, null)
        return details
    }

    @Test
    fun `onCreate warms region before settings, rules and contacts`() {
        // settingsCache.refresh() is suspend (CachedSettingsRepository reads DataStore via
        // SettingsStore); coVerifyOrder is the mockk entry point that can wait on suspend calls.
        coVerifyOrder {
            simRegionProvider.refresh()
            settingsCache.refresh()
            ruleCache.refresh()
            contactsCache.refresh()
        }
    }

    @Test
    fun `BLOCK rule disallows the call exactly once`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize("+4915112345678", "AT") } returns number
        every { screenIncomingCall.decide(number, false, Instant.EPOCH) } returns
            ScreeningDecision(RuleAction.BLOCK, "rule-block")

        service.onScreenCall(callWithHandle("+4915112345678"))

        assertEquals(1, responses.size)
        assertEquals(true, responses.single().disallowCall)
    }

    @Test
    fun `SILENCE rule silences the call exactly once`() {
        val number = PhoneNumber("+4312345678", "+4312345678", "AT")
        every { normalizer.normalize("+4312345678", "AT") } returns number
        every { screenIncomingCall.decide(number, false, Instant.EPOCH) } returns
            ScreeningDecision(RuleAction.SILENCE, "rule-silence")

        service.onScreenCall(callWithHandle("+4312345678"))

        assertEquals(1, responses.size)
        assertEquals(true, responses.single().silenceCall)
        assertFalse(responses.single().disallowCall)
    }

    @Test
    fun `no rule match allows the call exactly once`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns ScreeningDecision.allow()

        service.onScreenCall(callWithHandle("+4915112345678"))

        assertEquals(1, responses.size)
        assertFalse(responses.single().disallowCall)
        assertFalse(responses.single().silenceCall)
    }

    @Test
    fun `an outgoing call is allowed once, never screened, and never recorded`() {
        // Even with a BLOCK rule that would match the number, an outgoing call must pass
        // untouched: VorwahlGuard only screens incoming calls (PROJECT.md §1).
        every { settingsCache.current() } returns Settings(false, 90, false, false, true)

        service.onScreenCall(outgoingCall("+4915112345678"))

        assertEquals(1, responses.size)
        assertFalse(responses.single().disallowCall)
        assertFalse(responses.single().silenceCall)
        verify(exactly = 0) { normalizer.normalize(any(), any()) }
        verify(exactly = 0) { screenIncomingCall.decide(any(), any(), any()) }
        verify(exactly = 0) { recorder.record(any()) }
    }

    @Test
    fun `withheld caller id never dereferences the handle and allows the call`() {
        every { screenIncomingCall.decide(PhoneNumber.UNKNOWN, false, Instant.EPOCH) } returns
            ScreeningDecision.allow()

        service.onScreenCall(callWithNoHandle())

        assertEquals(1, responses.size)
        assertFalse(responses.single().disallowCall)
        // The null handle must short-circuit to PhoneNumber.UNKNOWN without touching the normalizer.
        verify(exactly = 0) { normalizer.normalize(any(), any()) }
    }

    @Test
    fun `any exception in the decision path falls through to allow exactly once`() {
        every { normalizer.normalize(any(), any()) } throws RuntimeException("boom")

        service.onScreenCall(callWithHandle("+4915112345678"))

        assertEquals(1, responses.size)
        assertFalse(responses.single().disallowCall)
        assertFalse(responses.single().silenceCall)
    }

    @Test
    fun `a matched decision is recorded`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns
            ScreeningDecision(RuleAction.BLOCK, "rule-block")

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 1) { recorder.record(any()) }
    }

    @Test
    fun `an unmatched allow records nothing`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns ScreeningDecision.allow()

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 0) { recorder.record(any()) }
    }

    @Test
    fun `exception after a matched decision allows the call and records nothing`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns
            ScreeningDecision(RuleAction.BLOCK, "rule-block")
        // The decision itself succeeded; only mapping it to a CallResponse blows up. The call
        // falls open to ALLOW, so recording the BLOCK would assert an action that never happened.
        service.mapper = mockk()
        every { service.mapper.toCallResponse(any(), any()) } throws IllegalStateException("boom")

        service.onScreenCall(callWithHandle("+4915112345678"))

        assertEquals(1, responses.size)
        assertFalse(responses.single().disallowCall)
        assertFalse(responses.single().silenceCall)
        verify(exactly = 0) { recorder.record(any()) }
    }

    @Test
    fun `recording happens only after respondToCall`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns
            ScreeningDecision(RuleAction.BLOCK, "rule-block")

        service.onScreenCall(callWithHandle("+4915112345678"))

        verifyOrder {
            service.respondToCall(any(), any())
            recorder.record(any())
        }
    }

    @Test
    fun `a throwing recorder does not disturb the response`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns
            ScreeningDecision(RuleAction.BLOCK, "rule-block")
        every { recorder.record(any()) } throws RuntimeException("disk full")

        service.onScreenCall(callWithHandle("+4915112345678"))

        assertEquals(1, responses.size)
        assertEquals(true, responses.single().disallowCall)
    }

    @Test
    fun `a call arriving before warming finishes is still answered within the bounded wait`() {
        // Telecom binds the service because a call is arriving; warming may still be in flight.
        // Simulate a warm-up that outlives the wait: the call must still get its one response.
        every { simRegionProvider.refresh() } answers { Thread.sleep(10_000) }
        val slowResponses = mutableListOf<CallResponse>()
        val slowService = wiredService(Dispatchers.IO, slowResponses)
        slowService.startCacheWarming()

        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns ScreeningDecision.allow()

        slowService.onScreenCall(callWithHandle("+4915112345678"))

        assertEquals(1, slowResponses.size)
        assertFalse(slowResponses.single().disallowCall)
        assertFalse(slowResponses.single().silenceCall)
    }

    @Test
    fun `contacts bypass consults the rule engine with isKnownContact true`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { settingsCache.current() } returns Settings(true, 90, false, false, false)
        every { normalizer.normalize(any(), any()) } returns number
        every { contactsLookup.isKnownContact(number) } returns true
        every { screenIncomingCall.decide(number, true, Instant.EPOCH) } returns
            ScreeningDecision.allow()

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 1) { screenIncomingCall.decide(number, true, Instant.EPOCH) }
        assertEquals(1, responses.size)
    }

    @Test
    fun `bypass disabled never queries contacts and passes isKnownContact false`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        // Bypass off (first arg false) even though the caller *is* a contact: the setting must be
        // consulted before contacts, so contactsLookup is never queried.
        every { settingsCache.current() } returns Settings(false, 90, false, false, false)
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(number, false, Instant.EPOCH) } returns
            ScreeningDecision(RuleAction.BLOCK, "rule-block")

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 0) { contactsLookup.isKnownContact(any()) }
        verify(exactly = 1) { screenIncomingCall.decide(number, false, Instant.EPOCH) }
    }

    @Test
    fun `a withheld caller with bypass on never queries contacts`() {
        // number.isKnown() is false for a withheld caller, so the contacts lookup short-circuits
        // even with bypass enabled — you cannot match a null number against the address book.
        every { settingsCache.current() } returns Settings(true, 90, false, false, false)
        every { screenIncomingCall.decide(PhoneNumber.UNKNOWN, false, Instant.EPOCH) } returns
            ScreeningDecision.allow()

        service.onScreenCall(callWithNoHandle())

        verify(exactly = 0) { contactsLookup.isKnownContact(any()) }
        verify(exactly = 1) { screenIncomingCall.decide(PhoneNumber.UNKNOWN, false, Instant.EPOCH) }
    }

    @Test
    fun `contact bypass with logAllowedCalls on records a reason-only event`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { settingsCache.current() } returns Settings(true, 90, false, false, true)
        every { normalizer.normalize(any(), any()) } returns number
        every { contactsLookup.isKnownContact(number) } returns true
        every { screenIncomingCall.decide(number, true, Instant.EPOCH) } returns
            ScreeningDecision.contactBypass()
        val captured = slot<CallEvent>()
        every { recorder.record(capture(captured)) } just Runs

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 1) { recorder.record(any()) }
        assertEquals(null, captured.captured.matchedRuleId())
        assertEquals(DecisionReason.CONTACT_BYPASS, captured.captured.reason())
        assertEquals(RuleAction.ALLOW, captured.captured.action())
    }

    @Test
    fun `no matching rule with logAllowedCalls on records a NO_MATCH event`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { settingsCache.current() } returns Settings(false, 90, false, false, true)
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns ScreeningDecision.allow()
        val captured = slot<CallEvent>()
        every { recorder.record(capture(captured)) } just Runs

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 1) { recorder.record(any()) }
        assertEquals(null, captured.captured.matchedRuleId())
        assertEquals(DecisionReason.NO_MATCH, captured.captured.reason())
    }

    @Test
    fun `logAllowedCalls off records nothing for contact bypass or no match`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { settingsCache.current() } returns Settings(true, 90, false, false, false)
        every { normalizer.normalize(any(), any()) } returns number
        every { contactsLookup.isKnownContact(number) } returns true
        every { screenIncomingCall.decide(number, true, Instant.EPOCH) } returns
            ScreeningDecision.contactBypass()

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 0) { recorder.record(any()) }
    }

    @Test
    fun `a matched decision is recorded regardless of logAllowedCalls`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { settingsCache.current() } returns Settings(false, 90, false, false, false)
        every { normalizer.normalize(any(), any()) } returns number
        every { screenIncomingCall.decide(any(), any(), any()) } returns
            ScreeningDecision(RuleAction.BLOCK, "rule-block")
        val captured = slot<CallEvent>()
        every { recorder.record(capture(captured)) } just Runs

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 1) { recorder.record(any()) }
        assertEquals("rule-block", captured.captured.matchedRuleId())
        assertEquals(DecisionReason.RULE_MATCH, captured.captured.reason())
    }
}
