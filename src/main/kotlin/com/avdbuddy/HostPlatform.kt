package com.avdbuddy

import java.awt.Desktop
import java.io.File
import java.nio.file.Files

internal enum class HostOs { MAC, WINDOWS, LINUX;
    companion object {
        fun detect(name: String = System.getProperty("os.name")): HostOs = when {
            name.startsWith("Mac", true) -> MAC
            name.startsWith("Windows", true) -> WINDOWS
            else -> LINUX
        }
    }
}

/** Injectable host inputs keep path rules testable without changing global properties. */
internal class HostPlatform(
    val os: HostOs = HostOs.detect(),
    val home: File = userHome,
    private val environment: Map<String, String> = System.getenv(),
    private val linuxUid: String? = if (os == HostOs.LINUX) runCatching {
        Files.getAttribute(File("/proc/self").toPath(), "unix:uid").toString()
    }.getOrNull() else null,
) {
    fun env(name: String): String? = environment[name]?.takeIf(String::isNotBlank)
    private val settingsName = APP_NAME
    val configDirectory: File get() = when (os) {
        HostOs.MAC -> File(home, "Library/Application Support/$settingsName")
        HostOs.WINDOWS -> File(env("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming"), settingsName)
        HostOs.LINUX -> File(env("XDG_CONFIG_HOME")?.let(::File)?.takeIf(File::isAbsolute) ?: File(home, ".config"), settingsName)
    }
    val defaultSdk: File get() = (env("ANDROID_HOME") ?: env("ANDROID_SDK_ROOT"))?.let(::File) ?: when (os) {
        HostOs.MAC -> File(home, "Library/Android/sdk")
        HostOs.WINDOWS -> File(env("LOCALAPPDATA")?.let(::File) ?: File(home, "AppData/Local"), "Android/Sdk")
        HostOs.LINUX -> File(home, "Android/Sdk")
    }
    val androidUserHome: File get() = env("ANDROID_USER_HOME")?.let(::File) ?: File(home, ".android")
    val emulatorHome: File get() = env("ANDROID_EMULATOR_HOME")?.let(::File) ?: androidUserHome
    val defaultAvds: File get() = env("ANDROID_AVD_HOME")?.let(::File) ?: File(emulatorHome, "avd")
    fun executable(name: String): String = name + if (os == HostOs.WINDOWS) ".exe" else ""
    fun script(name: String): String = name + if (os == HostOs.WINDOWS) ".bat" else ""
    val fileManagerName: String get() = when (os) {
        HostOs.MAC -> "Finder"
        HostOs.WINDOWS -> "File Explorer"
        HostOs.LINUX -> "File Manager"
    }
    val shortcutModifier: String get() = if (os == HostOs.MAC) "⌘" else "Ctrl+"

    // Match Android Emulator's ConfigDirs.cpp, including Linux's /run/user/<uid> fallback.
    val discoveryDirectories: List<File> get() = buildList {
        when (os) {
            HostOs.MAC -> add(File(home, "Library/Caches/TemporaryItems/avd/running"))
            HostOs.WINDOWS -> env("LOCALAPPDATA")?.let { add(File(it, "Temp/avd/running")) }
            HostOs.LINUX -> {
                env("XDG_RUNTIME_DIR")?.let { add(File(it, "avd/running")) }
                linuxUid?.let { add(File("/run/user/$it/avd/running")) }
            }
        }
        add(File(emulatorHome, "avd/running"))
    }.distinct()
}

internal fun revealInFileManager(file: File) {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            desktop.browseFileDirectory(file)
            return
        }
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(if (file.isDirectory) file else file.parentFile)
            return
        }
    }
    // AWT does not implement folder opening on every Linux desktop.
    check(HostOs.detect() == HostOs.LINUX) { "No file manager is available on this system." }
    ProcessBuilder("xdg-open", (if (file.isDirectory) file else file.parentFile).absolutePath)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
        .start().outputStream.close()
}
