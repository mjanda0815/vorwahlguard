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
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.components.PlaceholderScreen
import io.janda.vorwahlguard.ui.regeln.addrule.AddRuleSheet

/**
 * Rules list + creation. The list body is still the M0 placeholder (real list arrives in M4);
 * the FAB opens [AddRuleSheet] (issue #6), which covers both creation paths.
 */
@Composable
fun RegelnScreen(modifier: Modifier = Modifier) {
    var showAddRuleSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRuleSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.rules_fab_add))
            }
        },
    ) { innerPadding ->
        PlaceholderScreen(R.string.screen_rules_placeholder, Modifier.padding(innerPadding))
    }

    if (showAddRuleSheet) {
        AddRuleSheet(onDismiss = { showAddRuleSheet = false })
    }
}
