package io.janda.vorwahlguard.ui.einstellungen

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.janda.vorwahlguard.R

/**
 * Settings (issue #29): role status, contacts-bypass toggle (backed by `READ_CONTACTS`,
 * requested here on demand), retention preset picker, pseudonymisation and notify-on-block
 * toggles, and the About section — backed by [EinstellungenViewModel]. Role status is re-checked
 * on `ON_RESUME` ([EinstellungenViewModel.refreshRoleStatus]), the same precedent
 * `UebersichtScreen` uses so returning from the system role-request screen reflects a
 * just-granted role immediately.
 */
@Composable
fun EinstellungenScreen(modifier: Modifier = Modifier, viewModel: EinstellungenViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Resolved via stringResource (invalidates on Configuration change), not context.getString()
    // inside the click lambda below — lint (LocalContextGetResourceValueCall) flags the latter.
    val repoUrl = stringResource(R.string.settings_about_repo_url)

    // The role-request result itself is not consumed here: whatever the user chose, the role
    // status is re-derived from RoleManager on the ON_RESUME that follows below, not from this
    // callback's result code — same precedent as UebersichtScreen.
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onContactsPermissionResult(granted)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshRoleStatus()
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EinstellungenEffect.RequestContactsPermission ->
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        EinstellungenContent(
            state = uiState,
            onRequestRole = { viewModel.createRoleRequestIntent()?.let { roleLauncher.launch(it) } },
            onContactsBypassToggled = viewModel::onContactsBypassToggled,
            onPseudonymiseToggled = viewModel::onPseudonymiseToggled,
            onNotifyToggled = viewModel::onNotifyOnBlockToggled,
            onRetentionSelected = viewModel::onRetentionDaysSelected,
            onLogAllowedToggled = viewModel::onLogAllowedCallsToggled,
            onOpenRepo = {
                // A device with no browser (not exotic for this app's de-Googled audience) has
                // nothing to resolve ACTION_VIEW — startActivity would throw
                // ActivityNotFoundException and kill the process. Fail quietly instead.
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))) }
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}
