package io.janda.vorwahlguard.screening

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports whether VorwahlGuard currently holds `RoleManager.ROLE_CALL_SCREENING`
 * (CLAUDE.md §3 rule 4: only one app can hold it, and acquiring it kicks out the previous
 * holder). Unlike [SimRegionProvider], this deliberately does **not** cache the result behind a
 * `@Volatile` field warmed once: every method here reads [RoleManager] live. That split is
 * intentional, not an oversight — [SimRegionProvider] caches because it is read from the
 * `onScreenCall()` hot path, where a Binder round-trip per call is the hazard to avoid. This
 * class is UI-only (`UebersichtScreen`'s role-status card): it is never read from the hot path,
 * so the actual hazard is staleness — showing "not held" right after the user has just granted
 * the role in system settings, because a cached flag from before the grant was never refreshed.
 * A live read on every call trades a negligible IPC cost (an Übersicht recomposition, not a
 * ~5s-deadline screening decision) for always reflecting the current grant.
 */
@Singleton
class CallScreeningRoleProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val roleManager: RoleManager? = context.getSystemService(RoleManager::class.java)

    fun isRoleAvailable(): Boolean = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) ?: false

    fun isRoleHeld(): Boolean = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false

    fun createRoleRequestIntent(): Intent? = roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}
