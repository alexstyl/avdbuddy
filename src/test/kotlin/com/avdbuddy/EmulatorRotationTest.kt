package com.avdbuddy

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

// Headless command/state checks; no app windows or UI drivers.
class EmulatorRotationTest {
    @TempDir lateinit var root: File

    @Test fun `current emulator viewport format reads only the primary display`() {
        val dump = """
            Viewport INTERNAL: displayId=-1, uniqueId=, orientation=0, isActive=[0]
            Viewport INTERNAL: displayId=2, uniqueId=virtual:other, orientation=1, isActive=[1]
            Viewport INTERNAL: displayId=0, uniqueId=local:4619827259835644672, port=0, orientation=3, densityDpi=420
        """.trimIndent()
        assertEquals(3, inputDisplayRotation(dump))
        assertEquals(2, inputDisplayRotation("SurfaceOrientation: 2\n"))
        assertNull(inputDisplayRotation("Viewport INTERNAL: displayId=-1, orientation=0,"))
    }

    @Test fun `reading orientation does not override Android rotation policy`() {
        val fake = FakeAndroid(root)
        for (current in 0..3) {
            val commands = mutableListOf<Command>()
            val service = AndroidService(fake.settings, CommandExecutor { command, _, _ ->
                commands += command
                "SurfaceOrientation: $current\n"
            })
            assertEquals(current, service.displayRotation("emulator-5556"))
            assertEquals(listOf("-s", "emulator-5556", "shell", "dumpsys", "input"), commands.single().args)
        }
    }

    @Test fun `unknown orientation does not issue a rotation command`() {
        val fake = FakeAndroid(root)
        val commands = mutableListOf<Command>()
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ -> commands += command; "" })
        assertFailsWith<IllegalStateException> { service.displayRotation("emulator-5554") }
        assertEquals(1, commands.size)
    }

    @Test fun `stopped current device does not rotate another running emulator`() = runBlocking {
        val fake = FakeAndroid(root)
        val stopped = fake.device("Stopped")
        fake.device("Running")
        fake.active["Running"] = "emulator-5556"
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        state.selectPreview(stopped)
        fake.commands.clear()
        assertFalse(state.rotateCurrent(true))
        assertFalse(state.rotateCurrent(false))
        assertTrue(fake.commands.isEmpty())
        assertTrue(state.rotations.isEmpty())
    }

    @Test fun `shortcut rotates only the selected running emulator and reports failure`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("First")
        val selected = fake.device("Selected")
        fake.active["First"] = "emulator-5554"
        fake.active["Selected"] = "emulator-5556"
        var fail = false
        val executor = CommandExecutor { command, cancelled, output ->
            when {
                command.args.takeLast(2) == listOf("dumpsys", "input") -> "SurfaceOrientation: 0"
                else -> fake.run(command, cancelled, output)
            }
        }
        val poses = mutableListOf<Pair<String, Int>>()
        val display = object : DeviceDisplay by AdbDeviceDisplay() {
            override fun rotate(serial: String, rotation: Int) {
                if (fail) error("Device disconnected. Try restarting it.")
                poses += serial to rotation
            }
        }
        val state = AppState(this, initial = fake.settings, executor = executor, display = display)
        state.refresh()
        state.selectPreview(selected)
        assertTrue(state.rotateCurrent(true))
        coroutineContext.job.children.toList().joinAll()
        assertFalse(fake.commands.any { "wm" in it.args })
        val successful = state.rotations["Selected"]
        assertEquals(1, successful!!.turns)
        assertEquals(listOf("emulator-5556" to 3), poses)
        fail = true
        assertTrue(state.rotateCurrent(false))
        coroutineContext.job.children.toList().joinAll()
        assertSame(successful, state.rotations["Selected"])
        assertTrue(state.error)
        assertContains(state.status, "Device disconnected")
    }

    @Test fun `rapid reversals update immediately and coalesce commands while emulator is busy`() = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Selected")
        fake.active[device.name] = "emulator-5556"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val targets = java.util.Collections.synchronizedList(mutableListOf<Int>())
        var reads = 0
        val executor = CommandExecutor { command, cancelled, output ->
            when {
                command.args.takeLast(2) == listOf("dumpsys", "input") -> { reads++; "SurfaceOrientation: 0" }
                else -> fake.run(command, cancelled, output)
            }
        }
        val poses = mutableListOf<Int>()
        val display = object : DeviceDisplay by AdbDeviceDisplay() {
            override fun rotate(serial: String, rotation: Int) {
                targets += rotation
                if (targets.size == 1) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                poses += rotation
            }
        }
        val state = AppState(this, initial = fake.settings, executor = executor, display = display)
        state.refresh()
        state.selectPreview(device)
        assertTrue(state.rotateCurrent(true))
        assertEquals(1, state.rotations[device.name]!!.turns)
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            state.rotateCurrent(true)
            assertEquals(2, state.rotations[device.name]!!.turns)
            state.rotateCurrent(true)
            assertEquals(3, state.rotations[device.name]!!.turns)
            state.rotateCurrent(false)
            assertEquals(2, state.rotations[device.name]!!.turns)
        } finally { release.countDown() }
        coroutineContext.job.children.toList().joinAll()
        assertEquals(listOf(3, 2), targets)
        assertEquals(listOf(3, 2), poses)
        assertFalse(fake.commands.any { "wm" in it.args })
        assertEquals(1, reads)
        assertEquals(2, state.rotations[device.name]!!.turns)
    }

    @Test fun `opposite keypresses can cancel before any emulator command completes`() = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Selected")
        fake.active[device.name] = "emulator-5556"
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.args.takeLast(2) == listOf("dumpsys", "input")) "SurfaceOrientation: 0"
            else fake.run(command, cancelled, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor)
        state.refresh()
        state.selectPreview(device)
        fake.commands.clear()
        state.rotateCurrent(false)
        assertEquals(-1, state.rotations[device.name]!!.turns)
        state.rotateCurrent(true)
        assertEquals(0, state.rotations[device.name]!!.turns)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(fake.commands.isEmpty())
    }
}
