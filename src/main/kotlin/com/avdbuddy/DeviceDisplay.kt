package com.avdbuddy

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/** The capture is a preview; Android's Quick Boot snapshot owns the resumable device state. */
interface DeviceDisplay {
    fun load(device: Device): BufferedImage?
    fun capture(settings: AppSettings, device: Device, serial: String): BufferedImage?
    fun input(settings: AppSettings, serial: String, arguments: List<String>)
}

class AdbDeviceDisplay : DeviceDisplay {
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
}

internal fun screenPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): Pair<Int, Int> =
    ((x / viewWidth.coerceAtLeast(1) * imageWidth).toInt().coerceIn(0, imageWidth - 1)) to
        ((y / viewHeight.coerceAtLeast(1) * imageHeight).toInt().coerceIn(0, imageHeight - 1))
