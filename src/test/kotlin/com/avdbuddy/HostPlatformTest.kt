package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class HostPlatformTest {
    @TempDir lateinit var root: File
    private fun host(os: HostOs, env: Map<String, String> = emptyMap()) = HostPlatform(os, root, env, "1234", BuildIdentity("stable", "1.0.0"))

    @Test fun `defaults follow each desktop platform`() {
        val mac = host(HostOs.MAC)
        assertEquals(File(root, "Library/Application Support/AvdBuddy"), mac.configDirectory)
        assertEquals(File(root, "Library/Android/sdk"), mac.defaultSdk)
        val windows = host(HostOs.WINDOWS)
        assertEquals(File(root, "AppData/Roaming/AvdBuddy"), windows.configDirectory)
        assertEquals(File(root, "AppData/Local/Android/Sdk"), windows.defaultSdk)
        val linux = host(HostOs.LINUX)
        assertEquals(File(root, ".config/AvdBuddy"), linux.configDirectory)
        assertEquals(File(root, "Android/Sdk"), linux.defaultSdk)
        for (os in HostOs.entries) assertEquals(File(root, ".android/avd"), host(os).defaultAvds)
    }

    @Test fun `environment overrides ignore empty values and preserve spaces and unicode`() {
        val custom = File(root, "Custom SDK & devices 日本語")
        for (os in HostOs.entries) {
            val platform = host(os, mapOf("ANDROID_HOME" to custom.path, "ANDROID_SDK_ROOT" to "old-sdk",
                "ANDROID_AVD_HOME" to custom.path))
            assertEquals(custom, platform.defaultSdk)
            assertEquals(custom, platform.defaultAvds)
            assertEquals(File(custom, "avd"), host(os, mapOf("ANDROID_USER_HOME" to custom.path)).defaultAvds)
            assertEquals(File(custom, "avd"), host(os, mapOf("ANDROID_EMULATOR_HOME" to custom.path,
                "ANDROID_USER_HOME" to "unused")).defaultAvds)
            assertEquals(host(os).defaultSdk, host(os, mapOf("ANDROID_HOME" to "", "ANDROID_SDK_ROOT" to " ")).defaultSdk)
        }
        assertEquals(File(custom, "AvdBuddy"), host(HostOs.WINDOWS, mapOf("APPDATA" to custom.path)).configDirectory)
        assertEquals(File(custom, "Android/Sdk"), host(HostOs.WINDOWS, mapOf("LOCALAPPDATA" to custom.path)).defaultSdk)
        assertEquals(File(custom, "AvdBuddy"), host(HostOs.LINUX, mapOf("XDG_CONFIG_HOME" to custom.path)).configDirectory)
        assertEquals(File(root, ".config/AvdBuddy"), host(HostOs.LINUX, mapOf("XDG_CONFIG_HOME" to "relative")).configDirectory)
    }

    @Test fun `executable names match SDK archives`() {
        for (os in HostOs.entries) {
            val platform = host(os)
            assertEquals(if (os == HostOs.WINDOWS) "adb.exe" else "adb", platform.executable("adb"))
            assertEquals(if (os == HostOs.WINDOWS) "sdkmanager.bat" else "sdkmanager", platform.script("sdkmanager"))
        }
        val sdk = File(root, "Android SDK 日本語")
        val tools = Toolchain(sdk)
        assertFalse(tools.ready)
        tools.tools.values.forEach { it.parentFile.mkdirs(); it.writeText(""); it.setExecutable(true) }
        assertTrue(tools.ready)
        val versioned = File(sdk, "cmdline-tools/19.0")
        assertTrue(File(sdk, "cmdline-tools/latest").renameTo(versioned))
        assertEquals(File(versioned, "bin/${HostPlatform().script("sdkmanager")}"), tools.sdkmanager)
        assertTrue(tools.ready)
        tools.adb.delete()
        assertFalse(tools.ready)
    }

    @Test fun `discovery follows emulator runtime directory and Android overrides`() {
        val custom = File(root, "runtime")
        assertEquals(File(root, "Library/Caches/TemporaryItems/avd/running"), host(HostOs.MAC).discoveryDirectories.first())
        assertEquals(File(custom, "Temp/avd/running"), host(HostOs.WINDOWS, mapOf("LOCALAPPDATA" to custom.path)).discoveryDirectories.first())
        assertEquals(File(root, ".android/avd/running"), host(HostOs.WINDOWS).discoveryDirectories.single())
        assertEquals(listOf(File(custom, "avd/running"), File("/run/user/1234/avd/running"), File(root, ".android/avd/running")),
            host(HostOs.LINUX, mapOf("XDG_RUNTIME_DIR" to custom.path)).discoveryDirectories)
        assertEquals(File(custom, "avd/running"), host(HostOs.LINUX, mapOf("ANDROID_EMULATOR_HOME" to custom.path)).discoveryDirectories.last())
    }

    @Test fun `INI and preferences retain native paths including backslashes`() {
        val settings = AppSettings(File(root, "SDK 日本語 & spaces").path, File(root, "AVD folder").path)
        val store = SettingsStore(File(root, "config/settings.properties"))
        store.save(settings)
        assertEquals(settings, store.load())
        store.save(settings.copy(sdk = "D:\\Android SDK\\sdk"))
        assertEquals("D:\\Android SDK\\sdk", store.load().sdk)
        val ini = File(root, "device.ini")
        writeIni(ini, mapOf("path" to settings.avds, "other" to "C:\\Users\\Test\\Pixel.avd"))
        assertEquals(settings.avds, readIni(ini)["path"])
        assertEquals("C:\\Users\\Test\\Pixel.avd", readIni(ini)["other"])
        val device = Device("Pixel", root, mapOf("image.sysdir.1" to "system-images\\android-36\\google_apis\\x86_64\\"), 0)
        assertEquals("36", device.api)
    }
    @Test fun `storage identifiers avoid Windows reserved names on every host`() {
        for (name in listOf("CON", "nul", "Aux.foo", "COM1", "LPT9")) {
            assertNotNull(deviceNameError(name, emptyList()))
            assertEquals("_$name", availableAvdName(name, emptyList()))
        }
        assertEquals("Console", availableAvdName("Console", emptyList()))
        assertNull(deviceNameError("Pixel_10", emptyList()))
    }

    @Test fun `home expansion accepts both desktop separator conventions`() {
        assertEquals(userHome.absoluteFile, expandPath("~"))
        assertEquals(File(userHome, "Android SDK").absoluteFile, expandPath("~/Android SDK"))
        assertEquals(File(userHome, "Android SDK").absoluteFile, expandPath("~\\Android SDK"))
    }

    @Test fun `nightly preferences stay separate while Android locations are shared`() {
        for (os in HostOs.entries) {
            val stable = HostPlatform(os, root, emptyMap(), "1234", BuildIdentity("stable", "1.0.0"))
            val nightly = HostPlatform(os, root, emptyMap(), "1234", BuildIdentity("nightly", "1.0.0-nightly.test"))
            assertNotEquals(stable.configDirectory, nightly.configDirectory)
            assertEquals(stable.configDirectory.parentFile, nightly.configDirectory.parentFile)
            assertEquals(stable.defaultSdk, nightly.defaultSdk)
            assertEquals(stable.defaultAvds, nightly.defaultAvds)
        }
    }

}
