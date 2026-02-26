package com.droidlink.app.adb.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SyncProtocolTest {

    @Test
    fun `sync IDs match expected ASCII values`() {
        assertEquals(toSyncId("STAT"), SyncProtocol.STAT)
        assertEquals(toSyncId("LIST"), SyncProtocol.LIST)
        assertEquals(toSyncId("SEND"), SyncProtocol.SEND)
        assertEquals(toSyncId("RECV"), SyncProtocol.RECV)
        assertEquals(toSyncId("DATA"), SyncProtocol.DATA)
        assertEquals(toSyncId("DONE"), SyncProtocol.DONE)
        assertEquals(toSyncId("OKAY"), SyncProtocol.OKAY)
        assertEquals(toSyncId("FAIL"), SyncProtocol.FAIL)
        assertEquals(toSyncId("QUIT"), SyncProtocol.QUIT)
    }

    @Test
    fun `createSyncRequest has correct format`() {
        val path = "/sdcard/test.txt"
        val request = SyncProtocol.createSyncRequest(SyncProtocol.RECV, path)

        val buffer = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(SyncProtocol.RECV, buffer.getInt())
        assertEquals(path.length, buffer.getInt())

        val pathBytes = ByteArray(path.length)
        buffer.get(pathBytes)
        assertEquals(path, String(pathBytes))
    }

    @Test
    fun `createSendRequest includes mode in path`() {
        val path = "/sdcard/file.txt"
        val mode = 0x1A4 // 644 octal
        val request = SyncProtocol.createSendRequest(path, mode)

        val buffer = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(SyncProtocol.SEND, buffer.getInt())

        val expectedPath = "$path,$mode"
        assertEquals(expectedPath.length, buffer.getInt())

        val pathBytes = ByteArray(expectedPath.length)
        buffer.get(pathBytes)
        assertEquals(expectedPath, String(pathBytes))
    }

    @Test
    fun `createDataChunk has correct header and data`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val chunk = SyncProtocol.createDataChunk(data)

        val buffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(SyncProtocol.DATA, buffer.getInt())
        assertEquals(data.size, buffer.getInt())

        val chunkData = ByteArray(data.size)
        buffer.get(chunkData)
        org.junit.Assert.assertArrayEquals(data, chunkData)
    }

    @Test
    fun `createDataChunk with offset and length`() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5, 6)
        val chunk = SyncProtocol.createDataChunk(data, offset = 2, length = 3)

        val buffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(SyncProtocol.DATA, buffer.getInt())
        assertEquals(3, buffer.getInt())

        val chunkData = ByteArray(3)
        buffer.get(chunkData)
        org.junit.Assert.assertArrayEquals(byteArrayOf(2, 3, 4), chunkData)
    }

    @Test
    fun `createDone has correct format`() {
        val modTime = 1700000000
        val done = SyncProtocol.createDone(modTime)

        val buffer = ByteBuffer.wrap(done).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(SyncProtocol.DONE, buffer.getInt())
        assertEquals(modTime, buffer.getInt())
    }

    @Test
    fun `readSyncResponse parses id and length`() {
        val responseBytes = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(SyncProtocol.OKAY)
            .putInt(42)
            .array()

        val input = ByteArrayInputStream(responseBytes)
        val response = SyncProtocol.readSyncResponse(input)

        assertEquals(SyncProtocol.OKAY, response.id)
        assertEquals(42, response.length)
    }

    @Test
    fun `readData reads exact number of bytes`() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val input = ByteArrayInputStream(data)
        val result = SyncProtocol.readData(input, 5)
        org.junit.Assert.assertArrayEquals(data, result)
    }

    @Test
    fun `readData reads partial when length is smaller`() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val input = ByteArrayInputStream(data)
        val result = SyncProtocol.readData(input, 3)
        org.junit.Assert.assertArrayEquals(byteArrayOf(10, 20, 30), result)
    }

    private fun toSyncId(s: String): Int {
        return ByteBuffer.wrap(s.toByteArray(Charsets.US_ASCII))
            .order(ByteOrder.LITTLE_ENDIAN)
            .getInt()
    }
}
