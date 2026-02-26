package com.droidlink.app.adb.crypto

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Manages RSA key pairs for ADB authentication.
 * Generates, stores, and retrieves RSA keys used in the ADB AUTH handshake.
 */
class AdbKeyManager(private val context: Context) {

    private val keyDir: File by lazy {
        File(context.filesDir, "adb_keys").also { it.mkdirs() }
    }

    private val privateKeyFile: File get() = File(keyDir, "adbkey")
    private val publicKeyFile: File get() = File(keyDir, "adbkey.pub")

    private var cachedKeyPair: KeyPair? = null

    /**
     * Get or generate the RSA key pair for ADB authentication.
     */
    fun getKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }

        val keyPair = if (privateKeyFile.exists() && publicKeyFile.exists()) {
            loadKeyPair()
        } else {
            generateKeyPair().also { saveKeyPair(it) }
        }
        cachedKeyPair = keyPair
        return keyPair
    }

    /**
     * Sign a token with the private key for ADB AUTH_SIGNATURE.
     */
    fun signToken(token: ByteArray): ByteArray {
        val keyPair = getKeyPair()
        val signature = java.security.Signature.getInstance("SHA1withRSA")
        signature.initSign(keyPair.private)
        signature.update(token)
        return signature.sign()
    }

    /**
     * Get the public key in ADB format for AUTH_RSAPUBLICKEY.
     * ADB uses a custom format: base64 of the public key struct + " " + user@host
     */
    fun getAdbPublicKey(): ByteArray {
        val keyPair = getKeyPair()
        val publicKey = keyPair.public as RSAPublicKey
        val adbKeyBytes = encodeAdbPublicKey(publicKey)
        val base64Key = Base64.encodeToString(adbKeyBytes, Base64.NO_WRAP)
        val identifier = " DroidLink@${android.os.Build.MODEL}"
        return (base64Key + identifier).toByteArray(Charsets.UTF_8)
    }

    /**
     * Encode RSA public key in ADB's custom format.
     * The format is: RSANUMBYTES (uint32) + n0inv (uint32) + n (RSANUMBYTES) + rr (RSANUMBYTES) + exponent (uint32)
     */
    private fun encodeAdbPublicKey(publicKey: RSAPublicKey): ByteArray {
        val modulus = publicKey.modulus
        val exponent = publicKey.publicExponent

        val modulusBytes = 256 // 2048-bit key = 256 bytes
        val words = modulusBytes / 4 // 64 words

        // Calculate n0inv = -1 / n[0] mod 2^32
        val n0inv = modulus.negate().modInverse(BigInteger.ONE.shiftLeft(32)).toLong()

        // Calculate rr = (2^(modulusBytes*8))^2 mod n
        val r = BigInteger.ONE.shiftLeft(modulusBytes * 8)
        val rr = r.multiply(r).mod(modulus)

        val buffer = java.nio.ByteBuffer.allocate(4 + 4 + modulusBytes + modulusBytes + 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // Number of 32-bit words in modulus
        buffer.putInt(words)

        // n0inv
        buffer.putInt(n0inv.toInt())

        // Modulus (little-endian)
        val modulusByteArray = modulus.toByteArray()
        val modulusLE = ByteArray(modulusBytes)
        for (i in 0 until minOf(modulusByteArray.size, modulusBytes)) {
            modulusLE[i] = modulusByteArray[modulusByteArray.size - 1 - i]
        }
        buffer.put(modulusLE)

        // RR (little-endian)
        val rrByteArray = rr.toByteArray()
        val rrLE = ByteArray(modulusBytes)
        for (i in 0 until minOf(rrByteArray.size, modulusBytes)) {
            rrLE[i] = rrByteArray[rrByteArray.size - 1 - i]
        }
        buffer.put(rrLE)

        // Exponent
        buffer.putInt(exponent.toInt())

        return buffer.array()
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private fun saveKeyPair(keyPair: KeyPair) {
        privateKeyFile.writeBytes(keyPair.private.encoded)
        publicKeyFile.writeBytes(keyPair.public.encoded)
    }

    private fun loadKeyPair(): KeyPair {
        val privateKeyBytes = privateKeyFile.readBytes()
        val publicKeyBytes = publicKeyFile.readBytes()

        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

        return KeyPair(publicKey, privateKey)
    }
}
