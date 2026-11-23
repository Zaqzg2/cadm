package com.cady.cadymarketing.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cady.cadymarketing.core.components.ManagerOnlyScreen
import com.cady.cadymarketing.core.navigation.BottomTab
import com.cady.cadymarketing.core.navigation.Routes
import com.cady.cadymarketing.core.role.canUse
import com.cady.cadymarketing.core.theme.LocalCadyStyle
import com.cady.cadymarketing.core.theme.barLook
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.feature.audience.AudienceScreen
import com.cady.cadymarketing.feature.audience.SendListScreen
import com.cady.cadymarketing.feature.calendar.CalendarScreen
import com.cady.cadymarketing.feature.campaigns.CampaignDetailScreen
import com.cady.cadymarketing.feature.campaigns.CampaignsScreen
import com.cady.cadymarketing.feature.home.HomeScreen
import com.cady.cadymarketing.feature.identity.IdentityScreen
import com.cady.cadymarketing.feature.library.LibraryScreen
import com.cady.cadymarketing.feature.more.MoreScreen
import com.cady.cadymarketing.feature.products.ProductsScreen
import com.cady.cadymarketing.feature.reports.ReportsScreen
import com.cady.cadymarketing.feature.inventory.InventoryLinksScreen
import com.cady.cadymarketing.feature.settings.AppearanceScreen
import com.cady.cadymarketing.feature.settings.LogScreen
import com.cady.cadymarketing.feature.settings.SettingsScreen
import com.cady.cadymarketing.feature.settings.TeamScreen
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
    openContentId: String? = null,
    onOpenContentHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    // Before the first destination is known the start tab is about to show, so the bar stays visible
    // (no flicker on launch).
    val showBottomBar = currentRoute == null || currentRoute in Routes.tabRoutes
    val goBack: () -> Unit = { navController.popBackStack() }

    val shell: ShellViewModel = hiltViewModel()
    LaunchedEffect(Unit) { shell.onShellOpened() }

    // A reminder notification asked to open a post: the Studio opens on it (once).
    LaunchedEffect(openContentId) {
        if (openContentId != null) {
            navController.navigate(Routes.studio(contentId = openContentId))
            onOpenContentHandled()
        }
    }

    Scaffold(
        // See-through: the page is drawn once behind the whole app by the theme.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
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
            composable(Routes.HOME) {
                HomeScreen(
                    onStudio = { productId, ideaOfDay ->
                        navController.navigate(Routes.studio(productId = productId, ideaOfDay = ideaOfDay))
                    },
                    onLibrary = { navController.navigate(Routes.LIBRARY) },
                    onCampaigns = { navController.openTab(BottomTab.CAMPAIGNS) },
                    onProducts = { navController.navigate(Routes.PRODUCTS) },
                )
            }
            // The Studio can be opened on a saved post, a campaign, a day, a product or the idea of the day;
            // every one of those is an optional argument, so the plain "studio" route still works for the tab.
            composable(Routes.STUDIO_PATTERN, arguments = studioArguments) {
                StudioScreen(onClose = goBack, onOpenProducts = { navController.navigate(Routes.PRODUCTS) })
            }
            composable(Routes.CAMPAIGNS) {
                CampaignsScreen(onOpenCampaign = { id -> navController.navigate(Routes.campaign(id)) })
            }
            composable(
                Routes.CAMPAIGN_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType }),
            ) {
                CampaignDetailScreen(
                    onBack = goBack,
                    onOpenPost = { id -> navController.navigate(Routes.studio(contentId = id)) },
                    onNewPost = { campaignId -> navController.navigate(Routes.studio(campaignId = campaignId)) },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onNewPost = { date -> navController.navigate(Routes.studio(date = date)) },
                    onNewArrivalPost = { productId, date -> navController.navigate(Routes.studio(productId = productId, date = date)) },
                    onOpenPost = { id -> navController.navigate(Routes.studio(contentId = id)) },
                    onOpenCampaign = { id -> navController.navigate(Routes.campaign(id)) },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    email = email,
                    onNavigate = { route -> navController.navigate(route) },
                    onSignOut = onSignOut,
                )
            }
            composable(Routes.PRODUCTS) { ProductsScreen(onBack = goBack) }
            // The screens that are the manager's: a marketer who gets here (a shared link, an old back stack) sees a clear refusal.
            composable(Routes.IDENTITY) {
                if (canUse(Feature.EDIT_IDENTITY)) IdentityScreen(onBack = goBack) else ManagerOnlyScreen("الهوية", goBack)
            }
            composable(Routes.TEMPLATES) {
                if (canUse(Feature.EDIT_TEMPLATES)) TemplatesScreen(onBack = goBack) else ManagerOnlyScreen("القوالب والمفردات", goBack)
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onBack = goBack,
                    onOpenPost = { id -> navController.navigate(Routes.studio(contentId = id)) },
                    onSendToAudience = { id -> navController.navigate(Routes.audience(contentId = id)) },
                )
            }
            composable(Routes.AUDIENCE_PATTERN, arguments = optionalContentArgument) {
                if (canUse(Feature.AUDIENCE)) {
                    AudienceScreen(
                        onBack = goBack,
                        onSend = { segmentId, contentId -> navController.navigate(Routes.sendList(segmentId, contentId)) },
                    )
                } else {
                    ManagerOnlyScreen("الجمهور", goBack)
                }
            }
            composable(Routes.SEND_LIST_PATTERN, arguments = sendListArguments) {
                if (canUse(Feature.AUDIENCE)) SendListScreen(onBack = goBack) else ManagerOnlyScreen("قائمة الإرسال", goBack)
            }
            composable(Routes.REPORTS) {
                if (canUse(Feature.REPORTS)) ReportsScreen(onBack = goBack) else ManagerOnlyScreen("التقارير", goBack)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = goBack,
                    onTeam = { navController.navigate(Routes.TEAM) },
                    onLog = { navController.navigate(Routes.LOG) },
                    onInventoryLinks = { navController.navigate(Routes.INVENTORY_LINKS) },
                    onAppearance = { navController.navigate(Routes.APPEARANCE) },
                )
            }
            composable(Routes.APPEARANCE) { AppearanceScreen(onBack = goBack) }
            composable(Routes.INVENTORY_LINKS) {
                if (canUse(Feature.MANAGE_INVENTORY)) InventoryLinksScreen(onBack = goBack) else ManagerOnlyScreen("ربط الأصناف", goBack)
            }
            composable(Routes.TEAM) {
                if (canUse(Feature.MANAGE_TEAM)) TeamScreen(onBack = goBack) else ManagerOnlyScreen("فريق التسويق", goBack)
            }
            composable(Routes.LOG) { LogScreen(onBack = goBack) }
        }
    }
}

