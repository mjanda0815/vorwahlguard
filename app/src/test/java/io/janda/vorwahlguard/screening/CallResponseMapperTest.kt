package io.janda.vorwahlguard.screening

import io.janda.vorwahlguard.domain.model.RuleAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the [RuleAction] -> `CallResponse` flag mapping (CLAUDE.md §3 rule 5). Runs under
 * Robolectric because `CallResponse.Builder` is a framework class; `setSilenceCall` is API 29+
 * (the app's `minSdk`), so the config pins that level.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CallResponseMapperTest {

    private val mapper = CallResponseMapper()

    @Test
    fun `BLOCK disallows and rejects the call`() {
        val response = mapper.toCallResponse(RuleAction.BLOCK, notifyOnBlock = true)

        assertTrue(response.disallowCall)
        assertTrue(response.rejectCall)
        assertFalse(response.silenceCall)
        // The system call-log entry is always kept so the user can verify what was blocked.
        assertFalse(response.skipCallLog)
    }

    @Test
    fun `BLOCK with notifyOnBlock true keeps the notification`() {
        val response = mapper.toCallResponse(RuleAction.BLOCK, notifyOnBlock = true)

        assertFalse(response.skipNotification)
    }

    @Test
    fun `BLOCK with notifyOnBlock false skips the notification`() {
        val response = mapper.toCallResponse(RuleAction.BLOCK, notifyOnBlock = false)

        assertTrue(response.skipNotification)
    }

    @Test
    fun `SILENCE silences the call without disallowing it`() {
        val response = mapper.toCallResponse(RuleAction.SILENCE, notifyOnBlock = false)

        assertTrue(response.silenceCall)
        assertFalse(response.disallowCall)
        assertFalse(response.rejectCall)
    }

    @Test
    fun `ALLOW touches no flag`() {
        val response = mapper.toCallResponse(RuleAction.ALLOW, notifyOnBlock = true)

        assertFalse(response.disallowCall)
        assertFalse(response.rejectCall)
        assertFalse(response.silenceCall)
        assertFalse(response.skipCallLog)
        assertFalse(response.skipNotification)
    }
}
