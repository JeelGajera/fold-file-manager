package com.jeelgajera.fold.core.storage.index

import com.jeelgajera.fold.core.storage.mime.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The curation rules.
 *
 * These matter more than their size suggests: every false positive here is a
 * file the user made that silently stops appearing in Recent, in an app whose
 * whole premise is not hiding files. The negative cases are therefore the point
 * of this suite, not an afterthought.
 */
class NoiseFilterTest {

    private fun noise(path: String): Boolean =
        NoiseFilter.isNoise(path, path.substringAfterLast('/'), isDirectory = false)

    // --- Things that must be filtered ---------------------------------------

    @Test
    fun `app managed storage is noise`() {
        assertTrue(noise("/storage/emulated/0/Android/data/com.example/files/a.jpg"))
        assertTrue(noise("/storage/emulated/0/Android/obb/com.example/main.obb"))
        assertTrue(noise("/storage/emulated/0/Android/media/com.whatsapp/Sent/IMG.jpg"))
    }

    @Test
    fun `thumbnail and cache directories are noise`() {
        assertTrue(noise("/storage/emulated/0/DCIM/.thumbnails/1234.jpg"))
        assertTrue(noise("/storage/emulated/0/SomeApp/cache/blob.bin"))
        assertTrue(noise("/storage/emulated/0/.cache/x.dat"))
    }

    @Test
    fun `half written downloads are noise`() {
        assertTrue(noise("/storage/emulated/0/Download/film.mkv.part"))
        assertTrue(noise("/storage/emulated/0/Download/iso.crdownload"))
        assertTrue(noise("/storage/emulated/0/Download/x.tmp"))
    }

    @Test
    fun `database sidecars and trash markers are noise`() {
        assertTrue(noise("/storage/emulated/0/App/notes.db-journal"))
        assertTrue(noise("/storage/emulated/0/App/notes.db-wal"))
        assertTrue(noise("/storage/emulated/0/DCIM/.trashed-1699-IMG.jpg"))
        assertTrue(noise("/storage/emulated/0/Music/.nomedia"))
    }

    // --- Things that must never be filtered ---------------------------------

    @Test
    fun `the file types FOLD exists for are not noise`() {
        // Every one of these is invisible to a media picker. Filtering any of
        // them would remove the app's reason to exist from its own home screen.
        assertFalse(noise("/storage/emulated/0/Documents/notes.md"))
        assertFalse(noise("/storage/emulated/0/Download/app-release.apk"))
        assertFalse(noise("/storage/emulated/0/Download/diagnostics.log"))
        assertFalse(noise("/storage/emulated/0/Books/novel.epub"))
        assertFalse(noise("/storage/emulated/0/Download/firmware.bin"))
    }

    @Test
    fun `noise segments are matched whole, not as substrings`() {
        // A person's folder that merely starts with a filtered word.
        assertFalse(noise("/storage/emulated/0/cached-notes/todo.md"))
        assertFalse(noise("/storage/emulated/0/Android-tutorials/lesson.pdf"))
    }

    @Test
    fun `a directory is never noise`() {
        assertFalse(NoiseFilter.isNoise("/storage/emulated/0/Android/data", "data", isDirectory = true))
    }

    @Test
    fun `an empty file is not noise`() {
        // A note created a second ago is zero bytes and belongs at the top of
        // Recent, which is exactly what a size-based rule would remove.
        assertFalse(noise("/storage/emulated/0/Documents/new-note.md"))
    }
}

/** [FileCategory.of] decides a location bucket before a type bucket. */
class FileCategoryLocationTest {

    @Test
    fun `files under Download are downloads regardless of type`() {
        assertEquals(
            FileCategory.DOWNLOADS,
            FileCategory.of("/storage/emulated/0/Download/report.pdf", "application/pdf"),
        )
        assertEquals(
            FileCategory.DOWNLOADS,
            FileCategory.of("/storage/emulated/0/Downloads/photo.jpg", "image/jpeg"),
        )
    }

    @Test
    fun `files elsewhere fall back to their mime type`() {
        assertEquals(
            FileCategory.DOCUMENTS,
            FileCategory.of("/storage/emulated/0/Documents/report.pdf", "application/pdf"),
        )
        assertEquals(
            FileCategory.IMAGES,
            FileCategory.of("/storage/emulated/0/DCIM/Camera/photo.jpg", "image/jpeg"),
        )
    }

    @Test
    fun `a lookalike folder is not the download folder`() {
        assertEquals(
            FileCategory.IMAGES,
            FileCategory.of("/storage/emulated/0/Downloaded-2019/photo.jpg", "image/jpeg"),
        )
    }
}
