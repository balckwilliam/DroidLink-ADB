package com.droidlink.app.presentation.screens.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidlink.app.adb.connection.AdbConnection
import com.droidlink.app.domain.model.DeviceConnection
import com.droidlink.app.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val connectionRepository: ConnectionRepository,
    private val adbConnection: AdbConnection
) : ViewModel() {

    val connectionHistory = connectionRepository.getAllConnections()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val connectionState = adbConnection.connectionState

    private val _hostInput = MutableStateFlow("")
    val hostInput: StateFlow<String> = _hostInput.asStateFlow()

    private val _portInput = MutableStateFlow("5555")
    val portInput: StateFlow<String> = _portInput.asStateFlow()

    fun updateHost(host: String) {
        _hostInput.value = host
    }

    fun updatePort(port: String) {
        _portInput.value = port
    }

    fun connect() {
        val host = _hostInput.value.trim()
        val port = _portInput.value.trim().toIntOrNull() ?: 5555

        if (host.isBlank()) return

        viewModelScope.launch {
            val success = adbConnection.connect(host, port)
            if (success) {
                val existing = connectionRepository.findByAddress(host, port)
                if (existing != null) {
                    connectionRepository.updateConnection(
                        existing.copy(lastConnected = System.currentTimeMillis())
                    )
                } else {
                    connectionRepository.addConnection(
                        DeviceConnection(
                            host = host,
                            port = port,
                            name = adbConnection.deviceBanner
                        )
                    )
                }
            }
        }
    }

    fun connectToSaved(connection: DeviceConnection) {
        _hostInput.value = connection.host
        _portInput.value = connection.port.toString()
        connect()
    }

    fun disconnect() {
        viewModelScope.launch {
            adbConnection.disconnect()
        }
    }

    fun deleteConnection(connection: DeviceConnection) {
        viewModelScope.launch {
            connectionRepository.deleteConnection(connection)
        }
    }
}
