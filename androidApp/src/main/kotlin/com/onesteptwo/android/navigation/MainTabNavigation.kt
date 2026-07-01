package com.onesteptwo.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clerk.api.Clerk
import com.onesteptwo.android.AppContainer
import com.onesteptwo.android.ui.history.DayDetailScreen
import com.onesteptwo.android.ui.history.HistoryScreen
import com.onesteptwo.android.ui.home.HomeScreen
import com.onesteptwo.android.ui.progress.ProgressScreen
import com.onesteptwo.android.ui.settings.AddChildScreen
import com.onesteptwo.android.ui.settings.EditChildScreen
import com.onesteptwo.android.ui.settings.InviteCaregiverScreen
import com.onesteptwo.android.ui.settings.SettingsScreen
import com.onesteptwo.android.viewmodel.ChildSelectionViewModel
import com.onesteptwo.android.viewmodel.ChildSelectionViewModelFactory
import timber.log.Timber
import java.time.LocalDate

private data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    TabItem("home", "Home", Icons.Outlined.Home),
    TabItem("history", "History", Icons.Outlined.CalendarMonth),
    TabItem("progress", "Progress", Icons.AutoMirrored.Outlined.TrendingUp),
    TabItem("settings", "Settings", Icons.Outlined.Settings)
)

/**
 * 4-tab shell (REQ-035): a single Scaffold + NavigationBar wrapping one nested NavHost, not one
 * NavHost per tab (05-01-PLAN.md architecture decision). [ChildSelectionViewModel] is constructed
 * once here so Home/History/Progress share one active-child context (REQ-031).
 */
@Composable
fun MainTabNavigation(container: AppContainer, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val childSelectionViewModel: ChildSelectionViewModel = viewModel(
        factory = ChildSelectionViewModelFactory(container.childrenRepository)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val hideTabBar = currentDestination?.route?.startsWith("history/day/") == true

    Scaffold(
        bottomBar = {
            if (!hideTabBar) NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            composable("home") { HomeScreen(container, childSelectionViewModel) }
            composable("history") {
                HistoryScreen(
                    container = container,
                    childSelectionViewModel = childSelectionViewModel,
                    onDayClick = { date -> navController.navigate("history/day/$date") }
                )
            }
            composable(
                route = "history/day/{date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) { backStackEntry ->
                val dateArg = backStackEntry.arguments?.getString("date") ?: return@composable
                val activeChild by childSelectionViewModel.activeChild.collectAsState()
                val childId = activeChild?.id ?: return@composable
                DayDetailScreen(
                    date = java.time.LocalDate.parse(dateArg),
                    childId = childId,
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("progress") { ProgressScreen(childSelectionViewModel) }
            composable("settings") {
                SettingsScreen(
                    container = container,
                    onNavigateToInvite = { navController.navigate("settings/invite") },
                    onNavigateToAddChild = { navController.navigate("settings/children/add") },
                    onNavigateToEditChild = { childId -> navController.navigate("settings/children/$childId/edit") },
                    onSignOut = onSignOut
                )
            }
            composable("settings/children/add") {
                AddChildScreen(container = container, onDone = { navController.popBackStack() })
            }
            composable(
                route = "settings/children/{childId}/edit",
                arguments = listOf(navArgument("childId") { type = NavType.StringType })
            ) { backStackEntry ->
                val childId = backStackEntry.arguments?.getString("childId") ?: return@composable
                val children by childSelectionViewModel.children.collectAsState()
                val child = children.firstOrNull { it.id == childId } ?: return@composable
                EditChildScreen(container = container, child = child, onDone = { navController.popBackStack() })
            }
            // Double-gated: SettingsScreen only exposes the action to org:admin (button
            // visibility), and this route independently re-verifies the role (T-3-05).
            composable("settings/invite") {
                val isAdmin = Clerk.organizationMembership?.role == "org:admin"
                LaunchedEffect(isAdmin) {
                    if (!isAdmin) {
                        Timber.w("MainTabNavigation: non-admin reached settings/invite — popping back")
                        navController.popBackStack()
                    }
                }
                if (isAdmin) {
                    InviteCaregiverScreen(onDone = { navController.popBackStack() })
                }
            }
        }
    }
}
