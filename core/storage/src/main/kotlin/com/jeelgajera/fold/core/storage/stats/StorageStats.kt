package com.jeelgajera.fold.core.storage.stats

import android.os.Environment
import android.os.StatFs
import com.jeelgajera.fold.core.storage.index.CategoryTotal
import com.jeelgajera.fold.core.storage.mime.FileCategory

/**
 * What the home screen's meter draws.
 *
 * [usedBytes] and [totalBytes] come from `StatFs`, which reports the volume's own
 * accounting -- so it includes the operating system, other apps' private data,
 * and everything FOLD cannot see. [byCategory] comes from FOLD's index, which
 * only covers what it can read.
 *
 * The two therefore do not add up, and the UI must not pretend otherwise: the
 * categories are drawn as a proportion of what FOLD indexed, and the headline
 * number is the volume's. Presenting the difference as an "Other" slice would be
 * an invented figure.
 */
data class StorageSnapshot(
    val usedBytes: Long,
    val totalBytes: Long,
    val byCategory: Map<FileCategory, CategorySlice>,
) {
    val freeBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    /** Total of what FOLD actually indexed. Always <= [usedBytes]. */
    val indexedBytes: Long get() = byCategory.values.sumOf { it.bytes }
}

data class CategorySlice(val count: Int, val bytes: Long)

/** Reads the volume's own free/total figures. */
object VolumeStats {

    fun primaryVolume(): Pair<Long, Long> {
        val path = Environment.getExternalStorageDirectory()
        return try {
            val stat = StatFs(path.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            (total - available) to total
        } catch (e: IllegalArgumentException) {
            // The volume was unmounted between the check and the read.
            0L to 0L
        }
    }

    fun snapshot(totals: List<CategoryTotal>): StorageSnapshot {
        val (used, total) = primaryVolume()
        val byCategory = totals.associate { row ->
            val category = runCatching { FileCategory.valueOf(row.category) }
                .getOrDefault(FileCategory.OTHER)
            category to CategorySlice(row.count, row.bytes)
        }
        return StorageSnapshot(usedBytes = used, totalBytes = total, byCategory = byCategory)
    }
}
