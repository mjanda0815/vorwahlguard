package io.janda.vorwahlguard.screening

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService.CallResponse
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.rules.CachedRuleRepository
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.model.ScreeningDecision
import io.janda.vorwahlguard.domain.model.Settings
import io.janda.vorwahlguard.domain.port.`in`.ScreenIncomingCall
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.ContactsLookup
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.domain.port.out.SettingsRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
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
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var recorder: CallEventRecorder
    private lateinit var clock: Clock

    /** Every [respondToCall] the service issues, in order, for count and flag assertions. */
    private val responses = mutableListOf<CallResponse>()

    private val defaultSettings = Settings(false, 90, false, false)

    @Before
    fun setUp() {
        screenIncomingCall = mockk()
        normalizer = mockk()
        contactsLookup = mockk()
        contactsCache = mockk(relaxed = true)
        ruleCache = mockk(relaxed = true)
        simRegionProvider = mockk(relaxed = true)
        settingsRepository = mockk()
        recorder = mockk()
        clock = mockk()

        every { simRegionProvider.current() } returns "AT"
        every { settingsRepository.current() } returns defaultSettings
        every { clock.now() } returns Instant.EPOCH
        every { recorder.record(any()) } just Runs

        service = spyk(VorwahlGuardScreeningService())
        every { service.respondToCall(any(), capture(responses)) } just Runs

        service.screenIncomingCall = screenIncomingCall
        service.normalizer = normalizer
        service.contactsLookup = contactsLookup
        service.contactsCache = contactsCache
        service.ruleCache = ruleCache
        service.simRegionProvider = simRegionProvider
        service.settingsRepository = settingsRepository
        service.recorder = recorder
        service.clock = clock
        service.mapper = CallResponseMapper()
        // Unconfined makes serviceScope.launch {} run synchronously on the test thread, so cache
        // warming (onCreate) and event recording become deterministic instead of racing.
        service.backgroundDispatcher = Dispatchers.Unconfined

        // Drive warming directly rather than through onCreate(): the Hilt-generated onCreate()
        // performs field injection that needs a bound Application, absent in a plain JVM test.
        service.startCacheWarming()
    }

    private fun callWithHandle(number: String): Call.Details {
        val details = mockk<Call.Details>()
        every { details.handle } returns Uri.fromParts("tel", number, null)
        return details
    }

    private fun callWithNoHandle(): Call.Details {
        val details = mockk<Call.Details>()
        every { details.handle } returns null
        return details
    }

    @Test
    fun `onCreate warms region before contacts and rules`() {
        verifyOrder {
            simRegionProvider.refresh()
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
    fun `contacts bypass consults the rule engine with isKnownContact true`() {
        val number = PhoneNumber("+4915112345678", "+4915112345678", "DE")
        every { settingsRepository.current() } returns Settings(true, 90, false, false)
        every { normalizer.normalize(any(), any()) } returns number
        every { contactsLookup.isKnownContact(number) } returns true
        every { screenIncomingCall.decide(number, true, Instant.EPOCH) } returns
            ScreeningDecision.allow()

        service.onScreenCall(callWithHandle("+4915112345678"))

        verify(exactly = 1) { screenIncomingCall.decide(number, true, Instant.EPOCH) }
        assertEquals(1, responses.size)
    }
}
