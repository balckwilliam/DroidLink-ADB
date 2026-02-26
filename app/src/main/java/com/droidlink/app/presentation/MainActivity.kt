package com.droidlink.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.droidlink.app.DroidLinkApplication
import com.droidlink.app.presentation.navigation.DroidLinkNavGraph
import com.droidlink.app.presentation.navigation.Screen
import com.droidlink.app.presentation.screens.apps.AppsViewModel
import com.droidlink.app.presentation.screens.connection.ConnectionViewModel
import com.droidlink.app.presentation.screens.files.FilesViewModel
import com.droidlink.app.presentation.screens.shell.ShellViewModel
import com.droidlink.app.presentation.theme.DroidLinkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DroidLinkApplication

        val connectionViewModel = ConnectionViewModel(
            connectionRepository = app.connectionRepository,
            adbConnection = app.adbConnection
        )
        val appsViewModel = AppsViewModel(adbRepository = app.adbRepository)
        val filesViewModel = FilesViewModel(adbRepository = app.adbRepository)
        val shellViewModel = ShellViewModel(adbRepository = app.adbRepository)

        setContent {
            DroidLinkTheme {
                DroidLinkApp(
                    connectionViewModel = connectionViewModel,
                    appsViewModel = appsViewModel,
                    filesViewModel = filesViewModel,
                    shellViewModel = shellViewModel
                )
            }
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Connection, Icons.Default.Link, "Connect"),
    BottomNavItem(Screen.Apps, Icons.Default.Apps, "Apps"),
    BottomNavItem(Screen.Files, Icons.Default.Folder, "Files"),
    BottomNavItem(Screen.Shell, Icons.Default.Terminal, "Shell"),
    BottomNavItem(Screen.ScreenMirror, Icons.Default.ScreenShare, "Screen")
)

@Composable
fun DroidLinkApp(
    navController: NavHostController = rememberNavController(),
    connectionViewModel: ConnectionViewModel,
    appsViewModel: AppsViewModel,
    filesViewModel: FilesViewModel,
    shellViewModel: ShellViewModel
) {
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { innerPadding ->
        DroidLinkNavGraph(
            navController = navController,
            connectionViewModel = connectionViewModel,
            appsViewModel = appsViewModel,
            filesViewModel = filesViewModel,
            shellViewModel = shellViewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.screen.route,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