/** The post the audience screen was opened to send (optional); see [Routes.audience]. */
private val optionalContentArgument = listOf(
    navArgument(Routes.ARG_CONTENT_ID) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    },
)

/** The segment of a send list (required) and its post (optional); see [Routes.sendList]. */
private val sendListArguments = listOf(
    navArgument(Routes.ARG_SEGMENT_ID) { type = NavType.StringType },
    navArgument(Routes.ARG_CONTENT_ID) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    },
)

/** What the Studio may be asked to start on; see [Routes.studio]. All of them are optional. */
private val studioArguments = listOf(
    Routes.ARG_CONTENT_ID,
    Routes.ARG_CAMPAIGN_ID,
    Routes.ARG_DATE,
    Routes.ARG_PRODUCT_ID,
    Routes.ARG_IDEA,
).map { name ->
    navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }
}

@Composable
private fun MarketingBottomBar(
    currentDestination: NavDestination?,
    onTabClick: (BottomTab) -> Unit,
) {
    val look = LocalCadyStyle.current
    NavigationBar(
        modifier = Modifier.barLook(look),
        containerColor = look.barContainer,
    ) {
        BottomTab.entries.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = look.barSelected,
                    selectedTextColor = look.barSelected,
                    indicatorColor = look.barIndicator,
                ),
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
