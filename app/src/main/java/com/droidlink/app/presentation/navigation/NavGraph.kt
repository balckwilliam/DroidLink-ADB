package com.droidlink.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.droidlink.app.presentation.screens.apps.AppsScreen
import com.droidlink.app.presentation.screens.connection.ConnectionScreen
import com.droidlink.app.presentation.screens.files.FilesScreen
import com.droidlink.app.presentation.screens.screen.ScreenMirrorScreen
import com.droidlink.app.presentation.screens.shell.ShellScreen

@Composable
fun DroidLinkNavGraph(
    navController: NavHostController,
    connectionViewModel: com.droidlink.app.presentation.screens.connection.ConnectionViewModel,
    appsViewModel: com.droidlink.app.presentation.screens.apps.AppsViewModel,
    filesViewModel: com.droidlink.app.presentation.screens.files.FilesViewModel,
    shellViewModel: com.droidlink.app.presentation.screens.shell.ShellViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Connection.route,
        modifier = modifier
    ) {
        composable(Screen.Connection.route) {
            ConnectionScreen(viewModel = connectionViewModel)
        }
        composable(Screen.Apps.route) {
            AppsScreen(viewModel = appsViewModel)
        }
        composable(Screen.Files.route) {
            FilesScreen(viewModel = filesViewModel)
        }
        composable(Screen.Shell.route) {
            ShellScreen(viewModel = shellViewModel)
        }
        composable(Screen.ScreenMirror.route) {
            ScreenMirrorScreen()
        }
    }
}
