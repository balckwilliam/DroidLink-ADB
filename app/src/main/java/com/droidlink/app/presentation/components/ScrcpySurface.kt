package com.droidlink.app.presentation.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.droidlink.app.presentation.screens.screen.ScreenMirrorViewModel

/**
 * Composable that renders a hardware-accelerated [SurfaceView] and routes
 * Compose [pointerInput] events to the scrcpy input controller via
 * [ScreenMirrorViewModel].
 *
 * The [SurfaceHolder.Callback] notifies the view model when the surface is
 * created, resized, or destroyed so the [VideoDecoder] can be attached or
 * detached at the right time.
 */
@Composable
fun ScrcpySurface(
    viewModel: ScreenMirrorViewModel,
    modifier: Modifier = Modifier
) {
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }

    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        viewModel.onSurfaceReady(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        surfaceWidth = width
                        surfaceHeight = height
                        viewModel.onSurfaceSizeChanged(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        viewModel.onSurfaceDestroyed()
                    }
                })
            }
        },
        modifier = modifier.pointerInput(Unit) {
            // Use Compose PointerInput to intercept touch events and forward them
            // to the scrcpy input controller as binary InjectTouchEvent messages.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val action: Byte = when (event.type) {
                        PointerEventType.Press -> 0x00   // DOWN
                        PointerEventType.Release -> 0x01 // UP
                        PointerEventType.Move -> 0x02    // MOVE
                        else -> {
                            // Ignore scroll, hover, and other pointer event types.
                            event.changes.forEach { it.consume() }
                            continue
                        }
                    }
                    for (change in event.changes) {
                        viewModel.sendTouchEvent(
                            action = action,
                            pointerId = change.id.value,
                            x = change.position.x,
                            y = change.position.y,
                            surfaceWidth = surfaceWidth,
                            surfaceHeight = surfaceHeight,
                            pressure = change.pressure
                        )
                        change.consume()
                    }
                }
            }
        }
    )
}
