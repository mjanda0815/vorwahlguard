package io.janda.vorwahlguard.data.contacts

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers [ContactsPermissionState] against Robolectric's [org.robolectric.shadows.ShadowContextWrapper]
 * grant state (`Application` is a `ContextWrapper`, so `Shadows.shadowOf(application)` resolves to
 * that shadow — confirmed via `javap` against the pinned `shadows-framework` 4.16.1 jar, since
 * `ShadowApplication` itself does not declare `grantPermissions`/`denyPermissions` in this version,
 * only its `ShadowContextWrapper` superclass does). [isGranted] deliberately re-checks
 * [androidx.core.content.ContextCompat.checkSelfPermission] on every call rather than caching — the
 * third test proves that by mutating the shadow's grant state between two calls on the *same*
 * instance and observing the result change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ContactsPermissionStateTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    private fun state(): ContactsPermissionState = ContactsPermissionState(application)

    @Test
    fun `isGranted is true when READ_CONTACTS has been granted`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)

        assertTrue(state().isGranted())
    }

    @Test
    fun `isGranted is false when READ_CONTACTS was never granted`() {
        assertFalse(state().isGranted())
    }

    @Test
    fun `isGranted is false when READ_CONTACTS has been explicitly denied`() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        shadowOf(application).denyPermissions(Manifest.permission.READ_CONTACTS)

        assertFalse(state().isGranted())
    }

    @Test
    fun `a live re-check on the same instance reflects a grant that happens after construction`() {
        val permissionState = state()
        assertFalse(permissionState.isGranted())

        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)

        assertTrue(permissionState.isGranted())
    }
}
