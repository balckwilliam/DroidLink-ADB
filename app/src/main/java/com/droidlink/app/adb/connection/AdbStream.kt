package com.droidlink.app.adb.connection

import com.droidlink.app.adb.protocol.AdbMessage
import java.io.IOException

/**
 * Represents an open ADB stream (channel) on an ADB connection.
 * Each stream has a local and remote ID pair.
 */
class AdbStream(
    val localId: Int,
    val remoteId: Int,
    private val connection: AdbConnection,
    private val maxData: Int
) {
    var isClosed = false
        private set

    /**
     * Write data to this stream.
     */
    suspend fun write(data: ByteArray) {
        if (isClosed) throw IOException("Stream is closed")

        var offset = 0
        while (offset < data.size) {
            val chunkSize = minOf(data.size - offset, maxData)
            val chunk = data.copyOfRange(offset, offset + chunkSize)
            connection.sendMessage(AdbMessage.createWrite(localId, remoteId, chunk))
            offset += chunkSize
        }
    }

    /**
     * Close this stream.
     */
    suspend fun close() {
        if (!isClosed) {
            isClosed = true
            connection.sendMessage(AdbMessage.createClose(localId, remoteId))
        }
    }
}
