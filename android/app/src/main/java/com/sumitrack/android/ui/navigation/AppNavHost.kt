package com.sumitrack.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sumitrack.android.ui.AppViewModel
import com.sumitrack.android.ui.SessionState
import com.sumitrack.android.ui.screens.MainScreen
import com.sumitrack.android.ui.screens.auth.LoginScreen

@Composable
fun AppNavHost(appViewModel: AppViewModel = hiltViewModel()) {
    val sessionState by appViewModel.sessionState.collectAsStateWithLifecycle()

    // Hoisted unconditionally — evita violación de reglas de Compose sobre @Composable en ramas
    val navController = rememberNavController()

    // Solo reacciona a LoggedOut: la navegación a órdenes la maneja onLoginSuccess/startDestination
    LaunchedEffect(sessionState) {
        if (sessionState == SessionState.LoggedOut) {
            navController.navigate(Routes.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    when (sessionState) {
        SessionState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        else -> {
            val startDestination = if (sessionState == SessionState.LoggedIn) {
                Routes.Orders.route
            } else {
                Routes.Login.route
            }

            NavHost(navController = navController, startDestination = startDestination) {
                composable(Routes.Login.route) {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate(Routes.Orders.route) {
                            popUpTo(Routes.Login.route) { inclusive = true }
                        }
                    })
                }
                composable(Routes.Orders.route) {
                    MainScreen()
                }
            }
        }
    }
}
