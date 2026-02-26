package com.droidlink.app.presentation.screens.screen

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.droidlink.app.DroidLinkApplication
import com.droidlink.app.adb.scrcpy.ScrcpyInputController
import com.droidlink.app.adb.scrcpy.ScrcpyServerManager
import com.droidlink.app.adb.scrcpy.ScrcpySession
import com.droidlink.app.presentation.components.VideoDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages the scrcpy screen-mirroring lifecycle and exposes UI state to
 * [ScreenMirrorScreen].
 *
 * State machine:
 * ```
 * Idle ──startMirroring()──► PushingServer ──► StartingServer ──► Streaming
 *  ▲                                                                   │
 *  └──────────────────── stopMirroring() / error / surface destroyed ──┘
 * ```
 */
class ScreenMirrorViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val connection = (app as DroidLinkApplication).adbConnection
    private val serverManager = ScrcpyServerManager(context, connection)

    private var currentSession: ScrcpySession? = null
    private var inputController: ScrcpyInputController? = null
    private var videoDecoder: VideoDecoder? = null

    /** Surface dimensions tracked after [onSurfaceSizeChanged]. */
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    /** Surface provided by [ScrcpySurface] when it is ready for rendering. */
    private var pendingSurface: Surface? = null

    // ── Public state ──────────────────────────────────────────────────────────

    sealed class MirrorState {
        data object Idle : MirrorState()
        data object PushingServer : MirrorState()
        data object StartingServer : MirrorState()
        data object Streaming : MirrorState()
        data class Error(val message: String) : MirrorState()
    }

    private val _state = MutableStateFlow<MirrorState>(MirrorState.Idle)
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    // ── Surface lifecycle callbacks (called from ScrcpySurface) ───────────────

    fun onSurfaceReady(surface: Surface) {
        pendingSurface = surface
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    fun onSurfaceDestroyed() {
        pendingSurface = null
        stopMirroring()
    }

    // ── Mirroring control ─────────────────────────────────────────────────────

    /**
     * Push the server JAR to the device, start it, and begin decoding.
     * Does nothing when already streaming.
     */
    fun startMirroring() {
        val surface = pendingSurface ?: return
        if (_state.value is MirrorState.Streaming) return

        viewModelScope.launch {
            // Phase 1: push JAR
            _state.value = MirrorState.PushingServer
            if (!serverManager.pushServer()) {
                _state.value = MirrorState.Error("Failed to push scrcpy-server.jar to device")
                return@launch
            }

            // Phase 2: start server
            _state.value = MirrorState.StartingServer
            val session = try {
                serverManager.startServer()
            } catch (e: Exception) {
                _state.value = MirrorState.Error("Failed to start server: ${e.message}")
                return@launch
            }

            currentSession = session
            inputController = ScrcpyInputController(session, viewModelScope)
            session.startReading(viewModelScope)

            // Phase 3: decode
            _state.value = MirrorState.Streaming
            val decoder = VideoDecoder(surface)
            videoDecoder = decoder
            try {
                decoder.start(
                    session.videoInputStream,
                    surfaceWidth.coerceAtLeast(MIN_SURFACE_WIDTH),
                    surfaceHeight.coerceAtLeast(MIN_SURFACE_HEIGHT)
                )
            } finally {
                // Decoder loop exited (stream closed or stop() called).
                if (_state.value is MirrorState.Streaming) {
                    _state.value = MirrorState.Idle
                }
                videoDecoder = null
                currentSession = null
                inputController = null
            }
        }
    }

    /** Stop the current mirroring session and reset to [MirrorState.Idle]. */
    fun stopMirroring() {
        viewModelScope.launch {
            videoDecoder?.stop()
            videoDecoder = null
            currentSession?.close()
            currentSession = null
            inputController = null
            _state.value = MirrorState.Idle
        }
    }

    /**
     * Forward a Compose touch event to the scrcpy input controller.
     * Called by [com.droidlink.app.presentation.components.ScrcpySurface].
     */
    fun sendTouchEvent(
        action: Byte,
        pointerId: Long,
        x: Float,
        y: Float,
        surfaceWidth: Int,
        surfaceHeight: Int,
        pressure: Float
    ) {
        inputController?.sendTouchEvent(action, pointerId, x, y, surfaceWidth, surfaceHeight, pressure)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { stopMirroring() }
    }

    companion object {
        /** Minimum MediaCodec decoder width used when the surface has not been sized yet. */
        private const val MIN_SURFACE_WIDTH = 1024
        /** Minimum MediaCodec decoder height used when the surface has not been sized yet. */
        private const val MIN_SURFACE_HEIGHT = 600
    }
}
