package io.janda.vorwahlguard.ui.einstellungen

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the stateless [EinstellungenContent]: role status copy for both `roleHeld` states and the
 * enable-role button's visibility/wiring, the contacts-bypass switch and its permission-denied
 * explanation, the pseudonymise and notify switches, the retention preset dropdown (both its
 * displayed value and driving [androidx.compose.material3.ExposedDropdownMenuBox] end to end —
 * open via the anchor field, pick a [androidx.compose.material3.DropdownMenuItem] by its rendered
 * text), the About section, and the `loaded=false` "nothing rendered" contract — mirrors
 * [io.janda.vorwahlguard.ui.uebersicht.UebersichtContentTest]'s conventions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class EinstellungenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // EinstellungenContent lays out exactly four Switch controls, in this order: contacts-bypass,
    // log-allowed-calls (issue #59), pseudonymise, notify-on-block (RetentionRow's
    // ExposedDropdownMenuBox is not a `toggleable` node, so it does not shift these indices).
    private companion object {
        const val CONTACTS_BYPASS_SWITCH_INDEX = 0
        const val LOG_ALLOWED_SWITCH_INDEX = 1
        const val PSEUDONYMISE_SWITCH_INDEX = 2
        const val NOTIFY_SWITCH_INDEX = 3
    }

    private lateinit var context: Context
    private lateinit var roleStatusHeld: String
    private lateinit var roleStatusNotHeld: String
    private lateinit var enableRoleButton: String
    private lateinit var permissionDeniedText: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        roleStatusHeld = context.getString(R.string.settings_role_status_held)
        roleStatusNotHeld = context.getString(R.string.settings_role_status_not_held)
        enableRoleButton = context.getString(R.string.overview_role_warning_button)
        permissionDeniedText = context.getString(R.string.settings_contacts_permission_denied)
    }

    private fun retentionText(days: Int): String =
        context.resources.getQuantityString(R.plurals.settings_retention_days, days, days)

    private fun loadedState(
        roleAvailable: Boolean = true,
        roleHeld: Boolean = false,
        contactsBypassEnabled: Boolean = false,
        pseudonymiseNumbers: Boolean = false,
        notifyOnBlock: Boolean = false,
        logAllowedCalls: Boolean = false,
        retentionDays: Int = 90,
        contactsPermissionDenied: Boolean = false,
        appVersion: String = "1.2.3",
    ): EinstellungenUiState = EinstellungenUiState(
        loaded = true,
        roleAvailable = roleAvailable,
        roleHeld = roleHeld,
        contactsBypassEnabled = contactsBypassEnabled,
        pseudonymiseNumbers = pseudonymiseNumbers,
        notifyOnBlock = notifyOnBlock,
        logAllowedCalls = logAllowedCalls,
        retentionDays = retentionDays,
        contactsPermissionDenied = contactsPermissionDenied,
        appVersion = appVersion,
    )

    private fun setContent(
        state: EinstellungenUiState,
        onRequestRole: () -> Unit = {},
        onContactsBypassToggled: (Boolean) -> Unit = {},
        onPseudonymiseToggled: (Boolean) -> Unit = {},
        onNotifyToggled: (Boolean) -> Unit = {},
        onRetentionSelected: (Int) -> Unit = {},
        onLogAllowedToggled: (Boolean) -> Unit = {},
        onOpenRepo: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                EinstellungenContent(
                    state = state,
                    onRequestRole = onRequestRole,
                    onContactsBypassToggled = onContactsBypassToggled,
                    onPseudonymiseToggled = onPseudonymiseToggled,
                    onNotifyToggled = onNotifyToggled,
                    onRetentionSelected = onRetentionSelected,
                    onLogAllowedToggled = onLogAllowedToggled,
                    onOpenRepo = onOpenRepo,
                )
            }
        }
    }

    @Test
    fun roleStatusShowsTheHeldCopyWhenRoleIsHeld() {
        setContent(loadedState(roleHeld = true))

        composeTestRule.onNodeWithText(roleStatusHeld).assertExists()
    }

    @Test
    fun roleStatusShowsTheNotHeldCopyWhenRoleIsNotHeld() {
        setContent(loadedState(roleHeld = false))

        composeTestRule.onNodeWithText(roleStatusNotHeld).assertExists()
    }

    @Test
    fun enableRoleButtonIsShownWhenRoleIsNotHeld() {
        setContent(loadedState(roleHeld = false))

        composeTestRule.onNodeWithText(enableRoleButton).assertExists()
    }

    @Test
    fun enableRoleButtonIsHiddenWhenRoleIsHeld() {
        setContent(loadedState(roleHeld = true))

        composeTestRule.onNodeWithText(enableRoleButton).assertDoesNotExist()
    }

    @Test
    fun enableRoleButtonIsHiddenWhenTheRoleIsUnavailableEvenIfNotHeld() {
        setContent(loadedState(roleAvailable = false, roleHeld = false))

        composeTestRule.onNodeWithText(enableRoleButton).assertDoesNotExist()
    }

    @Test
    fun tappingTheEnableRoleButtonInvokesOnRequestRole() {
        var invoked = false
        setContent(loadedState(roleHeld = false), onRequestRole = { invoked = true })

        composeTestRule.onNodeWithText(enableRoleButton).performClick()

        assertTrue(invoked)
    }

    @Test
    fun contactsBypassSwitchReflectsEnabledStateOn() {
        setContent(loadedState(contactsBypassEnabled = true))

        composeTestRule.onAllNodes(isToggleable())[CONTACTS_BYPASS_SWITCH_INDEX].assertIsOn()
    }

    @Test
    fun contactsBypassSwitchReflectsEnabledStateOff() {
        setContent(loadedState(contactsBypassEnabled = false))

        composeTestRule.onAllNodes(isToggleable())[CONTACTS_BYPASS_SWITCH_INDEX].assertIsOff()
    }

    @Test
    fun tappingTheContactsBypassSwitchInvokesOnContactsBypassToggledWithTheOppositeValue() {
        var received: Boolean? = null
        setContent(
            loadedState(contactsBypassEnabled = false),
            onContactsBypassToggled = { received = it },
        )

        composeTestRule.onAllNodes(isToggleable())[CONTACTS_BYPASS_SWITCH_INDEX].performClick()

        assertEquals(true, received)
    }

    @Test
    fun contactsPermissionDeniedTextShownWhenDenied() {
        setContent(loadedState(contactsPermissionDenied = true))

        composeTestRule.onNodeWithText(permissionDeniedText).assertExists()
    }

    @Test
    fun contactsPermissionDeniedTextHiddenWhenNotDenied() {
        setContent(loadedState(contactsPermissionDenied = false))

        composeTestRule.onNodeWithText(permissionDeniedText).assertDoesNotExist()
    }

    @Test
    fun logAllowedCallsSwitchReflectsItsStateField() {
        setContent(loadedState(logAllowedCalls = true))

        composeTestRule.onAllNodes(isToggleable())[LOG_ALLOWED_SWITCH_INDEX].assertIsOn()
    }

    @Test
    fun logAllowedCallsSwitchInvokesOnLogAllowedToggledWithTheOppositeValue() {
        var received: Boolean? = null
        setContent(
            loadedState(logAllowedCalls = false),
            onLogAllowedToggled = { received = it },
        )

        composeTestRule.onAllNodes(isToggleable())[LOG_ALLOWED_SWITCH_INDEX].performScrollTo().performClick()

        assertEquals(true, received)
    }

    @Test
    fun logAllowedCallsTitleIsRendered() {
        setContent(loadedState())

        composeTestRule.onNodeWithText(context.getString(R.string.settings_log_allowed_title)).assertExists()
    }

    @Test
    fun pseudonymiseSwitchReflectsItsStateField() {
        setContent(loadedState(pseudonymiseNumbers = true))

        composeTestRule.onAllNodes(isToggleable())[PSEUDONYMISE_SWITCH_INDEX].assertIsOn()
    }

    @Test
    fun pseudonymiseSwitchInvokesOnPseudonymiseToggledWithTheOppositeValue() {
        var received: Boolean? = null
        setContent(
            loadedState(pseudonymiseNumbers = false),
            onPseudonymiseToggled = { received = it },
        )

        // Below the fold in the scrollable Column: Robolectric only assigns non-empty bounds to
        // nodes actually within the (small) test viewport, so performClick() needs a preceding
        // performScrollTo() here — the higher-up contacts-bypass switch above needs none.
        composeTestRule.onAllNodes(isToggleable())[PSEUDONYMISE_SWITCH_INDEX].performScrollTo().performClick()

        assertEquals(true, received)
    }

    @Test
    fun notifySwitchReflectsItsStateField() {
        setContent(loadedState(notifyOnBlock = true))

        composeTestRule.onAllNodes(isToggleable())[NOTIFY_SWITCH_INDEX].assertIsOn()
    }

    @Test
    fun notifySwitchInvokesOnNotifyToggledWithTheOppositeValue() {
        var received: Boolean? = null
        setContent(
            loadedState(notifyOnBlock = true),
            onNotifyToggled = { received = it },
        )

        composeTestRule.onAllNodes(isToggleable())[NOTIFY_SWITCH_INDEX].performScrollTo().performClick()

        assertEquals(false, received)
    }

    @Test
    fun retentionDropdownShowsTheCurrentSelection() {
        setContent(loadedState(retentionDays = 90))

        composeTestRule.onNodeWithText(retentionText(90)).assertExists()
    }

    @Test
    fun selectingADifferentRetentionPresetInvokesOnRetentionSelected() {
        var received: Int? = null
        setContent(
            loadedState(retentionDays = 90),
            onRetentionSelected = { received = it },
        )

        // Open the dropdown by clicking its anchor field (currently showing the "90 days" text)...
        composeTestRule.onNodeWithText(retentionText(90)).performClick()
        // ...then pick the "180 days" preset item, which only appears once the menu is expanded.
        composeTestRule.onNodeWithText(retentionText(180)).performClick()

        assertEquals(180, received)
    }

    @Test
    fun aboutSectionShowsTheFormattedVersionString() {
        setContent(loadedState(appVersion = "9.9.9"))

        composeTestRule.onNodeWithText(context.getString(R.string.settings_about_version, "9.9.9")).assertExists()
    }

    @Test
    fun aboutSectionShowsTheMitLicenseText() {
        setContent(loadedState())

        composeTestRule.onNodeWithText(context.getString(R.string.settings_about_license)).assertExists()
    }

    @Test
    fun tappingTheRepoRowInvokesOnOpenRepo() {
        var invoked = false
        setContent(loadedState(), onOpenRepo = { invoked = true })

        composeTestRule.onNodeWithText(context.getString(R.string.settings_about_repo)).performScrollTo().performClick()

        assertTrue(invoked)
    }

    @Test
    fun notYetLoadedStateRendersNothing() {
        setContent(EinstellungenUiState(loaded = false))

        composeTestRule.onNodeWithText(roleStatusHeld).assertDoesNotExist()
        composeTestRule.onNodeWithText(roleStatusNotHeld).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_about_license)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_retention_title)).assertDoesNotExist()
    }
}
