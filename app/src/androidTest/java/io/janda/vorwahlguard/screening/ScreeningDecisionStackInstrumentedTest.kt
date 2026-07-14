package io.janda.vorwahlguard.screening

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.janda.vorwahlguard.data.db.VorwahlGuardDatabase
import io.janda.vorwahlguard.data.rules.CachedRuleRepository
import io.janda.vorwahlguard.data.rules.RoomRuleSnapshotSource
import io.janda.vorwahlguard.data.rules.RuleDao
import io.janda.vorwahlguard.data.rules.toEntity
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.PhoneNumber
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.domain.service.LibPhoneNumberNormalizer
import io.janda.vorwahlguard.domain.service.ScreenIncomingCallService
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs CLAUDE.md §4's conflict resolution and §5's action mapping through the *real* stack on
 * real ART: Room (in-memory, not Robolectric's shadow SQLite) → [RoomRuleSnapshotSource] →
 * [CachedRuleRepository] → the real, unmodified [ScreenIncomingCallService] → real
 * `libphonenumber` normalization → [CallResponseMapper]. Run as `connectedReleaseAndroidTest`,
 * this is the R8/libphonenumber release gate CLAUDE.md §13 asks for — a broken keep rule (empty
 * metadata, a `normalize()` crash) fails here, on the real minified classpath, not in production.
 */
@RunWith(AndroidJUnit4::class)
class ScreeningDecisionStackInstrumentedTest {

    private lateinit var database: VorwahlGuardDatabase
    private lateinit var dao: RuleDao
    private lateinit var ruleCache: CachedRuleRepository
    private lateinit var screenIncomingCall: ScreenIncomingCallService
    private val normalizer = LibPhoneNumberNormalizer()
    private val mapper = CallResponseMapper()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, VorwahlGuardDatabase::class.java).build()
        dao = database.ruleDao()
        ruleCache = CachedRuleRepository(RoomRuleSnapshotSource(dao))
        screenIncomingCall = ScreenIncomingCallService(ruleCache)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun countryWideSilenceRuleSilencesAMatchingAustrianNumber() = runTest {
        seedRule(pattern = "+43*", action = RuleAction.SILENCE)
        ruleCache.refresh()
        val number = normalizer.normalize("+436631234567", null)

        val decision = screenIncomingCall.decide(number, false, Instant.now())
        val response = mapper.toCallResponse(decision.action(), notifyOnBlock = false)

        assertTrue(decision.matched())
        assertTrue(response.silenceCall)
        assertFalse(response.disallowCall)
    }

    @Test
    fun exactBlockRuleRejectsExactlyThatNumber() = runTest {
        seedRule(pattern = "+436631234567", action = RuleAction.BLOCK)
        ruleCache.refresh()
        val number = normalizer.normalize("+436631234567", null)

        val decision = screenIncomingCall.decide(number, false, Instant.now())
        val response = mapper.toCallResponse(decision.action(), notifyOnBlock = false)

        assertTrue(decision.matched())
        assertTrue(response.disallowCall)
        assertTrue(response.rejectCall)
    }

    @Test
    fun noMatchingRuleAllowsAndRecordsNothing() = runTest {
        seedRule(pattern = "+43*", action = RuleAction.BLOCK)
        ruleCache.refresh()
        val number = normalizer.normalize("+12025550100", null)

        val decision = screenIncomingCall.decide(number, false, Instant.now())
        val response = mapper.toCallResponse(decision.action(), notifyOnBlock = false)

        assertFalse(decision.matched())
        assertFalse(response.disallowCall)
        assertFalse(response.silenceCall)
    }

    @Test
    fun withheldCallerIdIsNotCaughtByANonPrivateCountryRule() = runTest {
        seedRule(pattern = "+43*", action = RuleAction.BLOCK)
        ruleCache.refresh()

        val decision = screenIncomingCall.decide(PhoneNumber.UNKNOWN, false, Instant.now())

        assertFalse("a bare country/prefix rule must never match a withheld caller id", decision.matched())
    }

    private suspend fun seedRule(pattern: String, action: RuleAction) {
        val rule = Rule(
            UUID.randomUUID().toString(),
            PatternSyntax.parse(pattern),
            action,
            true,
            null,
            Instant.now(),
        )
        dao.upsert(rule.toEntity())
    }
}
