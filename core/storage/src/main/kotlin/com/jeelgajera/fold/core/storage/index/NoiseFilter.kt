package com.jeelgajera.fold.core.storage.index

/**
 * Which files are machine noise rather than something a person made.
 *
 * ### Why this exists at all, in an app whose premise is showing everything
 *
 * FOLD reads the filesystem precisely so that a `.md`, a `.log` or a firmware
 * image is visible when a media picker would hide it. Filtering therefore needs
 * a narrow and defensible definition, or it becomes the thing the app was built
 * to avoid.
 *
 * The distinction that makes it safe is *surface*, not *visibility*:
 *
 * - **Recent** answers "what did I just work on". A thumbnail an app regenerated
 *   two seconds ago is technically the most recently modified file on the device
 *   and is never the answer, so it is filtered.
 * - **Browse, search and the storage meter** are complete. Nothing here is
 *   applied to them. A file flagged as noise is still indexed, still listed in
 *   its folder, still searchable by name, and still counted in the totals.
 *
 * So this never removes a file from the app -- it only keeps one off the two
 * curated surfaces, and the category listing offers a control to bring them back.
 *
 * ### What deliberately is *not* noise
 *
 * `.log`, `.md`, `.apk`, `.epub`, `.json`, `.bin` and every other "the picker
 * cannot see this" type. Those are the app's reason to exist. Empty files are
 * not noise either: a note created a moment ago is zero bytes and belongs at the
 * top of Recent, which is exactly where a size rule would remove it from.
 */
object NoiseFilter {

    /**
     * Whether [path] is app-generated churn.
     *
     * Directories are never noise -- a folder is a place, and hiding it from a
     * listing would break navigation rather than tidy it.
     */
    fun isNoise(path: String, name: String, isDirectory: Boolean): Boolean {
        if (isDirectory) return false
        val segments = path.split('/').filter { it.isNotEmpty() }
        return isAppManaged(segments) || hasNoiseSegment(segments) || isTransient(name)
    }

    /**
     * Storage an app owns and manages on the user's behalf.
     *
     * `Android/media` is the one that matters in practice: it is readable, and it
     * is where messaging apps keep every image anyone ever sent, which would
     * otherwise drown Recent on a phone with an active group chat.
     */
    private fun isAppManaged(segments: List<String>): Boolean {
        val android = segments.indexOf("Android")
        if (android < 0 || android + 1 >= segments.size) return false
        return segments[android + 1] in APP_MANAGED_CHILDREN
    }

    /**
     * Matched segment-wise, never as a substring: a folder called `cached-notes`
     * is a person's folder and must not be caught by a rule aimed at `cache`.
     */
    private fun hasNoiseSegment(segments: List<String>): Boolean = segments.any { it.lowercase() in NOISE_SEGMENTS }

    private fun isTransient(name: String): Boolean {
        if (name == ".nomedia") return true
        val lower = name.lowercase()
        if (TRANSIENT_PREFIXES.any { lower.startsWith(it) }) return true
        if (TRANSIENT_SUFFIXES.any { lower.endsWith(it) }) return true
        val dot = lower.lastIndexOf('.')
        return dot > 0 && lower.substring(dot + 1) in TRANSIENT_EXTENSIONS
    }

    /** Children of `Android/` that belong to an app rather than to the user. */
    private val APP_MANAGED_CHILDREN = setOf("data", "obb", "media")

    private val NOISE_SEGMENTS = setOf(
        ".thumbnails",
        ".thumbs",
        "cache",
        "caches",
        ".cache",
        ".trash",
        ".trashed",
        "lost+found",
    )

    /** Half-written downloads and database sidecars. Live files, briefly. */
    private val TRANSIENT_EXTENSIONS = setOf(
        "tmp",
        "temp",
        "part",
        "partial",
        "crdownload",
        "opdownload",
        "aria2",
        "filepart",
        "!qb",
        "bak",
    )

    private val TRANSIENT_SUFFIXES = listOf("-journal", "-wal", "-shm", ".lock")

    /** MediaStore's trash and pending naming. */
    private val TRANSIENT_PREFIXES = listOf(".trashed-", ".pending-")
}
