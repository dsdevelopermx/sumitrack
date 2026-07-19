package com.sumitrack.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sumitrack.android.ui.screens.clients.ClientFormScreen
import com.sumitrack.android.ui.screens.clients.ClientListScreen
import com.sumitrack.android.ui.screens.clients.ClientProfileScreen
import com.sumitrack.android.ui.screens.orders.OrderListScreen
import com.sumitrack.android.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.Orders.route,
        modifier = modifier,
    ) {
        composable(Routes.Orders.route)   { OrderListScreen() }
        composable(Routes.Clients.route)  {
            ClientListScreen(
                onAddClientClick = {
                    navController.navigate(Routes.ClientForm.createRoute()) { launchSingleTop = true }
                },
                onClientClick = { clientId ->
                    navController.navigate(Routes.ClientProfile.createRoute(clientId)) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.Settings.route) { SettingsScreen() }
        composable(
            route = Routes.ClientForm.route,
            arguments = listOf(navArgument("clientId") { type = NavType.StringType; nullable = true }),
        ) {
            ClientFormScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ClientProfile.route,
            arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
        ) {
            ClientProfileScreen(
                onBackClick = { navController.popBackStack() },
                onEditClick = { clientId ->
                    navController.navigate(Routes.ClientForm.createRoute(clientId))
                },
            )
        }
    }
}
