package com.jeelgajera.fold.core.storage.model

import kotlinx.serialization.Serializable

/** Which addressing scheme a path uses. */
enum class FsScheme {
    /** An absolute path in the java.io.File tree. Requires MANAGE_EXTERNAL_STORAGE. */
    RAW,

    /** A Storage Access Framework document URI. Works with only per-tree grants. */
    SAF,
}

/**
 * A location in the filesystem, independent of which provider resolves it.
 *
 * This is the type that lets the whole app stop caring whether it is running
 * with All Files Access or with a handful of SAF tree grants. A screen holds an
 * [FsPath]; the provider decides what it means.
 *
 * [value] is an absolute POSIX path for [FsScheme.RAW] and a document URI string
 * for [FsScheme.SAF]. It is stored normalised: no trailing slash except at the
 * root, no empty segments, no `.` or `..` segments. Constructing one through
 * [raw] is the only supported way to get a RAW path, and it normalises for you.
 */
@Serializable
data class FsPath(
    val scheme: FsScheme,
    val value: String,
) {
    /** The last segment. `/storage/emulated/0/Download` -> `Download`. */
    val name: String
        get() = when (scheme) {
            FsScheme.RAW -> value.substringAfterLast('/').ifEmpty { "/" }
            FsScheme.SAF -> value.substringAfterLast("%2F").substringAfterLast('/')
        }

    /** Everything after the final dot in [name], lowercased. Empty when there is none. */
    val extension: String
        get() {
            val n = name
            val dot = n.lastIndexOf('.')
            // A leading dot is a hidden-file marker, not an extension: `.gitconfig`
            // has no extension, `.tar.gz` has `gz`.
            return if (dot <= 0) "" else n.substring(dot + 1).lowercase()
        }

    /** True for dot-prefixed names. A visibility property -- never a security one. */
    val isHiddenName: Boolean
        get() = name.startsWith('.')

    /** The containing directory, or null at a root. Only meaningful for RAW paths. */
    val parent: FsPath?
        get() {
            if (scheme != FsScheme.RAW) return null
            if (value == "/") return null
            val cut = value.lastIndexOf('/')
            return when {
                cut < 0 -> null
                cut == 0 -> FsPath(FsScheme.RAW, "/")
                else -> FsPath(FsScheme.RAW, value.substring(0, cut))
            }
        }

    /** Appends a single path segment. Rejects separators so it cannot be used to escape. */
    fun child(segment: String): FsPath {
        require(scheme == FsScheme.RAW) { "child() is only defined for RAW paths" }
        require('/' !in segment) { "A path segment cannot contain '/': $segment" }
        require(segment != "." && segment != "..") { "Relative segments are not addressable" }
        return raw(if (value == "/") "/$segment" else "$value/$segment")
    }

    /** True when this path is [other] or sits underneath it. Segment-aware, not prefix-aware. */
    fun isWithin(other: FsPath): Boolean {
        if (scheme != other.scheme) return false
        if (value == other.value) return true
        val base = if (other.value.endsWith('/')) other.value else other.value + "/"
        return value.startsWith(base)
    }

    /** The segments, for a breadcrumb. `/storage/emulated/0` -> [storage, emulated, 0]. */
    fun segments(): List<String> =
        value.split('/').filter { it.isNotEmpty() }

    override fun toString(): String = value

    companion object {
        /**
         * Builds a normalised RAW path.
         *
         * Normalisation resolves `.` and `..` textually and collapses repeated
         * separators. It is *not* a security boundary on its own -- a symlink can
         * still point outside the tree, which is why every provider canonicalises
         * against the real filesystem before doing anything. See `PathGuard`.
         */
        fun raw(path: String): FsPath {
            val absolute = if (path.startsWith('/')) path else "/$path"
            val out = ArrayList<String>()
            absolute.split('/').forEach { segment ->
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                    else -> out.add(segment)
                }
            }
            return FsPath(FsScheme.RAW, if (out.isEmpty()) "/" else "/" + out.joinToString("/"))
        }

        fun saf(uri: String): FsPath = FsPath(FsScheme.SAF, uri)
    }
}
