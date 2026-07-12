package io.janda.vorwahlguard.ui.regeln.addrule

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme
import java.time.Instant
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the stateless [PrefixInputContent]: the pattern field's error/supportingText gating
 * (empty text never shows the invalid message even though it is not a valid pattern, per the
 * composable's `showInvalid = patternText.isNotEmpty() && !patternValid`), the duplicate message
 * taking priority display-wise once the text is non-empty and valid, the "Nummer testen" outcome
 * text for every [TestOutcome] branch (including the %1$s action-label substitution), and the
 * two text field callbacks wiring keystrokes back up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PrefixInputContentTest {

    @get:JUnitRule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var patternLabel: String
    private lateinit var patternInvalid: String
    private lateinit var patternDuplicate: String
    private lateinit var testLabel: String
    private lateinit var testInvalid: String
    private lateinit var testNoMatch: String
    private lateinit var blockLabel: String
    private lateinit var silenceLabel: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        patternLabel = context.getString(R.string.add_rule_pattern_label)
        patternInvalid = context.getString(R.string.add_rule_pattern_invalid)
        patternDuplicate = context.getString(R.string.add_rule_pattern_duplicate)
        testLabel = context.getString(R.string.add_rule_test_label)
        testInvalid = context.getString(R.string.add_rule_test_invalid)
        testNoMatch = context.getString(R.string.add_rule_test_no_match)
        blockLabel = context.getString(R.string.action_block)
        silenceLabel = context.getString(R.string.action_silence)
    }

    private fun draftMatchText(actionLabel: String): String =
        context.getString(R.string.add_rule_test_draft_match, actionLabel)

    private fun existingMatchText(actionLabel: String): String =
        context.getString(R.string.add_rule_test_existing_match, actionLabel)

    private fun setContent(
        patternText: String = "",
        patternValid: Boolean = true,
        duplicate: Boolean = false,
        testInput: String = "",
        testOutcome: TestOutcome? = null,
        onPatternTextChange: (String) -> Unit = {},
        onTestInputChange: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            VorwahlGuardTheme(dynamicColor = false) {
                PrefixInputContent(
                    patternText = patternText,
                    patternValid = patternValid,
                    duplicate = duplicate,
                    testInput = testInput,
                    testOutcome = testOutcome,
                    onPatternTextChange = onPatternTextChange,
                    onTestInputChange = onTestInputChange,
                )
            }
        }
    }

    @Test
    fun emptyUntouchedPatternShowsNeitherInvalidNorDuplicateMessage() {
        setContent(patternText = "", patternValid = false, duplicate = false)

        composeTestRule.onNodeWithText(patternInvalid).assertDoesNotExist()
        composeTestRule.onNodeWithText(patternDuplicate).assertDoesNotExist()
    }

    @Test
    fun invalidPatternTextShowsTheInvalidPatternMessage() {
        setContent(patternText = "garbage", patternValid = false, duplicate = false)

        composeTestRule.onNodeWithText(patternInvalid).assertExists()
    }

    @Test
    fun validButDuplicatePatternShowsTheDuplicateMessageNotTheInvalidOne() {
        setContent(patternText = "+43*", patternValid = true, duplicate = true)

        composeTestRule.onNodeWithText(patternDuplicate).assertExists()
        composeTestRule.onNodeWithText(patternInvalid).assertDoesNotExist()
    }

    @Test
    fun nullTestOutcomeShowsNoOutcomeText() {
        setContent(testOutcome = null)

        composeTestRule.onNodeWithText(testInvalid).assertDoesNotExist()
        composeTestRule.onNodeWithText(testNoMatch).assertDoesNotExist()
        composeTestRule.onNodeWithText(draftMatchText(blockLabel)).assertDoesNotExist()
        composeTestRule.onNodeWithText(existingMatchText(silenceLabel)).assertDoesNotExist()
    }

    @Test
    fun invalidNumberOutcomeShowsTheInvalidNumberMessage() {
        setContent(testOutcome = TestOutcome.InvalidNumber)

        composeTestRule.onNodeWithText(testInvalid).assertExists()
    }

    @Test
    fun noMatchOutcomeShowsTheNoMatchMessage() {
        setContent(testOutcome = TestOutcome.NoMatch)

        composeTestRule.onNodeWithText(testNoMatch).assertExists()
    }

    @Test
    fun draftMatchedOutcomeShowsTheFormattedDraftMatchMessageWithTheActionLabel() {
        setContent(testOutcome = TestOutcome.DraftMatched(RuleAction.BLOCK))

        composeTestRule.onNodeWithText(draftMatchText(blockLabel)).assertExists()
    }

    @Test
    fun existingRuleMatchedOutcomeShowsTheFormattedExistingMatchMessageWithTheActionLabel() {
        val rule = Rule(
            "rule-1",
            PatternSyntax.parse("+43663*"),
            RuleAction.SILENCE,
            true,
            null,
            Instant.EPOCH,
        )

        setContent(testOutcome = TestOutcome.ExistingRuleMatched(rule, RuleAction.SILENCE))

        composeTestRule.onNodeWithText(existingMatchText(silenceLabel)).assertExists()
    }

    @Test
    fun typingIntoThePatternFieldInvokesOnPatternTextChangeWithTheTypedText() {
        var captured: String? = null
        setContent(patternText = "", onPatternTextChange = { captured = it })

        composeTestRule.onNodeWithText(patternLabel).performTextInput("+43*")

        assert(captured == "+43*") {
            "expected onPatternTextChange to be invoked with +43*, but was $captured"
        }
    }

    @Test
    fun typingIntoTheTestInputFieldInvokesOnTestInputChangeWithTheTypedText() {
        var captured: String? = null
        setContent(testInput = "", onTestInputChange = { captured = it })

        composeTestRule.onNodeWithText(testLabel).performTextInput("+436631234567")

        assert(captured == "+436631234567") {
            "expected onTestInputChange to be invoked with +436631234567, but was $captured"
        }
    }
}
