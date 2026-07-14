package io.janda.vorwahlguard.screening

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [CallScreeningRoleProvider] against the real, on-device [android.app.role.RoleManager]
 * — Robolectric's `ShadowRoleManager` (see the JVM `CallScreeningRoleProviderTest`) only ever
 * simulates this. The role is granted/revoked here via `cmd role`, run as the instrumentation
 * shell UID: on any userdebug build or a consumer device with USB debugging enabled (which is
 * exactly what running this test at all requires), `shell` holds `MANAGE_ROLE_HOLDERS`, so this
 * needs no human tapping the system consent dialog (store-readiness review, issue #29).
 *
 * `CALL_SCREENING` is an exclusive role: granting it to this app's test package kicks out
 * whichever app held it before (CLAUDE.md §3 rule 4) — [tearDown] restores that prior holder so
 * running this test doesn't leave the device's real call-screening app deselected.
 */
@RunWith(AndroidJUnit4::class)
class CallScreeningRoleInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageName = context.packageName
    private lateinit var previousHolders: List<String>

    @Before
    fun setUp() {
        previousHolders = currentHolders()
    }

    @After
    fun tearDown() {
        removeRoleHolder(packageName)
        previousHolders.forEach(::addRoleHolder)
    }

    @Test
    fun grantingTheRoleIsReflectedByIsRoleHeld() {
        addRoleHolder(packageName)
        waitUntil { CallScreeningRoleProvider(context).isRoleHeld() }

        assertTrue(CallScreeningRoleProvider(context).isRoleHeld())
    }

    @Test
    fun removingTheRoleIsReflectedByIsRoleHeld() {
        addRoleHolder(packageName)
        waitUntil { CallScreeningRoleProvider(context).isRoleHeld() }

        removeRoleHolder(packageName)
        waitUntil { !CallScreeningRoleProvider(context).isRoleHeld() }

        assertFalse(CallScreeningRoleProvider(context).isRoleHeld())
    }

    @Test
    fun roleIsAvailableOnThisDevice() {
        // minSdk 29 (CLAUDE.md §2): every device this app can install on must expose the role.
        assertTrue(CallScreeningRoleProvider(context).isRoleAvailable())
    }

    private fun currentHolders(): List<String> =
        shell("cmd role get-role-holders $ROLE").lineSequence().filter { it.isNotBlank() }.toList()

    private fun addRoleHolder(pkg: String) {
        shell("cmd role add-role-holder $ROLE $pkg")
    }

    private fun removeRoleHolder(pkg: String) {
        shell("cmd role remove-role-holder $ROLE $pkg")
    }

    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd))).use {
            it.readText()
        }
    }

    /** Role propagation is near-instant but not synchronous with the shell command returning. */
    private fun waitUntil(timeoutMs: Long = 3_000, pollMs: Long = 50, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(pollMs)
        }
    }

    private companion object {
        const val ROLE = "android.app.role.CALL_SCREENING"
    }
}
