package com.jeelgajera.fold.core.storage.provider

import com.jeelgajera.fold.core.storage.model.FsPath
import java.io.File
import java.io.IOException

/**
 * The one place a textual path becomes a real file, and the only place that
 * decides whether FOLD is allowed to touch it.
 *
 * Path traversal is the top risk in this app -- not because of the browser UI,
 * which only ever hands over paths it produced itself, but because of the LAN
 * server, where every path arrives from a stranger on the network. So the check
 * lives in the provider layer rather than in a route handler: a new endpoint, or
 * a bug in an existing one, cannot route around it.
 *
 * Three things are checked, in this order:
 *
 * 1. **Canonicalisation.** `File.getCanonicalFile()` resolves `.`, `..` and every
 *    symlink along the path against the real filesystem. Textual normalisation
 *    alone is not enough: a path through a symlinked directory normalises to
 *    something harmless-looking while resolving somewhere else entirely.
 * 2. **Denied roots win.** The vault's blob directory is denied unconditionally,
 *    before any allowlist is consulted. There is no argument, no flag and no
 *    caller identity that turns this off.
 * 3. **Allowed roots.** The result must sit inside one of the explicitly rooted
 *    allowlist entries. Containment is checked segment-wise on canonical paths,
 *    so a sibling directory whose name merely starts with an allowed root's name
 *    is not treated as being inside it.
 *
 * A failure never says where the path resolved to. This error can reach an HTTP
 * response, and telling an attacker that their traversal landed somewhere real
 * would confirm the device's layout for them.
 */
class PathGuard(
    allowedRoots: List<File>,
    deniedRoots: List<File>,
) {
    private val allowed: List<File> = allowedRoots.mapNotNull { it.canonicalOrNull() }
    private val denied: List<File> = deniedRoots.mapNotNull { it.canonicalOrNull() }

    /**
     * Canonicalises [path] and returns the file only if it is in bounds.
     *
     * Used for reads and writes alike; a write target that does not exist yet
     * still canonicalises through its existing ancestors, so a symlinked parent
     * directory cannot be used to plant a file outside the tree.
     */
    fun resolve(path: FsPath): Result<File> {
        val canonical = File(path.value).canonicalOrNull()
            ?: return Result.failure(FsError.OutOfBounds(path))
        return check(canonical, path)
    }

    /** Resolves a child of an already-checked directory. Rejects separators in [segment]. */
    fun resolveChild(parent: File, segment: String, reported: FsPath): Result<File> {
        if (segment.isEmpty() || segment == "." || segment == ".." || segment.contains('/')) {
            return Result.failure(FsError.OutOfBounds(reported))
        }
        val canonical = File(parent, segment).canonicalOrNull()
            ?: return Result.failure(FsError.OutOfBounds(reported))
        return check(canonical, reported)
    }

    /** True when [file] is inside a denied root. Used by the indexer to skip subtrees. */
    fun isDenied(file: File): Boolean {
        val canonical = file.canonicalOrNull() ?: return true
        return denied.any { canonical.isInside(it) }
    }

    private fun check(canonical: File, reported: FsPath): Result<File> {
        if (denied.any { canonical.isInside(it) }) {
            return Result.failure(FsError.OutOfBounds(reported))
        }
        if (allowed.none { canonical.isInside(it) }) {
            return Result.failure(FsError.OutOfBounds(reported))
        }
        return Result.success(canonical)
    }

    private companion object {
        /**
         * Segment-aware containment on two already-canonical paths.
         *
         * Plain string-prefix comparison is what lets a sibling directory pass as
         * a child; comparing on a trailing separator is what stops it.
         */
        fun File.isInside(root: File): Boolean {
            val self = path
            val base = root.path
            if (self == base) return true
            val prefix = if (base.endsWith(File.separator)) base else base + File.separator
            return self.startsWith(prefix)
        }

        fun File.canonicalOrNull(): File? = try {
            canonicalFile
        } catch (e: IOException) {
            // A dangling symlink, a path longer than the kernel allows, or a
            // directory the process cannot traverse. All of them mean "not ours".
            null
        } catch (e: SecurityException) {
            null
        }
    }
}
