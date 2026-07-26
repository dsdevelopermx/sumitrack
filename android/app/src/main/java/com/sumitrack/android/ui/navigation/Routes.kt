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

    object ClientProfile : Routes("client_profile/{clientId}") {
        fun createRoute(clientId: String): String = "client_profile/$clientId"
    }

    object ProductList : Routes("product_list")

    object ProductForm : Routes("product_form?productId={productId}") {
        fun createRoute(productId: String? = null): String =
            if (productId != null) "product_form?productId=$productId" else "product_form"
    }

    object NewOrderClientSelect : Routes("new_order_client_select")

    object NewOrderItems : Routes("new_order_items/{clientId}") {
        fun createRoute(clientId: String): String = "new_order_items/$clientId"
    }

    object OrderSummary : Routes("order_summary/{clientId}/{cart}") {
        fun createRoute(clientId: String, cart: String): String = "order_summary/$clientId/$cart"
    }

    object Payment : Routes("payment/{clientId}/{cart}") {
        fun createRoute(clientId: String, cart: String): String = "payment/$clientId/$cart"
    }
}
