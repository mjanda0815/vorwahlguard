package io.janda.vorwahlguard.screening

import android.app.role.RoleManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowRoleManager

/**
 * Covers [CallScreeningRoleProvider] against Robolectric's [ShadowRoleManager], which — pinned
 * Robolectric 4.16.1, confirmed via `javap` against the resolved `shadows-framework` jar since
 * there was no prior precedent for a `RoleManager` shadow in this codebase — genuinely supports
 * controlling both axes this class reads: `addHeldRole`/`removeHeldRole` back
 * [RoleManager.isRoleHeld], `addAvailableRole`/`removeAvailableRole` back
 * [RoleManager.isRoleAvailable]. `createRequestRoleIntent` is *not* shadowed (absent from
 * [ShadowRoleManager]'s method list), so it runs the real platform implementation Robolectric
 * ships for that SDK — it returns a real, non-null [android.content.Intent] built from
 * `RoleManager.ACTION_REQUEST_ROLE` without touching any state [ShadowRoleManager] exposes, so
 * only its non-nullness is asserted here, not its extras.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CallScreeningRoleProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val roleManager: RoleManager = context.getSystemService(RoleManager::class.java)
    private val shadowRoleManager: ShadowRoleManager = shadowOf(roleManager)

    private fun provider(): CallScreeningRoleProvider = CallScreeningRoleProvider(context)

    @Test
    fun `role available and held reports isRoleHeld true`() {
        shadowRoleManager.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
        shadowRoleManager.addHeldRole(RoleManager.ROLE_CALL_SCREENING)

        assertTrue(provider().isRoleHeld())
    }

    @Test
    fun `role available but not held reports isRoleHeld false`() {
        shadowRoleManager.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)

        assertFalse(provider().isRoleHeld())
    }

    @Test
    fun `role unavailable by default reports isRoleAvailable false`() {
        // Fresh ShadowRoleManager state: nothing has been added yet, so the role is unavailable
        // without needing an explicit removeAvailableRole call (which throws IllegalArgumentException
        // for a role that was never added — see the next test for the explicit add-then-remove path).
        assertFalse(provider().isRoleAvailable())
    }

    @Test
    fun `a role made available and then explicitly removed reports isRoleAvailable false`() {
        shadowRoleManager.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
        shadowRoleManager.removeAvailableRole(RoleManager.ROLE_CALL_SCREENING)

        assertFalse(provider().isRoleAvailable())
    }

    @Test
    fun `role available reports isRoleAvailable true`() {
        shadowRoleManager.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)

        assertTrue(provider().isRoleAvailable())
    }

    @Test
    fun `createRoleRequestIntent returns a non-null intent when the role is available`() {
        shadowRoleManager.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)

        assertNotNull(provider().createRoleRequestIntent())
    }
}
