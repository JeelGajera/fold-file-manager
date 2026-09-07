package com.jeelgajera.fold.core.storage.mime

/**
 * The six buckets on the home screen, plus the catch-all.
 *
 * Derived from the MIME type rather than from the extension, so a file with no
 * extension that sniffs as a JPEG still counts as an image. [OTHER] is a real
 * category with a real count -- files never disappear from the totals because
 * FOLD could not classify them.
 */
enum class FileCategory {
    DOWNLOADS,
    DOCUMENTS,
    IMAGES,
    VIDEO,
    AUDIO,
    ARCHIVES,
    OTHER,
    ;

    companion object {

        /**
         * The category a file belongs to, given where it lives and what it is.
         *
         * [DOWNLOADS] is a *location*, not a MIME type -- it is the one bucket a
         * user thinks of as a place -- so it is decided first and wins outright.
         * That keeps the seven categories disjoint, which the storage meter
         * depends on: a PDF counted as both a download and a document would be
         * drawn twice and the bands would sum past the volume's used bytes.
         */
        fun of(path: String, mime: String): FileCategory = if (isUnderDownloads(path)) DOWNLOADS else ofMime(mime)

        /**
         * Matched segment-wise so `/Download` does not also catch a user's
         * `/Downloaded-2019` folder.
         */
        private fun isUnderDownloads(path: String): Boolean = path.split('/').any { segment ->
            segment.equals("Download", ignoreCase = true) || segment.equals("Downloads", ignoreCase = true)
        }

        fun ofMime(mime: String): FileCategory = when {
            mime.startsWith("image/") -> IMAGES
            mime.startsWith("video/") -> VIDEO
            mime.startsWith("audio/") -> AUDIO
            mime.startsWith("text/") -> DOCUMENTS
            mime in DOCUMENT_TYPES -> DOCUMENTS
            mime in ARCHIVE_TYPES -> ARCHIVES
            else -> OTHER
        }

        /**
         * Types the full-text searcher is willing to open.
         *
         * Anything not on this list is matched by name only, regardless of how
         * small it is -- opening an unknown binary to grep it is how a file
         * manager ends up reading a wallet or a key by accident.
         */
        fun isTextLike(mime: String): Boolean = mime.startsWith("text/") ||
            mime in setOf(
                "application/json",
                "application/yaml",
                "application/toml",
                "application/sql",
                "application/x-sh",
                "application/xml",
                "application/javascript",
            )

        private val DOCUMENT_TYPES = setOf(
            "application/pdf",
            "application/epub+zip",
            "application/rtf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/json",
            "application/yaml",
            "application/toml",
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
        )

        private val ARCHIVE_TYPES = setOf(
            "application/zip",
            "application/gzip",
            "application/x-bzip2",
            "application/x-xz",
            "application/zstd",
            "application/x-tar",
            "application/x-7z-compressed",
            "application/vnd.rar",
            "application/java-archive",
            "application/vnd.android.package-archive",
            "application/vnd.debian.binary-package",
            "application/x-iso9660-image",
        )
    }
}
