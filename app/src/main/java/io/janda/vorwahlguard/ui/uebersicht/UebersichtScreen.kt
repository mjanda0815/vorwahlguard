package io.janda.vorwahlguard.ui.uebersicht

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.components.PlaceholderScreen

/** Dashboard. M0 placeholder; real content (hero counter, sparkline, top lists) in M4. */
@Composable
fun UebersichtScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(R.string.screen_overview_placeholder, modifier)
}
