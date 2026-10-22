package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.awt.image.BufferedImage
import java.awt.Color
import javax.imageio.ImageIO
import kotlin.test.*

class FixtureDisplay : DeviceDisplay {
    val inputEvents = mutableListOf<List<String>>()
    val captures = mutableListOf<String>()
    private val screens = mutableMapOf<String,BufferedImage>()
    override fun load(device: Device): BufferedImage = screens.getOrPut(device.name) { fixtureScreen(device.type == DeviceType.TABLET) }
    override fun capture(settings: AppSettings, device: Device, serial: String): BufferedImage { captures.add(device.name); return load(device) }
    override fun input(settings: AppSettings, serial: String, arguments: List<String>) { inputEvents.add(arguments) }
}
fun fixtureScreen(tablet: Boolean = false): BufferedImage {
    val w=if(tablet) 800 else 432; val h=960
    return BufferedImage(w,h,BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().let { g ->
            g.paint=java.awt.GradientPaint(0f,0f,Color(110,160,180),w.toFloat(),h.toFloat(),Color(30,70,80));g.fillRect(0,0,w,h)
            g.color=Color(170,190,140);g.fillOval(w/3,380,w,850)
            g.color=Color(40,100,100);g.fillOval(-w/2,560,w*2,700)
            g.color=Color.WHITE;g.font=java.awt.Font("SansSerif",java.awt.Font.PLAIN,60);g.drawString("10:24",30,190)
            g.font=java.awt.Font("SansSerif",java.awt.Font.PLAIN,18);g.drawString("Thursday, October 22",30,225)
            for(i in 0..3){g.color= listOf(Color(90,160,240),Color(220,100,75),Color(230,200,70),Color(180,140,220))[i];g.fillRoundRect(30+i*(w-60)/4,810,55,55,22,22)}
            g.color=Color.WHITE;g.fillRoundRect(w/2-50,940,100,5,5,5);g.dispose()
        }
    }
}
class DeviceDisplayTest {
    @TempDir lateinit var root:File
    @Test fun `capture persists a valid screen and failed capture preserves it`() {
        val fake = FakeAndroid(root); val device=fake.device("Preview")
        val png=File(root,"screen.png");ImageIO.write(fixtureScreen(),"png",png)
        val adb=File(fake.sdk,"platform-tools/adb")
        adb.writeText("#!/bin/sh\ncat '${png.path}'\n");adb.setExecutable(true)
        val display=AdbDeviceDisplay()
        val image=display.capture(fake.settings,device,"emulator-5554")!!
        assertEquals(432,image.width)
        assertEquals(image.getRGB(20,20),display.load(device)!!.getRGB(20,20))
        adb.writeText("#!/bin/sh\nprintf 'not an image'\nexit 1\n")
        assertNull(display.capture(fake.settings,device,"emulator-5554"))
        assertEquals(image.getRGB(20,20),AdbDeviceDisplay().load(device)!!.getRGB(20,20))
        assertFalse(device.directory.listFiles()!!.any { it.name.startsWith(".avdbuddy-capture-") })
    }
    @Test fun `screen coordinates scale and clamp to the current display`() {
        assertEquals(540 to 1200,screenPoint(150f,300f,300,600,1080,2400))
        assertEquals(1079 to 0,screenPoint(400f,-10f,300,600,1080,2400))
        assertEquals(960 to 540,screenPoint(200f,100f,400,200,1920,1080))
    }
    @Test fun `launch uses a headless emulator and keeps Quick Boot enabled`() {
        val fake=FakeAndroid(root); val device=fake.device("Preview")
        AndroidService(fake.settings,fake).launch(device)
        val args=fake.commands.single { it.detached }.args
        assertContains(args,"-no-window")
        assertFalse(args.any { it.startsWith("-no-snapshot") || it == "-wipe-data" })
    }
}
