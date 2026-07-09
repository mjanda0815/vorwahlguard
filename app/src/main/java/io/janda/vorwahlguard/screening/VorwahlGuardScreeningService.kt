package io.janda.vorwahlguard.screening

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * M0 placeholder for the call screening service.
 *
 * It satisfies the manifest contract — so the app installs and `lint` does not flag a
 * missing class — but performs **no screening**: every call is allowed. The real
 * decision path (in-memory rule cache warmed in [onCreate], exactly-one [respondToCall],
 * null-handle guard, fail-open on any exception, BLOCK/SILENCE/ALLOW mapping) arrives in
 * M2. See CLAUDE.md §3.
 */
class VorwahlGuardScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Fail open until the screening logic lands in M2: allow every call.
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
