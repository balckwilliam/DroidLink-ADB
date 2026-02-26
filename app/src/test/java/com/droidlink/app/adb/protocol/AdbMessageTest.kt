package com.droidlink.app.adb.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbMessageTest {

    @Test
    fun `CNXN command constant matches protocol spec`() {
        // "CNXN" in little-endian = 0x4e584e43
        val expected = ByteBuffer.wrap("CNXN".toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt()
        assertEquals(expected, AdbMessage.CMD_CNXN)
    }

    @Test
    fun `AUTH command constant matches protocol spec`() {
        val expected = ByteBuffer.wrap("AUTH".toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt()
        assertEquals(expected, AdbMessage.CMD_AUTH)
    }

    @Test
    fun `OPEN command constant matches protocol spec`() {
        val expected = ByteBuffer.wrap("OPEN".toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt()
        assertEquals(expected, AdbMessage.CMD_OPEN)
    }

    @Test
    fun `OKAY command constant matches protocol spec`() {
        val expected = ByteBuffer.wrap("OKAY".toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt()
        assertEquals(expected, AdbMessage.CMD_OKAY)
    }

    @Test
    fun `WRTE command constant matches protocol spec`() {
        val expected = ByteBuffer.wrap("WRTE".toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt()
        assertEquals(expected, AdbMessage.CMD_WRTE)
    }

    @Test
    fun `CLSE command constant matches protocol spec`() {
        val expected = ByteBuffer.wrap("CLSE".toByteArray()).order(ByteOrder.LITTLE_ENDIAN).getInt()
        assertEquals(expected, AdbMessage.CMD_CLSE)
    }

    @Test
    fun `magic is command XOR 0xFFFFFFFF`() {
        val message = AdbMessage(command = AdbMessage.CMD_CNXN, arg0 = 0, arg1 = 0)
        assertEquals(AdbMessage.CMD_CNXN xor -1, message.magic)
    }

    @Test
    fun `toBytes produces correct header size`() {
        val message = AdbMessage(command = AdbMessage.CMD_CNXN, arg0 = 1, arg1 = 2)
        val bytes = message.toBytes()
        assertEquals(AdbMessage.HEADER_SIZE, bytes.size)
    }

    @Test
    fun `toBytes includes data after header`() {
        val data = "hello".toByteArray()
        val message = AdbMessage(
            command = AdbMessage.CMD_WRTE,
            arg0 = 1,
            arg1 = 2,
            data = data
        )
        val bytes = message.toBytes()
        assertEquals(AdbMessage.HEADER_SIZE + data.size, bytes.size)
        assertArrayEquals(data, bytes.copyOfRange(AdbMessage.HEADER_SIZE, bytes.size))
    }

    @Test
    fun `parse round-trip`() {
        val original = AdbMessage(
            command = AdbMessage.CMD_CNXN,
            arg0 = AdbMessage.PROTOCOL_VERSION,
            arg1 = AdbMessage.MAX_DATA,
            data = "host::".toByteArray()
        )
        val bytes = original.toBytes()
        val header = bytes.copyOfRange(0, AdbMessage.HEADER_SIZE)
        val data = bytes.copyOfRange(AdbMessage.HEADER_SIZE, bytes.size)

        val parsed = AdbMessage.parse(header, data)
        assertEquals(original.command, parsed.command)
        assertEquals(original.arg0, parsed.arg0)
        assertEquals(original.arg1, parsed.arg1)
        assertArrayEquals(original.data, parsed.data)
    }

    @Test
    fun `parse with empty data`() {
        val original = AdbMessage(command = AdbMessage.CMD_OKAY, arg0 = 1, arg1 = 2)
        val bytes = original.toBytes()
        val header = bytes.copyOfRange(0, AdbMessage.HEADER_SIZE)

        val parsed = AdbMessage.parse(header, ByteArray(0))
        assertEquals(AdbMessage.CMD_OKAY, parsed.command)
        assertEquals(1, parsed.arg0)
        assertEquals(2, parsed.arg1)
        assertEquals(0, parsed.data.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse rejects invalid magic`() {
        val header = ByteBuffer.allocate(AdbMessage.HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(AdbMessage.CMD_CNXN)  // command
            .putInt(0)                     // arg0
            .putInt(0)                     // arg1
            .putInt(0)                     // data_length
            .putInt(0)                     // data_crc32
            .putInt(0x12345678)            // wrong magic
            .array()
        AdbMessage.parse(header)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse rejects data length mismatch`() {
        val original = AdbMessage(
            command = AdbMessage.CMD_WRTE,
            arg0 = 1,
            arg1 = 2,
            data = "hello".toByteArray()
        )
        val bytes = original.toBytes()
        val header = bytes.copyOfRange(0, AdbMessage.HEADER_SIZE)
        // Pass wrong data size
        AdbMessage.parse(header, ByteArray(0))
    }

    @Test
    fun `createConnect produces valid CNXN message`() {
        val msg = AdbMessage.createConnect("host::")
        assertEquals(AdbMessage.CMD_CNXN, msg.command)
        assertEquals(AdbMessage.PROTOCOL_VERSION, msg.arg0)
        assertEquals(AdbMessage.MAX_DATA, msg.arg1)
        assertEquals("host::", String(msg.data))
    }

    @Test
    fun `createOpen includes null terminator`() {
        val msg = AdbMessage.createOpen(1, "shell:")
        assertEquals(AdbMessage.CMD_OPEN, msg.command)
        assertEquals(1, msg.arg0)
        assertEquals(0, msg.arg1)
        val dataStr = String(msg.data, Charsets.UTF_8)
        assert(dataStr.endsWith("\u0000")) { "OPEN data should be null-terminated" }
        assert(dataStr.startsWith("shell:")) { "OPEN data should start with destination" }
    }

    @Test
    fun `createAuthSignature uses AUTH_SIGNATURE type`() {
        val sig = byteArrayOf(1, 2, 3)
        val msg = AdbMessage.createAuthSignature(sig)
        assertEquals(AdbMessage.CMD_AUTH, msg.command)
        assertEquals(AdbMessage.AUTH_SIGNATURE, msg.arg0)
        assertArrayEquals(sig, msg.data)
    }

    @Test
    fun `createAuthPublicKey adds null terminator`() {
        val key = "testkey".toByteArray()
        val msg = AdbMessage.createAuthPublicKey(key)
        assertEquals(AdbMessage.CMD_AUTH, msg.command)
        assertEquals(AdbMessage.AUTH_RSAPUBLICKEY, msg.arg0)
        assertEquals(key.size + 1, msg.data.size)
        assertEquals(0.toByte(), msg.data.last())
    }

    @Test
    fun `dataChecksum computes correct sum`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val msg = AdbMessage(command = 0, arg0 = 0, arg1 = 0, data = data)
        assertEquals(15, msg.dataChecksum) // 1+2+3+4+5 = 15
    }

    @Test
    fun `dataChecksum treats bytes as unsigned`() {
        val data = byteArrayOf(-1) // 0xFF unsigned = 255
        val msg = AdbMessage(command = 0, arg0 = 0, arg1 = 0, data = data)
        assertEquals(255, msg.dataChecksum)
    }

    @Test
    fun `equals and hashCode work correctly`() {
        val msg1 = AdbMessage(command = 1, arg0 = 2, arg1 = 3, data = byteArrayOf(4, 5))
        val msg2 = AdbMessage(command = 1, arg0 = 2, arg1 = 3, data = byteArrayOf(4, 5))
        assertEquals(msg1, msg2)
        assertEquals(msg1.hashCode(), msg2.hashCode())
    }

    @Test
    fun `createWrite produces WRTE message`() {
        val data = "test".toByteArray()
        val msg = AdbMessage.createWrite(1, 2, data)
        assertEquals(AdbMessage.CMD_WRTE, msg.command)
        assertEquals(1, msg.arg0)
        assertEquals(2, msg.arg1)
        assertArrayEquals(data, msg.data)
    }

    @Test
    fun `createClose produces CLSE message`() {
        val msg = AdbMessage.createClose(1, 2)
        assertEquals(AdbMessage.CMD_CLSE, msg.command)
        assertEquals(1, msg.arg0)
        assertEquals(2, msg.arg1)
        assertEquals(0, msg.data.size)
    }

    @Test
    fun `createOkay produces OKAY message`() {
        val msg = AdbMessage.createOkay(1, 2)
        assertEquals(AdbMessage.CMD_OKAY, msg.command)
        assertEquals(1, msg.arg0)
        assertEquals(2, msg.arg1)
        assertEquals(0, msg.data.size)
    }
}
