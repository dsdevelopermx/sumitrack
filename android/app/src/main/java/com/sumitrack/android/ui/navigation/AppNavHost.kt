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

    when (sessionState) {
        SessionState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        else -> {
            val startDestination = when (sessionState) {
                SessionState.LoggedIn -> Routes.Main.route
                else -> Routes.Auth.route
            }

            val navController = rememberNavController()

            LaunchedEffect(sessionState) {
                when (sessionState) {
                    SessionState.LoggedOut -> navController.navigate(Routes.Auth.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                    SessionState.LoggedIn -> navController.navigate(Routes.Main.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                    else -> Unit
                }
            }

            NavHost(navController = navController, startDestination = startDestination) {
                composable(Routes.Auth.route) {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate(Routes.Main.route) {
                            popUpTo(Routes.Auth.route) { inclusive = true }
                        }
                    })
                }
                composable(Routes.Main.route) {
                    MainScreen()
                }
            }
        }
    }
}
