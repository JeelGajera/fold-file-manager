package com.jeelgajera.fold.core.storage.mime

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * FOLD's own MIME resolution.
 *
 * This class is the technical half of the reason the app exists. Android's
 * `MimeTypeMap` does not know `.md`, `.log`, `.epub`, `.apk` or half a dozen
 * other types people actually download, and an app that asks it for a type gets
 * `null` and -- in most file managers -- either hides the file or refuses to
 * share it. FOLD never does either. Resolution runs in three stages and the last
 * one always succeeds:
 *
 * 1. **Extension table.** Cheap, deterministic, and where 99% of files land.
 * 2. **Content sniffing.** For files with no extension or an unknown one, read
 *    the first few hundred bytes and match a magic-number signature. This is what
 *    resolves a type for a file such as `blob_9f2c11`, which has no extension
 *    to go on.
 * 3. **`application/octet-stream`.** The fallback is a type, not an error. The
 *    file is listed, openable and shareable either way.
 *
 * Stage 2 is deliberately opt-in per call ([resolve] with a stream) because it
 * costs a read: directory listings use the extension table only, and the share
 * sheet -- where getting the type wrong means the receiving app rejects the file
 * -- pays for the sniff.
 */
object MimeResolver {

    const val OCTET_STREAM = "application/octet-stream"

    /**
     * Extension to MIME type.
     *
     * Entries the platform's own map gets wrong or misses entirely are marked.
     * Text-like types matter twice over: they decide what full-text search will
     * open, and they decide whether a note app will accept a shared file.
     */
    private val byExtension: Map<String, String> = buildMap {
        // --- Text and markup. The gap this app was built to close. ---
        put("md", "text/markdown")          // Not in MimeTypeMap. The headline case.
        put("markdown", "text/markdown")
        put("mdx", "text/markdown")
        put("txt", "text/plain")
        put("log", "text/plain")            // Not in MimeTypeMap.
        put("csv", "text/csv")
        put("tsv", "text/tab-separated-values")
        put("rtf", "application/rtf")
        put("html", "text/html")
        put("htm", "text/html")
        put("xml", "text/xml")
        put("css", "text/css")
        put("ics", "text/calendar")
        put("vcf", "text/vcard")
        put("srt", "application/x-subrip")
        put("vtt", "text/vtt")

        // --- Source and config. Searchable as text. ---
        put("kt", "text/x-kotlin")
        put("kts", "text/x-kotlin")
        put("java", "text/x-java-source")
        put("js", "text/javascript")
        put("mjs", "text/javascript")
        put("ts", "text/x-typescript")
        put("tsx", "text/x-typescript")
        put("jsx", "text/javascript")
        put("py", "text/x-python")
        put("rb", "text/x-ruby")
        put("go", "text/x-go")
        put("rs", "text/x-rust")
        put("c", "text/x-c")
        put("h", "text/x-c")
        put("cpp", "text/x-c++src")
        put("hpp", "text/x-c++hdr")
        put("cs", "text/x-csharp")
        put("swift", "text/x-swift")
        put("sh", "application/x-sh")
        put("bash", "application/x-sh")
        put("json", "application/json")
        put("jsonl", "application/json")
        put("yaml", "application/yaml")
        put("yml", "application/yaml")
        put("toml", "application/toml")
        put("ini", "text/plain")
        put("conf", "text/plain")
        put("properties", "text/plain")
        put("gradle", "text/plain")
        put("sql", "application/sql")
        put("patch", "text/x-diff")
        put("diff", "text/x-diff")

        // --- Documents ---
        put("pdf", "application/pdf")
        put("epub", "application/epub+zip")  // Missing on many devices.
        put("mobi", "application/x-mobipocket-ebook")
        put("azw3", "application/vnd.amazon.ebook")
        put("doc", "application/msword")
        put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        put("xls", "application/vnd.ms-excel")
        put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        put("ppt", "application/vnd.ms-powerpoint")
        put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        put("odt", "application/vnd.oasis.opendocument.text")
        put("ods", "application/vnd.oasis.opendocument.spreadsheet")

        // --- Images ---
        put("jpg", "image/jpeg")
        put("jpeg", "image/jpeg")
        put("png", "image/png")
        put("gif", "image/gif")
        put("webp", "image/webp")
        put("bmp", "image/bmp")
        put("svg", "image/svg+xml")
        put("heic", "image/heic")
        put("heif", "image/heif")
        put("avif", "image/avif")
        put("ico", "image/x-icon")
        put("tif", "image/tiff")
        put("tiff", "image/tiff")
        put("dng", "image/x-adobe-dng")

        // --- Audio ---
        put("mp3", "audio/mpeg")
        put("m4a", "audio/mp4")
        put("aac", "audio/aac")
        put("flac", "audio/flac")
        put("ogg", "audio/ogg")
        put("opus", "audio/opus")
        put("wav", "audio/wav")
        put("mid", "audio/midi")
        put("amr", "audio/amr")

        // --- Video ---
        put("mp4", "video/mp4")
        put("m4v", "video/mp4")
        put("mkv", "video/x-matroska")
        put("webm", "video/webm")
        put("avi", "video/x-msvideo")
        put("mov", "video/quicktime")
        put("3gp", "video/3gpp")
        // `.ts` is deliberately NOT mapped to video/mp2t. On a phone the
        // extension is overwhelmingly TypeScript, and a transport stream that
        // guesses wrong opens in a video player instead of an editor. Transport
        // streams still resolve correctly through content sniffing.

        // --- Archives and packages ---
        put("zip", "application/zip")
        put("gz", "application/gzip")
        put("tgz", "application/gzip")
        put("bz2", "application/x-bzip2")
        put("xz", "application/x-xz")
        put("zst", "application/zstd")
        put("tar", "application/x-tar")
        put("7z", "application/x-7z-compressed")
        put("rar", "application/vnd.rar")
        put("apk", "application/vnd.android.package-archive")
        put("apks", "application/vnd.android.package-archive")
        put("aab", "application/x-authorware-bin")
        put("jar", "application/java-archive")
        put("deb", "application/vnd.debian.binary-package")
        put("iso", "application/x-iso9660-image")

        // --- Firmware and device images. The reason people sideload a file manager. ---
        put("img", "application/octet-stream")
        put("bin", "application/octet-stream")
        put("elf", "application/x-elf")
        put("dtb", "application/octet-stream")

        // --- Keys and certificates. Vault candidates. ---
        put("pem", "application/x-pem-file")
        put("crt", "application/x-x509-ca-cert")
        put("cer", "application/x-x509-ca-cert")
        put("p12", "application/x-pkcs12")
        put("pfx", "application/x-pkcs12")
        put("asc", "application/pgp-signature")
        put("gpg", "application/pgp-encrypted")

        // --- Fonts ---
        put("ttf", "font/ttf")
        put("otf", "font/otf")
        put("woff", "font/woff")
        put("woff2", "font/woff2")
    }

