package com.droidlink.app.domain.repository

import com.droidlink.app.domain.model.DeviceApp
import com.droidlink.app.domain.model.FileEntry

/**
 * Repository interface for ADB operations.
 */
interface AdbRepository {
    suspend fun connect(host: String, port: Int): Boolean
    suspend fun disconnect()
    suspend fun executeShell(command: String): String
    suspend fun listPackages(): List<DeviceApp>
    suspend fun installApk(apkData: ByteArray, packageName: String): Boolean
    suspend fun uninstallApp(packageName: String): Boolean
    suspend fun clearAppData(packageName: String): Boolean
    suspend fun disableApp(packageName: String): Boolean
    suspend fun enableApp(packageName: String): Boolean
    suspend fun listFiles(path: String): List<FileEntry>
    suspend fun pullFile(remotePath: String): ByteArray
    suspend fun pushFile(remotePath: String, data: ByteArray, permissions: Int = 0x1A4): Boolean
    val isConnected: Boolean
}
