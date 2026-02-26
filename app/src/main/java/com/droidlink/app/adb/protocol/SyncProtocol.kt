package com.droidlink.app.adb.protocol

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implementation of ADB Sync protocol for file operations.
 * Used for pushing and pulling files from connected devices.
 */
object SyncProtocol {
    // Sync commands (little-endian 4-byte identifiers)
    val STAT = "STAT".toSyncId()
    val LIST = "LIST".toSyncId()
    val SEND = "SEND".toSyncId()
    val RECV = "RECV".toSyncId()
    val DATA = "DATA".toSyncId()
    val DONE = "DONE".toSyncId()
    val OKAY = "OKAY".toSyncId()
    val FAIL = "FAIL".toSyncId()
    val QUIT = "QUIT".toSyncId()

    const val SYNC_DATA_MAX = 64 * 1024 // 64KB max data per chunk

    /**
     * Create a sync request packet (command + length + path).
     */
    fun createSyncRequest(command: Int, path: String): ByteArray {
        val pathBytes = path.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + pathBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(pathBytes.size)
            .put(pathBytes)
        return buffer.array()
    }

    /**
     * Create a SEND request with path and permissions.
     */
    fun createSendRequest(path: String, mode: Int): ByteArray {
        val pathWithMode = "$path,$mode"
        return createSyncRequest(SEND, pathWithMode)
    }

    /**
     * Create a data chunk for file transfer.
     */
    fun createDataChunk(data: ByteArray, offset: Int = 0, length: Int = data.size): ByteArray {
        val buffer = ByteBuffer.allocate(8 + length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(DATA)
            .putInt(length)
            .put(data, offset, length)
        return buffer.array()
    }

    /**
     * Create a DONE message with modification timestamp.
     */
    fun createDone(modifiedTime: Int): ByteArray {
        val buffer = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(DONE)
            .putInt(modifiedTime)
        return buffer.array()
    }

    /**
     * Read a sync response (id + length).
     */
    fun readSyncResponse(input: InputStream): SyncResponse {
        val header = ByteArray(8)
        var bytesRead = 0
        while (bytesRead < 8) {
            val read = input.read(header, bytesRead, 8 - bytesRead)
            if (read == -1) throw java.io.IOException("Unexpected end of stream")
            bytesRead += read
        }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val id = buffer.getInt()
        val length = buffer.getInt()
        return SyncResponse(id, length)
    }

    /**
     * Read data of specified length from input stream.
     */
    fun readData(input: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var bytesRead = 0
        while (bytesRead < length) {
            val read = input.read(data, bytesRead, length - bytesRead)
            if (read == -1) throw java.io.IOException("Unexpected end of stream")
            bytesRead += read
        }
        return data
    }

    data class SyncResponse(val id: Int, val length: Int)

    data class FileStat(
        val mode: Int,
        val size: Int,
        val modifiedTime: Int
    )

    data class DirectoryEntry(
        val name: String,
        val mode: Int,
        val size: Int,
        val modifiedTime: Int
    )
}

private fun String.toSyncId(): Int {
    val bytes = this.toByteArray(Charsets.US_ASCII)
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt()
}
