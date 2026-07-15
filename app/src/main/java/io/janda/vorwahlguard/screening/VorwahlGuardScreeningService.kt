package io.janda.vorwahlguard.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.events.PRIVATE_NUMBER_PLACEHOLDER
import io.janda.vorwahlguard.data.events.RetentionPurger
import io.janda.vorwahlguard.data.events.UNKNOWN_REGION_CODE
import io.janda.vorwahlguard.data.rules.CachedRuleRepository
import io.janda.vorwahlguard.data.settings.CachedSettingsRepository
import io.janda.vorwahlguard.domain.model.CallEvent
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.model.ScreeningDecision
import io.janda.vorwahlguard.domain.port.`in`.ScreenIncomingCall
import io.janda.vorwahlguard.domain.port.out.CallEventRecorder
import io.janda.vorwahlguard.domain.port.out.Clock
import io.janda.vorwahlguard.domain.port.out.ContactsLookup
import io.janda.vorwahlguard.domain.port.out.NumberNormalizer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The screening hot path (CLAUDE.md §3). `onScreenCall()` has a ~5s system-imposed deadline:
 * every port it reads from is served from in-memory caches warmed by [onCreate] — no disk I/O,
 * no Binder IPC, no `runBlocking` on the calling thread. Telecom binds this service *because* a
 * call is arriving, so the very first screen may race that warm-up; it waits a bounded
 * [WARM_TIMEOUT_MS] for the caches and then proceeds with whatever is cached, degrading toward
 * allow. [respondToCall] is invoked exactly once per call, and any exception raised while
 * computing the decision falls through to an allowing [CallScreeningService.CallResponse]
 * rather than preventing (or duplicating) that call.
 *
 * Past the initial warm, the rule and settings caches are kept current by observing their
 * Room/DataStore sources for the lifetime of the service ([CachedRuleRepository.observeAndCache],
 * [CachedSettingsRepository.observeAndCache]) and a retention purge runs once per bind
 * ([RetentionPurger.purge]) — none of these hold [cachesWarmed] open; they are fire-and-forget
 * jobs started once the latch has already been released.
 */
@AndroidEntryPoint
class VorwahlGuardScreeningService : CallScreeningService() {

    @Inject lateinit var screenIncomingCall: ScreenIncomingCall
    @Inject lateinit var normalizer: NumberNormalizer
    @Inject lateinit var contactsLookup: ContactsLookup
    @Inject lateinit var contactsCache: CachedContactsLookup
    @Inject lateinit var ruleCache: CachedRuleRepository
    @Inject lateinit var simRegionProvider: SimRegionProvider
    @Inject lateinit var settingsCache: CachedSettingsRepository
    @Inject lateinit var recorder: CallEventRecorder
    @Inject lateinit var purger: RetentionPurger
    @Inject lateinit var clock: Clock
    @Inject lateinit var mapper: CallResponseMapper

    /**
     * Overridable only for tests, which substitute a synchronous dispatcher so that cache
     * warming and event recording become deterministic instead of racing the test thread.
     * Production code never touches this — it stays [Dispatchers.IO], off the hot path.
     */
    internal var backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO

    private lateinit var serviceScope: CoroutineScope
    private val cachesWarmed = CountDownLatch(1)

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
     * Once the latch opens, [ruleCache] and [settingsCache] switch to observing their persistent
     * source for the rest of the service's lifetime, and a retention purge runs once — all three
     * as separate jobs that must never hold [cachesWarmed] open themselves.
     *
     * Split out from [onCreate] so unit tests can drive warming directly: the Hilt-generated
     * `onCreate()` performs field injection that needs a bound `Application`, which a plain JVM
     * test does not have. Production only ever reaches this via [onCreate].
     */
    internal fun startCacheWarming() {
        serviceScope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
        serviceScope.launch {
            try {
                // Each step individually guarded: these now do real I/O (Room, DataStore,
                // ContentResolver), and one failing must neither skip the remaining steps nor
                // escape the coroutine — an unhandled exception here kills the process
                // mid-incoming-call, the worst failure mode (CLAUDE.md §3 rule 3).
                guarded("warm region") { simRegionProvider.refresh() }
                guarded("warm settings") { settingsCache.refresh() }
                guarded("warm rules") { ruleCache.refresh() }
                guarded("warm contacts") { contactsCache.refresh() }
            } finally {
                // Opens even on cancellation: a failed warm must degrade to "screen with
                // whatever is cached", not stall every onScreenCall() until the timeout.
                cachesWarmed.countDown()
            }

            // Only after the latch is open (ADR 0011): keep the caches current for the rest of
            // the service's life and run housekeeping. Individually guarded for the same reason
            // as the warm steps.
            launch { guarded("observe rules") { ruleCache.observeAndCache() } }
            launch { guarded("observe settings") { settingsCache.observeAndCache() } }
            launch { guarded("retention purge") { purger.purge() } }
        }
    }

