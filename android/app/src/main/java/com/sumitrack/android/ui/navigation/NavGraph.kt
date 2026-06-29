package com.sumitrack.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sumitrack.android.ui.screens.clients.ClientListScreen
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
        composable(Routes.Clients.route)  { ClientListScreen() }
        composable(Routes.Settings.route) { SettingsScreen() }
    }
}
