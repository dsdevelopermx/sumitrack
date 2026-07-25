package com.sumitrack.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sumitrack.android.ui.screens.clients.ClientFormScreen
import com.sumitrack.android.ui.screens.clients.ClientListScreen
import com.sumitrack.android.ui.screens.clients.ClientProfileScreen
import com.sumitrack.android.ui.screens.orders.ClientSelectScreen
import com.sumitrack.android.ui.screens.orders.ItemListScreen
import com.sumitrack.android.ui.screens.orders.OrderListScreen
import com.sumitrack.android.ui.screens.products.ProductFormScreen
import com.sumitrack.android.ui.screens.products.ProductListScreen
import com.sumitrack.android.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.Orders.route,
        modifier = modifier,
    ) {
        composable(Routes.Orders.route) {
            OrderListScreen(
                onNewOrderClick = {
                    navController.navigate(Routes.NewOrderClientSelect.route) { launchSingleTop = true }
                },
            )
        }
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
        composable(Routes.Settings.route) {
            SettingsScreen(
                onCatalogClick = {
                    navController.navigate(Routes.ProductList.route) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.ClientForm.route,
            arguments = listOf(navArgument("clientId") { type = NavType.StringType; nullable = true }),
        ) {
            ClientFormScreen(
                onSaved = { newClientId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("newClientId", newClientId)
                    navController.popBackStack()
                },
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
        composable(Routes.ProductList.route) {
            ProductListScreen(
                onBackClick = { navController.popBackStack() },
                onAddProductClick = {
                    navController.navigate(Routes.ProductForm.createRoute()) { launchSingleTop = true }
                },
                onProductClick = { productId ->
                    navController.navigate(Routes.ProductForm.createRoute(productId)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.ProductForm.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true }),
        ) {
            ProductFormScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.NewOrderClientSelect.route) { backStackEntry ->
            // getStateFlow (no get() de una sola vez) — patrón recomendado por Compose Navigation
            // para leer resultados de SavedStateHandle: garantiza recomposición cuando
            // ClientFormScreen.onSaved escribe el valor después de que esta pantalla ya se compuso.
            val newClientId by backStackEntry.savedStateHandle
                .getStateFlow<String?>("newClientId", null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(newClientId) {
                val id = newClientId
                if (id != null) {
                    backStackEntry.savedStateHandle["newClientId"] = null
                    navController.navigate(Routes.NewOrderItems.createRoute(id)) { launchSingleTop = true }
                }
            }
            ClientSelectScreen(
                onBackClick = { navController.popBackStack() },
                onClientSelected = { clientId ->
                    navController.navigate(Routes.NewOrderItems.createRoute(clientId)) { launchSingleTop = true }
                },
                onNewClientClick = {
                    navController.navigate(Routes.ClientForm.createRoute()) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.NewOrderItems.route,
            arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
        ) {
            ItemListScreen(
                onBackClick = { navController.popBackStack() },
                onGoToSettingsClick = {
                    navController.navigate(Routes.Settings.route) { launchSingleTop = true }
                },
            )
        }
    }
}
