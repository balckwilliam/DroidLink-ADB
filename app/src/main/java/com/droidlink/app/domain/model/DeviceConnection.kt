package com.droidlink.app.domain.model

/**
 * Domain model representing a device connection.
 */
data class DeviceConnection(
    val id: Long = 0,
    val host: String,
    val port: Int,
    val name: String = "",
    val lastConnected: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false
) {
    val address: String get() = "$host:$port"
}
