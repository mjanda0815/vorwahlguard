package io.janda.vorwahlguard.ui.protokoll

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.ui.regeln.addrule.labelRes
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the stateless [ProtokollListContent]: the per-[CallEventDisplay] row text (raw number,
 * region name, unknown-region fallback, withheld-number label), the two distinct empty-state
 * variants (`loaded` unfiltered vs. `loaded` filtered — [ProtokollUiState.hasAnyEvents] is what
 * distinguishes them, see its KDoc), the "nothing extra while not loaded" behavior, and the
 * filter chip row wiring back to `onFilterSelect`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ProtokollListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var emptyTitle: String
    private lateinit var emptyBody: String
    private lateinit var emptyFilteredTitle: String
    private lateinit var emptyFilteredBody: String
    private lateinit var regionUnknown: String
    private lateinit var privateLabel: String
    private lateinit var filterAll: String
    private lateinit var logReasonContact: String
    private lateinit var logReasonNoRule: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        emptyTitle = context.getString(R.string.log_empty_title)
        emptyBody = context.getString(R.string.log_empty_body)
        emptyFilteredTitle = context.getString(R.string.log_empty_filtered_title)
        emptyFilteredBody = context.getString(R.string.log_empty_filtered_body)
        regionUnknown = context.getString(R.string.log_region_unknown)
        privateLabel = context.getString(R.string.rules_private_label)
        filterAll = context.getString(R.string.log_filter_all)
        logReasonContact = context.getString(R.string.log_reason_contact)
        logReasonNoRule = context.getString(R.string.log_reason_no_rule)
    }

    private fun setContent(state: ProtokollUiState, onFilterSelect: (RuleAction?) -> Unit = {}) {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                ProtokollListContent(state = state, onFilterSelect = onFilterSelect)
            }
        }
    }

    private fun row(
        id: String = "row-1",
        timestampText: String = "13.07.26, 10:00",
        action: RuleAction = RuleAction.BLOCK,
        display: CallEventDisplay,
        allowReason: AllowReasonUi? = null,
        contactName: String? = null,
    ): CallEventRowUi = CallEventRowUi(id, timestampText, action, display, allowReason, contactName = contactName)

    @Test
    fun numberRowShowsTheE164TextAndTheActionLabel() {
        val timestampText = "13.07.26, 10:00"
        val displayRow = row(action = RuleAction.BLOCK, timestampText = timestampText, display = CallEventDisplay.Number("+4915112345678"))
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText("+4915112345678", substring = true).assertExists()
        // The BLOCK label appears twice on screen — once as the always-rendered filter chip,
        // once as the row's own action label — so a single-match onNodeWithText would fail here.
        // Two occurrences is the proof the row renders its action label at all: only the chip
        // (one occurrence) would remain if the row didn't.
        composeTestRule.onAllNodesWithText(context.getString(RuleAction.BLOCK.labelRes())).assertCountEquals(2)
    }

    @Test
    fun numberRowShowsItsTimestampText() {
        val timestampText = "13.07.26, 10:00"
        val displayRow = row(timestampText = timestampText, display = CallEventDisplay.Number("+4915112345678"))
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(timestampText, substring = true).assertExists()
    }

    @Test
    fun regionRowShowsTheCountryName() {
        val displayRow = row(display = CallEventDisplay.Region("🇦🇹", "Austria"))
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText("Austria", substring = true).assertExists()
    }

    @Test
    fun unknownRegionRowShowsTheRegionUnknownString() {
        val displayRow = row(display = CallEventDisplay.UnknownRegion("XX"))
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(regionUnknown).assertExists()
    }

    @Test
    fun privateRowShowsTheWithheldNumberLabel() {
        val displayRow = row(display = CallEventDisplay.Private)
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(privateLabel).assertExists()
    }

    @Test
    fun aRowWithNoAllowReasonDoesNotShowAReasonCaption() {
        val displayRow = row(action = RuleAction.BLOCK, display = CallEventDisplay.Number("+4915112345678"))
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(logReasonContact).assertDoesNotExist()
        composeTestRule.onNodeWithText(logReasonNoRule).assertDoesNotExist()
    }

    @Test
    fun aContactBypassRowShowsTheContactReasonCaption() {
        val displayRow = row(
            action = RuleAction.ALLOW,
            display = CallEventDisplay.Number("+4915112345678"),
            allowReason = AllowReasonUi.CONTACT_BYPASS,
        )
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(logReasonContact).assertExists()
    }

    @Test
    fun aNoMatchRowShowsTheNoMatchingRuleReasonCaption() {
        val displayRow = row(
            action = RuleAction.ALLOW,
            display = CallEventDisplay.Number("+4915112345678"),
            allowReason = AllowReasonUi.NO_MATCH,
        )
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(logReasonNoRule).assertExists()
    }

    @Test
    fun aContactBypassRowWithAContactNameShowsTheNameInPlaceOfTheNumber() {
        val displayRow = row(
            action = RuleAction.ALLOW,
            display = CallEventDisplay.Number("+4915112345678"),
            allowReason = AllowReasonUi.CONTACT_BYPASS,
            contactName = "Alex Kontakt",
        )
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText("Alex Kontakt", substring = true).assertExists()
        composeTestRule.onNodeWithText("+4915112345678", substring = true).assertDoesNotExist()
    }

    @Test
    fun aContactBypassRowWithoutAContactNameFallsBackToTheNumber() {
        val displayRow = row(
            action = RuleAction.ALLOW,
            display = CallEventDisplay.Number("+4915112345678"),
            allowReason = AllowReasonUi.CONTACT_BYPASS,
            contactName = null,
        )
        setContent(ProtokollUiState(events = listOf(displayRow), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText("+4915112345678", substring = true).assertExists()
    }

    @Test
    fun unfilteredEmptyStateShowsTheUnfilteredEmptyTitleAndBody() {
        setContent(ProtokollUiState(events = emptyList(), hasAnyEvents = false, loaded = true))

        composeTestRule.onNodeWithText(emptyTitle).assertExists()
        composeTestRule.onNodeWithText(emptyBody).assertExists()
    }

    @Test
    fun unfilteredEmptyStateDoesNotShowTheFilteredEmptyCopy() {
        setContent(ProtokollUiState(events = emptyList(), hasAnyEvents = false, loaded = true))

        composeTestRule.onNodeWithText(emptyFilteredTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(emptyFilteredBody).assertDoesNotExist()
    }

    @Test
    fun filteredEmptyStateShowsTheFilteredEmptyTitleAndBody() {
        setContent(ProtokollUiState(events = emptyList(), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(emptyFilteredTitle).assertExists()
        composeTestRule.onNodeWithText(emptyFilteredBody).assertExists()
    }

    @Test
    fun filteredEmptyStateDoesNotShowTheUnfilteredEmptyCopy() {
        setContent(ProtokollUiState(events = emptyList(), hasAnyEvents = true, loaded = true))

        composeTestRule.onNodeWithText(emptyTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(emptyBody).assertDoesNotExist()
    }

    @Test
    fun notYetLoadedStateShowsNeitherEmptyStateVariant() {
        setContent(ProtokollUiState(events = emptyList(), hasAnyEvents = false, loaded = false))

        composeTestRule.onNodeWithText(emptyTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(emptyBody).assertDoesNotExist()
        composeTestRule.onNodeWithText(emptyFilteredTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText(emptyFilteredBody).assertDoesNotExist()
    }

    @Test
    fun tappingTheAllFilterChipInvokesOnFilterSelectWithNull() {
        var selected: RuleAction? = RuleAction.BLOCK
        setContent(
            state = ProtokollUiState(loaded = true),
            onFilterSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(filterAll).performClick()

        assertNull(selected)
    }

    @Test
    fun tappingTheBlockFilterChipInvokesOnFilterSelectWithBlock() {
        var selected: RuleAction? = null
        setContent(
            state = ProtokollUiState(loaded = true),
            onFilterSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(context.getString(RuleAction.BLOCK.labelRes())).performClick()

        assertEquals(RuleAction.BLOCK, selected)
    }
}
