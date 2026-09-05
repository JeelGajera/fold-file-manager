package com.jeelgajera.fold.core.storage.index

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One indexed file.
 *
 * The index exists so search can answer in milliseconds over a device with a
 * couple of hundred thousand files, rather than walking the tree per keystroke.
 * It stores metadata only -- names, sizes, dates, types. File *contents* are
 * never indexed: a content index of every text file on the device would be large,
 * would have to be kept in sync, and would be a second copy of the user's writing
 * sitting in a database. Content search reads the files live instead, and pays
 * for it in latency it reports honestly.
 *
 * Nothing inside the vault is ever written here. The indexer skips those subtrees
 * through `PathGuard`, so an unlocked vault does not leak into search results.
 */
@Entity(
    tableName = "file_index",
    indices = [
        Index("parentPath"),
        Index("nameLower"),
        Index("extension"),
        Index("category"),
        Index("lastModified"),
        Index("sizeBytes"),
    ],
)
data class FileIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "path")
    val path: String,

    val parentPath: String,
    val name: String,

    /** Lower-cased copy of [name] so a case-insensitive prefix search can use an index. */
    val nameLower: String,

    val extension: String,
    val mimeType: String,

    /** [com.jeelgajera.fold.core.storage.mime.FileCategory] name. */
    val category: String,

    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val isHidden: Boolean,

    /** When FOLD last saw this entry. Drives reconciliation: stale rows are pruned. */
    val indexedAt: Long,
)
