package com.avdbuddy

import com.sun.net.httpserver.HttpServer
import dev.nucleusframework.updater.*
import dev.nucleusframework.updater.provider.GenericProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*
import org.junit.jupiter.api.Test

class AppUpdatesTest {
    @Test fun `development and brew installs cannot self update`() {
        assertNotNull(updateInstallation(null, false))
        assertNotNull(updateInstallation(File("/project/build/AvdBuddy.app"), false))
        assertTrue(updateInstallation(File("/Applications/AvdBuddy.app"), true)!!.contains("brew upgrade"))
        assertNull(updateInstallation(File("/Applications/AvdBuddy.app"), false))
        assertEquals(File("/Applications/AvdBuddy.app"), appBundle(File("/Applications/AvdBuddy.app/Contents/runtime/Contents/Home")))
    }

    @Test fun `Nucleus discovers downloads and verifies update checksum`(): Unit = runBlocking {
        val payload = "local test update".toByteArray()
        var checksum = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(payload))
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = if (exchange.requestURI.path.endsWith(".yml")) """
                version: 1.1.0
                releaseDate: '2026-10-22T00:00:00.000Z'
                files:
                  - url: AvdBuddy-1.1.0-arm64.zip
                    sha512: $checksum
                    size: ${payload.size}
                path: AvdBuddy-1.1.0-arm64.zip
                sha512: $checksum
            """.trimIndent().toByteArray() else payload
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val cache = kotlin.io.path.createTempDirectory("avdbuddy-updates").toFile()
        try {
            fun updater() = NucleusUpdater {
                currentVersion = "1.0.0"
                provider = GenericProvider("http://127.0.0.1:${server.address.port}")
                executableType = "zip"
                differentialDownload = false
                cacheDir = cache
            }
            val good = updater()
            val info = assertIs<UpdateResult.Available>(good.checkForUpdates()).info
            assertEquals("1.1.0", info.version)
            val file = good.downloadUpdate(info).toList().last().file!!
            assertContentEquals(payload, file.readBytes())
            cache.deleteRecursively(); cache.mkdirs()
            checksum = Base64.getEncoder().encodeToString(ByteArray(64))
            val bad = updater()
            val badInfo = assertIs<UpdateResult.Available>(bad.checkForUpdates()).info
            assertFails { bad.downloadUpdate(badInfo).toList() }
            Unit
        } finally { server.stop(0); cache.deleteRecursively() }
    }
}
