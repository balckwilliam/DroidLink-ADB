package com.droidlink.app.presentation.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidlink.app.adb.connection.AdbConnection
import com.droidlink.app.domain.model.DeviceConnection

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val host by viewModel.hostInput.collectAsState()
    val port by viewModel.portInput.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val history by viewModel.connectionHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Connect to Device",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = viewModel::updateHost,
                label = { Text("IP Address") },
                placeholder = { Text("192.168.1.100") },
                modifier = Modifier.weight(2f),
                singleLine = true
            )
            OutlinedTextField(
                value = port,
                onValueChange = viewModel::updatePort,
                label = { Text("Port") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Connection status and buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (connectionState) {
                is AdbConnection.ConnectionState.Disconnected -> {
                    Button(
                        onClick = viewModel::connect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect")
                    }
                }
                is AdbConnection.ConnectionState.Connecting,
                is AdbConnection.ConnectionState.Authenticating -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (connectionState is AdbConnection.ConnectionState.Connecting)
                            "Connecting..." else "Authenticating..."
                    )
                }
                is AdbConnection.ConnectionState.Connected -> {
                    OutlinedButton(
                        onClick = viewModel::disconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect")
                    }
                }
                is AdbConnection.ConnectionState.Error -> {
                    Button(
                        onClick = viewModel::connect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        // Status message
        when (val state = connectionState) {
            is AdbConnection.ConnectionState.Connected -> {
                Text(
                    text = "Connected: ${state.banner}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            is AdbConnection.ConnectionState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Connection history
        Text(
            text = "Connection History",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history) { connection ->
                ConnectionHistoryItem(
                    connection = connection,
                    onConnect = { viewModel.connectToSaved(connection) },
                    onDelete = { viewModel.deleteConnection(connection) }
                )
            }
        }
    }
}

@Composable
private fun ConnectionHistoryItem(
    connection: DeviceConnection,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connection.address,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (connection.name.isNotEmpty()) {
                    Text(
                        text = connection.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onConnect) {
                Icon(Icons.Default.Link, contentDescription = "Connect")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
