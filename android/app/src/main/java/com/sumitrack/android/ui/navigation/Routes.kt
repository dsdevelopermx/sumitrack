package com.sumitrack.android.ui.navigation

sealed class Routes(val route: String) {
    object Login    : Routes("login")
    object Orders   : Routes("orders")
    object Clients  : Routes("clients")
    object Settings : Routes("settings")
}
