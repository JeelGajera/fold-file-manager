package com.jeelgajera.fold.core.storage.mime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MIME resolution, including the cases the platform gets wrong.
 *
 * The first group is the reason the app exists: these are the extensions
 * `MimeTypeMap` returns null for, which is what makes other file managers hide
 * or refuse the files.
 */
class MimeResolverTest {

    // --- the gap this app closes -----------------------------------------

    @Test
    fun `resolves the extensions the platform map misses`() {
        assertEquals("text/markdown", MimeResolver.fromName("handoff-1-claude-design.md"))
        assertEquals("text/plain", MimeResolver.fromName("logcat-2026-09-03.log"))
        assertEquals("application/epub+zip", MimeResolver.fromName("everyday-things.epub"))
        assertEquals(
            "application/vnd.android.package-archive",
            MimeResolver.fromName("fold-debug-v0.4.apk"),
        )
        assertEquals("application/toml", MimeResolver.fromName("Cargo.toml"))
    }

    @Test
    fun `an unknown extension falls back rather than failing`() {
        assertEquals(MimeResolver.OCTET_STREAM, MimeResolver.fromName("thing.qqzz"))
        assertEquals(null, MimeResolver.fromExtension("qqzz"))
    }

    @Test
    fun `a file with no extension falls back`() {
        assertEquals(MimeResolver.OCTET_STREAM, MimeResolver.fromName("blob_9f2c11"))
        assertEquals(MimeResolver.OCTET_STREAM, MimeResolver.fromName("Makefile"))
    }

    @Test
    fun `a leading dot is a hidden marker, not an extension`() {
        // .gitconfig must not resolve as a "gitconfig" type, and must not crash.
        assertEquals(MimeResolver.OCTET_STREAM, MimeResolver.fromName(".gitconfig"))
        assertEquals(MimeResolver.OCTET_STREAM, MimeResolver.fromName(".nomedia"))
    }

    @Test
    fun `extension matching is case insensitive`() {
        assertEquals("image/jpeg", MimeResolver.fromName("IMG_20260812.JPG"))
        assertEquals("text/markdown", MimeResolver.fromName("README.MD"))
    }

    @Test
    fun `a multi-part extension uses the last segment`() {
        assertEquals("application/gzip", MimeResolver.fromName("backup.tar.gz"))
    }

    @Test
    fun `dot-ts resolves as TypeScript, not as a transport stream`() {
        // Deliberate: on a phone this extension is overwhelmingly source code, and
        // guessing video sends it to a media player instead of an editor.
        assertEquals("text/x-typescript", MimeResolver.fromName("main.ts"))
    }

    // --- content sniffing -------------------------------------------------

    @Test
    fun `sniffs a PDF`() {
        assertEquals("application/pdf", MimeResolver.sniff("%PDF-1.7\n...".toByteArray()))
    }

    @Test
    fun `sniffs a PNG`() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00,
        )
        assertEquals("image/png", MimeResolver.sniff(png))
    }

    @Test
    fun `sniffs an ELF binary`() {
        val elf = byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00)
        assertEquals("application/x-elf", MimeResolver.sniff(elf))
    }

    @Test
    fun `separates the RIFF family by its subtype`() {
        assertEquals("audio/wav", MimeResolver.sniff(riff("WAVE")))
        assertEquals("image/webp", MimeResolver.sniff(riff("WEBP")))
        assertEquals("video/x-msvideo", MimeResolver.sniff(riff("AVI ")))
    }

    @Test
    fun `separates the ISO base media family by its brand`() {
        assertEquals("image/heic", MimeResolver.sniff(ftyp("heic")))
        assertEquals("image/avif", MimeResolver.sniff(ftyp("avif")))
        assertEquals("video/mp4", MimeResolver.sniff(ftyp("isom")))
    }

    @Test
    fun `tells an APK from a plain zip`() {
        assertEquals(
            "application/vnd.android.package-archive",
            MimeResolver.sniff(zipContaining("AndroidManifest.xml")),
        )
        assertEquals("application/zip", MimeResolver.sniff(zipContaining("hello.txt")))
    }

    @Test
    fun `an unsigned but plainly textual file sniffs as text`() {
        val text = "# Handoff\n\nflash the firmware.bin over fastboot\n".toByteArray()
        assertEquals("text/plain", MimeResolver.sniff(text))
    }

    @Test
    fun `random binary sniffs as octet-stream rather than guessing`() {
        val binary = ByteArray(64) { (it * 37 % 251).toByte() }
        binary[3] = 0 // A NUL is the strongest single signal that this is not text.
        assertEquals(MimeResolver.OCTET_STREAM, MimeResolver.sniff(binary))
    }

    @Test
    fun `an empty file sniffs as octet-stream`() {
        assertEquals(MimeResolver.OCTET_STREAM, MimeResolver.sniff(ByteArray(0)))
    }

    // --- the text test ----------------------------------------------------

    @Test
    fun `looksLikeText rejects anything containing a NUL`() {
        assertFalse(MimeResolver.looksLikeText(byteArrayOf(0x41, 0x00, 0x42)))
    }

    @Test
    fun `looksLikeText accepts tabs and newlines`() {
        assertTrue(MimeResolver.looksLikeText("a\tb\r\nc\n".toByteArray()))
    }

    @Test
    fun `looksLikeText rejects mostly-unprintable input`() {
        val mostlyControl = ByteArray(50) { 0x01 }
        assertFalse(MimeResolver.looksLikeText(mostlyControl))
    }

    // --- category mapping -------------------------------------------------

    @Test
    fun `categories come from the resolved type`() {
        assertEquals(FileCategory.IMAGES, FileCategory.ofMime("image/heic"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.ofMime("text/markdown"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.ofMime("application/zip"))
        assertEquals(FileCategory.OTHER, FileCategory.ofMime(MimeResolver.OCTET_STREAM))
    }

    @Test
    fun `only text-like types are offered to the contents searcher`() {
        assertTrue(FileCategory.isTextLike("text/markdown"))
        assertTrue(FileCategory.isTextLike("application/json"))
        // A firmware image is not grepped, however small it is.
        assertFalse(FileCategory.isTextLike(MimeResolver.OCTET_STREAM))
        assertFalse(FileCategory.isTextLike("application/pdf"))
    }

    // --- helpers ----------------------------------------------------------

    private fun riff(subtype: String): ByteArray {
        val out = ByteArray(16)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(out, 0)
        subtype.toByteArray(Charsets.US_ASCII).copyInto(out, 8)
        return out
    }

    private fun ftyp(brand: String): ByteArray {
        val out = ByteArray(16)
        "ftyp".toByteArray(Charsets.US_ASCII).copyInto(out, 4)
        brand.toByteArray(Charsets.US_ASCII).copyInto(out, 8)
        return out
    }

    private fun zipContaining(name: String): ByteArray {
        val header = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val body = name.toByteArray(Charsets.US_ASCII)
        val out = ByteArray(header.size + 26 + body.size)
        header.copyInto(out, 0)
        body.copyInto(out, header.size + 26)
        return out
    }
}
