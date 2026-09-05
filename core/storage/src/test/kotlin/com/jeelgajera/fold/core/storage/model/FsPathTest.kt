package com.jeelgajera.fold.core.storage.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FsPathTest {

    @Test
    fun `normalises relative segments and repeated separators`() {
        assertEquals("/storage/emulated/0", FsPath.raw("/storage//emulated/./0").value)
        assertEquals("/storage/0", FsPath.raw("/storage/emulated/../0").value)
        assertEquals("/", FsPath.raw("/..").value)
        assertEquals("/", FsPath.raw("/a/../..").value)
    }

    @Test
    fun `makes a bare path absolute`() {
        assertEquals("/Download/notes.md", FsPath.raw("Download/notes.md").value)
    }

    @Test
    fun `extension ignores a leading dot`() {
        assertEquals("md", FsPath.raw("/a/notes.md").extension)
        assertEquals("gz", FsPath.raw("/a/backup.tar.gz").extension)
        assertEquals("", FsPath.raw("/a/.gitconfig").extension)
        assertEquals("", FsPath.raw("/a/Makefile").extension)
    }

    @Test
    fun `hidden is a name property`() {
        assertTrue(FsPath.raw("/a/.obsidian").isHiddenName)
        assertFalse(FsPath.raw("/a/obsidian").isHiddenName)
    }

    @Test
    fun `parent walks up and stops at the root`() {
        assertEquals("/a", FsPath.raw("/a/b").parent?.value)
        assertEquals("/", FsPath.raw("/a").parent?.value)
        assertNull(FsPath.raw("/").parent)
    }

    @Test
    fun `child rejects anything that could escape`() {
        val dir = FsPath.raw("/storage/emulated/0")
        assertEquals("/storage/emulated/0/notes.md", dir.child("notes.md").value)
        assertThrows(IllegalArgumentException::class.java) { dir.child("..") }
        assertThrows(IllegalArgumentException::class.java) { dir.child("a/b") }
        assertThrows(IllegalArgumentException::class.java) { dir.child(".") }
    }

    @Test
    fun `containment is segment-aware, not prefix-aware`() {
        val download = FsPath.raw("/storage/emulated/0/Download")
        assertTrue(FsPath.raw("/storage/emulated/0/Download/a.md").isWithin(download))
        assertTrue(download.isWithin(download))
        // The prefix bug this guards against.
        assertFalse(FsPath.raw("/storage/emulated/0/Download-evil/a.md").isWithin(download))
        assertFalse(FsPath.raw("/storage/emulated/0").isWithin(download))
    }

    @Test
    fun `schemes never contain one another`() {
        val raw = FsPath.raw("/storage/emulated/0")
        val saf = FsPath.saf("content://com.android.externalstorage.documents/tree/primary%3ADownload")
        assertFalse(saf.isWithin(raw))
        assertFalse(raw.isWithin(saf))
    }

    @Test
    fun `segments drop empties for a breadcrumb`() {
        assertEquals(
            listOf("storage", "emulated", "0", "Download"),
            FsPath.raw("/storage/emulated/0/Download").segments(),
        )
        assertEquals(emptyList<String>(), FsPath.raw("/").segments())
    }
}
