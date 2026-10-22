package com.avdbuddy

import androidx.compose.ui.awt.ComposeWindow
import dev.sebastiano.spectre.core.ComposeAutomator
import dev.sebastiano.spectre.testing.runSpectreTest
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

class PreviewUiTest {
    @TempDir lateinit var root:File

    @Test @Timeout(60)
    fun `preview selection stays stopped and play turns its screen to color`(): Unit = runSpectreTest {
        val fake=FakeAndroid(root); fake.device("Pixel_9"); fake.device("Tablet","35","pixel_tablet")
        val display=FixtureDisplay()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        lateinit var state:AppState
        val window=withContext(Dispatchers.Main) {
            state=AppState(scope, SettingsStore(File(root,"settings")), fake.settings, fake, display)
            ComposeWindow().apply { setSize(1100,800); setLocation(120,80);setContent { AvdBuddyTheme { App(state) } };isVisible=true }
        }
        val automator=ComposeAutomator.inProcess()
        suspend fun click(tag:String) { val node=automator.waitForNode(tag=tag);automator.focusWindow(node);automator.click(node);delay(300) }
        suspend fun capture(name:String, expectColor:Boolean) {
            delay(800)
            val node=automator.waitForNode(tag=if(expectColor) "LiveDeviceScreen" else "StoppedDeviceScreen")
            val surface=automator.tree().windows().first { it.findOneByTestTag("DeviceShelf")!=null }
            val image=automator.screenshot(surface.windowIndex)
            val output=File("build/reports/spectre/$name.png");output.parentFile.mkdirs();ImageIO.write(image,"png",output)
            // Sample the wallpaper above the play overlay. Window captures are scaled to display density.
            val shelf=automator.waitForNode(tag="DeviceShelf")
            val scale=image.width/shelf.boundsOnScreen.width
            val x=((node.boundsOnScreen.x-shelf.boundsOnScreen.x+node.boundsOnScreen.width*.25)*scale).toInt()
            val y=((node.boundsOnScreen.y-shelf.boundsOnScreen.y+node.boundsOnScreen.height*.30)*scale).toInt()
            val c=java.awt.Color(image.getRGB(x,y))
            if(expectColor) assertTrue(kotlin.math.abs(c.red-c.blue)>10,"Running screen must be in color: $c")
            else assertTrue(kotlin.math.abs(c.red-c.blue)<3,"Stopped screenshot must be monochrome: $c")
        }
        try {
            automator.waitForNode(tag="Launch-Pixel_9")
            withTimeout(5000) { while(state.frames.isEmpty()) delay(50) }
            click("Device-Tablet");click("Device-Pixel_9")
            assertTrue(fake.active.isEmpty())
            capture("11-preview-stopped",false)
            click("Launch-Pixel_9")
            automator.waitForNode(tag="LiveDeviceScreen")
            capture("12-preview-live",true)
            click("LiveDeviceScreen")
            withTimeout(5000){while(display.inputEvents.none { it.firstOrNull()=="tap" })delay(50)}
            click("Device-Tablet")
            assertEquals(setOf("Pixel_9"),fake.active.keys.toSet())
            automator.waitForNode(tag="Launch-Tablet")
            click("Device-Pixel_9");click("Stop-Pixel_9")
            automator.waitForNode(tag="StoppedDeviceScreen")
            capture("13-preview-stopped-again",false)
            assertTrue(fake.active.isEmpty())
        } finally { withContext(Dispatchers.Main){window.dispose()};scope.cancel() }
    }

    /** Read-only smoke test against an already running emulator; never boots or stops a device. */
    @Test @Timeout(60) @EnabledIfEnvironmentVariable(named="AVDBUDDY_LIVE_PREVIEW_TEST",matches="true")
    fun `capture an already running emulator in the real preview`(): Unit = runSpectreTest {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        lateinit var state:AppState
        val window=withContext(Dispatchers.Main) {
            state=AppState(scope)
            ComposeWindow().apply { setSize(1100,800);setLocation(120,80);setContent { AvdBuddyTheme { App(state) } };isVisible=true }
        }
        try {
            withTimeout(30_000) { while(state.running.isEmpty() || state.devices.none { it.name in state.running }) delay(100) }
            withContext(Dispatchers.Main) { state.selectPreview(state.devices.first { it.name in state.running }) }
            withTimeout(15_000) { while(state.frames[state.previewDevice!!.directory.absolutePath]==null)delay(100) }
            delay(1000)
            val automator=ComposeAutomator.inProcess()
            automator.waitForNode(tag="LiveDeviceScreen")
            val surface=automator.tree().windows().first { it.findOneByTestTag("DeviceShelf")!=null }
            val output=File("build/reports/spectre/14-real-emulator.png");output.parentFile.mkdirs()
            ImageIO.write(automator.screenshot(surface.windowIndex),"png",output)
            assertTrue(File(state.previewDevice!!.directory,".avdbuddy-preview.png").length()>0)
        } finally { withContext(Dispatchers.Main){window.dispose()};scope.cancel() }
    }
}
