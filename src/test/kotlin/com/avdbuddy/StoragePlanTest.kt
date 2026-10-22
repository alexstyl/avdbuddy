package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.coroutines.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class StoragePlanTest {
    @TempDir lateinit var root:File
    private val settings = AppSettings("/sdk", "/devices")
    private fun spec(installed: Boolean=false, sd: Int?=null) = CreateSpec("Pixel",SystemImage("system-images;android-36;google_apis;arm64-v8a","Android",installed),"pixel_9",storage=16,sdCard=sd)
    @Test fun `same volume includes archive extraction and full device budget once`() {
        val plan=calculateStoragePlan(settings,spec(sd=2),ImageFootprint(2*GIB,8*GIB)) { DiskSpace("same",31*GIB) }
        assertEquals(18*GIB,plan.deviceCapacity)
        assertEquals(30*GIB,plan.required)
        assertEquals(1,plan.disks.size)
        assertTrue(plan.canCreate)
    }
    @Test fun `separate SDK drive can block installation even with enough device space`() {
        val plan=calculateStoragePlan(settings,spec(),ImageFootprint(2*GIB,8*GIB)) { file ->
            if(file.path=="/sdk") DiskSpace("sdk",10*GIB) else DiskSpace("device",50*GIB)
        }
        assertEquals(2,plan.disks.size)
        assertFalse(plan.disks[0].enough)
        assertTrue(plan.disks[1].enough)
        assertFalse(plan.canCreate)
    }
    @Test fun `installed image needs no SDK download or additional extraction space`() {
        val visited=mutableListOf<File>()
        val plan=calculateStoragePlan(settings,spec(installed=true),ImageFootprint(0,0)) { file ->
            visited+=file;DiskSpace("device",17*GIB)
        }
        assertEquals(listOf(File("/devices")),visited)
        assertEquals(17*GIB,plan.required)
        assertTrue(plan.canCreate)
    }
    @Test fun `insufficient device space disables creation`() {
        val plan=calculateStoragePlan(settings,spec(installed=true),ImageFootprint(0,0)) { DiskSpace("device",8*GIB) }
        assertFalse(plan.canCreate)
    }
    @Test fun `ZIP directory provides unpacked byte count without extracting files`() {
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for((name,size) in listOf("system.img" to 4096, "userdata.img" to 8192)) {
                zip.putNextEntry(ZipEntry(name));zip.write(ByteArray(size));zip.closeEntry()
            }
        }
        val zip=output.toByteArray()
        val (offset,length)=zipDirectoryLocation(zip)
        assertEquals(12288,zipUnpackedSize(zip.copyOfRange(offset.toInt(),(offset+length).toInt())))
    }
    @Test fun `ZIP64 entries support system images larger than four GiB`() {
        val directory=ByteBuffer.allocate(59).order(ByteOrder.LITTLE_ENDIAN)
        directory.putInt(0,0x02014b50);directory.putInt(24,-1)
        directory.putShort(28,1);directory.putShort(30,12)
        directory.putShort(47,1);directory.putShort(49,8);directory.putLong(51,10*GIB)
        assertEquals(10*GIB,zipUnpackedSize(directory.array()))
    }
    @Test fun `ZIP64 archive directories can point past four GiB`() {
        val tail=ByteBuffer.allocate(98).order(ByteOrder.LITTLE_ENDIAN)
        tail.putInt(0,0x06064b50);tail.putLong(40,3000);tail.putLong(48,5*GIB)
        tail.putInt(76,0x06054b50);tail.putInt(88,-1);tail.putInt(92,-1)
        assertEquals(5*GIB to 3000L,zipDirectoryLocation(tail.array()))
    }
    @Test fun `creation rechecks disk space after a successful review`():Unit=runBlocking {
        val fake=FakeAndroid(root)
        var available=128*GIB
        val state=AppState(this,SettingsStore(File(root,"settings")),fake.settings,fake) { spec, settings ->
            calculateStoragePlan(settings,spec,ImageFootprint(0,0)) { DiskSpace("disk",available) }
        }
        assertTrue(state.reviewStorage(spec(installed=true)).canCreate)
        available=GIB
        state.create(spec(installed=true))
        withTimeout(5000) { while(state.creating) delay(20) }
        assertContains(state.createError,"Not enough available disk space")
        assertTrue(fake.commands.isEmpty(),"A rejected creation must not download or create device files")
    }
    @Test fun `invalid ZIP metadata does not produce an optimistic zero size`() {
        assertFailsWith<IllegalStateException> { zipDirectoryLocation(ByteArray(30)) }
        assertFailsWith<IllegalArgumentException> { zipUnpackedSize(ByteArray(30)) }
    }
}
