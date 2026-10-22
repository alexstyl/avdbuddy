package com.avdbuddy

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.*
import java.util.Properties
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

const val APP_VERSION = "1.0.0"
val userHome: File get() = File(System.getProperty("user.home"))

data class AppSettings(val sdk: String, val avds: String)
class SettingsStore(private val file: File = File(HostPlatform().configDirectory, "settings.properties")) {
    fun load(): AppSettings {
        val p = Properties().apply { if (file.isFile) file.inputStream().use(::load) }
        fun legacy(key: String): String? = if (HostOs.detect() == HostOs.MAC) runCatching {
            val process = ProcessBuilder("/usr/bin/defaults", "read", "com.alexstyl.avdbuddy", key).start()
            val value = process.inputStream.bufferedReader().readText().trim()
            value.takeIf { process.waitFor() == 0 && it.isNotEmpty() }
        }.getOrNull() else null
        val sdk = p.getProperty("sdk") ?: legacy("android.sdk.path.override")
            ?: HostPlatform().defaultSdk.path
        val avds = p.getProperty("avds") ?: legacy("avdbuddy.avdStoragePath") ?: defaultAvdHome()
        return AppSettings(sdk, avds)
    }
    fun save(settings: AppSettings) {
        file.parentFile.mkdirs()
        val temp = File.createTempFile("settings-", ".tmp", file.parentFile)
        try {
            temp.outputStream().use { Properties().apply { setProperty("sdk", settings.sdk); setProperty("avds", settings.avds) }.store(it, "AvdBuddy") }
            Files.move(temp.toPath(), file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        } finally { temp.delete() }
    }
}
fun defaultAvdHome(): String = HostPlatform().defaultAvds.path
fun expandPath(value: String): File = File(if ((value.trim().startsWith("~/") || value.trim().startsWith("~\\"))) File(userHome, value.trim().drop(2)).path else value.trim()).absoluteFile
fun readIni(file: File): Map<String, String> = if (!file.isFile) emptyMap() else file.readLines().mapNotNull {
    val i = it.indexOf('='); if (i < 0) null else it.take(i).trim() to it.drop(i + 1).trim()
}.toMap()
fun writeIni(file: File, values: Map<String, String>) {
    file.writeText(values.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
}

data class Toolchain(val root: File) {
    private val host = HostPlatform()
    private fun tool(name: String): File {
        val dirs = File(root, "cmdline-tools").listFiles()?.filter(File::isDirectory).orEmpty()
        return (listOf(File(root, "cmdline-tools/latest/bin/$name")) + dirs.sortedByDescending { it.name }.map { File(it, "bin/$name") })
            .firstOrNull { it.canExecute() } ?: File(root, "cmdline-tools/latest/bin/$name")
    }
    val sdkmanager get() = tool(host.script("sdkmanager"))
    val avdmanager get() = tool(host.script("avdmanager"))
    val emulator get() = File(root, "emulator/${host.executable("emulator")}")
    val adb get() = File(root, "platform-tools/${host.executable("adb")}")
    val tools get() = linkedMapOf("sdkmanager" to sdkmanager, "avdmanager" to avdmanager, "emulator" to emulator, "adb" to adb)
    val ready get() = tools.values.all { it.isFile && it.canExecute() }
    fun requireReady() { require(ready) { "Choose an Android SDK with command-line tools, emulator, and platform-tools in Settings." } }
}

fun androidCommandError(output: String, exitCode: Int): String {
    if (output.contains("No space left on device", ignoreCase = true) ||
        output.contains("not enough space", ignoreCase = true)) {
        return "Not enough disk space to unpack the Android image. Free space on the drive containing your Android SDK, or choose an SDK on another drive in Settings, then retry."
    }
    val lines = output.replace('\r', '\n').lines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("[") }
    val failure = lines.lastOrNull { it.contains("error", true) || it.contains("failed", true) || it.contains("exception", true) }
    return (failure ?: lines.takeLast(3).joinToString("\n")).take(1000)
        .ifBlank { "Android command exited with $exitCode." }
}

data class Command(val executable: File, val args: List<String>, val env: Map<String, String> = emptyMap(), val input: String? = null, val detached: Boolean = false)
fun interface CommandExecutor { fun run(command: Command, cancelled: AtomicBoolean, output: (String) -> Unit): String }
class ProcessExecutor : CommandExecutor {
    override fun run(command: Command, cancelled: AtomicBoolean, output: (String) -> Unit): String {
        val builder = ProcessBuilder(listOf(command.executable.path) + command.args).redirectErrorStream(true)
        builder.environment().putAll(command.env)
        if (command.detached) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).start().outputStream.close()
            return ""
        }
        val process = builder.start()
        val text = StringBuilder()
        val reader = thread(isDaemon = true, name = "android-output") {
            process.inputStream.bufferedReader().use { stream ->
                val chars = CharArray(2048)
                while (true) {
                    val n = stream.read(chars)
                    if (n < 0) break
                    val chunk = String(chars, 0, n)
                    synchronized(text) { text.append(chunk) }
                    output(chunk)
                }
            }
        }
        try {
            process.outputStream.bufferedWriter().use { if (command.input != null) it.write(command.input) }
            while (!process.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (cancelled.get() || Thread.currentThread().isInterrupted) {
                    process.destroy()
                    if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
                    throw CancellationException("Operation cancelled.")
                }
            }
            reader.join()
            val result = synchronized(text) { text.toString() }
            check(process.exitValue() == 0) { androidCommandError(result, process.exitValue()) }
            return result
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
}

enum class DeviceType(val label: String, val profile: String) {
    PHONE("Phone", "pixel_9"), FOLDABLE("Foldable", "pixel_fold"), TABLET("Tablet", "pixel_tablet"),
    WEAR("Wear OS", "wearos_large_round"), DESKTOP("Desktop", "desktop_small"), TV("TV", "tv_1080p"),
    CAR("Automotive", "automotive_1080p_landscape"), XR("XR", "xr_headset_device");
    fun accepts(tag: String): Boolean = when (this) {
        WEAR -> tag.contains("wear")
        TV -> tag.contains("tv")
        CAR -> tag.contains("automotive")
        XR -> tag.contains("xr")
        DESKTOP -> tag.contains("desktop")
        else -> !listOf("wear", "tv", "automotive", "xr", "desktop").any(tag::contains)
    }
    companion object {
        fun from(config: Map<String, String>): DeviceType {
            val name = config["hw.device.name"].orEmpty()
            return when {
                name.contains("fold") -> FOLDABLE
                name.contains("tablet") || (config["hw.lcd.width"]?.toIntOrNull() ?: 0) >= 1600 -> TABLET
                name.startsWith("wear") -> WEAR
                name.startsWith("tv") -> TV
                name.startsWith("automotive") -> CAR
                name.startsWith("desktop") -> DESKTOP
                name.startsWith("xr") -> XR
                else -> PHONE
            }
        }
    }
}
data class Device(val name: String, val directory: File, val config: Map<String, String>, val bytes: Long) {
    val type get() = DeviceType.from(config)
    val api get() = Regex("android-([^/;\\\\]+)").find(config["image.sysdir.1"].orEmpty())?.groupValues?.get(1)
        ?: config["target"]?.removePrefix("android-") ?: "—"
    val displayName get() = config["avd.ini.displayname"]?.takeIf(String::isNotBlank) ?: name.replace('_', ' ')
    val missingStorage get() = !directory.isDirectory || directory.listFiles().orEmpty().any { Files.isSymbolicLink(it.toPath()) && !it.exists() }
}
data class SystemImage(val path: String, val description: String, val installed: Boolean) {
    val api get() = path.split(';').getOrElse(1) { "" }.removePrefix("android-")
    val tag get() = path.split(';').getOrElse(2) { "" }
    val abi get() = path.split(';').getOrElse(3) { "" }
    val services get() = when { tag.contains("playstore") || tag.contains("google_tv") -> "Google Play"; tag.contains("google") -> "Google APIs"; else -> "None" }
    val title get() = "${androidFamilyTitle(api)} · $services · ${if (abi == "arm64-v8a") "arm64" else abi}"
}
fun parseImages(text: String): List<SystemImage> {
    var installed = false
    val images = linkedMapOf<String, SystemImage>()
    text.lineSequence().forEach { line ->
        when {
            line.trim().startsWith("Installed packages:") -> installed = true
            line.trim().startsWith("Available Packages:", true) -> installed = false
            else -> {
                val parts = line.trim().split('|').map(String::trim)
                if (parts.size >= 3 && Regex("system-images;[^;]+;[^;]+;[^;\\s]+").matches(parts[0])) {
                    val image = SystemImage(parts[0], parts[2], installed)
                    if (images[image.path]?.installed != true) images[image.path] = image
                }
            }
        }
    }
    return images.values.sortedWith(compareByDescending<SystemImage> { Regex("[0-9.]+").find(it.api)?.value?.toDoubleOrNull() ?: 0.0 }.thenBy { it.path })
}
data class DeviceProfile(val id: String, val name: String)
fun parseProfiles(text: String): List<DeviceProfile> = Regex("id:.*?\\\"([^\\\"]+)\\\"\\s*\\n\\s*Name: ([^\\n]+)").findAll(text).map { DeviceProfile(it.groupValues[1], it.groupValues[2].trim()) }.toList()
data class CreateSpec(val name: String, val image: SystemImage, val profile: String, val ram: Int? = null, val storage: Int = 16, val sdCard: Int? = null, val frame: Boolean = true,
    val hardware: HardwareProfile? = null, val displayName: String = name)

class AndroidService(val settings: AppSettings, private val executor: CommandExecutor = ProcessExecutor()) {
    val toolchain = Toolchain(expandPath(settings.sdk))
    val avdRoot = expandPath(settings.avds)
    fun command(executable: File, args: List<String>, cancelled: AtomicBoolean = AtomicBoolean(), input: String? = null, detached: Boolean = false, output: (String) -> Unit = {}): String {
        return executor.run(Command(executable, args, mapOf("ANDROID_AVD_HOME" to avdRoot.path, "ANDROID_HOME" to toolchain.root.path, "ANDROID_SDK_ROOT" to toolchain.root.path), input, detached), cancelled, output)
    }
    fun devices(): List<Device> = avdRoot.listFiles().orEmpty().filter { it.extension == "ini" }.sortedBy { it.name.lowercase() }.map { ini ->
        val name = ini.nameWithoutExtension
        val directory = readIni(ini)["path"]?.let(::File) ?: File(avdRoot, "$name.avd")
        val bytes = if (directory.isDirectory) directory.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }.sumOf { it.length() } else 0L
        Device(name, directory, readIni(File(directory, "config.ini")), bytes)
    }
    fun images(cancelled: AtomicBoolean = AtomicBoolean(), output: (String) -> Unit = {}) = parseImages(command(toolchain.sdkmanager, listOf("--list"), cancelled, output = output))
    fun profiles() = parseProfiles(command(toolchain.avdmanager, listOf("list", "device")))
    fun running(): Map<String, String> {
        if (!toolchain.adb.canExecute()) return emptyMap()
        val result = linkedMapOf<String, String>()
        val serials = command(toolchain.adb, listOf("devices")).lines().filter { it.startsWith("emulator-") && it.endsWith("\tdevice") }.map { it.substringBefore('\t') }
        serials.forEach { serial ->
            val name = command(toolchain.adb, listOf("-s", serial, "emu", "avd", "name")).lines().firstOrNull { it.isNotBlank() && it != "OK" }
            if (name != null) result[name.trim()] = serial
        }
        return result
    }
    fun displayRotation(serial: String): Int {
        val dump = command(toolchain.adb, listOf("-s", serial, "shell", "dumpsys", "input"))
        return inputDisplayRotation(dump)
            ?: error("Could not read the device orientation. Try rotating again once Android has started.")
    }
    fun launch(device: Device) {
        toolchain.requireReady(); requireAvdStorage()
        require(!device.missingStorage) { "${device.displayName} has unavailable storage. Connect its drive before launching." }
        val grpcPort = java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
        val args = mutableListOf("-avd", device.name, "-no-window", "-gpu", "host", "-grpc", grpcPort.toString(), "-grpc-use-token")
        val skin = device.config["skin.path"]?.let(::File)
        if (device.config["showDeviceFrame"] != "no" && skin?.isDirectory == true) args += listOf("-skindir", skin.parent, "-skin", skin.name)
        command(toolchain.emulator, args, detached = true)
    }
    fun stop(serial: String) { command(toolchain.adb, listOf("-s", serial, "emu", "kill")) }
    fun restart(serial: String) { command(toolchain.adb, listOf("-s", serial, "reboot")) }
    fun requireAvdStorage() { require(avdRoot.isDirectory) { "Emulator storage is unavailable at ${avdRoot.path}. Connect its drive or choose another folder in Settings." } }
    fun validateName(name: String) {
        require(Regex("[A-Za-z0-9._-]+").matches(name)) { "Use letters, numbers, dots, underscores, or dashes in the name." }
        require(!File(avdRoot, "$name.ini").exists() && !Files.exists(File(avdRoot, "$name.avd").toPath(), NOFOLLOW_LINKS)) { "A device named $name already exists." }
    }
    private fun resolveCreationName(spec: CreateSpec): CreateSpec {
        val issue = creationNameError(spec.displayName, devices().map(Device::displayName))
        require(issue == null) { issue.orEmpty() }
        // Unregistered directories and broken links also occupy storage names.
        val occupied = avdRoot.listFiles().orEmpty().filter { it.extension in listOf("ini", "avd") }.map(File::nameWithoutExtension)
        return spec.copy(name = availableAvdName(spec.displayName, occupied))
    }

