package com.avdbuddy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*

@Composable fun EmulatorPanel(device: Device, state: AppState) {
    val running = device.name in state.running
    val starting = device.name in state.starting && !running
    val liveFrame = state.frames[device.directory.absolutePath]
    val frameRotation = state.frameRotations[device.directory.absolutePath] ?: 0
    val request = state.rotations[device.name]?.takeIf { it.serial == state.running[device.name] }
    val targetAngle = -(request?.initialRotation ?: frameRotation) * 90f + (request?.turns ?: 0) * 90f
    val rotation = remember(device.name, state.running[device.name]) {
        Animatable(targetAngle)
    }
    LaunchedEffect(rotation, targetAngle) {
        // Cancellation preserves the current angle. Drop old momentum so a
        // direction change responds immediately instead of continuing the old turn.
        rotation.animateTo(
            targetValue = targetAngle,
            animationSpec = spring(dampingRatio = .75f, stiffness = 700f),
            initialVelocity = 0f,
        )
    }
    val angle = rotation.value
    val rotating = kotlin.math.abs(angle - targetAngle) > .1f
    val frame = liveFrame
    val focus = remember(device.name) { FocusRequester() }
    val landscape = device.type in listOf(DeviceType.TV, DeviceType.CAR, DeviceType.DESKTOP)
    // The touch protocol addresses the unrotated physical framebuffer.
    val nativeSize = frame?.let { nativeScreenSize(it.width, it.height, frameRotation) }
    val width = nativeSize?.first ?: device.config["hw.lcd.width"]?.toIntOrNull() ?: if(landscape) 1920 else if(device.type == DeviceType.TABLET) 1600 else 1080
    val height = nativeSize?.second ?: device.config["hw.lcd.height"]?.toIntOrNull() ?: if(landscape) 1080 else if(device.type == DeviceType.TABLET) 2560 else if(device.type == DeviceType.WEAR) 1080 else 2400
    Column(Modifier.fillMaxSize().testTag("EmulatorPanel")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp, vertical=18.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(device.displayName, modifier=Modifier.testTag("PreviewDeviceName"))
                Spacer(Modifier.height(3.dp))
                Label(androidFamilyTitle(device.api), color=JewelTheme.globalColors.text.info)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(horizontal=30.dp, vertical=10.dp), contentAlignment=Alignment.Center) {
            val bezel = 0.dp
            // Keep one physical surface; its scale and touch coordinates never
            // change when it turns. The parent transform maps pointer positions.
            val naturalRatio = width.toFloat() / height.coerceAtLeast(1)
            val screenWidth = minOf(maxWidth - bezel*2, (maxHeight-bezel*2)*naturalRatio).coerceAtLeast(1.dp)
            val screenHeight = screenWidth / naturalRatio
            Box(Modifier.requiredSize(screenWidth+bezel*2,screenHeight+bezel*2)
                .graphicsLayer { rotationZ = angle }
                .background(JewelTheme.globalColors.panelBackground)
                .padding(bezel).testTag("EmulatorBezel")) {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)
                    .focusRequester(focus).onPreviewKeyEvent { event ->
                        if (!running || rotating || event.type != KeyEventType.KeyDown) false else {
                            val code = when(event.key) { Key.Enter -> "66"; Key.Backspace -> "67"; Key.Escape -> "4"; Key.DirectionLeft -> "21"; Key.DirectionRight -> "22"; Key.DirectionUp -> "19"; Key.DirectionDown -> "20"; else -> null }
                            if(code != null) { state.sendInput(device, listOf("keyevent",code)); true }
                            else {
                                val char = event.utf16CodePoint.toChar()
                                if(!event.isMetaPressed && !event.isCtrlPressed && (char.isLetterOrDigit() && char.code < 128 || char == ' ')) {
                                    state.sendInput(device, listOf("text",if(char==' ') "%s" else char.toString())); true
                                } else false
                            }
                        }
                    }.focusable().pointerInput(device.name, running, rotating, width, height) {
                        if(running && !rotating) awaitEachGesture {
                            val down = awaitFirstDown(); focus.requestFocus(); down.consume()
                            var last = screenPoint(down.position.x, down.position.y, size.width, size.height, width, height)
                            state.sendTouch(device, last.first, last.second, true)
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    last = screenPoint(change.position.x, change.position.y, size.width, size.height, width, height)
                                    change.consume()
                                    if (change.pressed) state.sendTouch(device, last.first, last.second, true)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                state.sendTouch(device, last.first, last.second, false)
                            }
                        }
                    }.testTag(if(running) "LiveDeviceScreen" else "StoppedDeviceScreen")) {
                    if(frame != null) Image(frame, "${device.displayName} screen",
                        Modifier.align(Alignment.Center)
                            .requiredSize(
                                if (frameRotation % 2 == 0) screenWidth else screenHeight,
                                if (frameRotation % 2 == 0) screenHeight else screenWidth,
                            )
                            // Undo the stream's orientation inside the physical surface.
                            .graphicsLayer { rotationZ = frameRotation * 90f },
                        contentScale=ContentScale.FillBounds,
                        colorFilter=if(running) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
                    if(!running) {
                        Column(Modifier.align(Alignment.Center).background(JewelTheme.globalColors.panelBackground).padding(12.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                            org.jetbrains.jewel.ui.component.DefaultButton(onClick={state.launch(device)}, enabled=!starting && !state.busy && !device.missingStorage,
                                modifier=Modifier.testTag("Launch-${device.name}").semantics { contentDescription="Start ${device.displayName}" }) {
                                Icon("play")
                                Spacer(Modifier.width(6.dp))
                                Label(if(starting) "Starting…" else "Start device")
                            }
                            Spacer(Modifier.height(16.dp))
                            if(device.missingStorage) Label("Storage unavailable")
                            if(frame == null && !starting) { Spacer(Modifier.height(7.dp)); Label("No saved screen yet", color=JewelTheme.globalColors.text.info) }
                        }
                    } else if(frame == null || state.displayErrors[device.name] != null) {
                        Label(state.displayErrors[device.name] ?: "Waiting for the device screen…", Modifier.align(Alignment.Center).padding(20.dp))
                    }
                }
            }
        }
    }
}
