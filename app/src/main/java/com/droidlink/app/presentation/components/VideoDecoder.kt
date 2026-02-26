package com.droidlink.app.presentation.components

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hardware-accelerated H.264 decoder that feeds the raw video stream produced
 * by the scrcpy server into a [MediaCodec] and renders frames directly to a
 * [Surface].
 *
 * ### Scrcpy v2.x stream format
 *
 * **Initial metadata header** (72 bytes, skipped before decoding):
 * ```
 *   8  bytes  device_id   (big-endian uint64)
 *   64 bytes  device_name (null-padded UTF-8)
 * ```
 *
 * **Per-frame header** (12 bytes, big-endian):
 * ```
 *   8 bytes  pts_flags
 *              bit 63 = is config packet (SPS/PPS)
 *              bit 62 = is key frame
 *              bits 0-61 = presentation timestamp (µs)
 *   4 bytes  data_size
 * ```
 * Followed by `data_size` bytes of raw H.264 NAL unit data.
 */
class VideoDecoder(private val surface: Surface) {

    private var codec: MediaCodec? = null

    @Volatile
    private var running = false

    /**
     * Configure [MediaCodec], start it, and enter the decode loop.
     *
     * This is a **suspending, blocking** call — run inside a coroutine on
     * [Dispatchers.IO] (e.g. inside `viewModelScope.launch { decoder.start(…) }`).
     * The function returns when [stop] is called or the stream is closed.
     *
     * @param inputStream  Raw byte stream from [com.droidlink.app.adb.scrcpy.ScrcpySession.videoInputStream].
     * @param width        Initial width hint for the [MediaFormat] (updated by SPS in-stream).
     * @param height       Initial height hint.
     */
    suspend fun start(
        inputStream: InputStream,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ) = withContext(Dispatchers.IO) {
        running = true
        val mediaCodec = MediaCodec.createDecoderByType(MIME_H264)
        codec = mediaCodec

        val format = MediaFormat.createVideoFormat(MIME_H264, width, height)
        mediaCodec.configure(format, surface, null, 0)
        mediaCodec.start()

        try {
            // Skip the 72-byte metadata header sent by every scrcpy v2.x session.
            skipFully(inputStream, METADATA_HEADER_SIZE)

            val bufferInfo = MediaCodec.BufferInfo()

            while (running) {
                // Read the 12-byte per-frame header.
                val ptsFlags = readLong(inputStream)
                val dataSize = readInt(inputStream)
                val data = readFully(inputStream, dataSize)

                val isConfig = (ptsFlags and FLAG_CONFIG) != 0L
                val isKeyFrame = (ptsFlags and FLAG_KEY_FRAME) != 0L
                val pts = ptsFlags and PTS_MASK

                val codecFlags = when {
                    isConfig -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    isKeyFrame -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else -> 0
                }

                // Submit data to an available input buffer.
                val inputIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuf: ByteBuffer = mediaCodec.getInputBuffer(inputIndex)!!
                    inputBuf.clear()
                    inputBuf.put(data)
                    mediaCodec.queueInputBuffer(inputIndex, 0, data.size, pts, codecFlags)
                }

                // Release all available decoded output frames to the Surface.
                drainOutput(mediaCodec, bufferInfo)
            }
        } catch (_: IOException) {
            // Expected when the session is closed or stop() is called.
        } catch (e: Exception) {
            // Log unexpected codec errors so they can be diagnosed while still
            // allowing the coroutine to exit cleanly.
            Log.e(TAG, "Unexpected error in video decode loop", e)
        } finally {
            runCatching { mediaCodec.stop() }
            runCatching { mediaCodec.release() }
            codec = null
        }
    }

    /** Stop the decode loop and release codec resources. */
    fun stop() {
        running = false
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    // ── Output draining ──────────────────────────────────────────────────────

    private fun drainOutput(codec: MediaCodec, info: MediaCodec.BufferInfo) {
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 0)
            when {
                idx >= 0 -> {
                    // render = true → frame is pushed to Surface automatically.
                    codec.releaseOutputBuffer(idx, /* render = */ true)
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* no-op */ }
                else -> break
            }
        }
    }

    // ── Stream-reading helpers ────────────────────────────────────────────────

    private fun skipFully(stream: InputStream, count: Int) {
        var remaining = count.toLong()
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) throw IOException("EOF while skipping metadata header")
            remaining -= skipped
        }
    }

    private fun readLong(stream: InputStream): Long {
        val buf = readFully(stream, 8)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).getLong()
    }

    private fun readInt(stream: InputStream): Int {
        val buf = readFully(stream, 4)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).getInt()
    }

    private fun readFully(stream: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = stream.read(buf, offset, size - offset)
            if (read == -1) throw IOException("Unexpected end of video stream")
            offset += read
        }
        return buf
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "VideoDecoder"
        private const val MIME_H264 = "video/avc"
        /** 8 bytes device_id + 64 bytes device_name = 72 bytes. */
        private const val METADATA_HEADER_SIZE = 72
        private const val DEQUEUE_TIMEOUT_US = 10_000L // 10 ms
        private const val FLAG_CONFIG = 1L shl 63
        private const val FLAG_KEY_FRAME = 1L shl 62
        private const val PTS_MASK = (1L shl 62) - 1L
        private const val DEFAULT_WIDTH = 1024
        private const val DEFAULT_HEIGHT = 600
    }
}
