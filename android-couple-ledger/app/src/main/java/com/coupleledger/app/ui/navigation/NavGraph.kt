package com.coupleledger.app.ui.navigation

sealed class Destination(val route: String) {
    data object Onboarding : Destination("onboarding")
    data object Dashboard : Destination("dashboard")
    data object Cards : Destination("cards")
    data object AddCard : Destination("cards/add")
    data object Transactions : Destination("transactions")
    data object Settings : Destination("settings")
}
