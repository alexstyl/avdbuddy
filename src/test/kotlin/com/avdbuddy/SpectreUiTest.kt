package com.avdbuddy

import androidx.compose.ui.awt.ComposeWindow
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.core.RobotDriver
import kotlinx.coroutines.*
import dev.sebastiano.spectre.testing.runSpectreTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.awt.Rectangle
import java.awt.Window
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

class SpectreUiTest {
    @TempDir lateinit var root:File
    @Test @Timeout(120)
    fun `real Jewel windows support settings and device creation`():Unit=runSpectreTest {
        val fake=FakeAndroid(root);fake.device("Pixel_9", "36");fake.device("Pixel_Tablet","35","pixel_tablet")
        fake.additionalImages = "\nsystem-images;android-36.1;google_apis_playstore;arm64-v8a | 1 | Google Play ARM 64 Extension Image\nsystem-images;android-36;google_apis;x86_64 | 1 | Google APIs Intel System Image\nsystem-images;android-21;google_apis;x86 | 1 | Lollipop Google APIs Intel System Image\nsystem-images;android-10;default;armeabi-v7a | 1 | Gingerbread ARM System Image\n"
        val display = FixtureDisplay()
        var availableSpace = 128 * GIB
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        lateinit var state:AppState
        val window=withContext(Dispatchers.Main) {
            state=AppState(scope,SettingsStore(File(root,"settings.properties")),fake.settings,fake,display) { spec, settings ->
                calculateStoragePlan(settings,spec,if(spec.image.installed) ImageFootprint(0,0) else ImageFootprint(2*GIB,6*GIB)) { DiskSpace("test-volume",availableSpace) }
            }
            ComposeWindow().apply {
                title="AvdBuddy · Spectre";setSize(1040,720);setLocation(120,80)
                setContent{AvdBuddyTheme{App(state)}};isVisible=true;toFront()
            }
        }
        val automator=ComposeAutomator.inProcess()
        val reports=File("build/reports/spectre").apply{mkdirs()}
        suspend fun click(tag:String) {
            val node=automator.waitForNode(tag=tag)
            automator.focusWindow(node)
            automator.click(node)
            delay(250)
        }
        suspend fun fill(tag:String,value:String) {
            val node=automator.waitForNode(tag=tag)
            val input=automator
            input.focusWindow(node)
            delay(400)
            input.click(input.waitForNode(tag=tag))
            delay(250)
            input.pressKey(java.awt.event.KeyEvent.VK_A,java.awt.event.InputEvent.META_DOWN_MASK)
            delay(100)
            input.pressKey(java.awt.event.KeyEvent.VK_BACK_SPACE)
            delay(100)
            input.typeText(value)
            delay(200)
            assertEquals(value,automator.waitForNode(tag=tag).editableText,"Field $tag must accept the typed value")
        }
        suspend fun capture(name:String,title:String?=null) {
            delay(500)
            val tag = when(name) {
                "01-device-shelf" -> "DeviceShelf"
                "02-settings" -> "SettingsWindow"
                "07-delete-confirmation" -> "DeleteWindow"
                else -> "CreateWindow"
            }
            val surface=automator.tree().windows().first{it.findOneByTestTag(tag)!=null}
            ImageIO.write(automator.screenshot(surface.windowIndex),"png",File(reports,"$name.png"))
        }
        try {
            automator.waitForNode(tag="Device-Pixel_9");delay(1500);capture("01-device-shelf")
            withContext(Dispatchers.Main) { state.checkUpdates() }
            val updateStatus = automator.waitForNode(tag="UpdateStatus")
            assertTrue(updateStatus.text.orEmpty().contains("installed builds"))
            val updatesSurface = automator.tree().windows().first { it.findOneByTestTag("UpdatesWindow") != null }
            ImageIO.write(automator.screenshot(updatesSurface.windowIndex), "png", File(reports, "10-software-updates.png"))
            withContext(Dispatchers.Main) { state.updates.visible = false }
            automator.waitUntilGone(tag="UpdatesWindow")
            val sidebar=automator.waitForNode(tag="DeviceSidebar")
            val preview=automator.waitForNode(tag="EmulatorPanel")
            assertTrue(sidebar.boundsOnScreen.x + sidebar.boundsOnScreen.width <= preview.boundsOnScreen.x)
            click("Device-Pixel_Tablet")
            assertEquals("Pixel Tablet",automator.waitForNode(tag="PreviewDeviceName").text)
            click("Device-Pixel_9")
            assertEquals("Pixel 9",automator.waitForNode(tag="PreviewDeviceName").text)
            assertTrue(fake.active.isEmpty(), "Selecting devices must never start them")
            assertNotNull(automator.findOneByTestTag("StoppedDeviceScreen"))
            capture("01-device-shelf")
            click("Settings");automator.waitForNode(tag="SettingsWindow")
            val sdk=automator.waitForNode(tag="SdkLocationLabel");val avd=automator.waitForNode(tag="AvdLocationLabel")
            assertTrue(sdk.boundsOnScreen.y<avd.boundsOnScreen.y,"SDK location must appear above emulator location")
            assertEquals(fake.avds.path,automator.waitForNode(tag="AvdPath").editableText)
            capture("02-settings","Settings")
            val other=File(root,"Another Devices Folder").apply{mkdirs()}
            fill("AvdPath",other.path)
            assertTrue(other.isDirectory && other.canWrite(), "The temporary storage folder must be available")
            automator.waitForIdle()
            click("SaveSettings")
            check(automator.findOneByTestTag("SettingsError") == null) {
                "Save failed for " + automator.findOneByTestTag("AvdPath")?.editableText + ": " + automator.findOneByTestTag("SettingsError")?.text
            }
            automator.waitUntilGone(tag="SettingsWindow")
            withContext(Dispatchers.Main){assertEquals(other.path,state.settings.avds)}
            click("Settings");fill("AvdPath",fake.avds.path)
            click("SaveSettings");automator.waitForNode(tag="Device-Pixel_9")
            click("CreateDevice");automator.waitForNode(tag="CreateWindow");capture("03-create-device","What would you like to create?")
            click("Type-TABLET");click("Type-PHONE");click("ContinueCreate")
            automator.waitForNode(tag="Release-37")
            assertNull(automator.findOneByTestTag("SearchVersions"), "Version selection must not show a search field")
            assertNull(automator.findOneByText("2 of 3"), "The step counter must not be displayed")
            assertNotNull(automator.findOneByText("Android 2.3.3 Gingerbread"))
            assertNotNull(automator.findOneByText("Android 5.0 Lollipop"))
            val versions=automator.waitForNode(tag="AndroidVersionsPane")
            val variants=automator.waitForNode(tag="AndroidImagesPane")
            assertTrue(versions.boundsOnScreen.x + versions.boundsOnScreen.width < variants.boundsOnScreen.x,
                "Android families must be on the left and system images on the right")
            val footer=automator.waitForNode(tag="CreateWindow-Footer")
            val paneBottom=versions.boundsOnScreen.y + versions.boundsOnScreen.height
            assertTrue(kotlin.math.abs(footer.boundsOnScreen.y-paneBottom) <= 3,
                "Both picker panes must extend to the footer without an empty bottom strip")
            click("Version-36")
            automator.waitForNode(tag="Release-36.1")
            assertNotNull(automator.findOneByTestTag("Release-36"))
            assertNull(automator.findOneByText("Google APIs · arm64"))
            assertNull(automator.findOneByText("Google Play · arm64"))
            click("Release-36.1")
            assertFalse(automator.waitForNode(tag="ContinueCreate").isDisabled)
            click("ContinueCreate")
            assertNotNull(automator.findOneByTestTag("Architecture"))
            assertNotNull(automator.findOneByTestTag("GoogleServices"))
            click("PreviousCreate")
            click("Version-37")
            click("Version-36")
            assertNull(automator.findOneByTestTag("Release-37"))
            assertTrue(automator.waitForNode(tag="ContinueCreate").isDisabled)
            click("Release-36")
            assertFalse(automator.waitForNode(tag="ContinueCreate").isDisabled)
            capture("04-android-versions","Choose an Android version")
            click("Version-37")
            assertTrue(automator.waitForNode(tag="ContinueCreate").isDisabled,
                "Changing Android versions must clear a previously chosen image")
            click("Release-37");click("ContinueCreate")
            fill("DeviceName","Spectre_Pixel")
            capture("05-device-configuration","Make it your own")
            click("ContinueCreate")
            automator.waitForNode(tag="ReviewSpaceStatus")
            assertContains(automator.waitForNode(tag="ReviewImageStatus").text.orEmpty(),"will be downloaded")
            assertEquals("Create",automator.waitForNode(tag="ContinueCreate").text)
            assertFalse(automator.waitForNode(tag="ContinueCreate").isDisabled)
            capture("05b-device-review","Review your device")
            click("ContinueCreate");automator.waitForNode(tag="CreateDone")
            capture("06-device-created","Your device is ready")
            click("CreateDone");automator.waitForNode(tag="Device-Spectre_Pixel")
            click("Device-Spectre_Pixel")
            click("Launch-Spectre_Pixel")
            withTimeout(5000){while(fake.active["Spectre_Pixel"]==null)delay(50)}
            automator.waitForNode(tag="LiveDeviceScreen")
            click("DeviceHome")
            withTimeout(5000) { while(display.inputEvents.isEmpty()) delay(50) }
            assertEquals(listOf("keyevent","3"),display.inputEvents.last())
            click("Stop-Spectre_Pixel")
            automator.waitForNode(tag="StoppedDeviceScreen")
            assertContains(display.captures,"Spectre_Pixel")
            click("Actions-Spectre_Pixel");click("Delete-Spectre_Pixel")
            automator.waitForNode(tag="DeleteWindow");capture("07-delete-confirmation","Delete device?")
            click("CancelDelete");assertTrue(File(fake.avds,"Spectre_Pixel.ini").exists())
            click("Actions-Pixel_9");click("Rename-Pixel_9")
            automator.waitForNode(tag="RenameWindow")
            fill("RenameName","Renamed_Pixel");click("ConfirmRename")
            automator.waitForNode(tag="Device-Renamed_Pixel")
            fake.installFailure="Warning: An error occurred while preparing SDK package: No space left on device."
            click("CreateDevice");click("ContinueCreate")
            val pickerWidth=automator.waitForNode(tag="CreateWindow").boundsOnScreen.width
            click("Version-36");click("Release-36.1");click("ContinueCreate");click("ContinueCreate")
            automator.waitForNode(tag="ReviewSpaceStatus")
            click("ContinueCreate")
            automator.waitForNode(tag="CreateError")
            assertContains(automator.waitForNode(tag="CreateError").text.orEmpty(),"Not enough disk space")
            assertFalse(automator.waitForNode(tag="CreateError").text.orEmpty().contains("Unzipping"))
            assertEquals(pickerWidth,automator.waitForNode(tag="CreateWindow").boundsOnScreen.width)
            val activity=automator.waitForNode(tag="ActivityPanel")
            val failureFooter=automator.waitForNode(tag="CreateWindow-Footer")
            assertTrue(activity.boundsOnScreen.y+activity.boundsOnScreen.height <= failureFooter.boundsOnScreen.y)
            capture("08-download-failure","Couldn’t create your device")
            click("CreateDone")
            // Existing images are reused, while low disk space blocks creation before any command runs.
            withContext(Dispatchers.Main) { availableSpace=GIB }
            click("CreateDevice");click("ContinueCreate");click("Version-36");click("Release-36")
            click("ContinueCreate");click("ContinueCreate")
            automator.waitForNode(tag="ReviewSpaceStatus")
            assertContains(automator.waitForNode(tag="ReviewImageStatus").text.orEmpty(),"Already downloaded")
            assertTrue(automator.waitForNode(tag="ContinueCreate").isDisabled)
            assertContains(automator.waitForNode(tag="ReviewSpaceStatus").text.orEmpty(),"Not enough")
            capture("09-review-insufficient-space","Review your device")
            withContext(Dispatchers.Main) { availableSpace=128*GIB }
            click("RefreshReviewSpace")
            automator.waitForNode(tag="ReviewSpaceStatus")
            assertFalse(automator.waitForNode(tag="ContinueCreate").isDisabled)
            click("CancelCreate")
            assertTrue(fake.commands.any{it.env["ANDROID_AVD_HOME"]==fake.avds.path&&it.args.contains("--install")})
        } catch (t:Throwable) {
            println(automator.printTree())
            throw t
        } finally {
            withContext(Dispatchers.Main){window.dispose()};scope.cancel()
        }
    }
}