    /** Contains [block]'s failure to a log line; cancellation still propagates. */
    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Never the number, never the message — only the failure shape (CLAUDE.md §1, §12).
            Log.e(TAG, "$what failed: ${t.javaClass.simpleName}")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        var decision: ScreeningDecision? = null
        var screenedNumber: PhoneNumber? = null
        var logAllowedCalls = false

        val response = try {
            // The call that triggered the bind may arrive before startCacheWarming() finished;
            // a bounded wait keeps it screened without threatening the ~5s deadline.
            cachesWarmed.await(WARM_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
                // VorwahlGuard only screens *incoming* calls (PROJECT.md §1). Telecom can invoke
                // onScreenCall() for outgoing calls too on some OEMs; those are allowed unchanged
                // and never recorded (decision stays null, so the recording gate below skips
                // them). respondToCall() is still called exactly once, below.
                CallResponse.Builder().build()
            } else {
                // Call.Details.getHandle() is null for a withheld/private caller ID (CLAUDE.md §3
                // rule 2) — never dereference it unguarded.
                val handle = callDetails.handle
                val number = if (handle == null) {
                    PhoneNumber.UNKNOWN
                } else {
                    normalizer.normalize(handle.schemeSpecificPart, simRegionProvider.current())
                }
                screenedNumber = number

                val settings = settingsCache.current()
                logAllowedCalls = settings.logAllowedCalls()
                val isContact = settings.contactsBypassEnabled() &&
                    number.isKnown() &&
                    contactsLookup.isKnownContact(number)

                val computed = screenIncomingCall.decide(number, isContact, clock.now())
                val mapped = mapper.toCallResponse(computed.action(), settings.notifyOnBlock())
                // Committed only once the response is built: a decision whose mapping threw falls
                // open to ALLOW below, and recording it would claim an action that never happened.
                decision = computed
                mapped
            }
        } catch (t: Throwable) {
            decision = null
            // Never log the number itself (CLAUDE.md §1, §12) — only the failure shape.
            Log.e(TAG, "onScreenCall decision failed, falling through to allow: ${t.javaClass.simpleName}")
            CallResponse.Builder().build()
        }

        // Exactly one respondToCall() per onScreenCall(), regardless of what happened above
        // (CLAUDE.md §3 rule 3).
        respondToCall(callDetails, response)

        // Recording only happens after respondToCall() has already been invoked (CLAUDE.md §3
        // rule 1, docs/ARCHITECTURE.md), and by default only for a decision a rule actually fired
        // for — a contact bypass or a "no match" allow records nothing (ADR 0006, CLAUDE.md §4
        // rule 3). Issue #59 / ADR 0014 adds an opt-in [io.janda.vorwahlguard.domain.model.Settings.logAllowedCalls]
        // (default off) that also records those allowed calls, carrying a null matchedRuleId and
        // the decision's [io.janda.vorwahlguard.domain.model.DecisionReason] instead.
        // recorder.record() is itself fire-and-forget on the app's own [io.janda.vorwahlguard.di.ApplicationScope]
        // (RoomCallEventRecorder does zero I/O on the calling thread), so this call is direct —
        // no separate serviceScope.launch wrapper needed here.
        val finalDecision = decision
        val finalNumber = screenedNumber
        if (finalDecision != null && finalNumber != null && (finalDecision.matched() || logAllowedCalls)) {
            runCatching {
                recorder.record(
                    CallEvent(
                        UUID.randomUUID().toString(),
                        clock.now(),
                        finalNumber.e164() ?: PRIVATE_NUMBER_PLACEHOLDER,
                        finalNumber.region() ?: UNKNOWN_REGION_CODE,
                        finalDecision.matchedRuleId(),
                        finalDecision.action(),
                        finalDecision.reason(),
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "VorwahlGuardScreening"
        const val WARM_TIMEOUT_MS = 1_000L
    }
}
