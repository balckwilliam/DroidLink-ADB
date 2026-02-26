package com.droidlink.app.presentation.navigation

/**
 * Navigation destinations for the app.
 */
sealed class Screen(val route: String) {
    data object Connection : Screen("connection")
    data object Apps : Screen("apps")
    data object Files : Screen("files")
    data object Shell : Screen("shell")
    data object ScreenMirror : Screen("screen_mirror")
}
