package io.janda.vorwahlguard.ui.regeln.addrule

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduces `AddRuleSheet`'s real composition for the "Land wählen" tab: a single [LazyColumn]
 * with a header `item` followed by [countryPickerItems] — never a [LazyColumn] nested inside a
 * `Modifier.verticalScroll(...)` [androidx.compose.foundation.layout.Column]. Before that fix
 * (`CountryPickerContent`'s own nested `LazyColumn` inside `AddRuleSheet`'s scrollable `Column`),
 * this exact shape threw `IllegalStateException: Vertically scrollable component was measured
 * with an infinity maximum height constraints` at layout time — a failure that only shows up on
 * a real measure pass, never in a plain (non-Compose) unit test, which is why this needs
 * `createComposeRule` + Robolectric rather than a semantics-only assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AddRuleSheetLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val countries = (1..30).map { i ->
        CountryUi(iso2 = "C$i", callingCode = i, flagEmoji = "🏳", displayName = "Country $i")
    }

    @Test
    fun countryTabContentLaysOutWithoutThrowingWhenHostedInASingleLazyColumn() {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    item { Text("Header") }
                    countryPickerItems(
                        query = "",
                        countries = countries,
                        selectedCountry = null,
                        privateSelected = false,
                        resultingPattern = "",
                        collateral = null,
                        onQueryChange = {},
                        onCountrySelected = {},
                        onPrivateSelected = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(countries.first().displayName, substring = true).assertExists()
    }
}
