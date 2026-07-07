package com.sumitrack.android.ui.navigation

sealed class Routes(val route: String) {
    // Auth graph
    object Login    : Routes("login")

    // Main graph (inside tab navigation)
    object Orders   : Routes("orders")
    object Clients  : Routes("clients")
    object Settings : Routes("settings")

    // Top-level graph roots
    object Auth : Routes("auth_graph")
    object Main : Routes("main_graph")
}
