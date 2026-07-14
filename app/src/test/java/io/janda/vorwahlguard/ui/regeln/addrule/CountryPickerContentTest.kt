package io.janda.vorwahlguard.ui.regeln.addrule

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.Country
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the stateless [CountryPickerContent]: the country list rendering (flag/name/calling
 * code per row), search field wiring back to `onQueryChange`, row click wiring back to
 * `onCountrySelected`, and the "resulting pattern" text plus the CLAUDE.md §6 collateral warning
 * card, which is only shown once a country is selected and only carries the plural/singular
 * variant [CollateralCard] picks based on [CollateralInfo.remainingOtherCount].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CountryPickerContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var searchHint: String

    private val austria = CountryUi(
        iso2 = "AT",
        callingCode = 43,
        flagEmoji = Country("AT", 43).flagEmoji(),
        displayName = "Österreich",
    )
    private val germany = CountryUi(
        iso2 = "DE",
        callingCode = 49,
        flagEmoji = Country("DE", 49).flagEmoji(),
        displayName = "Deutschland",
    )
    private val unitedStates = CountryUi(
        iso2 = "US",
        callingCode = 1,
        flagEmoji = Country("US", 1).flagEmoji(),
        displayName = "United States",
    )
    private val countries = listOf(austria, germany, unitedStates)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        searchHint = context.getString(R.string.add_rule_search_hint)
    }

    private fun setContent(
        query: String = "",
        countries: List<CountryUi> = this.countries,
        selectedCountry: CountryUi? = null,
        privateSelected: Boolean = false,
        resultingPattern: String = "",
        collateral: CollateralInfo? = null,
        onQueryChange: (String) -> Unit = {},
        onCountrySelected: (CountryUi) -> Unit = {},
        onPrivateSelected: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                CountryPickerContent(
                    query = query,
                    countries = countries,
                    selectedCountry = selectedCountry,
                    privateSelected = privateSelected,
                    resultingPattern = resultingPattern,
                    collateral = collateral,
                    onQueryChange = onQueryChange,
                    onCountrySelected = onCountrySelected,
                    onPrivateSelected = onPrivateSelected,
                )
            }
        }
    }

    @Test
    fun pinnedPrivateRowShowsItsLabelAndHint() {
        setContent()

        composeTestRule.onNodeWithText(context.getString(R.string.rules_private_label)).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.add_rule_private_hint)).assertExists()
    }

    @Test
    fun pinnedPrivateRowStaysVisibleWhileACountryQueryFiltersTheList() {
        // The ViewModel filters `countries` by the query; the pinned row must not disappear with
        // them — withheld caller IDs have no country to match a query against.
        setContent(query = "Öst", countries = emptyList())

        composeTestRule.onNodeWithText(context.getString(R.string.rules_private_label)).assertExists()
    }

    @Test
    fun clickingThePinnedPrivateRowInvokesOnPrivateSelected() {
        var invoked = false
        setContent(onPrivateSelected = { invoked = true })

        composeTestRule.onNodeWithText(context.getString(R.string.rules_private_label)).performClick()

        assertEquals(true, invoked)
    }

    @Test
    fun privateSelectionShowsTheHumanReadableLabelNotTheRawToken() {
        setContent(privateSelected = true, resultingPattern = "PRIVATE")

        val readable = context.getString(
            R.string.add_rule_resulting_pattern,
            context.getString(R.string.rules_private_label),
        )
        composeTestRule.onNodeWithText(readable).assertExists()
        composeTestRule.onNodeWithText(
            context.getString(R.string.add_rule_resulting_pattern, "PRIVATE"),
        ).assertDoesNotExist()
    }

    @Test
    fun everyCountryRowShowsItsDisplayNameAndCallingCode() {
        setContent()

        countries.forEach { country ->
            composeTestRule.onNodeWithText(country.displayName, substring = true).assertExists()
            composeTestRule.onNodeWithText("+${country.callingCode}", substring = true).assertExists()
        }
    }

    @Test
    fun typingInSearchFieldInvokesOnQueryChangeWithTypedText() {
        var query: String? = null
        setContent(onQueryChange = { query = it })

        composeTestRule.onNodeWithText(searchHint).performTextInput("Öst")

        assertEquals("Öst", query)
    }

    @Test
    fun clickingCountryRowInvokesOnCountrySelectedWithThatCountry() {
        var selected: CountryUi? = null
        setContent(onCountrySelected = { selected = it })

        composeTestRule.onNodeWithText(germany.displayName, substring = true).performClick()

        assertEquals(germany, selected)
    }

    @Test
    fun noResultingPatternOrCollateralCardWhenNoCountrySelected() {
        setContent(selectedCountry = null, resultingPattern = "+43*", collateral = CollateralInfo("Kanada", 5))

        val resultingPatternText = context.getString(R.string.add_rule_resulting_pattern, "+43*")
        composeTestRule.onNodeWithText(resultingPatternText, substring = true).assertDoesNotExist()
    }

    @Test
    fun resultingPatternShownWithoutCollateralCardWhenCollateralIsNull() {
        setContent(selectedCountry = austria, resultingPattern = "+43*", collateral = null)

        val resultingPatternText = context.getString(R.string.add_rule_resulting_pattern, "+43*")
        composeTestRule.onNodeWithText(resultingPatternText).assertExists()

        val singleText = context.getString(R.string.add_rule_collateral_single, "Kanada")
        composeTestRule.onNodeWithText(singleText, substring = true).assertDoesNotExist()
    }

    @Test
    fun singularCollateralTextShownWhenNoOtherRegionsRemain() {
        setContent(
            selectedCountry = unitedStates,
            resultingPattern = "+1*",
            collateral = CollateralInfo("Kanada", 0),
        )

        val singleText = context.getString(R.string.add_rule_collateral_single, "Kanada")
        composeTestRule.onNodeWithText(singleText).assertExists()
    }

    @Test
    fun pluralCollateralTextShownWhenOtherRegionsRemain() {
        setContent(
            selectedCountry = unitedStates,
            resultingPattern = "+1*",
            collateral = CollateralInfo("Kanada", 5),
        )

        val pluralText = context.resources.getQuantityString(
            R.plurals.add_rule_collateral_more,
            5,
            "Kanada",
            5,
        )
        composeTestRule.onNodeWithText(pluralText).assertExists()
    }
}
