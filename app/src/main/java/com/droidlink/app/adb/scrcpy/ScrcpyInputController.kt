package com.droidlink.app.adb.scrcpy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serialises touch input into the Scrcpy v2.x **InjectTouchEvent** binary
 * protocol and sends it to the device via [ScrcpySession.sendControl].
 *
 * Binary layout — 32 bytes, big-endian:
 * ```
 * [0]      type          (0x02 = SC_CONTROL_MSG_TYPE_INJECT_TOUCH_EVENT)
 * [1]      action        (0=DOWN, 1=UP, 2=MOVE)
 * [2-5]    action_button (uint32)
 * [6-9]    buttons       (uint32)
 * [10-17]  pointer_id    (int64)
 * [18-21]  position.x    (int32)
 * [22-25]  position.y    (int32)
 * [26-27]  screen_width  (uint16)
 * [28-29]  screen_height (uint16)
 * [30-31]  pressure      (uint16, value = pressure_f32 × 0xFFFF)
 * ```
 *
 * Reference: https://github.com/Genymobile/scrcpy/blob/master/app/src/control_msg.h
 */
class ScrcpyInputController(
    private val session: ScrcpySession,
    private val scope: CoroutineScope
) {
    /**
     * Encode and send a touch event to the connected device.
     *
     * @param action       0 = DOWN, 1 = UP, 2 = MOVE.
     * @param pointerId    Pointer ID (from [androidx.compose.ui.input.pointer.PointerId.value]).
     * @param x            Touch X coordinate within the surface view.
     * @param y            Touch Y coordinate within the surface view.
     * @param screenWidth  Current surface view width in pixels.
     * @param screenHeight Current surface view height in pixels.
     * @param pressure     Touch pressure in [0.0, 1.0].
     */
    fun sendTouchEvent(
        action: Byte,
        pointerId: Long,
        x: Float,
        y: Float,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float
    ) {
        scope.launch {
            session.sendControl(
                buildTouchPacket(
                    action, pointerId,
                    x.toInt(), y.toInt(),
                    screenWidth, screenHeight,
                    pressure.coerceIn(0f, 1f)
                )
            )
        }
    }

    private fun buildTouchPacket(
        action: Byte,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float
    ): ByteArray {
        val buffer = ByteBuffer.allocate(MSG_SIZE).order(ByteOrder.BIG_ENDIAN)
        buffer.put(TYPE_INJECT_TOUCH_EVENT) // [0]
        buffer.put(action)                  // [1]
        buffer.putInt(0)                    // [2-5]  action_button
        buffer.putInt(BUTTON_PRIMARY)       // [6-9]  buttons
        buffer.putLong(pointerId)           // [10-17] pointer_id
        buffer.putInt(x)                    // [18-21] x
        buffer.putInt(y)                    // [22-25] y
        buffer.putShort(screenWidth.toShort())   // [26-27] screen_width
        buffer.putShort(screenHeight.toShort())  // [28-29] screen_height
        buffer.putShort((pressure * MAX_PRESSURE_VALUE).toInt().toShort()) // [30-31] pressure
        return buffer.array()
    }

    companion object {
        private const val TYPE_INJECT_TOUCH_EVENT: Byte = 0x02
        const val ACTION_DOWN: Byte = 0x00
        const val ACTION_UP: Byte = 0x01
        const val ACTION_MOVE: Byte = 0x02
        /** AMOTION_EVENT_BUTTON_PRIMARY */
        private const val BUTTON_PRIMARY = 1
        /** Maximum uint16 pressure value in the scrcpy protocol (pressure_f32 × 0xFFFF). */
        private const val MAX_PRESSURE_VALUE = 0xFFFF
        private const val MSG_SIZE = 32
    }
}
