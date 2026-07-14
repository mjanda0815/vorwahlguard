package io.janda.vorwahlguard.ui.protokoll

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.RuleAction

/**
 * Call event log (issue #25): a reverse-chronological list of screened calls with an action
 * filter, backed by [ProtokollViewModel]. No FAB — log rows are recorded by the screening service,
 * never created here. Tapping a row offers to create a rule for that caller (issue #76); the
 * outcome (created / already exists) is surfaced as a Toast.
 */
@Composable
fun ProtokollScreen(modifier: Modifier = Modifier, viewModel: ProtokollViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val createdTemplate = stringResource(R.string.protokoll_create_rule_created)
    val alreadyExists = stringResource(R.string.protokoll_create_rule_exists)
    val actionLabels = mapOf(
        RuleAction.BLOCK to stringResource(R.string.action_block),
        RuleAction.SILENCE to stringResource(R.string.action_silence),
        RuleAction.ALLOW to stringResource(R.string.action_allow),
    )

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is ProtokollEffect.RuleCreated ->
                    createdTemplate.format(actionLabels[effect.action])
                ProtokollEffect.RuleAlreadyExists -> alreadyExists
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        ProtokollListContent(
            state = uiState,
            onFilterSelect = viewModel::setFilter,
            modifier = Modifier.padding(innerPadding),
            onRowClick = viewModel::onRowClicked,
            onCreateRule = viewModel::createRule,
            onDismissPendingRule = viewModel::dismissPendingRule,
        )
    }
}
