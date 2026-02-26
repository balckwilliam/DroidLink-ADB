package com.droidlink.app.domain.model

/**
 * Domain model representing an installed application on a connected device.
 */
data class DeviceApp(
    val packageName: String,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val versionName: String = "",
    val label: String = ""
)
