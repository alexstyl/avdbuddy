package com.avdbuddy

import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

// Headless state and command checks only; no windows or UI interaction.
class DeviceSelectionTest {
    @TempDir lateinit var root: File

    @Test fun `toggle range and context selection preserve the intended targets`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("A", "B", "C", "D").map { fake.device(it) }
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        state.selectDevice(devices[0])
        state.selectDevice(devices[2], toggle = true)
        assertEquals(setOf("A", "C"), state.selected)
        assertEquals("C", state.previewDevice?.name)
        state.selectForContextMenu(devices[0])
        assertEquals(setOf("A", "C"), state.selected)
        state.selectDevice(devices[3], extend = true)
        assertEquals(setOf("C", "D"), state.selected)
        state.selectDevice(devices[0], extend = true)
        assertEquals(setOf("A", "B", "C"), state.selected)
        state.selectDevice(devices[3], toggle = true, extend = true)
        assertEquals(setOf("A", "B", "C", "D"), state.selected)
        state.selectDevice(devices[2], toggle = true)
        assertEquals(setOf("A", "B", "D"), state.selected)
        assertTrue(state.previewDevice?.name in state.selected)
        state.selectForContextMenu(devices[2])
        assertEquals(setOf("C"), state.selected)
    }

    @Test fun `batch start skips running and missing devices and preserves selection`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("A", "B", "Running", "Missing").map { fake.device(it) }
        devices.last().directory.deleteRecursively()
        fake.active["Running"] = "emulator-5560"
        val executor = CommandExecutor { command, cancelled, output ->
            fake.run(command, cancelled, output).also {
                if (command.detached) fake.active[command.args[1]] = if (command.args[1] == "A") "emulator-5554" else "emulator-5556"
            }
        }
        val state = AppState(this, initial = fake.settings, executor = executor)
        state.refresh()
        state.selected = devices.map { it.name }.toSet()
        state.previewName = "Running"
        state.launch(state.devices)
        assertTrue(state.busy)
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("A", "B"), fake.commands.filter { it.detached }.map { it.args[1] }.toSet())
        assertEquals(devices.map { it.name }.toSet(), state.selected)
        assertEquals("Running", state.previewDevice?.name)
        assertTrue(state.starting.isEmpty())
        assertFalse(state.error)
    }

    @Test fun `one failed launch does not skip the other selected devices`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("Broken", "Healthy").map { fake.device(it) }
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.detached && command.args[1] == "Broken") error("Cannot start")
            fake.run(command, cancelled, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor)
        state.refresh()
        state.launch(devices)
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("Healthy"), state.running.keys)
        assertTrue(state.error)
        assertContains(state.status, "Broken: Cannot start")
        assertTrue(state.starting.isEmpty())
    }

    @Test fun `batch stop and restart only address selected running devices`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("A", "B", "Other", "Stopped").map { fake.device(it) }
        fake.active.putAll(mapOf("A" to "emulator-5554", "B" to "emulator-5556", "Other" to "emulator-5558"))
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        state.restart(listOf(devices[0], devices[1], devices[3]))
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("emulator-5554", "emulator-5556"), fake.commands.filter { "reboot" in it.args }.map { it.args[1] }.toSet())
        state.stop(listOf(devices[0], devices[1], devices[3]))
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("Other"), state.running.keys)
        assertEquals(setOf("emulator-5554", "emulator-5556"), fake.commands.filter { "kill" in it.args }.map { it.args[1] }.toSet())
    }

    @Test fun `batch remove continues after failure and refreshes remaining selection`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("Broken", "Healthy", "Other").map { fake.device(it) }
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.args.take(2) == listOf("delete", "avd") && command.args.last() == "Broken") error("Cannot remove")
            fake.run(command, cancelled, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor)
        state.refresh()
        state.selected = setOf("Broken", "Healthy")
        state.delete(devices.take(2))
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("Broken", "Other"), state.devices.map { it.name }.toSet())
        assertEquals(setOf("Broken"), state.selected)
        assertTrue(state.error)
        assertContains(state.status, "Broken: Cannot remove")
    }
}
