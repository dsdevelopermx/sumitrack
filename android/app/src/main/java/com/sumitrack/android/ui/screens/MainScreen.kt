package com.sumitrack.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sumitrack.android.sync.SyncEvent
import com.sumitrack.android.sync.SyncStatusViewModel
import com.sumitrack.android.ui.components.OfflineBanner
import com.sumitrack.android.ui.navigation.NavGraph
import com.sumitrack.android.ui.navigation.Routes
import com.sumitrack.android.ui.theme.PrimaryVariant

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScreen(viewModel: SyncStatusViewModel = hiltViewModel()) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabs = remember {
        listOf(
            NavTab(Routes.Orders.route,   "Órdenes",  Icons.AutoMirrored.Filled.List),
            NavTab(Routes.Clients.route,  "Clientes", Icons.Filled.Person),
            NavTab(Routes.Settings.route, "Config",   Icons.Filled.Settings),
        )
    }

    LaunchedEffect(Unit) {
        viewModel.syncEvents.collect { event ->
            when (event) {
                is SyncEvent.Success -> snackbarHostState.showSnackbar(
                    "Sincronizado correctamente ☁",
                    duration = SnackbarDuration.Short,
                )
                is SyncEvent.Failure -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Error al sincronizar.",
                        actionLabel = "Reintentar",
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.retryPush()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (navController.currentDestination?.route != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = PrimaryVariant,
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isOffline) {
                OfflineBanner(modifier = Modifier.fillMaxWidth())
            }
            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            NavGraph(
                navController = navController,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
