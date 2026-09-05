package com.jeelgajera.fold.core.storage.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Queries over the metadata index.
 *
 * Name matching is a `LIKE` over the lower-cased name column rather than an FTS
 * table. That is a deliberate trade: an external-content FTS table would need the
 * index's primary key to be a rowid alias, which would mean carrying a synthetic
 * id alongside the path and keeping the two in step through every move and
 * delete. A `LIKE` scan over a few hundred thousand indexed rows lands in the low
 * tens of milliseconds on a phone, and the search screen shows the real elapsed
 * time rather than a decorative one.
 */
@Dao
interface FileIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<FileIndexEntity>)

    @Query("DELETE FROM file_index WHERE path = :path")
    suspend fun deleteByPath(path: String)

    /** Removes a directory and everything beneath it. Used when a folder disappears. */
    @Query("DELETE FROM file_index WHERE path = :path OR path LIKE :path || '/%'")
    suspend fun deleteSubtree(path: String)

    /**
     * Prunes rows the last sweep did not touch.
     *
     * A file deleted while FOLD was not watching leaves a row behind; this is how
     * reconciliation removes it without a second full walk to diff against.
     */
    @Query("DELETE FROM file_index WHERE parentPath = :parentPath AND indexedAt < :before")
    suspend fun pruneStale(parentPath: String, before: Long)

    @Query("SELECT COUNT(*) FROM file_index WHERE isDirectory = 0")
    fun observeFileCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_index WHERE isDirectory = 0")
    suspend fun fileCount(): Int

    @Query(
        """
        SELECT category, COUNT(*) AS count, COALESCE(SUM(sizeBytes), 0) AS bytes
        FROM file_index
        WHERE isDirectory = 0
        GROUP BY category
        """
    )
    fun observeCategoryTotals(): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT * FROM file_index
        WHERE isDirectory = 0 AND (:includeHidden OR isHidden = 0)
        ORDER BY lastModified DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int, includeHidden: Boolean): Flow<List<FileIndexEntity>>

    /**
     * Name search.
     *
     * [scopePrefix] restricts the search to one subtree; pass an empty string for
     * the whole index. [categories] empty means every category.
     */
    @Query(
        """
        SELECT * FROM file_index
        WHERE nameLower LIKE '%' || :query || '%'
          AND (:scopePrefix = '' OR path LIKE :scopePrefix || '/%')
          AND (:minSize = 0 OR sizeBytes >= :minSize)
          AND (:includeHidden OR isHidden = 0)
          AND (:categoryCount = 0 OR category IN (:categories))
        ORDER BY
          CASE WHEN nameLower = :query THEN 0
               WHEN nameLower LIKE :query || '%' THEN 1
               ELSE 2 END,
          lastModified DESC
        LIMIT :limit
        """
    )
    suspend fun searchByName(
        query: String,
        scopePrefix: String,
        minSize: Long,
        includeHidden: Boolean,
        categories: List<String>,
        categoryCount: Int,
        limit: Int,
    ): List<FileIndexEntity>

    /** Candidates for a contents search: text-like files under the size ceiling. */
    @Query(
        """
        SELECT * FROM file_index
        WHERE isDirectory = 0
          AND sizeBytes <= :maxBytes
          AND (:scopePrefix = '' OR path LIKE :scopePrefix || '/%')
          AND (:includeHidden OR isHidden = 0)
          AND mimeType IN (:textMimeTypes)
        ORDER BY lastModified DESC
        LIMIT :limit
        """
    )
    suspend fun textCandidates(
        scopePrefix: String,
        maxBytes: Long,
        includeHidden: Boolean,
        textMimeTypes: List<String>,
        limit: Int,
    ): List<FileIndexEntity>

    @Query("SELECT * FROM file_index WHERE parentPath = :parentPath")
    suspend fun childrenOf(parentPath: String): List<FileIndexEntity>

    @Query("DELETE FROM file_index")
    suspend fun clear()

    /** Replaces one directory's children atomically, so a listing is never half-updated. */
    @Transaction
    suspend fun replaceDirectory(parentPath: String, entries: List<FileIndexEntity>, sweptAt: Long) {
        upsertAll(entries)
        pruneStale(parentPath, sweptAt)
    }
}

/** One row of the home screen's category grid. */
data class CategoryTotal(
    val category: String,
    val count: Int,
    val bytes: Long,
)
