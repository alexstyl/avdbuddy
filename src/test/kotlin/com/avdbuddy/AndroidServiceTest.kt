package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class FakeAndroid(val root:File) : CommandExecutor {
    val sdk=File(root,"sdk");val avds=File(root,"avds")
    val commands=java.util.Collections.synchronizedList(mutableListOf<Command>())
    val active=linkedMapOf<String,String>()
    var additionalImages = ""
    var installFailure: String? = null
    init {
        listOf("cmdline-tools/latest/bin/sdkmanager","cmdline-tools/latest/bin/avdmanager","emulator/emulator","platform-tools/adb").forEach { path -> File(sdk,path).apply{parentFile.mkdirs();writeText("#!/bin/sh\n");setExecutable(true)} }
        avds.mkdirs()
    }
    val settings get()=AppSettings(sdk.path,avds.path)
    fun device(name:String,api:String="36",profile:String="pixel_9"):Device {
        val dir=File(avds,"$name.avd").apply{mkdirs()}
        writeIni(File(avds,"$name.ini"),mapOf("path" to dir.path,"target" to "android-$api"))
        writeIni(File(dir,"config.ini"),mapOf("avd.ini.displayname" to name.replace('_',' '),"hw.device.name" to profile,"image.sysdir.1" to "system-images/android-$api/google_apis/arm64-v8a/"))
        File(dir,"userdata-qemu.img").writeText("fixture-data")
        return AndroidService(settings,this).devices().first{it.name==name}
    }
    override fun run(command:Command,cancelled:AtomicBoolean,output:(String)->Unit):String {
        commands+=command
        if(command.args==listOf("--list"))return """
            Installed packages:
            system-images;android-36;google_apis;arm64-v8a | 1 | Google APIs ARM 64 System Image
            Available Packages:
            system-images;android-37;google_apis_playstore;arm64-v8a | 2 | Google Play ARM 64 System Image
            system-images;android-36;google_apis;arm64-v8a | 1 | Google APIs ARM 64 System Image
            system-images;android-35;android-tv;x86_64 | 1 | Android TV System Image
        """.trimIndent()+additionalImages
        if(command.args==listOf("list","device"))return "id: 1 or \"pixel_9\"\n    Name: Pixel 9\n\nid: 2 or \"pixel_9_pro\"\n    Name: Pixel 9 Pro\n"
        if(command.args==listOf("devices"))return "List of devices attached\n"+active.entries.joinToString("\n"){"${it.value}\tdevice"}
        if(command.args.contains("name")&&command.args.contains("emu"))return (active.entries.firstOrNull{it.value==command.args[1]}?.key ?: "")+"\nOK\n"
        if(command.args.contains("kill")){active.entries.removeIf{it.value==command.args[1]};return "OK"}
        if(command.detached){active[command.args[1]]="emulator-5554";return ""}
        if(command.args.firstOrNull()=="--install" && installFailure != null) {
            val log=(1..250).joinToString("\n") { "[$it%] Downloading and unpacking system.img" }+"\n"+installFailure+"\nEND OF ACTIVITY\n"
            output(log)
            throw IllegalStateException(androidCommandError(log,1))
        }
        if(command.args.take(2)==listOf("create","avd")) {
            val name=command.args[command.args.indexOf("-n")+1];device(name)
            output("Created $name\n");return "Created $name"
        }
        if(command.args.take(2)==listOf("delete","avd")) {
            val name=command.args.last();File(avds,"$name.avd").deleteRecursively();File(avds,"$name.ini").delete();return "Deleted $name"
        }
        output("Download complete.\n");return ""
    }
}
class AndroidServiceTest {
    @TempDir lateinit var root:File
    @Test fun `catalog retains installed state and filters device types`() {
        val fake=FakeAndroid(root);val images=AndroidService(fake.settings,fake).images()
        assertEquals(3,images.size);assertTrue(images.first{it.api=="36"}.installed)
        assertEquals(1,images.count{DeviceType.TV.accepts(it.tag)})
        assertEquals(2,images.count{DeviceType.PHONE.accepts(it.tag)})
    }
    @Test fun `create uses selected locations and writes configuration without links`() {
        val fake=FakeAndroid(root);val service=AndroidService(fake.settings,fake)
        val image=service.images().first();service.create(CreateSpec("Test_Pixel",image,"pixel_9",4096,32,2,false),AtomicBoolean()){}
        val command=fake.commands.first{it.args.take(2)==listOf("create","avd")}
        assertEquals(fake.avds.path,command.env["ANDROID_AVD_HOME"])
        assertEquals(File(fake.avds,"Test_Pixel.avd").path,command.args[command.args.indexOf("-p")+1])
        val config=readIni(File(fake.avds,"Test_Pixel.avd/config.ini"))
        assertEquals("4096",config["hw.ramSize"]);assertEquals("32G",config["disk.dataPartition.size"]);assertEquals("no",config["showDeviceFrame"])
        assertEquals("yes", config["hw.gpu.enabled"])
        assertEquals("host", config["hw.gpu.mode"])
        assertFalse(Files.isSymbolicLink(File(fake.sdk,"system-images").toPath()))
    }
    @Test fun `broken image link fails before running install`() {
        val fake=FakeAndroid(root);val service=AndroidService(fake.settings,fake)
        val link=File(fake.sdk,"system-images");val target=File(root,"offline/images")
        Files.createSymbolicLink(link.toPath(),target.toPath())
        val error=assertFailsWith<IllegalArgumentException>{service.create(CreateSpec("Test",SystemImage("system-images;android-36;google_apis;arm64-v8a","Android",false),"pixel_9"),AtomicBoolean()) {}}
        assertContains(error.message.orEmpty(),"unavailable drive");assertTrue(fake.commands.isEmpty());assertFalse(target.exists())
    }
    @Test fun `duplicate rename and delete preserve original device data`() {
        val fake=FakeAndroid(root);val original=fake.device("Pixel_Original");val service=AndroidService(fake.settings,fake)
        val clone=service.duplicate(original);assertEquals("Pixel_Original_Copy",clone)
        assertEquals("yes", readIni(File(fake.avds, "$clone.avd/config.ini"))["hw.gpu.enabled"])
        assertEquals("host", readIni(File(fake.avds, "$clone.avd/config.ini"))["hw.gpu.mode"])
        assertEquals("fixture-data",File(original.directory,"userdata-qemu.img").readText())
        val copy=service.devices().first{it.name==clone};service.rename(copy,"Renamed")
        assertFalse(File(fake.avds,"$clone.ini").exists());assertEquals(File(fake.avds,"Renamed.avd").path,readIni(File(fake.avds,"Renamed.ini"))["path"])
        service.delete(service.devices().first{it.name=="Renamed"});assertEquals(listOf("Pixel_Original"),service.devices().map{it.name})
    }
    @Test fun `settings round trip independent of legacy defaults`() {
        val store=SettingsStore(File(root,"prefs/settings.properties"));val settings=AppSettings("/tmp/My SDK","/tmp/My Devices")
        store.save(settings);assertEquals(settings,store.load())
    }
    @Test fun `command failure reports disk exhaustion without repeating progress output`() {
        val script=File(root,"fail.sh").apply {
            writeText("#!/bin/sh\nprintf '[===] 55%% Unzipping...\\rWarning: An error occurred: No space left on device.\\n'\nexit 1\n")
            setExecutable(true)
        }
        val streamed=StringBuilder()
        val failure=assertFailsWith<IllegalStateException> {
            ProcessExecutor().run(Command(script,emptyList()),AtomicBoolean()) { streamed.append(it) }
        }
        assertContains(streamed.toString(),"55%")
        assertContains(failure.message.orEmpty(),"Not enough disk space")
        assertFalse(failure.message.orEmpty().contains("55%"))
    }
    @Test fun `command failure keeps the relevant SDK error`() {
        assertEquals("Error: Package not found", androidCommandError("Loading repository\r[===] 10% fetching\nError: Package not found\n",1))
    }
    @Test fun `unavailable device directory is reported and cannot launch`() {
        val fake=FakeAndroid(root);val device=fake.device("Pixel");val service=AndroidService(fake.settings,fake)
        device.directory.deleteRecursively();assertTrue(service.devices().single().missingStorage)
        assertFailsWith<IllegalArgumentException>{service.launch(service.devices().single())};assertTrue(fake.commands.isEmpty())
    }
}
