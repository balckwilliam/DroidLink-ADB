package com.droidlink.app.domain.model

/**
 * Domain model representing a file or directory entry on a connected device.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val size: Long,
    val modifiedTime: Long,
    val isDirectory: Boolean,
    val permissions: String = ""
)
