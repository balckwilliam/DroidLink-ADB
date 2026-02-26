package com.droidlink.app.adb.scrcpy

import android.util.Log
import com.droidlink.app.adb.connection.AdbConnection
import com.droidlink.app.adb.connection.AdbStream
import com.droidlink.app.adb.protocol.AdbMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Manages an active scrcpy session over a single ADB shell stream.
 *
 * Incoming [AdbMessage.CMD_WRTE] messages are written to the [videoInputStream]
 * pipe so that [com.droidlink.app.presentation.components.VideoDecoder] can
 * consume them on a separate thread.  Outgoing control events (touch, etc.) are
 * sent through [sendControl].
 */
class ScrcpySession(
    internal val stream: AdbStream,
    private val connection: AdbConnection
) {
    private val pipedOutput = PipedOutputStream()

    /** Raw H.264 video bytes produced by the scrcpy server. */
    val videoInputStream: PipedInputStream = PipedInputStream(pipedOutput, PIPE_BUFFER_SIZE)

    private var readJob: Job? = null

    /**
     * Start the background coroutine that reads ADB messages and pipes video
     * data into [videoInputStream].  Must be called after [startServer] returns.
     */
    fun startReading(scope: CoroutineScope) {
        readJob = scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val msg = connection.readMessage()
                    when (msg.command) {
                        AdbMessage.CMD_WRTE -> {
                            pipedOutput.write(msg.data)
                            pipedOutput.flush()
                            // Acknowledge the write so the device keeps sending.
                            connection.sendMessage(
                                AdbMessage.createOkay(stream.localId, stream.remoteId)
                            )
                        }
                        AdbMessage.CMD_CLSE -> {
                            pipedOutput.close()
                            break
                        }
                        else -> { /* no-op for OKAY and other control messages */ }
                    }
                }
            } catch (e: Exception) {
                // Stream ended or connection dropped – signal EOF to the decoder.
                Log.d(TAG, "ScrcpySession read loop exited: ${e.message}")
                runCatching { pipedOutput.close() }
            }
        }
    }

    /**
     * Send raw bytes to the device stdin (used for scrcpy control events).
     */
    suspend fun sendControl(data: ByteArray) = stream.write(data)

    /** Close the session and release all resources. */
    suspend fun close() {
        readJob?.cancel()
        runCatching { pipedOutput.close() }
        stream.close()
    }

    companion object {
        private const val TAG = "ScrcpySession"
        /** Pipe buffer: 2 MB is enough for several unprocessed H.264 frames. */
        private const val PIPE_BUFFER_SIZE = 2 * 1024 * 1024
    }
}
