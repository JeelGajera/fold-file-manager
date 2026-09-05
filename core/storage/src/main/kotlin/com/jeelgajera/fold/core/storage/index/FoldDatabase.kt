package com.jeelgajera.fold.core.storage.index

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * FOLD's only database.
 *
 * It holds a metadata index and nothing else -- no file contents, no thumbnails,
 * no history of what was opened. It is disposable by design: deleting it costs a
 * re-index and loses nothing the filesystem does not already have. Settings ->
 * Cache offers exactly that.
 */
@Database(
    entities = [FileIndexEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FoldDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao

    companion object {
        const val NAME = "fold-index.db"
    }
}
