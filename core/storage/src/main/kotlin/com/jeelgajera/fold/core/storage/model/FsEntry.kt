package com.jeelgajera.fold.core.storage.model

/**
 * One thing in a directory listing.
 *
 * [mimeType] is always populated -- FOLD resolves a type for every file it lists,
 * falling back to `application/octet-stream` rather than dropping the entry.
 * Nothing is hidden for being unrecognised; that behaviour is the bug this app
 * exists to fix.
 */
data class FsEntry(
    val path: FsPath,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val isHidden: Boolean,
    val isSymlink: Boolean = false,
    val canRead: Boolean = true,
    val canWrite: Boolean = false,
    /** Populated for directories when it is cheap to know. Null means "not counted". */
    val childCount: Int? = null,
) {
    val extension: String get() = path.extension
}

/** How a listing is ordered. Mirrors the sort chip's DATE -> SIZE -> NAME cycle. */
enum class FsSort { DATE, SIZE, NAME }

/** Options applied to a listing at the provider boundary. */
data class ListOptions(
    val sort: FsSort = FsSort.DATE,
    val descending: Boolean = true,
    /** Dot-prefixed entries are omitted unless this is true. Visibility, not privacy. */
    val includeHidden: Boolean = false,
    /** Directories are grouped ahead of files. Off gives a pure sort. */
    val directoriesFirst: Boolean = true,
)
