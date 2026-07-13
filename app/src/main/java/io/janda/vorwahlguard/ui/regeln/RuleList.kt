package io.janda.vorwahlguard.ui.regeln

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.regeln.addrule.labelRes

/**
 * Stateless rules list body (issue #23): a whitelist section, a blacklist section, and the empty
 * state — mirrors `CountryPickerContent`'s private-sub-composable style. Every row is wrapped in
 * a [SwipeToDismissBox] that reports [onDelete] when swiped end-to-start (left in LTR, matching
 * the delete icon's CenterEnd position); there is no optimistic local removal here, the list
 * re-renders once [RegelnViewModel]'s Flow re-emits.
 */
@Composable
fun RuleListContent(
    state: RegelnUiState,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loaded && state.whitelist.isEmpty() && state.blacklist.isEmpty()) {
        EmptyRules(modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (state.whitelist.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.rules_section_whitelist)) }
            items(state.whitelist, key = { it.id }) { row ->
                SwipeToDeleteRow(row, onDelete)
            }
        }
        if (state.blacklist.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.rules_section_blacklist)) }
            items(state.blacklist, key = { it.id }) { row ->
                SwipeToDeleteRow(row, onDelete)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwipeToDeleteRow(row: RuleRowUi, onDelete: (String) -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { DeleteBackground() },
        // The delete icon in the background is pinned to CenterEnd, so only the swipe direction
        // that reveals that edge (end-to-start, i.e. swiping left in LTR) should dismiss — the
        // other direction would otherwise delete behind an empty bar with the icon on the wrong side.
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete(row.id) },
    ) {
        RuleRow(row)
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.rules_delete),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun RuleRow(row: RuleRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val label = row.label) {
            is RuleLabel.Country -> Text(text = label.flagEmoji)
            RuleLabel.Private -> Icon(imageVector = Icons.Filled.Lock, contentDescription = null)
            is RuleLabel.AmbiguousCode, RuleLabel.Raw -> Unit
        }
        Text(
            text = ruleRowLabelText(row),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(row.action.labelRes()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ruleRowLabelText(row: RuleRowUi): String = when (val label = row.label) {
    is RuleLabel.Country -> label.name
    is RuleLabel.AmbiguousCode ->
        row.patternText + " (" + pluralStringResource(R.plurals.rules_region_count, label.regionCount, label.regionCount) + ")"
    RuleLabel.Private -> stringResource(R.string.rules_private_label)
    RuleLabel.Raw -> row.patternText
}

@Composable
private fun EmptyRules(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.rules_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.rules_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
