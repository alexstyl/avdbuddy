package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class DirectoryListingTest {
    @TempDir lateinit var root: File

    @Test fun `folder picker lists only directories in case insensitive order`() {
        File(root, "zebra").mkdir()
        File(root, "Alpha").mkdir()
        File(root, ".android").mkdir()
        File(root, "notes.txt").writeText("not a directory")

        val listing = listDirectories(root)

        assertNull(listing.error)
        assertEquals(listOf(".android", "Alpha", "zebra"), listing.folders.map { it.name })
    }

    @Test fun `empty folders are valid destinations`() {
        val listing = listDirectories(root)
        assertNull(listing.error)
        assertTrue(listing.folders.isEmpty())
    }

    @Test fun `missing folders and regular files cannot be selected`() {
        val file = File(root, "file").apply { writeText("content") }
        for (invalid in listOf(file, File(root, "missing"))) {
            val listing = listDirectories(invalid)
            assertNotNull(listing.error)
            assertTrue(listing.folders.isEmpty())
        }
    }
}
