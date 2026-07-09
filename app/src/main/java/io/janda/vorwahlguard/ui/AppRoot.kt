package io.janda.vorwahlguard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.einstellungen.EinstellungenScreen
import io.janda.vorwahlguard.ui.protokoll.ProtokollScreen
import io.janda.vorwahlguard.ui.regeln.RegelnScreen
import io.janda.vorwahlguard.ui.uebersicht.UebersichtScreen

/** The four bottom-navigation destinations (PROJECT.md §7). */
private enum class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    OVERVIEW("overview", R.string.nav_overview, Icons.Filled.Home),
    RULES("rules", R.string.nav_rules, Icons.Filled.Lock),
    LOG("log", R.string.nav_log, Icons.Filled.DateRange),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(destination.icon, contentDescription = null)
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.OVERVIEW.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.OVERVIEW.route) { UebersichtScreen() }
            composable(Destination.RULES.route) { RegelnScreen() }
            composable(Destination.LOG.route) { ProtokollScreen() }
            composable(Destination.SETTINGS.route) { EinstellungenScreen() }
        }
    }
}
