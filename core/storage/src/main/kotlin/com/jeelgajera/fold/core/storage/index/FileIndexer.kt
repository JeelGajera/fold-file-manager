package com.jeelgajera.fold.core.storage.index

import com.jeelgajera.fold.core.storage.mime.FileCategory
import com.jeelgajera.fold.core.storage.mime.MimeResolver
import com.jeelgajera.fold.core.storage.provider.PathGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

/**
 * Walks the filesystem and keeps [FileIndexDao] in step with it.
 *
 * Two things it deliberately does not do:
 *
 * - **It never descends into a denied subtree.** The vault's blob directory is
 *   skipped through the same [PathGuard] the providers use, so vault contents
 *   cannot reach the index and therefore cannot reach search, the category
 *   counts, the widgets, or the LAN server.
 * - **It never reads file contents.** Types come from the extension table;
 *   sniffing 200 000 files to fill in a badge would cost more battery than the
 *   whole feature is worth.
 *
 * The walk is iterative rather than recursive: a symlink loop or a pathological
 * depth should not be able to overflow the stack, and a cancelled index should
 * stop at the next directory rather than at the end.
 */
class FileIndexer(
    private val dao: FileIndexDao,
    private val guard: PathGuard,
    private val io: CoroutineDispatcher,
) {

    /**
     * Indexes [root] and everything under it.
     *
     * @param onProgress called with the running file count, for the settings screen.
     * @return how many entries were written.
     */
    suspend fun indexTree(
        root: File,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        onProgress: (Int) -> Unit = {},
    ): Int = withContext(io) {
        val sweptAt = System.currentTimeMillis()
        var written = 0

        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        // Canonical paths already visited. A symlinked directory that points back
        // up the tree would otherwise index the same files forever.
        val seen = HashSet<String>()

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (dir, depth) = queue.removeFirst()
            if (depth > maxDepth) continue
            if (guard.isDenied(dir)) continue

            val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: continue
            if (!seen.add(canonical)) continue

            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            val batch = ArrayList<FileIndexEntity>(children.size)

            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (guard.isDenied(child)) continue

                val isDirectory = child.isDirectory
                if (isDirectory) queue.add(child to depth + 1)
                batch.add(child.toIndexEntity(dir.path, sweptAt, isDirectory))
            }

            if (batch.isNotEmpty()) {
                dao.replaceDirectory(dir.path, batch, sweptAt)
                written += batch.size
                onProgress(written)
            }
        }

        written
    }

    /** Re-indexes one directory. Cheap enough to run on every FileObserver overflow. */
    suspend fun indexDirectory(dir: File): Int = withContext(io) {
        if (guard.isDenied(dir)) return@withContext 0
        val sweptAt = System.currentTimeMillis()
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return@withContext 0
        val batch = children
            .filterNot { guard.isDenied(it) }
            .map { it.toIndexEntity(dir.path, sweptAt, it.isDirectory) }
        dao.replaceDirectory(dir.path, batch, sweptAt)
        batch.size
    }

    private fun File.toIndexEntity(parentPath: String, sweptAt: Long, isDirectory: Boolean): FileIndexEntity {
        val mime = if (isDirectory) DIRECTORY_MIME else MimeResolver.fromName(name)
        return FileIndexEntity(
            path = path,
            parentPath = parentPath,
            name = name,
            nameLower = name.lowercase(),
            extension = substringExtension(name),
            mimeType = mime,
            category = if (isDirectory) {
                FileCategory.OTHER.name
            } else {
                FileCategory.ofMime(mime).name
            },
            sizeBytes = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            isDirectory = isDirectory,
            isHidden = name.startsWith('.'),
            indexedAt = sweptAt,
        )
    }

    private fun substringExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "" else name.substring(dot + 1).lowercase()
    }

    companion object {
        private const val DIRECTORY_MIME = "inode/directory"

        /**
         * Depth ceiling for a full walk.
         *
         * Real storage does not nest this far. Something that does is either a
         * symlink cycle the canonical-path check somehow missed or a directory
         * bomb, and neither deserves an unbounded walk.
         */
        const val DEFAULT_MAX_DEPTH = 24
    }
}
