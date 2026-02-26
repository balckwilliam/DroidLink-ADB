package com.droidlink.app.adb.scrcpy

import android.content.Context
import android.util.Base64
import com.droidlink.app.adb.connection.AdbConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages the scrcpy server lifecycle on the connected ADB device.
 *
 * **Prerequisites**: place the matching `scrcpy-server.jar` (v[SCRCPY_VERSION])
 * in `app/src/main/assets/scrcpy-server.jar` before building.  A placeholder
 * file is committed to the repository to keep the directory structure; replace
 * it with the real binary from the official scrcpy releases.
 *
 * Workflow:
 * 1. Call [pushServer] to upload the JAR to the device via shell base64.
 * 2. Call [startServer] to launch the server and obtain a [ScrcpySession].
 * 3. Call [ScrcpySession.startReading] to begin piping video data.
 */
class ScrcpyServerManager(
    private val context: Context,
    private val connection: AdbConnection
) {
    companion object {
        /** Path on the device where the JAR will be pushed. */
        const val DEVICE_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"

        /** Asset filename inside `app/src/main/assets/`. */
        const val ASSET_NAME = "scrcpy-server.jar"

        /** Scrcpy server version string passed on the command line. */
        const val SCRCPY_VERSION = "2.0"

        /** Base64 chunk size used when pushing the JAR via shell. */
        private const val B64_CHUNK = 4096
    }

    /**
     * Push [ASSET_NAME] from assets to [DEVICE_SERVER_PATH] on the device.
     *
     * The file is encoded as base64 and sent through the shell to avoid
     * implementing the full ADB SYNC protocol.
     *
     * @return `true` if the push succeeded and the file is present on device.
     */
    suspend fun pushServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = context.assets.open(ASSET_NAME).use { it.readBytes() }
            val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
            val tmp = "${DEVICE_SERVER_PATH}.b64"

            // Write the base64 payload in chunks to respect shell line limits.
            // Base64 uses only [A-Za-z0-9+/=] — no shell metacharacters or single
            // quotes — so single-quoting the chunk is safe from injection.
            var first = true
            var offset = 0
            while (offset < encoded.length) {
                val end = minOf(offset + B64_CHUNK, encoded.length)
                val chunk = encoded.substring(offset, end)
                val redirect = if (first) ">" else ">>"
                connection.executeShell("printf '%s' '$chunk' $redirect $tmp")
                first = false
                offset = end
            }

            // Decode to the final path and verify.
            val result = connection.executeShell(
                "base64 -d $tmp > $DEVICE_SERVER_PATH && echo OK"
            )
            result.contains("OK")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Start the scrcpy server on the device and return a [ScrcpySession].
     *
     * The server is launched via `app_process` inside an ADB shell stream.
     * Video data arrives as [com.droidlink.app.adb.protocol.AdbMessage.CMD_WRTE]
     * messages; control events are written back to the same stream (stdin).
     *
     * @throws IOException if the shell stream cannot be opened.
     */
    suspend fun startServer(): ScrcpySession = withContext(Dispatchers.IO) {
        val command = buildServerCommand()
        val stream = connection.open("shell:$command")
            ?: throw IOException("Failed to open scrcpy shell stream")
        ScrcpySession(stream, connection)
    }

    /**
     * Build the shell command used to launch the scrcpy server.
     *
     * Format:
     * ```
     * CLASSPATH=<jar> app_process / com.genymobile.scrcpy.Server <version> \
     *   tunnel_forward=true audio=false control=true max_size=1024
     * ```
     */
    private fun buildServerCommand(): String =
        "CLASSPATH=$DEVICE_SERVER_PATH " +
                "app_process / com.genymobile.scrcpy.Server $SCRCPY_VERSION " +
                "tunnel_forward=true " +
                "audio=false " +
                "control=true " +
                "max_size=1024"
}
