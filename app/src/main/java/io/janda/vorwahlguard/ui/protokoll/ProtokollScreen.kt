package io.janda.vorwahlguard.ui.protokoll

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Call event log (issue #25): a reverse-chronological list of screened calls with an action
 * filter, backed by [ProtokollViewModel]. No FAB — log rows are recorded by the screening service,
 * never created here.
 */
@Composable
fun ProtokollScreen(modifier: Modifier = Modifier, viewModel: ProtokollViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { innerPadding ->
        ProtokollListContent(uiState, onFilterSelect = viewModel::setFilter, modifier = Modifier.padding(innerPadding))
    }
}
