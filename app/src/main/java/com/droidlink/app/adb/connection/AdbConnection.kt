package com.droidlink.app.adb.connection

import com.droidlink.app.adb.crypto.AdbKeyManager
import com.droidlink.app.adb.protocol.AdbMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages ADB connection to a remote device over TCP/IP.
 * Handles the connection lifecycle including handshake, authentication,
 * stream management, and disconnection.
 */
class AdbConnection(
    private val keyManager: AdbKeyManager
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val writeMutex = Mutex()
    private val localIdCounter = AtomicInteger(1)
    private val streams = ConcurrentHashMap<Int, AdbStream>()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var maxData = AdbMessage.MAX_DATA
    var deviceBanner: String = ""
        private set

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Authenticating : ConnectionState()
        data class Connected(val banner: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * Connect to a device at the given host:port and perform ADB handshake.
     */
    suspend fun connect(host: String, port: Int, timeoutMs: Int = 5000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.Connecting

                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                sock.tcpNoDelay = true
                socket = sock
                inputStream = sock.getInputStream()
                outputStream = sock.getOutputStream()

                // Send CNXN
                sendMessage(AdbMessage.createConnect())

                // Read response
                _connectionState.value = ConnectionState.Authenticating
                val response = readMessage()

                when (response.command) {
                    AdbMessage.CMD_AUTH -> {
                        handleAuth(response)
                    }
                    AdbMessage.CMD_CNXN -> {
                        // Device accepted without auth
                        handleConnected(response)
                        true
                    }
                    else -> {
                        _connectionState.value = ConnectionState.Error("Unexpected response: 0x${response.command.toString(16)}")
                        false
                    }
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
                disconnect()
                false
            }
        }

    /**
     * Handle ADB authentication flow.
     */
    private suspend fun handleAuth(authMessage: AdbMessage): Boolean {
        if (authMessage.arg0 == AdbMessage.AUTH_TOKEN) {
            // Try signing with existing key
            val signature = keyManager.signToken(authMessage.data)
            sendMessage(AdbMessage.createAuthSignature(signature))

            val response = readMessage()
            return when (response.command) {
                AdbMessage.CMD_CNXN -> {
                    handleConnected(response)
                    true
                }
                AdbMessage.CMD_AUTH -> {
                    // Device doesn't recognize our key, send public key
                    val publicKey = keyManager.getAdbPublicKey()
                    sendMessage(AdbMessage.createAuthPublicKey(publicKey))

                    // Wait for user to accept on device
                    val finalResponse = readMessage()
                    if (finalResponse.command == AdbMessage.CMD_CNXN) {
                        handleConnected(finalResponse)
                        true
                    } else {
                        _connectionState.value = ConnectionState.Error("Authentication rejected")
                        false
                    }
                }
                else -> {
                    _connectionState.value = ConnectionState.Error("Unexpected auth response")
                    false
                }
            }
        }
        _connectionState.value = ConnectionState.Error("Unknown auth type: ${authMessage.arg0}")
        return false
    }

    private fun handleConnected(cnxnMessage: AdbMessage) {
        maxData = cnxnMessage.arg1
        deviceBanner = String(cnxnMessage.data, Charsets.UTF_8)
        _connectionState.value = ConnectionState.Connected(deviceBanner)
    }

    /**
     * Open a new ADB stream to the given destination.
     * @param destination e.g., "shell:", "sync:", "shell:pm list packages"
     */
    suspend fun open(destination: String): AdbStream? = withContext(Dispatchers.IO) {
        val localId = localIdCounter.getAndIncrement()
        sendMessage(AdbMessage.createOpen(localId, destination))

        val response = readMessage()
        if (response.command == AdbMessage.CMD_OKAY) {
            val stream = AdbStream(
                localId = localId,
                remoteId = response.arg0,
                connection = this@AdbConnection,
                maxData = maxData
            )
            streams[localId] = stream
            stream
        } else {
            null
        }
    }

    /**
     * Execute a shell command and return the output.
     */
    suspend fun executeShell(command: String): String = withContext(Dispatchers.IO) {
        val stream = open("shell:$command")
            ?: throw IOException("Failed to open shell stream")

        val output = StringBuilder()
        try {
            while (true) {
                val message = readMessage()
                when (message.command) {
                    AdbMessage.CMD_WRTE -> {
                        output.append(String(message.data, Charsets.UTF_8))
                        sendMessage(AdbMessage.createOkay(stream.localId, stream.remoteId))
                    }
                    AdbMessage.CMD_CLSE -> {
                        sendMessage(AdbMessage.createClose(stream.localId, stream.remoteId))
                        break
                    }
                    else -> break
                }
            }
        } finally {
            streams.remove(stream.localId)
        }
        output.toString()
    }

    /**
     * Send a raw ADB message.
     */
    suspend fun sendMessage(message: AdbMessage) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val out = outputStream ?: throw IOException("Not connected")
                out.write(message.toBytes())
                out.flush()
            }
        }
    }

    /**
     * Read a raw ADB message from the connection.
     */
    suspend fun readMessage(): AdbMessage = withContext(Dispatchers.IO) {
        val input = inputStream ?: throw IOException("Not connected")

        // Read header
        val headerBytes = ByteArray(AdbMessage.HEADER_SIZE)
        readFully(input, headerBytes)

        // Parse data length from header
        val dataLength = ByteBuffer.wrap(headerBytes, 12, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getInt()

        // Read data
        val data = if (dataLength > 0) {
            ByteArray(dataLength).also { readFully(input, it) }
        } else {
            ByteArray(0)
        }

        AdbMessage.parse(headerBytes, data)
    }

    /**
     * Disconnect from the device.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            streams.values.forEach { stream ->
                try {
                    sendMessage(AdbMessage.createClose(stream.localId, stream.remoteId))
                } catch (_: Exception) { }
            }
            streams.clear()
            socket?.close()
        } catch (_: Exception) { }
        socket = null
        inputStream = null
        outputStream = null
        _connectionState.value = ConnectionState.Disconnected
    }

    val isConnected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } ?: false

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw IOException("Unexpected end of stream")
            offset += read
        }
    }
}
