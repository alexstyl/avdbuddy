package com.avdbuddy

import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class HardwareProfilesTest {
    @TempDir lateinit var root: File

    private fun image(api: String = "37", tag: String = "google_apis_playstore", abi: String = "arm64-v8a", installed: Boolean = false) =
        SystemImage("system-images;android-$api;$tag;$abi", "Android $api", installed)

    @Test fun `bundled profile screen and actual frame images agree`() {
        val profile = HardwareProfiles.all.single()
        assertEquals("Pixel 10", profile.name)
        for (name in listOf("back.webp", "mask.webp")) {
            profileResource("${profile.frame.directory}/$name").use { stream ->
                Image.makeFromEncoded(stream.readBytes()).use { artwork ->
                    if (name == "mask.webp") {
                        assertEquals(profile.display.width, artwork.width)
                        assertEquals(profile.display.height, artwork.height)
                    } else {
                        // The preview canvas must retain the complete frame, including side buttons.
                        assertEquals(profile.frame.width, artwork.width)
                        assertEquals(profile.frame.height, artwork.height)
                    }
                }
            }
        }
        val layout = profileResource("${profile.frame.directory}/layout").bufferedReader().use { it.readText() }
        assertContains(layout, "width ${profile.display.width}")
        assertContains(layout, "height ${profile.display.height}")
        assertContains(layout, "x ${profile.frame.screenX}")
        assertContains(layout, "y ${profile.frame.screenY}")
    }

    @Test fun `version catalog includes downloadable play images and never falls back across architectures`() {
        val images = listOf(image(), image("36", installed = true), image("35", "google_apis"),
            image("34", "default"), image("37", abi = "x86_64"), image("99-beta1"), image("canary"), image("36.1"), image("36-ext19"))
        assertEquals(listOf("37", "36.1", "36"), googlePlayImages(images, "aarch64").map { it.api })
        assertEquals(listOf("37"), googlePlayImages(images, "amd64").map { it.api })
        assertTrue(googlePlayImages(images.filter { it.abi == "arm64-v8a" }, "amd64").isEmpty())
        assertTrue(googlePlayImages(images, "riscv64").isEmpty())
    }

    @Test fun `yaml rejects invalid schema duplicate keys and screens outside the frame`() {
        val yaml = profileResource("profiles/pixel-10.yml").bufferedReader().use { it.readText() }
        fun parse(text: String) = parseHardwareProfile(ByteArrayInputStream(text.toByteArray()))
        assertFails { parse(yaml.replace("schema: 1", "schema: 2")) }
        assertFails { parse(yaml + "\nname: Duplicate\n") }
        assertFails { parse(yaml.replace("screenX: 59", "screenX: 1000")) }
        assertFails { parse(yaml.replace("device-frames/pixel-10", "../../outside")) }
    }

    @Test fun `friendly names remain display names and produce unique safe avd identifiers`() {
        assertEquals("My_Pixel_2", availableAvdName("My Pixel", listOf("my_pixel")))
        assertEquals("Pixel", availableAvdName("日本語", emptyList()))
        assertNull(creationNameError("My Pixel"))
        assertNotNull(creationNameError("  "))
        assertNotNull(creationNameError("Name\nInjected=value"))
        assertNotNull(creationNameError("My Pixel", listOf("my pixel")))
        assertEquals("My Pixel 3", suggestedDeviceName("My Pixel", listOf("my pixel", "My Pixel 2")))
    }

    @Test fun `fixed profile creates without SDK device definitions or SDK skins`() {
        val fake = FakeAndroid(root)
        val service = AndroidService(fake.settings, fake)
        val profile = HardwareProfiles.all.single()
        val spec = profile.createSpec("My Pixel", image(abi = nativeImageAbi()!!, installed = true), emptyList())
        service.create(spec, AtomicBoolean()) {}
        val create = fake.commands.single { it.args.take(2) == listOf("create", "avd") }
        assertFalse("-d" in create.args)
        assertFalse(fake.commands.any { it.args == listOf("list", "device") || it.args.firstOrNull() == "--install" })
        val directory = File(fake.avds, "${spec.name}.avd")
        val config = readIni(File(directory, "config.ini"))
        assertEquals("My Pixel", config["avd.ini.displayname"])
        assertEquals("1080", config["hw.lcd.width"])
        assertEquals("2424", config["hw.lcd.height"])
        assertEquals("420", config["hw.lcd.density"])
        assertEquals("4096", config["hw.ramSize"])
        assertEquals("16G", config["disk.dataPartition.size"])
        assertEquals("true", config["PlayStore.enabled"])
        assertEquals("pixel-10", config["avdbuddy.profile"])
        assertTrue(File(config.getValue("skin.path"), "layout").isFile)
        assertTrue(File(config.getValue("skin.path"), "mask.webp").isFile)
        assertFalse(File(fake.sdk, "skins").exists())
    }

    @Test fun `missing image downloads before creation and modified builtins are rejected`() {
        val fake = FakeAndroid(root)
        val service = AndroidService(fake.settings, fake)
        val spec = HardwareProfiles.all.single().createSpec("Download Pixel", image(abi = nativeImageAbi()!!), emptyList())
        service.create(spec, AtomicBoolean()) {}
        assertEquals(listOf("--install", spec.image.path), fake.commands.first().args)
        val count = fake.commands.size
        assertFailsWith<IllegalArgumentException> { service.create(spec.copy(name = "Tampered", ram = 8192), AtomicBoolean()) {} }
        assertEquals(count, fake.commands.size)
    }

    @Test fun `orphaned folder is preserved while creation uses a free storage identifier`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val orphan = File(fake.avds, "Pixel_10.avd").apply { mkdirs() }
        val savedData = File(orphan, "userdata-qemu.img").apply { writeText("keep existing data") }
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake) { spec, settings ->
            calculateStoragePlan(settings, spec, ImageFootprint(0, 0)) { DiskSpace("disk", 128 * GIB) }
        }
        val request = HardwareProfiles.all.single().createSpec("Pixel 10", image(abi = nativeImageAbi()!!, installed = true), emptyList())
        assertEquals("Pixel_10", request.name)
        state.create(request)
        withTimeout(5000) { while (state.creating) delay(20) }

        assertEquals("", state.createError)
        assertTrue(state.createFinished)
        assertEquals("Pixel_10_2", state.creationSpec?.name)
        assertEquals("Pixel 10", state.creationSpec?.displayName)
        assertEquals("Pixel_10_2", state.previewName)
        assertEquals(setOf("Pixel_10_2"), state.selected)
        assertEquals("Pixel 10", state.devices.single().displayName)
        assertEquals("keep existing data", savedData.readText())
        assertFalse(File(fake.avds, "Pixel_10.ini").exists())
        assertEquals("Pixel 10", readIni(File(fake.avds, "Pixel_10_2.avd/config.ini"))["avd.ini.displayname"])
    }

    @Test fun `stale UI identifiers cannot overwrite a registered device with a different display name`() {
        val fake = FakeAndroid(root)
        val old = fake.device("Pixel_10")
        writeIni(File(old.directory, "config.ini"), old.config + ("avd.ini.displayname" to "Old phone"))
        val service = AndroidService(fake.settings, fake)
        val request = HardwareProfiles.all.single().createSpec("Pixel 10", image(abi = nativeImageAbi()!!, installed = true), emptyList())
        val created = service.create(request, AtomicBoolean()) {}
        assertEquals("Pixel_10_2", created.name)
        assertEquals("Pixel 10", created.displayName)
        assertEquals("Old phone", readIni(File(old.directory, "config.ini"))["avd.ini.displayname"])
        assertEquals("fixture-data", File(old.directory, "userdata-qemu.img").readText())
    }
}
