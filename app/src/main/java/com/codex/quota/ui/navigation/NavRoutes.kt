package com.codex.quota.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Accounts : Screen("accounts")
    data object Settings : Screen("settings")
    data object AddAccount : Screen("add_account")
    data object Onboarding : Screen("onboarding")
    data object About : Screen("about")
    data object SettingsPage : Screen("settings_page/{page}") {
        fun createRoute(page: String): String = "settings_page/$page"
    }
    data object AccountDetail : Screen("account_detail/{accountId}") {
        fun createRoute(accountId: String): String = "account_detail/$accountId"
    }
}
