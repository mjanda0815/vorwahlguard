package io.janda.vorwahlguard.ui.regeln

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.ui.regeln.addrule.labelRes
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the stateless [RuleListContent]: section header visibility, the per-[RuleLabel] row
 * text (country name, pluralized ambiguous region count, withheld-number label, raw pattern
 * text), the empty state, the "nothing extra while not loaded" behavior, and swipe-to-delete
 * wiring back to `onDelete`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RuleListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var whitelistHeader: String
    private lateinit var blacklistHeader: String
    private lateinit var emptyTitle: String
    private lateinit var emptyBody: String
    private lateinit var privateLabel: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        whitelistHeader = context.getString(R.string.rules_section_whitelist)
        blacklistHeader = context.getString(R.string.rules_section_blacklist)
        emptyTitle = context.getString(R.string.rules_empty_title)
        emptyBody = context.getString(R.string.rules_empty_body)
        privateLabel = context.getString(R.string.rules_private_label)
    }

    private fun setContent(state: RegelnUiState, onDelete: (String) -> Unit = {}) {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                RuleListContent(state = state, onDelete = onDelete)
            }
        }
    }

    @Test
    fun bothSectionHeadersShownWhenBothListsAreNonEmpty() {
        val whitelistRow = RuleRowUi("1", "+436631234567", RuleAction.ALLOW, RuleLabel.Raw)
        val blacklistRow = RuleRowUi("2", "+43663*", RuleAction.BLOCK, RuleLabel.Raw)
        setContent(RegelnUiState(whitelist = listOf(whitelistRow), blacklist = listOf(blacklistRow), loaded = true))

        composeTestRule.onNodeWithText(whitelistHeader).assertExists()
        composeTestRule.onNodeWithText(blacklistHeader).assertExists()
    }

    @Test
    fun countryRowShowsTheCountryNameThePatternAndTheActionLabel() {
        val row = RuleRowUi("1", "+43*", RuleAction.BLOCK, RuleLabel.Country("🇦🇹", "Österreich"))
        setContent(RegelnUiState(blacklist = listOf(row), loaded = true))

        // The blocked pattern is now shown next to the country name (issue #91): "Österreich · +43*".
        composeTestRule.onNodeWithText("Österreich", substring = true).assertExists()
        composeTestRule.onNodeWithText("+43*", substring = true).assertExists()
        composeTestRule.onNodeWithText(context.getString(RuleAction.BLOCK.labelRes())).assertExists()
    }

    @Test
    fun ambiguousCodeRowHeaderShowsThePatternTextAndTheRegionCount() {
        val regions = List(4) { RegionEntry("🏳", "Gebiet ${it + 1}") }
        val row = RuleRowUi("1", "+44*", RuleAction.SILENCE, RuleLabel.AmbiguousCode(regions))
        setContent(RegelnUiState(blacklist = listOf(row), loaded = true))

        val regionCountText = context.resources.getQuantityString(R.plurals.rules_region_count, 4, 4)
        composeTestRule.onNodeWithText("+44* · $regionCountText").assertExists()
    }

    @Test
    fun ambiguousCodeRowWithFourRegionsShowsEveryRegionAndNoExpandAffordance() {
        val regions = listOf(
            RegionEntry("🇬🇧", "Vereinigtes Königreich"),
            RegionEntry("🇬🇬", "Guernsey"),
            RegionEntry("🇮🇲", "Isle of Man"),
            RegionEntry("🇯🇪", "Jersey"),
        )
        val row = RuleRowUi("1", "+44*", RuleAction.BLOCK, RuleLabel.AmbiguousCode(regions))
        setContent(RegelnUiState(blacklist = listOf(row), loaded = true))

        composeTestRule.onNodeWithText("Guernsey").assertExists()
        composeTestRule.onNodeWithText("Jersey").assertExists()
        // Exactly four regions fit the collapse limit, so there is no "… und X weitere" line.
        composeTestRule.onNodeWithText(context.getString(R.string.rules_regions_collapse)).assertDoesNotExist()
    }

    @Test
    fun ambiguousCodeRowWithManyRegionsCollapsesTheTailThenExpandsOnTap() {
        val regions = List(8) { RegionEntry("🏳", "Gebiet ${it + 1}") }
        val row = RuleRowUi("1", "+1*", RuleAction.SILENCE, RuleLabel.AmbiguousCode(regions))
        setContent(RegelnUiState(blacklist = listOf(row), loaded = true))

        // Collapsed: the first four regions show, the fifth is hidden behind the "more" line.
        composeTestRule.onNodeWithText("Gebiet 4").assertExists()
        composeTestRule.onNodeWithText("Gebiet 5").assertDoesNotExist()
        val moreText = context.resources.getQuantityString(R.plurals.rules_regions_more, 4, 4)
        composeTestRule.onNodeWithText(moreText).assertExists()

        // Tapping the card expands it and reveals the previously hidden regions.
        val header = "+1* · " + context.resources.getQuantityString(R.plurals.rules_region_count, 8, 8)
        composeTestRule.onNodeWithText(header).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Gebiet 5").assertExists()
        composeTestRule.onNodeWithText("Gebiet 8").assertExists()
    }

    @Test
    fun theSwipeToDeleteHintIsShownWhenTheListHasRows() {
        val row = RuleRowUi("1", "+436631234567", RuleAction.ALLOW, RuleLabel.Raw)
        setContent(RegelnUiState(blacklist = listOf(row), loaded = true))

        composeTestRule.onNodeWithText(context.getString(R.string.rules_swipe_hint)).assertExists()
    }

    @Test
    fun privateRowShowsTheWithheldNumberLabel() {
        val row = RuleRowUi("1", "PRIVATE", RuleAction.BLOCK, RuleLabel.Private)
        setContent(RegelnUiState(blacklist = listOf(row), loaded = true))

        composeTestRule.onNodeWithText(privateLabel).assertExists()
    }

    @Test
    fun rawRowShowsTheRawPatternText() {
        val row = RuleRowUi("1", "+436631234567", RuleAction.ALLOW, RuleLabel.Raw)
        setContent(RegelnUiState(whitelist = listOf(row), loaded = true))

        composeTestRule.onNodeWithText("+436631234567", substring = true).assertExists()
    }

    @Test
    fun emptyStateShowsTheEmptyTitleAndBodyWhenLoadedAndBothListsAreEmpty() {
        setContent(RegelnUiState(loaded = true))

        composeTestRule.onNodeWithText(emptyTitle).assertExists()
        composeTestRule.onNodeWithText(emptyBody).assertExists()
    }

    @Test
    fun notYetLoadedStateShowsNeitherTheEmptyStateNorAnySectionHeader() {
        setContent(RegelnUiState(loaded = false))

        composeTestRule.onNodeWithText(emptyTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(emptyBody).assertDoesNotExist()
        composeTestRule.onNodeWithText(whitelistHeader).assertDoesNotExist()
        composeTestRule.onNodeWithText(blacklistHeader).assertDoesNotExist()
    }

    @Test
    fun swipingARowInvokesOnDeleteWithThatRowsId() {
        val row = RuleRowUi("row-to-delete", "+436631234567", RuleAction.ALLOW, RuleLabel.Raw)
        var deletedId: String? = null
        setContent(
            state = RegelnUiState(whitelist = listOf(row), loaded = true),
            onDelete = { deletedId = it },
        )

        composeTestRule.onNodeWithText("+436631234567", substring = true).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertEquals(row.id, deletedId)
    }
}
