package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

/** CI integration smoke checks: real native SDK tools, without booting any device or UI. */
@EnabledIfEnvironmentVariable(named = "AVDBUDDY_TEST_INSTALLED_SDK", matches = "true")
@Timeout(90)
class InstalledSdkTest {
    @Test fun `installed host SDK tools execute through production process handling`() {
        val tools = Toolchain(HostPlatform().defaultSdk)
        assertTrue(tools.ready, "Incomplete SDK: ${tools.tools}")
        fun run(tool: java.io.File, vararg args: String) = ProcessExecutor().run(
            Command(tool, args.toList()), AtomicBoolean()) {}
        assertContains(run(tools.adb, "version"), "Android Debug Bridge")
        assertContains(run(tools.emulator, "-version"), "Android emulator version")
        assertTrue(run(tools.sdkmanager, "--version").any(Char::isDigit))
        assertTrue(parseProfiles(run(tools.avdmanager, "list", "device")).isNotEmpty())
    }
}
