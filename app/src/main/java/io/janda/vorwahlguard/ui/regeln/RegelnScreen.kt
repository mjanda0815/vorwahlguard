package io.janda.vorwahlguard.ui.regeln

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.components.PlaceholderScreen

/** Rules list + creation. M0 placeholder; both input paths arrive in M4 (issue #6). */
@Composable
fun RegelnScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(R.string.screen_rules_placeholder, modifier)
}
