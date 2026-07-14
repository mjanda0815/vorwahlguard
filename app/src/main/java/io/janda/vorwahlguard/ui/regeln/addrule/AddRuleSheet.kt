package io.janda.vorwahlguard.ui.regeln.addrule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.PatternSyntax
import kotlinx.coroutines.launch

/**
 * Modal host for rule creation (issue #6): title, the "Land wählen"/"Vorwahl eingeben" tabs,
 * the shared [ActionPicker] and the save button. All state lives in [AddRuleViewModel]; this
 * composable only renders it and forwards events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddRuleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                viewModel.reset()
                onDismiss()
            }
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            close()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        // Title/tabs above and ActionPicker/save below stay pinned; only the tab content
        // between them scrolls (issue #74 — with everything in one LazyColumn, the save button
        // sat below ~200 country rows, and selecting a country or PRIVATE gave no visible
        // feedback). The LazyColumn is the single scroll container: this outer Column has no
        // verticalScroll, so the "never nest a LazyColumn in a scrollable Column" rule from
        // AddRuleSheetLayoutTest still holds. weight(fill = false) keeps the sheet
        // content-sized when the list is short instead of forcing full height.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.add_rule_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.tab == AddRuleTab.COUNTRY,
                    onClick = { viewModel.selectTab(AddRuleTab.COUNTRY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.add_rule_tab_country))
                }
                SegmentedButton(
                    selected = uiState.tab == AddRuleTab.PREFIX,
                    onClick = { viewModel.selectTab(AddRuleTab.PREFIX) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.add_rule_tab_prefix))
                }
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                when (uiState.tab) {
                    AddRuleTab.COUNTRY -> countryPickerItems(
                        query = uiState.countryQuery,
                        countries = uiState.countries,
                        selectedCountry = uiState.selectedCountry,
                        privateSelected = uiState.selectedCountry == null &&
                            uiState.patternText == PatternSyntax.PRIVATE_TOKEN,
                        resultingPattern = uiState.patternText,
                        collateral = uiState.collateral,
                        onQueryChange = viewModel::onCountryQueryChanged,
                        onCountrySelected = viewModel::selectCountry,
                        onPrivateSelected = viewModel::selectPrivateRule,
                    )

                    AddRuleTab.PREFIX -> item {
                        PrefixInputContent(
                            patternText = uiState.patternText,
                            patternValid = uiState.patternValid,
                            patternMissingWildcard = uiState.patternMissingWildcard,
                            duplicate = uiState.duplicate,
                            testInput = uiState.testInput,
                            testOutcome = uiState.testOutcome,
                            onPatternTextChange = viewModel::onPatternTextChanged,
                            onTestInputChange = viewModel::onTestInputChanged,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }

            ActionPicker(
                selected = uiState.selectedAction,
                recommended = uiState.recommendedAction,
                onSelect = viewModel::selectAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            Button(
                onClick = { viewModel.save() },
                enabled = uiState.saveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.add_rule_save))
            }
        }
    }
}
