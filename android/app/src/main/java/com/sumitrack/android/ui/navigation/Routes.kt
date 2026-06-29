package com.sumitrack.android.ui.navigation

sealed class Routes(val route: String) {
    object Orders   : Routes("orders")
    object Clients  : Routes("clients")
    object Settings : Routes("settings")
}
