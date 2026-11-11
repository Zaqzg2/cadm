package com.cady.cadymarketing.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cady.cadymarketing.core.navigation.BottomTab
import com.cady.cadymarketing.core.navigation.Routes
import com.cady.cadymarketing.feature.audience.AudienceScreen
import com.cady.cadymarketing.feature.calendar.CalendarScreen
import com.cady.cadymarketing.feature.campaigns.CampaignsScreen
import com.cady.cadymarketing.feature.home.HomeScreen
import com.cady.cadymarketing.feature.identity.IdentityScreen
import com.cady.cadymarketing.feature.library.LibraryScreen
import com.cady.cadymarketing.feature.more.MoreScreen
import com.cady.cadymarketing.feature.products.ProductsScreen
import com.cady.cadymarketing.feature.reports.ReportsScreen
import com.cady.cadymarketing.feature.settings.SettingsScreen
import com.cady.cadymarketing.feature.studio.StudioScreen
import com.cady.cadymarketing.feature.templates.TemplatesScreen

/**
 * Bottom bar with five tabs. The Studio tab does not switch tabs: it opens a full-screen route that
 * hides the bar, and its X button returns to the tab you came from.
 */
@Composable
fun MainShell(
    email: String?,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    // Before the first destination is known the start tab is about to show, so the bar stays visible
    // (no flicker on launch).
    val showBottomBar = currentRoute == null || currentRoute in Routes.tabRoutes
    val goBack: () -> Unit = { navController.popBackStack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                MarketingBottomBar(
                    currentDestination = currentDestination,
                    onTabClick = { tab -> navController.openTab(tab) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            // The default 700 ms cross-fade feels slow; a short fade keeps navigation snappy.
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.STUDIO) { StudioScreen(onClose = goBack) }
            composable(Routes.CAMPAIGNS) { CampaignsScreen() }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    email = email,
                    onNavigate = { route -> navController.navigate(route) },
                    onSignOut = onSignOut,
                )
            }
            composable(Routes.PRODUCTS) { ProductsScreen(onBack = goBack) }
            composable(Routes.IDENTITY) { IdentityScreen(onBack = goBack) }
            composable(Routes.TEMPLATES) { TemplatesScreen(onBack = goBack) }
            composable(Routes.LIBRARY) { LibraryScreen(onBack = goBack) }
            composable(Routes.AUDIENCE) { AudienceScreen(onBack = goBack) }
            composable(Routes.REPORTS) { ReportsScreen(onBack = goBack) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = goBack) }
        }
    }
}

@Composable
private fun MarketingBottomBar(
    currentDestination: NavDestination?,
    onTabClick: (BottomTab) -> Unit,
) {
    NavigationBar {
        BottomTab.entries.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}

private fun NavHostController.openTab(tab: BottomTab) {
    if (tab.fullScreen) {
        navigate(tab.route) { launchSingleTop = true }
    } else {
        navigate(tab.route) {
            // One entry per tab; switching restores each tab's own state.
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
