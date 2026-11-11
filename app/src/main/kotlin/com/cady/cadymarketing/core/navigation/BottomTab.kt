package com.cady.cadymarketing.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The five tabs, in reading order (right to left in the RTL layout).
 * [fullScreen] tabs do not switch tabs: they open a full-screen route that hides the bar.
 */
enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val fullScreen: Boolean = false,
) {
    HOME(Routes.HOME, "الرئيسية", Icons.Filled.Home),
    STUDIO(Routes.STUDIO, "الاستوديو", Icons.Filled.Brush, fullScreen = true),
    CAMPAIGNS(Routes.CAMPAIGNS, "الحملات", Icons.Filled.Campaign),
    CALENDAR(Routes.CALENDAR, "التقويم", Icons.Filled.CalendarMonth),
    MORE(Routes.MORE, "المزيد", Icons.Filled.MoreHoriz),
}
