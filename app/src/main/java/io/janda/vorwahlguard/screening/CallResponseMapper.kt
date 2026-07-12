package io.janda.vorwahlguard.screening

import android.telecom.CallScreeningService.CallResponse
import io.janda.vorwahlguard.domain.model.RuleAction
import javax.inject.Inject

/**
 * Maps a domain [RuleAction] to the [CallResponse] flag combination the platform expects
 * (CLAUDE.md §3 rule 5, PROJECT.md §5).
 *
 * `setSilenceCall(true)` is independent of `setDisallowCall` — the `rejectCall`/`skipCallLog`/
 * `skipNotification` flags only take effect when `setDisallowCall(true)` is also set, so
 * `SILENCE` and `ALLOW` never touch them.
 */
class CallResponseMapper @Inject constructor() {

    fun toCallResponse(action: RuleAction, notifyOnBlock: Boolean): CallResponse =
        when (action) {
            RuleAction.BLOCK ->
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(false)
                    // Keep the system call log entry by default (PROJECT.md §5 footnote): a
                    // user must be able to verify what was blocked outside the app. M1's
                    // Settings record has no field for this yet — add one in M4 rather than
                    // hardcoding a different value here.
                    .setSkipCallLog(false)
                    .setSkipNotification(!notifyOnBlock)
                    .build()

            RuleAction.SILENCE ->
                CallResponse.Builder()
                    .setSilenceCall(true)
                    .build()

            RuleAction.ALLOW ->
                CallResponse.Builder().build()
        }
}
