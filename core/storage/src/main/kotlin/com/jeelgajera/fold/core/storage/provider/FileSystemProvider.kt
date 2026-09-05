package com.jeelgajera.fold.core.storage.provider

import com.jeelgajera.fold.core.storage.model.FsCapabilities
import com.jeelgajera.fold.core.storage.model.FsChange
import com.jeelgajera.fold.core.storage.model.FsEntry
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.model.ListOptions
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * The single seam every read and write in FOLD passes through.
 *
 * Two implementations exist: [RawFileProvider], which walks the `java.io.File`
 * tree and needs MANAGE_EXTERNAL_STORAGE, and [SafDocumentProvider], which works
 * inside Storage Access Framework tree grants. Nothing above this interface --
 * no screen, no ViewModel, no server route -- knows which one it has.
 *
 * That indirection is the most important decision in the project, and it is not
 * ceremony. Play Store review for All Files Access can be refused, and the
 * policy can tighten after a release. An app hard-wired to raw file access dies
 * with that decision; this one degrades. [capabilities] is what the UI reads to
 * decide which controls to offer, so the reduced-permission screens are a
 * supported mode rather than a failure path.
 *
 * ### Contract
 *
 * Implementations must:
 * - canonicalise every incoming path and refuse anything that resolves outside
 *   an allowed root, symlinks included;
 * - refuse every path inside the vault, no matter who is asking. Enforcing this
 *   here rather than in the LAN server's route handlers means a future route,
 *   or a bug in one, cannot expose vault blobs;
 * - never hide an entry for having an unrecognised type;
 * - return a `Result.failure` for an expected error (permission, missing file,
 *   no space) and throw only for programming mistakes.
 *
 * Both implementations are verified against one shared contract test suite.
 */
interface FileSystemProvider {

    val capabilities: FsCapabilities

    /** Lists [path]'s children. Fails if [path] is a file, missing, or out of bounds. */
    suspend fun list(path: FsPath, options: ListOptions = ListOptions()): Result<List<FsEntry>>

    /** Metadata for a single entry. */
    suspend fun stat(path: FsPath): Result<FsEntry>

    /** Opens [path] for reading. The caller closes the stream. */
    suspend fun read(path: FsPath): Result<InputStream>

    /**
     * Opens [path] for writing, creating it if needed.
     *
     * @param append when false the existing contents are truncated.
     */
    suspend fun write(path: FsPath, append: Boolean = false): Result<OutputStream>

    suspend fun createDirectory(path: FsPath): Result<FsEntry>

    /**
     * Moves or renames. Falls back to copy-then-delete across volumes.
     *
     * The two schemes address [to] differently, and the difference is real
     * rather than papered over: a RAW move takes the full destination path,
     * because it can name a file that does not exist yet. A SAF move takes the
     * destination *directory*, because a document URI cannot be constructed for
     * a document the provider has not created. Callers get the right shape from
     * `FsPath.scheme`.
     */
    suspend fun move(from: FsPath, to: FsPath): Result<Unit>

    /** Copies. [to] follows the same scheme-dependent rule as [move]. */
    suspend fun copy(from: FsPath, to: FsPath): Result<Unit>

    /** Deletes a file, or a directory and everything under it. */
    suspend fun delete(path: FsPath): Result<Unit>

    suspend fun exists(path: FsPath): Boolean

    /**
     * Live changes under [path].
     *
     * Emits [FsChange.Overflow] when the underlying watcher cannot guarantee
     * delivery, which is the caller's cue to re-list rather than to patch.
     */
    fun observe(path: FsPath): Flow<FsChange>
}

/** Expected, user-visible failures. Anything else is a bug and is thrown. */
sealed class FsError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(val path: FsPath) : FsError("No such file: ${path.value}")
    class NotADirectory(val path: FsPath) : FsError("Not a directory: ${path.value}")
    class PermissionDenied(val path: FsPath) : FsError("Permission denied: ${path.value}")

    /**
     * The path resolved outside every allowed root, or inside the vault.
     *
     * The message deliberately does not echo the resolved target: this error can
     * reach an HTTP response, and confirming where a traversal attempt landed
     * would leak the layout of the device.
     */
    class OutOfBounds(val path: FsPath) : FsError("Path is not accessible")

    class AlreadyExists(val path: FsPath) : FsError("Already exists: ${path.value}")
    class OutOfSpace(val path: FsPath) : FsError("Not enough space for ${path.value}")
    class Unsupported(val operation: String) :
        FsError("$operation is not available with the current storage access")

    class Io(message: String, cause: Throwable? = null) : FsError(message, cause)
}
