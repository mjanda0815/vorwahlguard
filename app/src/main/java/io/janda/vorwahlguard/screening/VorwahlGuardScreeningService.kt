package io.janda.vorwahlguard.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.rules.CachedRuleRepository
import io.janda.vorwahlguard.domain.model.CallEvent
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.model.ScreeningDecision
import io.janda.vorwahlguard.domain.port.`in`.ScreenIncomingCall
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.ContactsLookup
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import io.janda.vorwahlguard.domain.port.out.SettingsRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The screening hot path (CLAUDE.md §3). `onScreenCall()` has a ~5s system-imposed deadline:
 * every port it reads from must already be warmed in memory by [onCreate] — no disk I/O, no
 * Binder IPC, no `runBlocking` on the calling thread. [respondToCall] is invoked exactly once
 * per call, and any exception raised while computing the decision falls through to an allowing
 * [CallScreeningService.CallResponse] rather than preventing (or duplicating) that call.
 */
@AndroidEntryPoint
class VorwahlGuardScreeningService : CallScreeningService() {

    @Inject lateinit var screenIncomingCall: ScreenIncomingCall
    @Inject lateinit var normalizer: NumberNormalizer
    @Inject lateinit var contactsLookup: ContactsLookup
    @Inject lateinit var contactsCache: CachedContactsLookup
    @Inject lateinit var ruleCache: CachedRuleRepository
    @Inject lateinit var simRegionProvider: SimRegionProvider
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var recorder: CallEventRecorder
    @Inject lateinit var clock: Clock
    @Inject lateinit var mapper: CallResponseMapper

    /**
     * Overridable only for tests, which substitute a synchronous dispatcher so that cache
     * warming and event recording become deterministic instead of racing the test thread.
     * Production code never touches this — it stays [Dispatchers.IO], off the hot path.
     */
    internal var backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO

    private lateinit var serviceScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        startCacheWarming()
    }

    /**
     * Sets up [serviceScope] and warms every cache off the `onScreenCall()` hot path (CLAUDE.md
     * §3 rule 1). The service process may be started without the UI process ever having run, so
     * this is the only place these caches get warmed. Region must be refreshed before contacts,
     * since contact-number normalization depends on it.
     *
     * Split out from [onCreate] so unit tests can drive warming directly: the Hilt-generated
     * `onCreate()` performs field injection that needs a bound `Application`, which a plain JVM
     * test does not have. Production only ever reaches this via [onCreate].
     */
    internal fun startCacheWarming() {
        serviceScope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
        serviceScope.launch {
            simRegionProvider.refresh()
            ruleCache.refresh()
            contactsCache.refresh()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        var decision: ScreeningDecision? = null
        var screenedNumber: PhoneNumber? = null

        val response = try {
            // Call.Details.getHandle() is null for a withheld/private caller ID (CLAUDE.md §3
            // rule 2) — never dereference it unguarded.
            val handle = callDetails.handle
            val number = if (handle == null) {
                PhoneNumber.UNKNOWN
            } else {
                normalizer.normalize(handle.schemeSpecificPart, simRegionProvider.current())
            }
            screenedNumber = number

            val settings = settingsRepository.current()
            val isContact = settings.contactsBypassEnabled() &&
                number.isKnown() &&
                contactsLookup.isKnownContact(number)

            val computed = screenIncomingCall.decide(number, isContact, clock.now())
            decision = computed
            mapper.toCallResponse(computed.action(), settings.notifyOnBlock())
        } catch (t: Throwable) {
            // Never log the number itself (CLAUDE.md §1, §12) — only the failure shape.
            Log.e(TAG, "onScreenCall decision failed, falling through to allow: ${t.javaClass.simpleName}")
            CallResponse.Builder().build()
        }

        // Exactly one respondToCall() per onScreenCall(), regardless of what happened above
        // (CLAUDE.md §3 rule 3).
        respondToCall(callDetails, response)

        // Recording is fire-and-forget and only happens after respondToCall() has already been
        // invoked (CLAUDE.md §3 rule 1, docs/ARCHITECTURE.md), and only for a decision a rule
        // actually fired for — a contact bypass or a "no match" allow records nothing (ADR
        // 0006, CLAUDE.md §4 rule 3).
        val finalDecision = decision
        val finalNumber = screenedNumber
        if (finalDecision != null && finalDecision.matched() && finalNumber != null) {
            serviceScope.launch {
                runCatching {
                    recorder.record(
                        CallEvent(
                            UUID.randomUUID().toString(),
                            clock.now(),
                            finalNumber.e164() ?: PRIVATE_NUMBER_PLACEHOLDER,
                            finalNumber.region() ?: UNKNOWN_REGION_PLACEHOLDER,
                            finalDecision.matchedRuleId(),
                            finalDecision.action(),
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "VorwahlGuardScreening"
        const val PRIVATE_NUMBER_PLACEHOLDER = "PRIVATE"
        const val UNKNOWN_REGION_PLACEHOLDER = "UNKNOWN"
    }
}
