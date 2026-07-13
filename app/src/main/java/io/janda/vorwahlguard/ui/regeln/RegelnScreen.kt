package io.janda.vorwahlguard.ui.regeln

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.regeln.addrule.AddRuleSheet

/**
 * Rules list + creation (issue #23). The FAB opens [AddRuleSheet] (issue #6), which covers both
 * creation paths; the list body is [RuleListContent], grouped into whitelist/blacklist by
 * [RegelnViewModel].
 */
@Composable
fun RegelnScreen(modifier: Modifier = Modifier, viewModel: RegelnViewModel = hiltViewModel()) {
    var showAddRuleSheet by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRuleSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rules_fab_add))
            }
        },
    ) { innerPadding ->
        RuleListContent(uiState, onDelete = viewModel::deleteRule, modifier = Modifier.padding(innerPadding))
    }

    if (showAddRuleSheet) {
        AddRuleSheet(onDismiss = { showAddRuleSheet = false })
    }
}