    /**
     * Magic-number signatures, longest first.
     *
     * Each entry is an offset, the bytes expected there, and the type they imply.
     * Only formats with an unambiguous, stable prefix are listed -- a wrong guess
     * here is worse than the honest `application/octet-stream` fallback, because
     * the share sheet will act on it.
     */
    private val signatures: List<Signature> = listOf(
        Signature(0, byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D), "application/pdf"),           // %PDF-
        Signature(0, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), "image/png"),
        Signature(0, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "image/jpeg"),
        Signature(0, "GIF89a".toByteArray(Charsets.US_ASCII), "image/gif"),
        Signature(0, "GIF87a".toByteArray(Charsets.US_ASCII), "image/gif"),
        Signature(0, byteArrayOf(0x42, 0x4D), "image/bmp"),                                   // BM
        Signature(0, byteArrayOf(0x7F, 0x45, 0x4C, 0x46), "application/x-elf"),               // .ELF
        Signature(0, byteArrayOf(0x1F, 0x8B.toByte()), "application/gzip"),
        Signature(0, "BZh".toByteArray(Charsets.US_ASCII), "application/x-bzip2"),
        Signature(0, byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00), "application/x-xz"),
        Signature(0, byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte()), "application/zstd"),
        Signature(0, byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C), "application/x-7z-compressed"),
        Signature(0, "Rar!".toByteArray(Charsets.US_ASCII), "application/vnd.rar"),
        Signature(0, "OggS".toByteArray(Charsets.US_ASCII), "audio/ogg"),
        Signature(0, "fLaC".toByteArray(Charsets.US_ASCII), "audio/flac"),
        Signature(0, "ID3".toByteArray(Charsets.US_ASCII), "audio/mpeg"),
        Signature(0, byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()), "video/x-matroska"),
        Signature(0, "RIFF".toByteArray(Charsets.US_ASCII), null),        // Needs the subtype at offset 8.
        Signature(4, "ftyp".toByteArray(Charsets.US_ASCII), null),        // Needs the brand at offset 8.
        Signature(0, "%!PS".toByteArray(Charsets.US_ASCII), "application/postscript"),
        Signature(0, "-----BEGIN ".toByteArray(Charsets.US_ASCII), "application/x-pem-file"),
        Signature(0, byteArrayOf(0x50, 0x4B, 0x03, 0x04), null),          // ZIP: needs a peek inside.
    )

    /** How many bytes are read when sniffing. Enough for every signature above. */
    const val SNIFF_BYTES = 512

    /**
     * The extension-table lookup. Never touches the file.
     *
     * @return the resolved type, or null when the extension is unknown -- callers
     *   that cannot sniff should treat null as [OCTET_STREAM].
     */
    fun fromExtension(extension: String): String? =
        byExtension[extension.lowercase().removePrefix(".")]

    /** The extension lookup with the fallback already applied. */
    fun fromName(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return OCTET_STREAM
        return fromExtension(name.substring(dot + 1)) ?: OCTET_STREAM
    }

    /**
     * Full three-stage resolution.
     *
     * Sniffing runs only when the extension is unknown, so a correctly-named file
     * costs nothing extra. An unreadable file falls through to [OCTET_STREAM]
     * rather than failing -- the entry still gets listed.
     */
    fun resolve(file: File): String {
        fromExtension(file.extension)?.let { return it }
        return try {
            file.inputStream().use { sniff(it) }
        } catch (e: IOException) {
            OCTET_STREAM
        } catch (e: SecurityException) {
            OCTET_STREAM
        }
    }

    /**
     * Stage 2 on its own, for callers that already hold a stream.
     *
     * The stream is read from its current position and not reset -- pass a fresh
     * one, or one that supports mark/reset.
     */
    fun sniff(input: InputStream): String {
        val head = ByteArray(SNIFF_BYTES)
        val read = input.readAtMost(head)
        if (read <= 0) return OCTET_STREAM
        return sniff(head, read)
    }

    /** Stage 2 against a buffer already in memory. Pure, and the unit tests' entry point. */
    fun sniff(head: ByteArray, length: Int = head.size): String {
        for (signature in signatures) {
            if (!signature.matches(head, length)) continue
            signature.mime?.let { return it }
            // Signatures with no fixed type need a second look.
            refine(signature, head, length)?.let { return it }
        }
        // Nothing matched. If it reads as text, say so -- a plain-text type is
        // what lets the file open in a note app rather than a hex viewer.
        return if (looksLikeText(head, length)) "text/plain" else OCTET_STREAM
    }

    private fun refine(signature: Signature, head: ByteArray, length: Int): String? = when {
        // ZIP container. The interesting members sit at a fixed-ish offset in the
        // first local file header, which is enough to tell an EPUB or an APK from
        // a plain archive without inflating anything.
        signature.offset == 0 && signature.bytes.size == 4 && head[0] == 0x50.toByte() -> {
            val window = String(head, 0, length.coerceAtMost(256), Charsets.ISO_8859_1)
            when {
                window.contains("mimetypeapplication/epub+zip") -> "application/epub+zip"
                window.contains("AndroidManifest.xml") -> "application/vnd.android.package-archive"
                window.contains("word/") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                window.contains("xl/") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                window.contains("ppt/") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "application/zip"
            }
        }

        // RIFF: the real type is the four bytes at offset 8.
        signature.offset == 0 && signature.bytes.size == 4 && head[0] == 0x52.toByte() -> {
            when (asciiAt(head, 8, 4, length)) {
                "WAVE" -> "audio/wav"
                "WEBP" -> "image/webp"
                "AVI " -> "video/x-msvideo"
                else -> null
            }
        }

        // ISO base media: the brand at offset 8 separates MP4 from HEIC from AVIF.
        signature.offset == 4 -> when (asciiAt(head, 8, 4, length)) {
            "heic", "heix", "hevc" -> "image/heic"
            "mif1", "msf1" -> "image/heif"
            "avif" -> "image/avif"
            "qt  " -> "video/quicktime"
            "3gp4", "3gp5" -> "video/3gpp"
            else -> "video/mp4"
        }

        else -> null
    }

    /**
     * A conservative text test: no NUL bytes and mostly printable.
     *
     * Deliberately strict. Calling a binary "text/plain" would put it in front of
     * the full-text searcher and into a note app's import path, and both of those
     * are worse outcomes than an honest octet-stream.
     */
    fun looksLikeText(head: ByteArray, length: Int = head.size): Boolean {
        if (length == 0) return false
        var printable = 0
        for (i in 0 until length) {
            val b = head[i].toInt() and 0xFF
            if (b == 0) return false
            val ok = b >= 0x20 || b == 0x09 || b == 0x0A || b == 0x0D
            if (ok) printable++
        }
        return printable.toDouble() / length >= 0.90
    }

    private fun asciiAt(head: ByteArray, offset: Int, length: Int, available: Int): String? {
        if (offset + length > available) return null
        return String(head, offset, length, Charsets.US_ASCII)
    }

    private data class Signature(val offset: Int, val bytes: ByteArray, val mime: String?) {
        fun matches(head: ByteArray, available: Int): Boolean {
            if (offset + bytes.size > available) return false
            for (i in bytes.indices) {
                if (head[offset + i] != bytes[i]) return false
            }
            return true
        }

        // ByteArray in a data class needs these written out.
        override fun equals(other: Any?): Boolean =
            other is Signature && offset == other.offset &&
                bytes.contentEquals(other.bytes) && mime == other.mime

        override fun hashCode(): Int =
            (offset * 31 + bytes.contentHashCode()) * 31 + (mime?.hashCode() ?: 0)
    }

    /** Fills [buffer] as far as the stream allows. Returns bytes read, or -1 at EOF. */
    private fun InputStream.readAtMost(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
