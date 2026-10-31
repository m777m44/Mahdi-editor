package com.mahdi.activities.main.navigation

sealed class MainRoutes(val route: String) {
    object Main : MainRoutes("main")

    object Disclaimer : MainRoutes("t&c")
}
