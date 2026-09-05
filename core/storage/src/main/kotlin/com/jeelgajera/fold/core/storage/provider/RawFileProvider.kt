package com.jeelgajera.fold.core.storage.provider

import android.os.Build
import android.os.Environment
import android.os.FileObserver
import com.jeelgajera.fold.core.storage.mime.MimeResolver
import com.jeelgajera.fold.core.storage.model.FsCapabilities
import com.jeelgajera.fold.core.storage.model.FsChange
import com.jeelgajera.fold.core.storage.model.FsEntry
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.model.FsRoot
import com.jeelgajera.fold.core.storage.model.FsSort
import com.jeelgajera.fold.core.storage.model.ListOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * The primary provider: the real filesystem, through `java.io.File`.
 *
 * This is what MANAGE_EXTERNAL_STORAGE buys, and it is the only way to see every
 * file on the device. It deliberately never touches MediaStore -- MediaStore is
 * the bug FOLD exists to fix, and a listing that came from a media index would
 * be missing exactly the files people open this app to find.
 *
 * Every path goes through [guard] before anything happens to it, including
 * write targets that do not exist yet.
 */
class RawFileProvider(
    private val guard: PathGuard,
    private val roots: List<FsRoot>,
    private val io: CoroutineDispatcher,
) : FileSystemProvider {

    override val capabilities: FsCapabilities = FsCapabilities(
        canListArbitraryPaths = true,
        canWrite = true,
        canDelete = true,
        canMove = true,
        canObserve = true,
        canIndexWholeDevice = true,
        roots = roots,
    )

    override suspend fun list(path: FsPath, options: ListOptions): Result<List<FsEntry>> =
        withContext(io) {
            val dir = guard.resolve(path).getOrElse { return@withContext Result.failure(it) }
            if (!dir.exists()) return@withContext Result.failure(FsError.NotFound(path))
            if (!dir.isDirectory) return@withContext Result.failure(FsError.NotADirectory(path))

            // listFiles() returns null both for "not a directory" and for
            // "cannot read", and the two need different errors.
            val children = dir.listFiles()
                ?: return@withContext Result.failure(FsError.PermissionDenied(path))

            val entries = children
                .asSequence()
                .filter { options.includeHidden || !it.name.startsWith('.') }
                // A child that escapes the allowlist -- via a symlink out of the
                // tree, say -- is dropped from the listing rather than shown and
                // then refused on open.
                .filter { !guard.isDenied(it) }
                .map { it.toEntry() }
                .toList()

            Result.success(entries.sortedWith(comparatorFor(options)))
        }

    override suspend fun stat(path: FsPath): Result<FsEntry> = withContext(io) {
        val file = guard.resolve(path).getOrElse { return@withContext Result.failure(it) }
        if (!file.exists()) return@withContext Result.failure(FsError.NotFound(path))
        Result.success(file.toEntry())
    }

    override suspend fun read(path: FsPath): Result<InputStream> = withContext(io) {
        val file = guard.resolve(path).getOrElse { return@withContext Result.failure(it) }
        if (!file.exists()) return@withContext Result.failure(FsError.NotFound(path))
        if (file.isDirectory) return@withContext Result.failure(FsError.NotADirectory(path))
        try {
            Result.success(FileInputStream(file) as InputStream)
        } catch (e: Exception) {
            Result.failure(e.asFsError(path))
        }
    }

    override suspend fun write(path: FsPath, append: Boolean): Result<OutputStream> =
        withContext(io) {
            val file = guard.resolve(path).getOrElse { return@withContext Result.failure(it) }
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    return@withContext Result.failure(FsError.Io("Cannot create ${parent.path}"))
                }
            }
            try {
                Result.success(FileOutputStream(file, append) as OutputStream)
            } catch (e: Exception) {
                Result.failure(e.asFsError(path))
            }
        }

    override suspend fun createDirectory(path: FsPath): Result<FsEntry> = withContext(io) {
        val dir = guard.resolve(path).getOrElse { return@withContext Result.failure(it) }
        if (dir.exists()) return@withContext Result.failure(FsError.AlreadyExists(path))
        if (!dir.mkdirs()) return@withContext Result.failure(FsError.Io("Cannot create ${path.value}"))
        Result.success(dir.toEntry())
    }

    override suspend fun move(from: FsPath, to: FsPath): Result<Unit> = withContext(io) {
        val source = guard.resolve(from).getOrElse { return@withContext Result.failure(it) }
        val target = guard.resolve(to).getOrElse { return@withContext Result.failure(it) }
        if (!source.exists()) return@withContext Result.failure(FsError.NotFound(from))
        if (target.exists()) return@withContext Result.failure(FsError.AlreadyExists(to))

        // rename(2) is atomic but only within one filesystem. Across volumes --
        // internal storage to an SD card -- it fails and the copy path takes over.
        if (source.renameTo(target)) return@withContext Result.success(Unit)

        copyRecursively(source, target).fold(
            onSuccess = {
                if (source.deleteRecursively()) {
                    Result.success(Unit)
                } else {
                    // The copy landed; the original did not go away. Report it
                    // rather than claiming a move that only half happened.
                    Result.failure(FsError.Io("Copied to ${to.value} but could not remove the original"))
                }
            },
            onFailure = { failure ->
                target.deleteRecursively()
                Result.failure(failure)
            },
        )
    }

    override suspend fun copy(from: FsPath, to: FsPath): Result<Unit> = withContext(io) {
        val source = guard.resolve(from).getOrElse { return@withContext Result.failure(it) }
        val target = guard.resolve(to).getOrElse { return@withContext Result.failure(it) }
        if (!source.exists()) return@withContext Result.failure(FsError.NotFound(from))
        if (target.exists()) return@withContext Result.failure(FsError.AlreadyExists(to))
        copyRecursively(source, target)
    }

    override suspend fun delete(path: FsPath): Result<Unit> = withContext(io) {
        val file = guard.resolve(path).getOrElse { return@withContext Result.failure(it) }
        if (!file.exists()) return@withContext Result.failure(FsError.NotFound(path))
        if (file.deleteRecursively()) {
            Result.success(Unit)
        } else {
            Result.failure(FsError.Io("Could not delete ${path.value}"))
        }
    }

    override suspend fun exists(path: FsPath): Boolean = withContext(io) {
        guard.resolve(path).getOrNull()?.exists() == true
    }

    /**
     * Live changes from `FileObserver`.
     *
     * FileObserver is not reliable and is not treated as if it were: it watches a
     * single directory rather than a tree, it silently drops events when the
     * kernel queue overflows, and it stops for good when the watched directory is
     * removed. Both of those last two arrive as [FsChange.Overflow], which tells
     * the caller to re-list. `WorkManager` reconciliation covers the rest.
     */
    override fun observe(path: FsPath): Flow<FsChange> = callbackFlow {
        val dir = guard.resolve(path).getOrElse {
            close(it)
            return@callbackFlow
        }

        val observer = object : FileObserver(dir, WATCHED_EVENTS) {
            override fun onEvent(event: Int, relativePath: String?) {
                // IN_Q_OVERFLOW and IN_IGNORED live above the low 12 bits, so
                // they are checked on the raw event before it is masked.
                if (event and IN_Q_OVERFLOW != 0 || event and IN_IGNORED != 0) {
                    trySend(FsChange.Overflow(path))
                    return
                }
                val masked = event and ALL_EVENTS
                val child = relativePath?.let { name ->
                    runCatching { path.child(name) }.getOrNull()
                } ?: path

                val change = when {
                    masked and CREATE != 0 -> FsChange.Created(child)
                    masked and DELETE != 0 || masked and DELETE_SELF != 0 -> FsChange.Deleted(child)
                    masked and MOVED_FROM != 0 -> FsChange.MovedFrom(child)
                    masked and MOVED_TO != 0 -> FsChange.MovedTo(child)
                    masked and CLOSE_WRITE != 0 || masked and MODIFY != 0 -> FsChange.Modified(child)
                    else -> null
                }
                change?.let { trySend(it) }
            }
        }

        observer.startWatching()
        awaitClose { observer.stopWatching() }
    }.buffer(64, BufferOverflow.DROP_OLDEST).flowOn(io)

    // --- internals -------------------------------------------------------

    private fun File.toEntry(): FsEntry {
        val directory = isDirectory
        val symlink = isSymlink()
        return FsEntry(
            path = FsPath.raw(path),
            name = name,
            isDirectory = directory,
            sizeBytes = if (directory) 0L else length(),
            lastModifiedMillis = lastModified(),
            // Listings use the extension table only. Sniffing every file in a
            // 6000-image folder would cost 6000 reads for a badge nobody is
            // looking at; the share sheet sniffs when it matters.
            mimeType = if (directory) MIME_DIRECTORY else MimeResolver.fromName(name),
            isHidden = name.startsWith('.'),
            isSymlink = symlink,
            canRead = canRead(),
            canWrite = canWrite(),
            childCount = if (directory) list()?.size else null,
        )
    }

    private fun File.isSymlink(): Boolean = try {
        Files.isSymbolicLink(toPath())
    } catch (e: Exception) {
        // toPath() throws InvalidPathException on names the NIO layer rejects.
        // Those files still exist and still get listed; they just are not
        // reported as links.
        false
    }

    private fun copyRecursively(source: File, target: File): Result<Unit> = try {
        if (source.isDirectory) {
            source.copyRecursively(target, overwrite = false)
        } else {
            target.parentFile?.mkdirs()
            Files.copy(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.COPY_ATTRIBUTES,
                LinkOption.NOFOLLOW_LINKS,
            )
        }
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(e.asFsError(FsPath.raw(source.path)))
    }

    private fun Throwable.asFsError(path: FsPath): FsError = when {
        this is FsError -> this
        this is SecurityException -> FsError.PermissionDenied(path)
        message?.contains("No space left", ignoreCase = true) == true -> FsError.OutOfSpace(path)
        message?.contains("Permission denied", ignoreCase = true) == true ->
            FsError.PermissionDenied(path)
        else -> FsError.Io(message ?: "I/O failure on ${path.value}", this)
    }

    private fun comparatorFor(options: ListOptions): Comparator<FsEntry> {
        val base: Comparator<FsEntry> = when (options.sort) {
            FsSort.DATE -> compareBy { it.lastModifiedMillis }
            FsSort.SIZE -> compareBy { it.sizeBytes }
            FsSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val directed = if (options.descending) base.reversed() else base
        return if (options.directoriesFirst) {
            compareByDescending<FsEntry> { it.isDirectory }.then(directed)
        } else {
            directed
        }
    }

    companion object {
        const val MIME_DIRECTORY = "inode/directory"

        private const val WATCHED_EVENTS = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.DELETE_SELF or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY

        /** `FileObserver.ALL_EVENTS` is not public as a mask constant on every API level. */
        private const val ALL_EVENTS = 0x00000FFF
        private const val IN_Q_OVERFLOW = 0x00004000
        private const val IN_IGNORED = 0x00008000

        /**
         * The roots FOLD offers when it has All Files Access.
         *
         * Shared storage first because that is where a person's files are; the
         * filesystem root is offered too, because refusing to show it while
         * claiming "every file on your device" would be a lie.
         */
        fun defaultRoots(): List<FsRoot> = buildList {
            val shared = Environment.getExternalStorageDirectory()
            add(
                FsRoot(
                    path = FsPath.raw(shared.path),
                    label = "Internal storage",
                    isPrimary = true,
                )
            )
            add(FsRoot(path = FsPath.raw("/"), label = "Device", isPrimary = false))
        }

        /** True when the process currently holds All Files Access. */
        fun hasAllFilesAccess(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }
}
