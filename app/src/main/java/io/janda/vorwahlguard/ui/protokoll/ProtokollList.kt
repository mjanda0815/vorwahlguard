package io.janda.vorwahlguard.ui.protokoll

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.ui.regeln.addrule.labelRes

private val FILTER_OPTIONS: List<RuleAction?> = listOf(null, RuleAction.BLOCK, RuleAction.SILENCE, RuleAction.ALLOW)

/**
 * Stateless Protokoll list body (issue #25): an action filter row, then either the empty state or
 * a reverse-chronological [LazyColumn] — mirrors [io.janda.vorwahlguard.ui.regeln.RuleListContent]'s
 * private-sub-composable style. No swipe-to-delete: log rows are not user-deletable.
 */
@Composable
fun ProtokollListContent(
    state: ProtokollUiState,
    onFilterSelect: (RuleAction?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FilterRow(state.selectedFilter, onFilterSelect)

        if (state.loaded && state.events.isEmpty()) {
            EmptyLog(hasAnyEvents = state.hasAnyEvents, modifier = Modifier.fillMaxSize())
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.events, key = { it.id }) { row ->
                CallEventRow(row)
            }
        }
    }
}

@Composable
private fun FilterRow(selected: RuleAction?, onFilterSelect: (RuleAction?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FILTER_OPTIONS.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onFilterSelect(option) },
                label = { Text(text = if (option == null) stringResource(R.string.log_filter_all) else stringResource(option.labelRes())) },
            )
        }
    }
}

@Composable
private fun CallEventRow(row: CallEventRowUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (row.display) {
            is CallEventDisplay.Region -> Text(text = row.display.flagEmoji)
            CallEventDisplay.Private -> Icon(imageVector = Icons.Filled.Lock, contentDescription = null)
            is CallEventDisplay.Number, is CallEventDisplay.UnknownRegion -> Unit
        }
        Text(
            text = callEventRowLabelText(row),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(row.action.labelRes()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.allowReason != null) {
                Text(
                    text = stringResource(row.allowReason.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = row.timestampText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun AllowReasonUi.labelRes(): Int = when (this) {
    AllowReasonUi.CONTACT_BYPASS -> R.string.log_reason_contact
    AllowReasonUi.NO_MATCH -> R.string.log_reason_no_rule
}

@Composable
private fun callEventRowLabelText(row: CallEventRowUi): String = when (val display = row.display) {
    is CallEventDisplay.Number -> display.e164
    is CallEventDisplay.Region -> display.name
    is CallEventDisplay.UnknownRegion -> stringResource(R.string.log_region_unknown)
    CallEventDisplay.Private -> stringResource(R.string.rules_private_label)
}

@Composable
private fun EmptyLog(hasAnyEvents: Boolean, modifier: Modifier = Modifier) {
    val titleRes = if (hasAnyEvents) R.string.log_empty_filtered_title else R.string.log_empty_title
    val bodyRes = if (hasAnyEvents) R.string.log_empty_filtered_body else R.string.log_empty_body
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