    fun create(request: CreateSpec, cancelled: AtomicBoolean, output: (String) -> Unit): CreateSpec {
        var spec = request
        toolchain.requireReady()
        if (spec.hardware == null) validateName(spec.name)
        else require(creationNameError(spec.displayName) == null) { "Enter a valid device name." }
        spec.hardware?.let { hardware ->
            require(spec.profile == hardware.id && spec.ram == hardware.ramMiB && spec.storage == hardware.storageGiB && spec.sdCard == null && spec.frame) { "Built-in device settings are fixed by the profile." }
            require(spec.image in googlePlayImages(listOf(spec.image))) { "This device needs a host-native Google Play image." }
        }
        if (avdRoot.path == defaultAvdHome() && !avdRoot.exists() && avdRoot.toPath().startsWith(userHome.toPath())) avdRoot.mkdirs()
        requireAvdStorage()
        val images = File(toolchain.root, "system-images")
        require(!Files.isSymbolicLink(images.toPath()) || images.isDirectory) { "System image storage points to an unavailable drive. Connect it or select an SDK with local storage in Settings." }
        if (!images.exists()) check(images.mkdir()) { "Could not create system image storage at ${images.path}." }
        require(images.isDirectory && images.canWrite()) { "System image storage is not writable: ${images.path}" }
        if (!spec.image.installed) {
            output("Installing ${spec.image.description}\n")
            command(toolchain.sdkmanager, listOf("--install", spec.image.path), cancelled, input = "y\n".repeat(32), output = output)
        } else output("Using installed Android image. No download needed.\n")
        if (cancelled.get()) throw CancellationException("Creation cancelled.")
        // Resolve immediately before creation: the UI's device list can be stale,
        // and a long image download may outlive changes made by another SDK tool.
        if (spec.hardware != null) spec = resolveCreationName(spec)
        validateName(spec.name)
        val args = mutableListOf("create", "avd", "-n", spec.name, "-k", spec.image.path, "-p", File(avdRoot, "${spec.name}.avd").path)
        // Bundled profiles are self-contained and need no matching SDK device definition.
        if (spec.hardware == null) args += listOf("-d", spec.profile)
        spec.sdCard?.let { args += listOf("-c", "${it * 1024}M") }
        command(toolchain.avdmanager, args, cancelled, input = "no\n", output = output)
        val iniFile = File(avdRoot, "${spec.name}.ini")
        val dir = readIni(iniFile)["path"]?.let(::File) ?: File(avdRoot, "${spec.name}.avd")
        val config = readIni(File(dir, "config.ini")).toMutableMap()
        config["avd.ini.displayname"] = spec.displayName
        config["disk.dataPartition.size"] = "${spec.storage}G"
        config["showDeviceFrame"] = if (spec.frame) "yes" else "no"
        config["hw.gpu.enabled"] = "yes"
        config["hw.gpu.mode"] = "host"
        spec.ram?.let { config["hw.ramSize"] = it.toString() }
        val skin = File(toolchain.root, "skins/${spec.profile}")
        if (spec.frame && skin.isDirectory) { config["skin.name"] = spec.profile; config["skin.path"] = skin.path; config["skin.dynamic"] = "yes" }
        spec.hardware?.let { profile ->
            val bundledSkin = profile.installFrame(dir)
            config.putAll(mapOf(
                "avdbuddy.profile" to profile.id,
                "hw.device.name" to profile.id.replace('-', '_'),
                "hw.device.manufacturer" to "Google",
                "hw.lcd.width" to profile.display.width.toString(),
                "hw.lcd.height" to profile.display.height.toString(),
                "hw.lcd.density" to profile.display.density.toString(),
                "hw.initialOrientation" to "portrait",
                "hw.keyboard" to "yes",
                "hw.mainKeys" to "no",
                "hw.dPad" to "no",
                "hw.trackBall" to "no",
                "hw.gps" to "yes",
                "hw.accelerometer" to "yes",
                "hw.sensors.orientation" to "yes",
                "hw.camera.back" to "virtualscene",
                "hw.camera.front" to "emulated",
                "PlayStore.enabled" to "true",
                "skin.name" to bundledSkin.name,
                "skin.path" to bundledSkin.path,
                "skin.dynamic" to "yes",
            ))
            // Do not inherit a generic profile's identity hash.
            config.remove("hw.device.hash2")
        }
        if (spec.profile.startsWith("tv_") || spec.profile.contains("landscape")) config["hw.initialOrientation"] = "landscape"
        writeIni(File(dir, "config.ini"), config)
        return spec
    }
    fun delete(device: Device) { toolchain.requireReady(); requireAvdStorage(); command(toolchain.avdmanager, listOf("delete", "avd", "-n", device.name)) }
    fun duplicate(device: Device): String {
        requireAvdStorage(); require(!device.missingStorage) { "Connect the device's drive before duplicating." }
        var name = device.name + "_Copy"; var n = 2
        while (File(avdRoot, "$name.ini").exists() || File(avdRoot, "$name.avd").exists()) name = device.name + "_Copy_${n++}"
        validateName(name)
        val destination = File(avdRoot, "$name.avd")
        try {
            device.directory.walkTopDown().forEach { file ->
                require(!Files.isSymbolicLink(file.toPath())) { "This device contains linked storage. Copy it to a regular folder before duplicating." }
                val relative = file.relativeTo(device.directory).path
                if (file.isDirectory) File(destination, relative).mkdirs() else Files.copy(file.toPath(), File(destination, relative).toPath())
            }
            normalize(destination, device.directory.path, name)
            val config = File(destination, "config.ini")
            writeIni(config, readIni(config) + mapOf("hw.gpu.enabled" to "yes", "hw.gpu.mode" to "host"))
            writeIni(File(avdRoot, "$name.ini"), readIni(File(avdRoot, "${device.name}.ini")).minus("path.rel") + ("path" to destination.path))
            return name
        } catch (e: Exception) { destination.deleteRecursively(); File(avdRoot, "$name.ini").delete(); throw e }
    }
    fun rename(device: Device, name: String) {
        requireAvdStorage(); validateName(name)
        val destination = File(avdRoot, "$name.avd")
        val oldIni = File(avdRoot, "${device.name}.ini")
        val newIni = File(avdRoot, "$name.ini")
        val original = oldIni.readText()
        Files.move(device.directory.toPath(), destination.toPath())
        try {
            normalize(destination, device.directory.path, name)
            writeIni(newIni, readIni(oldIni).minus("path.rel") + ("path" to destination.path))
            check(oldIni.delete()) { "Could not remove the old device definition." }
        } catch (e: Exception) {
            if (destination.exists()) { normalize(destination, destination.path, device.name, device.directory.path); Files.move(destination.toPath(), device.directory.toPath()) }
            oldIni.writeText(original); newIni.delete(); throw e
        }
    }
    private fun normalize(directory: File, oldPath: String, name: String, targetPath: String = directory.path) {
        directory.walkTopDown().filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && it.extension in listOf("ini", "txt", "conf") }.forEach {
            it.writeText(it.readText().replace(oldPath, targetPath))
        }
        val config = File(directory, "config.ini")
        writeIni(config, readIni(config) + ("avd.ini.displayname" to name) + ("AvdId" to name))
        listOf("hardware-qemu.ini", "multiinstance.lock", "read-snapshot.txt", "emu-launch-params.txt", "tmpAdbCmds").forEach { File(directory, it).deleteRecursively() }
    }
    fun diagnostics() = buildString {
        appendLine("AvdBuddy $APP_VERSION · Kotlin ${KotlinVersion.CURRENT}")
        appendLine("${System.getProperty("os.name")} ${System.getProperty("os.version")} · ${System.getProperty("os.arch")}")
        appendLine("SDK: ${toolchain.root}\nAVDs: $avdRoot")
        toolchain.tools.forEach { (name, file) -> appendLine("$name: ${if(file.canExecute()) "available" else "missing"} ($file)") }
        appendLine("System images: ${File(toolchain.root, "system-images").canonicalPath}")
        appendLine("Devices: ${devices().joinToString { it.name }}")
    }
}

internal fun inputDisplayRotation(dump: String): Int? =
    Regex("Viewport INTERNAL: displayId=0,.*?orientation=([0-3])(?:,|\\s)")
        .find(dump)?.groupValues?.get(1)?.toInt()
        ?: Regex("SurfaceOrientation: ([0-3])").find(dump)?.groupValues?.get(1)?.toInt()
fun formatSize(bytes: Long): String = when { bytes > 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0); bytes > 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0); else -> "${bytes / 1000} KB" }
