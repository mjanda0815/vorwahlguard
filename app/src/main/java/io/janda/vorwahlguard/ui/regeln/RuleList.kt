package io.janda.vorwahlguard.ui.regeln

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.regeln.addrule.labelRes

/**
 * How many regions of a multi-region rule are shown before the row collapses the rest behind a
 * tap-to-expand affordance (issue #91). `+44*` (4 regions) fits exactly; `+1*` (~24) collapses.
 */
private const val COLLAPSED_REGION_LIMIT = 4

/**
 * Stateless rules list body (issue #23): a swipe-to-delete hint, a whitelist section, a blacklist
 * section, and the empty state — mirrors `CountryPickerContent`'s private-sub-composable style.
 * Every row is wrapped in a [SwipeToDismissBox] that reports [onDelete] when swiped end-to-start
 * (left in LTR, matching the delete icon's CenterEnd position); there is no optimistic local
 * removal here, the list re-renders once [RegelnViewModel]'s Flow re-emits. A multi-region rule
 * (issue #91) renders as one taller card that still swipes as a single unit.
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

    Column(modifier = modifier.fillMaxSize()) {
        // Only shown once there is a non-empty list to act on — the empty case returned above,
        // and while not loaded there is nothing to hint at (issue #91: swipe-to-delete was
        // previously undiscoverable).
        if (state.loaded) {
            SwipeHint()
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (state.whitelist.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.rules_section_whitelist)) }
                items(state.whitelist, key = { it.id }) { row ->
                    SwipeToDeleteRow(row, onDelete)
                    HorizontalDivider()
                }
            }
            if (state.blacklist.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.rules_section_blacklist)) }
                items(state.blacklist, key = { it.id }) { row ->
                    SwipeToDeleteRow(row, onDelete)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SwipeHint() {
    Text(
        text = stringResource(R.string.rules_swipe_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
    when (val label = row.label) {
        is RuleLabel.AmbiguousCode -> AmbiguousRuleRow(row, label)
        is RuleLabel.Country, RuleLabel.Private, RuleLabel.Raw -> SingleLineRuleRow(row)
    }
}

@Composable
private fun SingleLineRuleRow(row: RuleRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val label = row.label) {
            is RuleLabel.Country -> Text(text = label.flagEmoji)
            RuleLabel.Private -> Icon(imageVector = Icons.Filled.Lock, contentDescription = null)
            is RuleLabel.AmbiguousCode, RuleLabel.Raw -> Unit
        }
        Text(
            text = singleLineLabelText(row),
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
private fun singleLineLabelText(row: RuleRowUi): String = when (val label = row.label) {
    // The blocked pattern is always visible now, not just for multi-region rules (issue #91):
    // a single-country rule reads "Österreich · +43*" instead of just the country name.
    is RuleLabel.Country -> label.name + " · " + row.patternText
    RuleLabel.Private -> stringResource(R.string.rules_private_label)
    RuleLabel.Raw -> row.patternText
    // AmbiguousCode is rendered by AmbiguousRuleRow, never here; kept for exhaustiveness.
    is RuleLabel.AmbiguousCode -> row.patternText
}

/**
 * A multi-region rule (issue #91): the code + region count as a header, then one line per affected
 * region (flag + localized name), indented below it. When there are more than [COLLAPSED_REGION_LIMIT]
 * regions the tail is hidden behind a "… und X weitere" line and the whole card toggles on tap. It
 * remains a single [SwipeToDismissBox] child, so it still deletes as one unit.
 */
@Composable
private fun AmbiguousRuleRow(row: RuleRowUi, label: RuleLabel.AmbiguousCode) {
    val total = label.regions.size
    val expandable = total > COLLAPSED_REGION_LIMIT
    var expanded by rememberSaveable(row.id) { mutableStateOf(false) }
    val shown = if (expandable && !expanded) label.regions.take(COLLAPSED_REGION_LIMIT) else label.regions
    val hidden = total - shown.size

    val background = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
    val container = if (expandable) background.clickable { expanded = !expanded } else background

    Column(
        modifier = container.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.patternText + " · " +
                    pluralStringResource(R.plurals.rules_region_count, total, total),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(row.action.labelRes()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        shown.forEach { region ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = region.flagEmoji)
                Text(text = region.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expandable) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.rules_regions_collapse)
                } else {
                    pluralStringResource(R.plurals.rules_regions_more, hidden, hidden)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
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
