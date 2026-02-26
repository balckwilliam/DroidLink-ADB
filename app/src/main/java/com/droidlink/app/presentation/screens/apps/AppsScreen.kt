package com.droidlink.app.presentation.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidlink.app.domain.model.DeviceApp

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showSystemApps by viewModel.showSystemApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    val filteredApps = apps.filter { app ->
        (showSystemApps || !app.isSystemApp) &&
                (searchQuery.isBlank() || app.packageName.contains(searchQuery, ignoreCase = true))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Applications (${filteredApps.size})",
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(onClick = viewModel::loadApps) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Search packages") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilterChip(
                selected = showSystemApps,
                onClick = viewModel::toggleSystemApps,
                label = { Text("Show System Apps") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps) { app ->
                        AppItem(
                            app = app,
                            onUninstall = { viewModel.uninstallApp(app) },
                            onClearData = { viewModel.clearAppData(app) },
                            onToggleEnabled = {
                                if (app.isEnabled) viewModel.disableApp(app)
                                else viewModel.enableApp(app)
                            }
                        )
                    }
                }
            }
        }

        statusMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::clearStatusMessage) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(message)
            }
        }
    }
}

@Composable
private fun AppItem(
    app: DeviceApp,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (app.isEnabled) 1f else 0.5f
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (app.isSystemApp) {
                    Text(
                        text = "System",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onToggleEnabled) {
                Icon(
                    if (app.isEnabled) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                    contentDescription = if (app.isEnabled) "Disable" else "Enable"
                )
            }
            IconButton(onClick = onClearData) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear data")
            }
            if (!app.isSystemApp) {
                IconButton(onClick = onUninstall) {
                    Icon(Icons.Default.Delete, contentDescription = "Uninstall")
                }
            }
        }
    }
}
