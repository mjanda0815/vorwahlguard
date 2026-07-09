package io.janda.vorwahlguard.ui.protokoll

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.components.PlaceholderScreen

/** Call event log. M0 placeholder; reverse-chronological list + filters in M4. */
@Composable
fun ProtokollScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(R.string.screen_log_placeholder, modifier)
}
