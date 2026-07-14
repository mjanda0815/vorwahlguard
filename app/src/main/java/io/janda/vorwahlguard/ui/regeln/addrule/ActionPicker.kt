package io.janda.vorwahlguard.ui.regeln.addrule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.RuleAction

private val ACTIONS = listOf(RuleAction.BLOCK, RuleAction.SILENCE, RuleAction.ALLOW)

/** Stateless Sperren/Lautlos/Zulassen segmented control, kept generic for reuse (e.g. rule editing). */
@Composable
fun ActionPicker(
    selected: RuleAction,
    recommended: RuleAction,
    onSelect: (RuleAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ACTIONS.forEachIndexed { index, action ->
                SegmentedButton(
                    selected = action == selected,
                    onClick = { onSelect(action) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ACTIONS.size),
                ) {
                    Text(stringResource(action.labelRes()))
                }
            }
        }
        if (recommended == RuleAction.SILENCE) {
            Text(
                text = stringResource(R.string.add_rule_silence_recommended),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Issue #60: a "straight to voicemail" action was requested and rejected — the platform
        // has no voicemail-routing response, and BLOCK already yields exactly that on carriers
        // with an active mailbox (network-side, identical to manually declining). This hint makes
        // the behavior discoverable instead of adding a fourth action that couldn't differ.
        if (selected == RuleAction.BLOCK) {
            Text(
                text = stringResource(R.string.add_rule_block_mailbox_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

internal fun RuleAction.labelRes(): Int = when (this) {
    RuleAction.BLOCK -> R.string.action_block
    RuleAction.SILENCE -> R.string.action_silence
    RuleAction.ALLOW -> R.string.action_allow
}
