package com.droidlink.app.adb.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents an ADB protocol message.
 * ADB message format:
 *   command (4 bytes) - identifies the type of message
 *   arg0 (4 bytes) - first argument
 *   arg1 (4 bytes) - second argument
 *   data_length (4 bytes) - length of payload
 *   data_crc32 (4 bytes) - checksum of data payload
 *   magic (4 bytes) - command ^ 0xFFFFFFFF
 *   data (data_length bytes) - payload
 */
data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = ByteArray(0)
) {
    val dataLength: Int get() = data.size
    val dataChecksum: Int get() = computeChecksum(data)
    val magic: Int get() = command xor MAGIC_MASK

    /**
     * Serialize this message to bytes for transmission.
     */
    fun toBytes(): ByteArray {
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(dataLength)
            .putInt(dataChecksum)
            .putInt(magic)
            .array()
        return header + data
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdbMessage) return false
        return command == other.command &&
                arg0 == other.arg0 &&
                arg1 == other.arg1 &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_SIZE = 24
        private const val MAGIC_MASK = -0x1 // 0xFFFFFFFF

        // ADB Protocol Commands
        const val CMD_CNXN = 0x4e584e43 // "CNXN"
        const val CMD_AUTH = 0x48545541 // "AUTH"
        const val CMD_OPEN = 0x4e45504f // "OPEN"
        const val CMD_OKAY = 0x59414b4f // "OKAY"
        const val CMD_CLSE = 0x45534c43 // "CLSE"
        const val CMD_WRTE = 0x45545257 // "WRTE"

        // AUTH types
        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        // Protocol version and max data
        const val PROTOCOL_VERSION = 0x01000000
        const val MAX_DATA = 1024 * 1024 // 1MB for v1

        /**
         * Parse an ADB message from raw bytes (header + data).
         */
        fun parse(headerBytes: ByteArray, data: ByteArray = ByteArray(0)): AdbMessage {
            require(headerBytes.size >= HEADER_SIZE) { "Header must be at least $HEADER_SIZE bytes" }
            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.getInt()
            val arg0 = buffer.getInt()
            val arg1 = buffer.getInt()
            val dataLength = buffer.getInt()
            val dataChecksum = buffer.getInt()
            val magic = buffer.getInt()

            // Validate magic
            require(magic == command xor MAGIC_MASK) {
                "Invalid magic: expected ${command xor MAGIC_MASK}, got $magic"
            }

            // Validate data length
            require(data.size == dataLength) {
                "Data length mismatch: expected $dataLength, got ${data.size}"
            }

            return AdbMessage(command, arg0, arg1, data)
        }

        /**
         * Create a CNXN (connect) message.
         */
        fun createConnect(banner: String = "host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,fixed_push_symlink_timestamp,abb_exec,remount_shell,track_app,sendrecv_v2,sendrecv_v2_brotli,sendrecv_v2_lz4,sendrecv_v2_zstd,sendrecv_v2_dry_run_send"): AdbMessage {
            return AdbMessage(
                command = CMD_CNXN,
                arg0 = PROTOCOL_VERSION,
                arg1 = MAX_DATA,
                data = banner.toByteArray(Charsets.UTF_8)
            )
        }

        /**
         * Create an AUTH signature message.
         */
        fun createAuthSignature(signature: ByteArray): AdbMessage {
            return AdbMessage(
                command = CMD_AUTH,
                arg0 = AUTH_SIGNATURE,
                arg1 = 0,
                data = signature
            )
        }

        /**
         * Create an AUTH public key message.
         */
        fun createAuthPublicKey(publicKey: ByteArray): AdbMessage {
            return AdbMessage(
                command = CMD_AUTH,
                arg0 = AUTH_RSAPUBLICKEY,
                arg1 = 0,
                data = publicKey + byteArrayOf(0) // null-terminated
            )
        }

        /**
         * Create an OPEN message to open a new stream.
         */
        fun createOpen(localId: Int, destination: String): AdbMessage {
            return AdbMessage(
                command = CMD_OPEN,
                arg0 = localId,
                arg1 = 0,
                data = (destination + "\u0000").toByteArray(Charsets.UTF_8)
            )
        }

        /**
         * Create a WRTE message.
         */
        fun createWrite(localId: Int, remoteId: Int, data: ByteArray): AdbMessage {
            return AdbMessage(
                command = CMD_WRTE,
                arg0 = localId,
                arg1 = remoteId,
                data = data
            )
        }

        /**
         * Create an OKAY message.
         */
        fun createOkay(localId: Int, remoteId: Int): AdbMessage {
            return AdbMessage(
                command = CMD_OKAY,
                arg0 = localId,
                arg1 = remoteId
            )
        }

        /**
         * Create a CLSE message.
         */
        fun createClose(localId: Int, remoteId: Int): AdbMessage {
            return AdbMessage(
                command = CMD_CLSE,
                arg0 = localId,
                arg1 = remoteId
            )
        }

        private fun computeChecksum(data: ByteArray): Int {
            var sum = 0
            for (byte in data) {
                sum += byte.toInt() and 0xFF
            }
            return sum
        }
    }
}
