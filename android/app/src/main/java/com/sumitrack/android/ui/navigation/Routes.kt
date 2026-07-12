package com.sumitrack.android.ui.navigation

sealed class Routes(val route: String) {
    object Login    : Routes("login")
    object Orders   : Routes("orders")
    object Clients  : Routes("clients")
    object Settings : Routes("settings")

    object ClientForm : Routes("client_form?clientId={clientId}") {
        fun createRoute(clientId: String? = null): String =
            if (clientId != null) "client_form?clientId=$clientId" else "client_form"
    }
}
