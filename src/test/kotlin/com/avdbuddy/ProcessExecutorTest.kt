package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import kotlin.test.*

@Timeout(20)
class ProcessExecutorTest {
    @TempDir lateinit var root: File

    @Test fun `native process preserves arguments environment stdin and output`() {
        val arguments = listOf("SDK with spaces", "日本語", "system-images;android-36;google_apis_playstore;x86_64", "a&b", "a=b")
        val command = fixtureCommand("echo", *arguments.toTypedArray()).copy(
            env = mapOf("AVDBUDDY_TEST_VALUE" to "custom value"), input = "no\n")
        val output = ProcessExecutor().run(command, AtomicBoolean()) {}
        assertEquals(arguments + listOf("custom value", "no"), output.lines().filter(String::isNotEmpty))
    }

    @Test fun `SDK shell launcher handles paths spaces arguments and input on the real host`() {
        val windows = HostOs.detect() == HostOs.WINDOWS
        val script = File(root, "SDK with spaces & parentheses (test)/${HostPlatform().script("sdkmanager")}")
        script.parentFile.mkdirs()
        script.writeText(if (windows) "@echo off\r\necho %~1\r\necho %~2\r\nset /p answer=\r\necho %answer%\r\nexit /b 0\r\n"
            else "#!/bin/sh\nprintf '%s\\n' \"\$1\" \"\$2\"\nread answer\nprintf '%s\\n' \"\$answer\"\n")
        script.setExecutable(true)
        val output = ProcessExecutor().run(Command(script, listOf("AVD folder & space", "system-images;android-36;google_apis_playstore;x86_64"), input = "no\n"), AtomicBoolean()) {}
        assertEquals(listOf("AVD folder & space", "system-images;android-36;google_apis_playstore;x86_64", "no"),
            output.lines().filter(String::isNotEmpty))
    }

    @Test fun `detached launch closes stdin without a Unix null device`() {
        val marker = File(root, "detached marker")
        ProcessExecutor().run(fixtureCommand("detach", marker.path).copy(detached = true), AtomicBoolean()) {}
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
        while (!marker.exists() && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals("finished", marker.readText())
    }

    @Test fun `cancellation stops a native subprocess`() {
        assertFailsWith<CancellationException> {
            ProcessExecutor().run(fixtureCommand("wait"), AtomicBoolean(true)) {}
        }
    }
    @Test fun `cancellation stops children of a launcher process`() {
        val cancelled = AtomicBoolean()
        val output = StringBuilder()
        assertFailsWith<CancellationException> {
            ProcessExecutor().run(fixtureCommand("spawn"), cancelled) { chunk ->
                output.append(chunk)
                if ('\n' in output) cancelled.set(true)
            }
        }
        val pid = output.toString().trim().toLong()
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (ProcessHandle.of(pid).map { it.isAlive }.orElse(false) && System.nanoTime() < deadline) Thread.sleep(20)
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), "Launcher child is still running")
    }

}
