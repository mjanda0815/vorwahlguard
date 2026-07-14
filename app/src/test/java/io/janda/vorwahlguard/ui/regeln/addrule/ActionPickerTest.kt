package io.janda.vorwahlguard.ui.regeln.addrule

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the stateless [ActionPicker] segmented control: label rendering (via [Context.getString]
 * so the assertions survive copy edits, per house style), the recommendation hint that is gated
 * on `recommended == RuleAction.SILENCE`, click wiring back to `onSelect`, and which segment is
 * shown as selected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ActionPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var blockLabel: String
    private lateinit var silenceLabel: String
    private lateinit var allowLabel: String
    private lateinit var recommendedHint: String
    private lateinit var mailboxHint: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        blockLabel = context.getString(R.string.action_block)
        silenceLabel = context.getString(R.string.action_silence)
        allowLabel = context.getString(R.string.action_allow)
        recommendedHint = context.getString(R.string.add_rule_silence_recommended)
        mailboxHint = context.getString(R.string.add_rule_block_mailbox_hint)
    }

    private fun setContent(
        selected: RuleAction,
        recommended: RuleAction,
        onSelect: (RuleAction) -> Unit = {},
    ) {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                ActionPicker(selected = selected, recommended = recommended, onSelect = onSelect)
            }
        }
    }

    @Test
    fun allThreeActionLabelsAreDisplayed() {
        setContent(selected = RuleAction.BLOCK, recommended = RuleAction.ALLOW)

        composeTestRule.onNodeWithText(blockLabel).assertExists()
        composeTestRule.onNodeWithText(silenceLabel).assertExists()
        composeTestRule.onNodeWithText(allowLabel).assertExists()
    }

    @Test
    fun recommendationHintShownWhenSilenceIsRecommended() {
        setContent(selected = RuleAction.BLOCK, recommended = RuleAction.SILENCE)

        composeTestRule.onNodeWithText(recommendedHint).assertExists()
    }

    @Test
    fun recommendationHintHiddenWhenBlockIsRecommended() {
        setContent(selected = RuleAction.ALLOW, recommended = RuleAction.BLOCK)

        composeTestRule.onNodeWithText(recommendedHint).assertDoesNotExist()
    }

    @Test
    fun recommendationHintHiddenWhenAllowIsRecommended() {
        setContent(selected = RuleAction.BLOCK, recommended = RuleAction.ALLOW)

        composeTestRule.onNodeWithText(recommendedHint).assertDoesNotExist()
    }

    @Test
    fun clickingSilenceSegmentInvokesOnSelectWithSilence() {
        var selected: RuleAction? = null
        setContent(
            selected = RuleAction.BLOCK,
            recommended = RuleAction.ALLOW,
            onSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(silenceLabel).performClick()

        assert(selected == RuleAction.SILENCE) {
            "expected onSelect to be invoked with SILENCE, but was $selected"
        }
    }

    @Test
    fun selectedSegmentIsShownAsSelected() {
        setContent(selected = RuleAction.SILENCE, recommended = RuleAction.ALLOW)

        composeTestRule.onNodeWithText(silenceLabel).assertIsSelected()
        composeTestRule.onNodeWithText(blockLabel).assertIsNotSelected()
        composeTestRule.onNodeWithText(allowLabel).assertIsNotSelected()
    }

    @Test
    fun mailboxHintIsShownWhenBlockIsSelected() {
        setContent(selected = RuleAction.BLOCK, recommended = RuleAction.BLOCK)

        composeTestRule.onNodeWithText(mailboxHint).assertExists()
    }

    @Test
    fun mailboxHintIsHiddenWhenBlockIsNotSelected() {
        setContent(selected = RuleAction.SILENCE, recommended = RuleAction.SILENCE)

        composeTestRule.onNodeWithText(mailboxHint).assertDoesNotExist()
    }

    @Test
    fun mailboxHintAndSilenceRecommendationCanShowTogether() {
        // A country rule recommends SILENCE, but the user explicitly picked BLOCK: both hints
        // are relevant at once — the recommendation and what BLOCK actually does.
        setContent(selected = RuleAction.BLOCK, recommended = RuleAction.SILENCE)

        composeTestRule.onNodeWithText(recommendedHint).assertExists()
        composeTestRule.onNodeWithText(mailboxHint).assertExists()
    }
}
