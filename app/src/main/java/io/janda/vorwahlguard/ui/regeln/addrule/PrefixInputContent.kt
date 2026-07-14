package io.janda.vorwahlguard.ui.regeln.addrule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R

/**
 * "Vorwahl eingeben" tab body: the free-text pattern field with live validation and a
 * "Nummer testen" field that reports what the in-progress rule (or an existing rule) would do
 * with the typed number. Stateless — every keystroke is reported up to `AddRuleViewModel`.
 *
 * [patternMissingWildcard] flags a syntactically valid but permanently dead pattern — a bare
 * calling code with no trailing `*` (e.g. `+43` instead of `+43*`) parses as an exact match on a
 * number that can never occur. Shown the same way as [duplicate]: the field turns to the error
 * style, but saving is still allowed (the pattern is legal grammar; this is a nudge, not a block).
 */
@Composable
fun PrefixInputContent(
    patternText: String,
    patternValid: Boolean,
    patternMissingWildcard: Boolean,
    duplicate: Boolean,
    testInput: String,
    testOutcome: TestOutcome?,
    onPatternTextChange: (String) -> Unit,
    onTestInputChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val showInvalid = patternText.isNotEmpty() && !patternValid
        OutlinedTextField(
            value = patternText,
            onValueChange = onPatternTextChange,
            label = { Text(stringResource(R.string.add_rule_pattern_label)) },
            isError = showInvalid || duplicate || patternMissingWildcard,
            supportingText = {
                when {
                    showInvalid -> Text(stringResource(R.string.add_rule_pattern_invalid))
                    duplicate -> Text(stringResource(R.string.add_rule_pattern_duplicate))
                    patternMissingWildcard ->
                        Text(stringResource(R.string.add_rule_pattern_missing_wildcard, patternText))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = testInput,
            onValueChange = onTestInputChange,
            label = { Text(stringResource(R.string.add_rule_test_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )

        val outcomeText = testOutcome?.toDisplayText()
        if (outcomeText != null) {
            Text(
                text = outcomeText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TestOutcome.toDisplayText(): String = when (this) {
    is TestOutcome.InvalidNumber -> stringResource(R.string.add_rule_test_invalid)
    is TestOutcome.NoMatch -> stringResource(R.string.add_rule_test_no_match)
    is TestOutcome.DraftMatched ->
        stringResource(R.string.add_rule_test_draft_match, stringResource(action.labelRes()))
    is TestOutcome.ExistingRuleMatched ->
        stringResource(R.string.add_rule_test_existing_match, stringResource(action.labelRes()))
}
