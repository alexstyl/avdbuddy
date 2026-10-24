package com.avdbuddy

import com.avdbuddy.iconography.icon
import com.avdbuddy.iconography.Icons
import com.avdbuddy.iconography.ArrowPath

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalFoundationApi::class)
@Composable fun EmulatorPanel(device: Device, state: AppState) {
    val running = device.name in state.running
    val starting = device.name in state.starting && !running
    val pending = state.pendingCreation?.name == device.name
    if (!running && !starting && !pending) {
        Column(
            Modifier.fillMaxSize().padding(30.dp).testTag("EmulatorPanel"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(device.type.icon, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(20.dp))
            Label(device.displayName,
                modifier = Modifier.testTag("PreviewDeviceName"))
            Spacer(Modifier.height(24.dp))
            ActionButton(if (starting) "Starting…" else "Start", primary = true,
                enabled = !starting && !state.busy && !device.missingStorage,
                tag = "Launch-${device.name}") { state.launch(device) }
            val unavailableReason = when {
                device.missingStorage -> "Storage unavailable"
                state.busy && !starting -> "Another device operation is in progress."
                else -> null
            }
            if (unavailableReason != null) {
                Spacer(Modifier.height(10.dp))
                Label(unavailableReason, color = JewelTheme.globalColors.text.info)
            }
        }
        return
    }
    val liveFrame = if (pending) null else state.frames[device.directory.absolutePath]
    val displayError = state.displayErrors[device.name]
    if (pending || starting || liveFrame == null || displayError != null) {
        Column(
            Modifier.fillMaxSize().padding(30.dp).testTag("EmulatorPanel"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Label(device.displayName, modifier = Modifier.testTag("PreviewDeviceName"))
            when {
                pending && state.createError.isNotBlank() -> {
                    Label(state.createError, color = JewelTheme.globalColors.text.error, modifier = Modifier.testTag("CreateError"))
                    ActionButton("Try again", primary = true, tag = "RetrySetup") { state.retryCreation() }
                    ActionButton("Remove", tag = "RemoveSetup") { state.discardPendingCreation() }
                }
                !pending && displayError != null -> Label(displayError, color = JewelTheme.globalColors.text.error)
                else -> {
                    org.jetbrains.jewel.ui.component.CircularProgressIndicator(Modifier.size(30.dp))
                    Label(if (pending) if (state.createCancelling) "Cancelling…" else "Setting up…" else "Starting…",
                        modifier = Modifier.testTag("DeviceSetupStatus"))
                    if (pending) ActionButton("Cancel", enabled = !state.createCancelling, tag = "CancelSetup") { state.cancelCreate() }
                }
            }
        }
        return
    }
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
    val hardware = remember(device.config, width, height) {
        HardwareProfiles.all.firstOrNull { profile ->
            device.config["showDeviceFrame"] != "no" &&
                (device.config["avdbuddy.profile"] == profile.id || device.config["hw.device.name"] == profile.id.replace('-', '_')) &&
                width == profile.display.width && height == profile.display.height
        }
    }
    val artwork = remember(hardware) { hardware?.let { profile ->
        profileResource("${profile.frame.directory}/back.webp").use {
            org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
        }
    } }
    val frameTint = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Column(Modifier.fillMaxSize().testTag("EmulatorPanel")) {
        Column(Modifier.fillMaxWidth().padding(start=24.dp, end=24.dp, bottom=12.dp)) {
            Text(device.displayName, modifier=Modifier.testTag("PreviewDeviceName"),
                style=JewelTheme.typography.h4TextStyle, maxLines=1, overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Label(androidFamilyTitle(device.api), color=JewelTheme.globalColors.text.info)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=30.dp, vertical=10.dp), contentAlignment=Alignment.Center) {
            val bezel = 9.dp
            // Keep one physical surface; its scale and touch coordinates never
            // change when it turns. The parent transform maps pointer positions.
            val naturalRatio = width.toFloat() / height.coerceAtLeast(1)
            val shell = hardware?.frame
            val shellWidth = if (shell == null) null else minOf(maxWidth, maxHeight * shell.width / shell.height)
            val screenWidth = if (shell != null && shellWidth != null) shellWidth * width / shell.width else minOf(maxWidth - bezel*2, (maxHeight-bezel*2)*naturalRatio).coerceAtLeast(1.dp)
            val screenHeight = screenWidth / naturalRatio
            val outerShape = RoundedCornerShape(if(device.type == DeviceType.WEAR) 100.dp else 34.dp)
            val innerShape = RoundedCornerShape(if (shell != null) screenWidth * shell.cornerRadius / width else if(device.type == DeviceType.WEAR) 100.dp else 26.dp)
            val surface = if (shell != null && shellWidth != null) Modifier.requiredSize(shellWidth, shellWidth * shell.height / shell.width)
                else Modifier.requiredSize(screenWidth+bezel*2,screenHeight+bezel*2)
            Box(surface
                .graphicsLayer { rotationZ = angle }
                .then(if (shell == null) Modifier.background(Color(0xFF101011), outerShape).border(2.dp, Color(0xFF666366), outerShape).padding(bezel) else Modifier)
                .clipToBounds().testTag("EmulatorBezel")) {
                val screen = if (shell != null && shellWidth != null) Modifier.offset(shellWidth * shell.screenX / shell.width, shellWidth * shell.screenY / shell.width).size(screenWidth, screenHeight) else Modifier.fillMaxSize()
                Box(screen.clip(innerShape).background(Color(0xFF202022))
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
                    }.testTag("LiveDeviceScreen")) {
                    Image(frame, "${device.displayName} screen",
                        Modifier.align(Alignment.Center)
                            .requiredSize(
                                if (frameRotation % 2 == 0) screenWidth else screenHeight,
                                if (frameRotation % 2 == 0) screenHeight else screenWidth,
                            )
                            // Undo the stream's orientation inside the physical surface.
                            .graphicsLayer { rotationZ = frameRotation * 90f },
                        contentScale=ContentScale.FillBounds)

                }
                // One continuous foreground contains the bezel, rounded aperture and camera.
                // Paint it over the screen so screen pixels can never cut into the frame.
                artwork?.let {
                    Image(it, "${hardware?.name} frame", Modifier.matchParentSize(),
                        contentScale = ContentScale.FillBounds, colorFilter = frameTint)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
            org.jetbrains.jewel.ui.component.Tooltip(tooltip = { Text("Rotate clockwise") }) {
                org.jetbrains.jewel.ui.component.OutlinedButton(
                    onClick = { state.rotateCurrent(clockwise = true) },
                    modifier = Modifier.testTag("RotateClockwise").semantics { contentDescription = "Rotate clockwise" },
                ) {
                    Icon(Icons.ArrowPath)
                }
            }
        }
    }
}
