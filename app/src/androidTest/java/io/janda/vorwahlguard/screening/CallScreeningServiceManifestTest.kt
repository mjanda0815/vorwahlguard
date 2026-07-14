package io.janda.vorwahlguard.screening

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies, against the real installed package rather than a Robolectric-parsed manifest, that
 * Telecom can actually discover and bind [VorwahlGuardScreeningService] (CLAUDE.md §3 rule 6).
 * This is the one thing a JVM/Robolectric test cannot prove: the merged manifest — after AGP's
 * manifest merger and, on a `connectedReleaseAndroidTest` run, after R8 — must still resolve the
 * `android.telecom.CallScreeningService` intent filter to an `exported`,
 * `BIND_SCREENING_SERVICE`-guarded component on the device Telecom will actually query.
 */
@RunWith(AndroidJUnit4::class)
class CallScreeningServiceManifestTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun screeningServiceIsDiscoverableViaTheCallScreeningServiceIntentAction() {
        val intent = Intent(TELECOM_ACTION).setPackage(context.packageName)

        val resolved = context.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)

        val match = resolved.firstOrNull {
            it.serviceInfo.name == VorwahlGuardScreeningService::class.java.name
        }
        assertTrue(
            "VorwahlGuardScreeningService must resolve for the $TELECOM_ACTION intent action",
            match != null,
        )
        assertTrue("the screening service must be exported (CLAUDE.md §3 rule 6)", match!!.serviceInfo.exported)
        assertEquals(
            "android.permission.BIND_SCREENING_SERVICE",
            match.serviceInfo.permission,
        )
    }

    private companion object {
        const val TELECOM_ACTION = "android.telecom.CallScreeningService"
    }
}
