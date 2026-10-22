package com.avdbuddy

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

data class EmulatorFrame(val bitmap: ImageBitmap, val rotation: Int)

/** The capture is a preview; Android's Quick Boot snapshot owns the resumable device state. */
interface DeviceDisplay {
    fun load(device: Device): BufferedImage?
    fun capture(settings: AppSettings, device: Device, serial: String): BufferedImage?
    fun stream(settings: AppSettings, device: Device, serial: String): Flow<EmulatorFrame>
    fun touch(serial: String, x: Int, y: Int, pressed: Boolean)
    fun input(settings: AppSettings, serial: String, arguments: List<String>)
    fun rotate(serial: String, rotation: Int)
}

class AdbDeviceDisplay : DeviceDisplay {
    private val live = EmulatorScreenStream()
    override fun rotate(serial: String, rotation: Int) = live.rotate(serial, rotation)
    override fun touch(serial: String, x: Int, y: Int, pressed: Boolean) = live.touch(serial, x, y, pressed)

    override fun stream(settings: AppSettings, device: Device, serial: String): Flow<EmulatorFrame> = flow {
        var last: ImageBitmap? = null
        try {
            live.frames(device, serial).collect { last = it.bitmap; emit(it) }
        } finally {
            // Save once when leaving the live view, never on the per-frame path.
            withContext(NonCancellable + Dispatchers.IO) {
                last?.let { frame -> runCatching { savePreview(device, frame.toAwtImage()) } }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun load(device: Device): BufferedImage? = runCatching {
        val saved = File(device.directory, ".avdbuddy-preview.png")
        val quickBoot = File(device.directory, "snapshots/default_boot/screenshot.png")
        (saved.takeIf { it.isFile } ?: quickBoot.takeIf { it.isFile })?.let { ImageIO.read(it) }
    }.getOrNull()

    override fun capture(settings: AppSettings, device: Device, serial: String): BufferedImage? {
        if (device.missingStorage) return null
        val temp = File.createTempFile(".avdbuddy-capture-", ".png", device.directory)
        return try {
            val process = ProcessBuilder(settings.sdk + "/platform-tools/adb", "-s", serial, "exec-out", "screencap", "-p")
                .redirectOutput(temp).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) return null
                if (process.exitValue() != 0 || temp.length() == 0L || temp.length() > 32 * 1024 * 1024) return null
                val image = ImageIO.read(temp) ?: return null
                val destination = File(device.directory, ".avdbuddy-preview.png").toPath()
                if (!Files.isRegularFile(destination) || Files.mismatch(temp.toPath(), destination) != -1L)
                    Files.move(temp.toPath(), destination, REPLACE_EXISTING, ATOMIC_MOVE)
                image
            } finally { if (process.isAlive) process.destroyForcibly() }
        } finally { temp.delete() }
    }

    override fun input(settings: AppSettings, serial: String, arguments: List<String>) {
        val process = ProcessBuilder(listOf(settings.sdk + "/platform-tools/adb", "-s", serial, "shell", "input") + arguments)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try { check(process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) { "Could not send input to the device." } }
        finally { if (process.isAlive) process.destroyForcibly() }
    }

    private fun savePreview(device: Device, image: BufferedImage) {
        if (device.missingStorage) return
        val temp = File.createTempFile(".avdbuddy-capture-", ".png", device.directory)
        try {
            check(ImageIO.write(image, "png", temp))
            Files.move(temp.toPath(), File(device.directory, ".avdbuddy-preview.png").toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        } finally { temp.delete() }
    }
}

internal fun screenPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): Pair<Int, Int> =
    ((x / viewWidth.coerceAtLeast(1) * imageWidth).toInt().coerceIn(0, imageWidth - 1)) to
        ((y / viewHeight.coerceAtLeast(1) * imageHeight).toInt().coerceIn(0, imageHeight - 1))

internal fun nativeScreenSize(imageWidth: Int, imageHeight: Int, rotation: Int): Pair<Int, Int> =
    if (rotation % 2 == 0) imageWidth to imageHeight else imageHeight to imageWidth
