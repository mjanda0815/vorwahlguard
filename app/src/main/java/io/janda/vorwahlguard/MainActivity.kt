package io.janda.vorwahlguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.janda.vorwahlguard.ui.AppRoot
import io.janda.vorwahlguard.ui.theme.VorwahlGuardTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VorwahlGuardTheme {
                AppRoot()
            }
        }
    }
}
