package io.janda.vorwahlguard.ui.einstellungen

import io.janda.vorwahlguard.data.AppInfoProvider
import io.janda.vorwahlguard.data.contacts.CachedContactsLookup
import io.janda.vorwahlguard.data.contacts.ContactsPermissionState
import io.janda.vorwahlguard.data.settings.SettingsStore
import io.janda.vorwahlguard.domain.model.Settings
import io.janda.vorwahlguard.screening.CallScreeningRoleProvider
import io.janda.vorwahlguard.ui.regeln.addrule.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test

/**
 * Covers [EinstellungenViewModel]: the [SettingsStore.settings] Flow populating [EinstellungenUiState],
 * every settings-writing action calling [SettingsStore.update] with the correctly-shaped
 * read-modify-write transform (asserted by capturing the transform lambda mockk passes through and
 * applying it to a known, non-default [Settings] fixture — [SettingsStore] itself is mocked, so the
 * transform is never actually run against real storage), the `READ_CONTACTS`-gated
 * `onContactsBypassToggled` branching, and the role-status read/re-read split
 * [UebersichtViewModelTest][io.janda.vorwahlguard.ui.uebersicht.UebersichtViewModelTest] also covers
 * for its sibling view model.
 *
 * [EinstellungenViewModel.effects] is a [kotlinx.coroutines.channels.Channel]-backed [kotlinx.coroutines.flow.Flow]
 * (there is no Turbine dependency in this project — confirmed against `gradle/libs.versions.toml`).
 * It is collected here by launching a plain coroutine on the same [UnconfinedTestDispatcher] that
 * backs `Dispatchers.Main` ([MainDispatcherRule]) *before* invoking the action under test: under
 * [UnconfinedTestDispatcher], that `launch` runs eagerly and suspends on the empty channel, and the
 * `_effects.send(...)` triggered by the action resumes it synchronously in the same call — so by the
 * time the triggering call returns, any emitted effect is already in the collected list. The job is
 * cancelled afterwards since the collection would otherwise run forever.
 */
class EinstellungenViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @get:JUnitRule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    /** A deliberately non-default fixture so a transform that only flips one field is provable. */
    private val nonDefaultSettings = Settings(false, 30, true, true)

    private lateinit var settingsFlow: MutableStateFlow<Settings>
    private lateinit var settingsStore: SettingsStore
    private lateinit var contactsLookup: CachedContactsLookup
    private lateinit var permissionState: ContactsPermissionState
    private lateinit var roleProvider: CallScreeningRoleProvider
    private lateinit var appInfoProvider: AppInfoProvider
    private lateinit var updateTransform: io.mockk.CapturingSlot<(Settings) -> Settings>

    @Before
    fun setUp() {
        settingsFlow = MutableStateFlow(nonDefaultSettings)
        settingsStore = mockk()
        every { settingsStore.settings } returns settingsFlow
        updateTransform = slot()
        coEvery { settingsStore.update(capture(updateTransform)) } just Runs

        contactsLookup = mockk(relaxed = true)

        permissionState = mockk()
        every { permissionState.isGranted() } returns true

        roleProvider = mockk()
        every { roleProvider.isRoleAvailable() } returns false
        every { roleProvider.isRoleHeld() } returns false
        every { roleProvider.createRoleRequestIntent() } returns null

        appInfoProvider = mockk()
        every { appInfoProvider.displayVersion() } returns "1.2.3"
        every { appInfoProvider.copyrightYear() } returns 2026
    }

    private fun createViewModel(): EinstellungenViewModel = EinstellungenViewModel(
        settingsStore,
        contactsLookup,
        permissionState,
        roleProvider,
        appInfoProvider,
        dispatcher,
    )

    /** Launches a background collector of [EinstellungenViewModel.effects]; caller must [Job.cancel] it. */
    private fun kotlinx.coroutines.CoroutineScope.collectEffects(
        viewModel: EinstellungenViewModel,
        into: MutableList<EinstellungenEffect>,
    ): Job = launch { viewModel.effects.toList(into) }

    @Test
    fun `a settings-flow emission populates uiState from the emitted Settings`() = runTest(dispatcher) {
        val state = createViewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals(nonDefaultSettings.contactsBypassEnabled(), state.contactsBypassEnabled)
        assertEquals(nonDefaultSettings.pseudonymiseNumbers(), state.pseudonymiseNumbers)
        assertEquals(nonDefaultSettings.notifyOnBlock(), state.notifyOnBlock)
        assertEquals(nonDefaultSettings.retentionDays(), state.retentionDays)
    }

    @Test
    fun `appVersion and copyrightYear are set from AppInfoProvider at construction`() = runTest(dispatcher) {
        val state = createViewModel().uiState.value

        assertEquals("1.2.3", state.appVersion)
        assertEquals(2026, state.copyrightYear)
    }

    @Test
    fun `onPseudonymiseToggled true flips only pseudonymiseNumbers`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onPseudonymiseToggled(true)

        coVerify(exactly = 1) { settingsStore.update(any()) }
        val result = updateTransform.captured.invoke(nonDefaultSettings)
        assertEquals(nonDefaultSettings.contactsBypassEnabled(), result.contactsBypassEnabled())
        assertEquals(nonDefaultSettings.retentionDays(), result.retentionDays())
        assertTrue(result.pseudonymiseNumbers())
        assertEquals(nonDefaultSettings.notifyOnBlock(), result.notifyOnBlock())
    }

    @Test
    fun `onNotifyOnBlockToggled true flips only notifyOnBlock`() = runTest(dispatcher) {
        val fixture = Settings(false, 30, true, false)
        val viewModel = createViewModel()

        viewModel.onNotifyOnBlockToggled(true)

        coVerify(exactly = 1) { settingsStore.update(any()) }
        val result = updateTransform.captured.invoke(fixture)
        assertEquals(fixture.contactsBypassEnabled(), result.contactsBypassEnabled())
        assertEquals(fixture.retentionDays(), result.retentionDays())
        assertEquals(fixture.pseudonymiseNumbers(), result.pseudonymiseNumbers())
        assertTrue(result.notifyOnBlock())
    }

    @Test
    fun `onRetentionDaysSelected flips only retentionDays`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onRetentionDaysSelected(180)

        coVerify(exactly = 1) { settingsStore.update(any()) }
        val result = updateTransform.captured.invoke(nonDefaultSettings)
        assertEquals(nonDefaultSettings.contactsBypassEnabled(), result.contactsBypassEnabled())
        assertEquals(180, result.retentionDays())
        assertEquals(nonDefaultSettings.pseudonymiseNumbers(), result.pseudonymiseNumbers())
        assertEquals(nonDefaultSettings.notifyOnBlock(), result.notifyOnBlock())
    }

    @Test
    fun `onContactsBypassToggled true with permission granted persists and refreshes the cache`() = runTest(dispatcher) {
        every { permissionState.isGranted() } returns true
        val viewModel = createViewModel()
        val effects = mutableListOf<EinstellungenEffect>()
        val job = collectEffects(viewModel, effects)

        viewModel.onContactsBypassToggled(true)

        coVerify(exactly = 1) { settingsStore.update(any()) }
        val result = updateTransform.captured.invoke(nonDefaultSettings)
        assertTrue(result.contactsBypassEnabled())
        assertEquals(nonDefaultSettings.retentionDays(), result.retentionDays())
        assertEquals(nonDefaultSettings.pseudonymiseNumbers(), result.pseudonymiseNumbers())
        assertEquals(nonDefaultSettings.notifyOnBlock(), result.notifyOnBlock())
        coVerify(exactly = 1) { contactsLookup.refresh() }
        assertTrue(effects.isEmpty())
        job.cancel()
    }

    @Test
    fun `onContactsBypassToggled true without the permission emits RequestContactsPermission and writes nothing`() = runTest(dispatcher) {
        every { permissionState.isGranted() } returns false
        val viewModel = createViewModel()
        val effects = mutableListOf<EinstellungenEffect>()
        val job = collectEffects(viewModel, effects)

        viewModel.onContactsBypassToggled(true)

        assertEquals(listOf(EinstellungenEffect.RequestContactsPermission), effects)
        coVerify(exactly = 0) { settingsStore.update(any()) }
        coVerify(exactly = 0) { contactsLookup.refresh() }
        job.cancel()
    }

    @Test
    fun `onContactsBypassToggled false never needs the permission and never refreshes`() = runTest(dispatcher) {
        every { permissionState.isGranted() } returns false
        val viewModel = createViewModel()
        val effects = mutableListOf<EinstellungenEffect>()
        val job = collectEffects(viewModel, effects)

        viewModel.onContactsBypassToggled(false)

        coVerify(exactly = 1) { settingsStore.update(any()) }
        val result = updateTransform.captured.invoke(nonDefaultSettings)
        assertFalse(result.contactsBypassEnabled())
        assertEquals(nonDefaultSettings.retentionDays(), result.retentionDays())
        assertEquals(nonDefaultSettings.pseudonymiseNumbers(), result.pseudonymiseNumbers())
        assertEquals(nonDefaultSettings.notifyOnBlock(), result.notifyOnBlock())
        coVerify(exactly = 0) { contactsLookup.refresh() }
        assertTrue(effects.isEmpty())
        job.cancel()
    }

    @Test
    fun `onContactsPermissionResult granted persists contactsBypassEnabled and refreshes the cache`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onContactsPermissionResult(true)

        coVerify(exactly = 1) { settingsStore.update(any()) }
        val result = updateTransform.captured.invoke(nonDefaultSettings)
        assertTrue(result.contactsBypassEnabled())
        coVerify(exactly = 1) { contactsLookup.refresh() }
    }

    @Test
    fun `onContactsPermissionResult denied writes nothing and marks the state as denied`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onContactsPermissionResult(false)

        coVerify(exactly = 0) { settingsStore.update(any()) }
        assertTrue(viewModel.uiState.value.contactsPermissionDenied)
    }

    @Test
    fun `a later granted result clears a prior denied flag`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.onContactsPermissionResult(false)
        assertTrue(viewModel.uiState.value.contactsPermissionDenied)

        viewModel.onContactsPermissionResult(true)

        assertFalse(viewModel.uiState.value.contactsPermissionDenied)
    }

    @Test
    fun `toggling the switch again after a denial clears the denied flag`() = runTest(dispatcher) {
        every { permissionState.isGranted() } returns false
        val viewModel = createViewModel()
        viewModel.onContactsPermissionResult(false)
        assertTrue(viewModel.uiState.value.contactsPermissionDenied)

        viewModel.onContactsBypassToggled(false)

        assertFalse(viewModel.uiState.value.contactsPermissionDenied)
    }

    @Test
    fun `re-enabling contacts bypass when already granted never re-prompts`() = runTest(dispatcher) {
        every { permissionState.isGranted() } returns true
        val viewModel = createViewModel()
        val effects = mutableListOf<EinstellungenEffect>()
        val job = collectEffects(viewModel, effects)

        viewModel.onContactsBypassToggled(true)

        assertTrue(effects.none { it is EinstellungenEffect.RequestContactsPermission })
        job.cancel()
    }

    @Test
    fun `refreshRoleStatus re-reads the provider and updates the state to newly stubbed values`() = runTest(dispatcher) {
        every { roleProvider.isRoleAvailable() } returns true
        every { roleProvider.isRoleHeld() } returns false
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.roleHeld)

        // Re-stub after construction: proves the live read is not a value cached at init time.
        every { roleProvider.isRoleHeld() } returns true
        viewModel.refreshRoleStatus()

        assertTrue(viewModel.uiState.value.roleHeld)
        assertTrue(viewModel.uiState.value.roleAvailable)
    }

    @Test
    fun `createRoleRequestIntent delegates to the role provider`() = runTest(dispatcher) {
        val intent = mockk<android.content.Intent>()
        every { roleProvider.createRoleRequestIntent() } returns intent
        val viewModel = createViewModel()

        val result = viewModel.createRoleRequestIntent()

        verify(exactly = 1) { roleProvider.createRoleRequestIntent() }
        assertSame(intent, result)
    }

    @Test
    fun `createRoleRequestIntent returns null when the role provider has none`() = runTest(dispatcher) {
        every { roleProvider.createRoleRequestIntent() } returns null
        val viewModel = createViewModel()

        assertNull(viewModel.createRoleRequestIntent())
    }
}
