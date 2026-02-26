package com.droidlink.app.data.repository

import com.droidlink.app.adb.connection.AdbConnection
import com.droidlink.app.adb.protocol.AdbMessage
import com.droidlink.app.adb.protocol.SyncProtocol
import com.droidlink.app.domain.model.DeviceApp
import com.droidlink.app.domain.model.FileEntry
import com.droidlink.app.domain.repository.AdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of AdbRepository using AdbConnection.
 */
class AdbRepositoryImpl(
    private val connection: AdbConnection
) : AdbRepository {

    override suspend fun connect(host: String, port: Int): Boolean {
        return connection.connect(host, port)
    }

    override suspend fun disconnect() {
        connection.disconnect()
    }

    override suspend fun executeShell(command: String): String {
        return connection.executeShell(command)
    }

    override suspend fun listPackages(): List<DeviceApp> = withContext(Dispatchers.IO) {
        val output = executeShell("pm list packages -f")
        output.lines()
            .filter { it.startsWith("package:") }
            .map { line ->
                val parts = line.removePrefix("package:").split("=")
                val packageName = if (parts.size >= 2) parts.last() else parts.first()
                val apkPath = if (parts.size >= 2) parts.first() else ""
                DeviceApp(
                    packageName = packageName.trim(),
                    isSystemApp = apkPath.startsWith("/system/")
                )
            }
            .sortedBy { it.packageName }
    }

    override suspend fun installApk(apkData: ByteArray, packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Use streaming install: pm install-create, pm install-write, pm install-commit
                val sessionResult = executeShell("pm install-create -S ${apkData.size}")
                val sessionId = Regex("\\[(\\d+)]").find(sessionResult)?.groupValues?.get(1)
                    ?: return@withContext false

                // Push APK data via shell stream
                val writeResult = executeShell(
                    "pm install-write -S ${apkData.size} $sessionId base.apk -"
                )

                val commitResult = executeShell("pm install-commit $sessionId")
                commitResult.contains("Success")
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun uninstallApp(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            val result = executeShell("pm uninstall $packageName")
            result.trim().contains("Success")
        }

    override suspend fun clearAppData(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            val result = executeShell("pm clear $packageName")
            result.trim().contains("Success")
        }

    override suspend fun disableApp(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            val result = executeShell("pm disable-user --user 0 $packageName")
            result.trim().contains("disabled")
        }

    override suspend fun enableApp(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            val result = executeShell("pm enable $packageName")
            result.trim().contains("enabled")
        }

    override suspend fun listFiles(path: String): List<FileEntry> =
        withContext(Dispatchers.IO) {
            val output = executeShell("ls -la $path")
            output.lines()
                .drop(1) // Skip "total" line
                .filter { it.isNotBlank() }
                .mapNotNull { parseLsLine(it, path) }
        }

    override suspend fun pullFile(remotePath: String): ByteArray =
        withContext(Dispatchers.IO) {
            // Use base64 encoding via shell for simplicity
            val output = executeShell("base64 $remotePath")
            android.util.Base64.decode(output.trim(), android.util.Base64.DEFAULT)
        }

    override suspend fun pushFile(remotePath: String, data: ByteArray, permissions: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val encoded = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                val result = executeShell("echo '$encoded' | base64 -d > $remotePath")
                executeShell("chmod ${permissions.toString(8).padStart(3, '0')} $remotePath")
                true
            } catch (e: Exception) {
                false
            }
        }

    override val isConnected: Boolean
        get() = connection.isConnected

    private fun parseLsLine(line: String, parentPath: String): FileEntry? {
        // Parse ls -la output: permissions links owner group size date time name
        val parts = line.split("\\s+".toRegex(), limit = 9)
        if (parts.size < 8) return null

        val permissions = parts[0]
        val isDirectory = permissions.startsWith("d")
        val size = parts[4].toLongOrNull() ?: 0
        val name = parts.getOrElse(8) { parts.last() }

        if (name == "." || name == "..") return null

        val fullPath = if (parentPath.endsWith("/")) "$parentPath$name" else "$parentPath/$name"

        return FileEntry(
            name = name,
            path = fullPath,
            size = size,
            modifiedTime = 0,
            isDirectory = isDirectory,
            permissions = permissions
        )
    }
}
