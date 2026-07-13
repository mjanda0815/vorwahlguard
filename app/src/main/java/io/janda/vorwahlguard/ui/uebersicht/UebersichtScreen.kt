package io.janda.vorwahlguard.ui.uebersicht

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Dashboard (issue #27): role-status warning, hero counter, 30-day sparkline, and top-countries/
 * top-rules lists, backed by [UebersichtViewModel]. Role status is re-checked on `ON_RESUME`
 * ([UebersichtViewModel.refreshRoleStatus]) so returning from the system role-request screen
 * (launched by [onRequestRole]) reflects a just-granted role immediately, without waiting for the
 * next aggregate Flow emission.
 */
@Composable
fun UebersichtScreen(modifier: Modifier = Modifier, viewModel: UebersichtViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The role-request result itself is not consumed here: whatever the user chose, the role
    // status is re-derived from RoleManager on the ON_RESUME that follows below, not from this
    // callback's result code.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshRoleStatus()
    }

    Scaffold(modifier = modifier) { innerPadding ->
        UebersichtContent(
            state = uiState,
            onRequestRole = { viewModel.createRoleRequestIntent()?.let { launcher.launch(it) } },
            modifier = Modifier.padding(innerPadding),
        )
    }
}
