package com.avdbuddy

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.composeunstyled.UnstyledButton

@Composable fun EmulatorPanel(device: Device, state: AppState) {
    val running = device.name in state.running
    val starting = device.name in state.starting && !running
    val frame = state.frames[device.directory.absolutePath]
    val focus = remember(device.name) { FocusRequester() }
    val landscape = device.type in listOf(DeviceType.TV, DeviceType.CAR, DeviceType.DESKTOP)
    val width = frame?.width ?: device.config["hw.lcd.width"]?.toIntOrNull() ?: if(landscape) 1920 else if(device.type == DeviceType.TABLET) 1600 else 1080
    val height = frame?.height ?: device.config["hw.lcd.height"]?.toIntOrNull() ?: if(landscape) 1080 else if(device.type == DeviceType.TABLET) 2560 else if(device.type == DeviceType.WEAR) 1080 else 2400
    val ratio = width.toFloat() / height.coerceAtLeast(1)
    Column(Modifier.fillMaxSize().testTag("EmulatorPanel")) {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp, vertical=18.dp), verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(device.displayName, size=17, weight=FontWeight.SemiBold, modifier=Modifier.testTag("PreviewDeviceName"))
                Spacer(Modifier.height(3.dp))
                Label(androidFamilyTitle(device.api), color=Palette.muted, size=12)
            }
            if(running) ActionButton("Stop", enabled=!state.busy, tag="Stop-${device.name}") { state.stop(device) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=30.dp, vertical=10.dp), contentAlignment=Alignment.Center) {
            val bezel = 9.dp
            val screenWidth = minOf(maxWidth - bezel*2, (maxHeight-bezel*2)*ratio).coerceAtLeast(1.dp)
            val screenHeight = screenWidth/ratio
            val outerShape = RoundedCornerShape(if(device.type == DeviceType.WEAR) 100.dp else 34.dp)
            val innerShape = RoundedCornerShape(if(device.type == DeviceType.WEAR) 100.dp else 26.dp)
            Box(Modifier.size(screenWidth+bezel*2,screenHeight+bezel*2)
                .background(Color(0xFF101011), outerShape).border(2.dp, Color(0xFF666366), outerShape)
                .padding(bezel).testTag("EmulatorBezel")) {
                Box(Modifier.fillMaxSize().clip(innerShape).background(Color(0xFF202022))
                    .focusRequester(focus).onPreviewKeyEvent { event ->
                        if (!running || event.type != KeyEventType.KeyDown) false else {
                            val code = when(event.key) { Key.Enter -> "66"; Key.Backspace -> "67"; Key.Escape -> "4"; Key.DirectionLeft -> "21"; Key.DirectionRight -> "22"; Key.DirectionUp -> "19"; Key.DirectionDown -> "20"; else -> null }
                            if(code != null) { state.sendInput(device, listOf("keyevent",code)); true }
                            else {
                                val char = event.utf16CodePoint.toChar()
                                if(!event.isMetaPressed && !event.isCtrlPressed && (char.isLetterOrDigit() && char.code < 128 || char == ' ')) {
                                    state.sendInput(device, listOf("text",if(char==' ') "%s" else char.toString())); true
                                } else false
                            }
                        }
                    }.focusable().pointerInput(device.name, running, width, height) {
                        if(running) awaitEachGesture {
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
                    if(frame != null) Image(frame, "${device.displayName} screen", Modifier.fillMaxSize(), contentScale=ContentScale.FillBounds,
                        colorFilter=if(running) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
                    if(!running) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.30f)))
                        Column(Modifier.align(Alignment.Center), horizontalAlignment=Alignment.CenterHorizontally) {
                            UnstyledButton(onClick={state.launch(device)}, enabled=!starting && !state.busy && !device.missingStorage,
                                modifier=Modifier.size(86.dp).background(Color(0xEDE9E3DE),RoundedCornerShape(50)).testTag("Launch-${device.name}")
                                    .semantics { contentDescription="Start ${device.displayName}" }) {
                                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Icon("play",Color(0xFF302B2C),Modifier.size(38.dp)) }
                            }
                            Spacer(Modifier.height(16.dp))
                            Label(if(starting) "Starting…" else if(device.missingStorage) "Storage unavailable" else "Start device", weight=FontWeight.Medium)
                            if(frame == null && !starting) { Spacer(Modifier.height(7.dp)); Label("No saved screen yet", size=11, color=Palette.muted) }
                        }
                    } else if(frame == null || state.displayErrors[device.name] != null) {
                        Label(state.displayErrors[device.name] ?: "Waiting for the device screen…", Modifier.align(Alignment.Center).padding(20.dp), size=12)
                    }
                }
            }
        }
    }
}
