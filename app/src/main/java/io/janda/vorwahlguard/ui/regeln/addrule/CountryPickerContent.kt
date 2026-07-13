package io.janda.vorwahlguard.ui.regeln.addrule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R

/**
 * "Land wählen" tab body: search field, the resulting pattern + CLAUDE.md §6 collateral warning
 * once a country is picked, and the filtered country list. Stateless — every interaction is
 * reported up to `AddRuleViewModel` via the callbacks.
 */
@Composable
fun CountryPickerContent(
    query: String,
    countries: List<CountryUi>,
    selectedCountry: CountryUi?,
    resultingPattern: String,
    collateral: CollateralInfo?,
    onQueryChange: (String) -> Unit,
    onCountrySelected: (CountryUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.add_rule_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (selectedCountry != null) {
            Text(
                text = stringResource(R.string.add_rule_resulting_pattern, resultingPattern),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (collateral != null) {
                CollateralCard(collateral, modifier = Modifier.padding(top = 8.dp))
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(countries, key = { it.iso2 }) { country ->
                CountryRow(
                    country = country,
                    selected = country.iso2 == selectedCountry?.iso2,
                    onClick = { onCountrySelected(country) },
                )
            }
        }
    }
}

@Composable
private fun CountryRow(country: CountryUi, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = country.flagEmoji)
            Text(
                text = country.displayName,
                modifier = Modifier.weight(1f),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(text = "+${country.callingCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollateralCard(collateral: CollateralInfo, modifier: Modifier = Modifier) {
    val text = if (collateral.remainingOtherCount <= 0) {
        stringResource(R.string.add_rule_collateral_single, collateral.firstOtherRegionName)
    } else {
        pluralStringResource(
            R.plurals.add_rule_collateral_more,
            collateral.remainingOtherCount,
            collateral.firstOtherRegionName,
            collateral.remainingOtherCount,
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}
