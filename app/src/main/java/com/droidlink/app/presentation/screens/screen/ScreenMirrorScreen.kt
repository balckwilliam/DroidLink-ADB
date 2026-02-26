package com.droidlink.app.presentation.screens.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidlink.app.presentation.components.ScrcpySurface

@Composable
fun ScreenMirrorScreen(viewModel: ScreenMirrorViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Video surface — takes all remaining vertical space.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ScrcpySurface(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Overlay progress / status while not streaming.
            when (state) {
                is ScreenMirrorViewModel.MirrorState.PushingServer -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Pushing server…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is ScreenMirrorViewModel.MirrorState.StartingServer -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Starting server…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is ScreenMirrorViewModel.MirrorState.Error -> {
                    Text(
                        text = (state as ScreenMirrorViewModel.MirrorState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> { /* Idle or Streaming — surface renders in the background */ }
            }
        }

        // Control bar at the bottom.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is ScreenMirrorViewModel.MirrorState.Streaming -> {
                    Button(onClick = viewModel::stopMirroring) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Stop")
                    }
                }
                is ScreenMirrorViewModel.MirrorState.Idle,
                is ScreenMirrorViewModel.MirrorState.Error -> {
                    Button(onClick = viewModel::startMirroring) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Start Mirroring")
                    }
                }
                else -> { /* show nothing during push / start */ }
            }
        }
    }
}
