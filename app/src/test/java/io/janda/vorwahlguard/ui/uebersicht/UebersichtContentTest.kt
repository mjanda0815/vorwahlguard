package io.janda.vorwahlguard.ui.uebersicht

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.ui.regeln.RuleLabel
import io.janda.vorwahlguard.ui.regeln.RuleRowUi
import io.janda.vorwahlguard.ui.regeln.addrule.labelRes
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the stateless [UebersichtContent]: the role-warning card's three visibility states and
 * its button wiring to `onRequestRole`, the hero counter's rendered total, [TopCountryUi.Known]/
 * [TopCountryUi.Unknown] row rendering, [TopRuleUi.Known]/[TopRuleUi.Deleted] row rendering, the
 * `hasAnyEvents=false` empty state suppressing the hero/sparkline/top lists, and the `loaded=false`
 * "nothing extra" behavior — mirrors [io.janda.vorwahlguard.ui.protokoll.ProtokollListContentTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UebersichtContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var roleWarningTitle: String
    private lateinit var roleWarningBody: String
    private lateinit var roleWarningButton: String
    private lateinit var emptyTitle: String
    private lateinit var emptyBody: String
    private lateinit var sparklineDescription: String
    private lateinit var ruleDeleted: String
    private lateinit var breakdownTitle: String
    private lateinit var blockLabel: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        roleWarningTitle = context.getString(R.string.overview_role_warning_title)
        roleWarningBody = context.getString(R.string.overview_role_warning_body)
        roleWarningButton = context.getString(R.string.overview_role_warning_button)
        emptyTitle = context.getString(R.string.overview_empty_title)
        emptyBody = context.getString(R.string.overview_empty_body)
        sparklineDescription = context.getString(R.string.overview_sparkline_description)
        ruleDeleted = context.getString(R.string.overview_rule_deleted)
        breakdownTitle = context.getString(R.string.overview_breakdown_title)
        blockLabel = context.getString(R.string.action_block)
    }

    private fun setContent(state: UebersichtUiState, onRequestRole: () -> Unit = {}) {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                UebersichtContent(state = state, onRequestRole = onRequestRole)
            }
        }
    }

    private fun sparkline(count: Int = 1): List<DaySparkPoint> =
        (0 until 30).map { offset -> DaySparkPoint(LocalDate.of(2026, 6, 14).plusDays(offset.toLong()), if (offset == 29) count else 0) }

    private fun loadedStateWithEvents(
        roleAvailable: Boolean = false,
        roleHeld: Boolean = false,
        topCountries: List<TopCountryUi> = emptyList(),
        topRules: List<TopRuleUi> = emptyList(),
        actionBreakdown: List<BreakdownEntry> = emptyList(),
    ): UebersichtUiState = UebersichtUiState(
        roleAvailable = roleAvailable,
        roleHeld = roleHeld,
        totalScreened = 5,
        sparkline = sparkline(5),
        topCountries = topCountries,
        topRules = topRules,
        actionBreakdown = actionBreakdown,
        hasAnyEvents = true,
        loaded = true,
    )

    @Test
    fun roleWarningCardIsShownWhenRoleIsAvailableButNotHeld() {
        setContent(loadedStateWithEvents(roleAvailable = true, roleHeld = false))

        composeTestRule.onNodeWithText(roleWarningTitle).assertExists()
    }

    @Test
    fun roleWarningCardIsHiddenWhenRoleIsHeld() {
        setContent(loadedStateWithEvents(roleAvailable = true, roleHeld = true))

        composeTestRule.onNodeWithText(roleWarningTitle).assertDoesNotExist()
    }

    @Test
    fun roleWarningCardIsHiddenWhenRoleIsUnavailable() {
        setContent(loadedStateWithEvents(roleAvailable = false, roleHeld = false))

        composeTestRule.onNodeWithText(roleWarningTitle).assertDoesNotExist()
    }

    @Test
    fun tappingTheRoleWarningCardButtonInvokesOnRequestRole() {
        var invoked = false
        setContent(loadedStateWithEvents(roleAvailable = true, roleHeld = false), onRequestRole = { invoked = true })

        composeTestRule.onNodeWithText(roleWarningButton).performClick()

        assertTrue(invoked)
    }

    @Test
    fun heroCounterShowsTheTotalScreenedNumber() {
        setContent(loadedStateWithEvents())

        composeTestRule.onNodeWithText("5").assertExists()
    }

    @Test
    fun aKnownTopCountryRowShowsTheCountryName() {
        setContent(loadedStateWithEvents(topCountries = listOf(TopCountryUi.Known("🇦🇹", "Austria", 3))))

        composeTestRule.onNodeWithText("Austria", substring = true).assertExists()
    }

    @Test
    fun anUnknownTopCountryRowShowsTheRegionCode() {
        setContent(loadedStateWithEvents(topCountries = listOf(TopCountryUi.Unknown("XX", 2))))

        composeTestRule.onNodeWithText("XX", substring = true).assertExists()
    }

    @Test
    fun aKnownTopRuleRowShowsItsPatternText() {
        val row = RuleRowUi("rule-1", "+436631234567", RuleAction.BLOCK, RuleLabel.Raw)
        setContent(loadedStateWithEvents(topRules = listOf(TopRuleUi.Known(row, 4))))

        composeTestRule.onNodeWithText("+436631234567", substring = true).assertExists()
    }

    @Test
    fun aKnownTopRuleRowShowsItsActionLabel() {
        val row = RuleRowUi("rule-1", "+436631234567", RuleAction.SILENCE, RuleLabel.Raw)
        setContent(loadedStateWithEvents(topRules = listOf(TopRuleUi.Known(row, 4))))

        composeTestRule.onNodeWithText(context.getString(RuleAction.SILENCE.labelRes()), substring = true).assertExists()
    }

    @Test
    fun aDeletedTopRuleRowShowsTheRuleDeletedString() {
        setContent(loadedStateWithEvents(topRules = listOf(TopRuleUi.Deleted(6))))

        composeTestRule.onNodeWithText(ruleDeleted).assertExists()
    }

    @Test
    fun emptyStateShowsTheEmptyTitleAndBody() {
        setContent(UebersichtUiState(hasAnyEvents = false, loaded = true))

        composeTestRule.onNodeWithText(emptyTitle).assertExists()
        composeTestRule.onNodeWithText(emptyBody).assertExists()
    }

    @Test
    fun emptyStateDoesNotShowTheHeroCounter() {
        setContent(UebersichtUiState(totalScreened = 0, hasAnyEvents = false, loaded = true))

        composeTestRule.onNodeWithText(context.getString(R.string.overview_hero_label)).assertDoesNotExist()
    }

    @Test
    fun emptyStateDoesNotShowTheSparkline() {
        setContent(UebersichtUiState(hasAnyEvents = false, loaded = true))

        composeTestRule.onNodeWithContentDescription(sparklineDescription).assertDoesNotExist()
    }

    @Test
    fun emptyStateDoesNotShowTheTopListsSectionHeaders() {
        setContent(UebersichtUiState(hasAnyEvents = false, loaded = true))

        composeTestRule.onNodeWithText(context.getString(R.string.overview_top_countries_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.overview_top_rules_title)).assertDoesNotExist()
    }

    @Test
    fun notYetLoadedStateShowsNothing() {
        setContent(UebersichtUiState(loaded = false))

        composeTestRule.onNodeWithText(emptyTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(emptyBody).assertDoesNotExist()
        composeTestRule.onNodeWithText(roleWarningTitle).assertDoesNotExist()
    }

    @Test
    fun theSparklineIsPresentInTheTreeWhenThereIsData() {
        setContent(loadedStateWithEvents())

        composeTestRule.onNodeWithContentDescription(sparklineDescription).assertExists()
    }

    @Test
    fun breakdownSectionIsHiddenWhenEveryCategoryIsZero() {
        setContent(
            loadedStateWithEvents(
                actionBreakdown = listOf(
                    BreakdownEntry(BreakdownCategory.BLOCK, 0),
                    BreakdownEntry(BreakdownCategory.SILENCE, 0),
                    BreakdownEntry(BreakdownCategory.ALLOW_RULE, 0),
                    BreakdownEntry(BreakdownCategory.CONTACT, 0),
                    BreakdownEntry(BreakdownCategory.NO_RULE, 0),
                ),
            ),
        )

        composeTestRule.onNodeWithText(breakdownTitle).assertDoesNotExist()
    }

    @Test
    fun breakdownSectionIsShownWhenAtLeastOneCategoryIsNonZero() {
        setContent(
            loadedStateWithEvents(
                actionBreakdown = listOf(BreakdownEntry(BreakdownCategory.BLOCK, 3)),
            ),
        )

        composeTestRule.onNodeWithText(breakdownTitle).assertExists()
    }

    @Test
    fun breakdownLegendShowsTheCategoryLabelAndCount() {
        setContent(
            loadedStateWithEvents(
                actionBreakdown = listOf(BreakdownEntry(BreakdownCategory.BLOCK, 3)),
            ),
        )

        composeTestRule.onNodeWithText(blockLabel, substring = true).assertExists()
        composeTestRule.onNodeWithText("3").assertExists()
    }

    @Test
    fun breakdownSectionIsHiddenWhenTheListIsEmpty() {
        setContent(loadedStateWithEvents(actionBreakdown = emptyList()))

        composeTestRule.onNodeWithText(breakdownTitle).assertDoesNotExist()
    }
}
