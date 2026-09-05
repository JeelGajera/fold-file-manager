package com.jeelgajera.fold.core.storage.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The Doto-side formatting: byte counts, rates and dates.
 *
 * FOLD reports storage in decimal units because that is what the sticker on the
 * phone means. Calling 256 000 000 000 bytes "238 GB" would be technically
 * defensible and would make the app look like it had lost 18GB of the user's
 * storage.
 */
object Formatting {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    /**
     * `412.6 MB`, `18.4 KB`, `0 B`.
     *
     * One decimal place below 100 and none above it, so the number stays the same
     * width as it grows -- important when a transfer readout is counting up in
     * place and a jittering string width would make it hard to read.
     */
    fun bytes(value: Long): String {
        if (value < 1000) return "$value B"
        var size = value.toDouble()
        var unit = 0
        while (size >= 1000 && unit < UNITS.lastIndex) {
            size /= 1000
            unit++
        }
        return if (size >= 100) {
            String.format(Locale.US, "%.0f %s", size, UNITS[unit])
        } else {
            String.format(Locale.US, "%.1f %s", size, UNITS[unit])
        }
    }

    /** The headline pair on the home screen: `83.4` and `GB`, formatted apart. */
    fun bytesSplit(value: Long): Pair<String, String> {
        val formatted = bytes(value)
        val space = formatted.lastIndexOf(' ')
        return if (space < 0) formatted to "" else {
            formatted.substring(0, space) to formatted.substring(space + 1)
        }
    }

    /** `11.4 MB/s`. */
    fun rate(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"

    /** `32 s left`, `4 m 10 s left`. Null when there is not enough information yet. */
    fun eta(remainingBytes: Long, bytesPerSecond: Long): String? {
        if (bytesPerSecond <= 0) return null
        val seconds = remainingBytes / bytesPerSecond
        return when {
            seconds < 60 -> "$seconds s left"
            seconds < 3600 -> "${seconds / 60} m ${seconds % 60} s left"
            else -> "${seconds / 3600} h ${(seconds % 3600) / 60} m left"
        }
    }

    /** `4:59`, for the vault's auto-lock countdown. */
    fun countdown(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    /**
     * `3 Sep` for this year, `3 Sep 2025` for anything older.
     *
     * Dropping the year for recent files is what keeps a listing's right-hand
     * column narrow enough not to squeeze the file name.
     */
    fun date(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0) return "--"
        val sameYear = yearOf(millis) == yearOf(now)
        val pattern = if (sameYear) "d MMM" else "d MMM yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    /** `18.4 KB · 3 Sep`, the standard second line of a file row. */
    fun fileMeta(sizeBytes: Long, lastModified: Long, isDirectory: Boolean, childCount: Int?): String =
        if (isDirectory) {
            val items = childCount?.let { "$it items" } ?: "Folder"
            "$items · ${date(lastModified)}"
        } else {
            "${bytes(sizeBytes)} · ${date(lastModified)}"
        }

    /** `218 431` -- grouped with thin spaces, matching the design's numerals. */
    fun count(value: Int): String {
        val digits = abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
        return if (value < 0) "-$grouped" else grouped
    }

    private fun yearOf(millis: Long): String =
        SimpleDateFormat("yyyy", Locale.US).format(Date(millis))
}
