package com.jeelgajera.fold.core.storage.provider

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.jeelgajera.fold.core.storage.mime.MimeResolver
import com.jeelgajera.fold.core.storage.model.FsCapabilities
import com.jeelgajera.fold.core.storage.model.FsChange
import com.jeelgajera.fold.core.storage.model.FsEntry
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.model.FsRoot
import com.jeelgajera.fold.core.storage.model.FsScheme
import com.jeelgajera.fold.core.storage.model.FsSort
import com.jeelgajera.fold.core.storage.model.ListOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.InputStream
import java.io.OutputStream

/**
 * The fallback provider: Storage Access Framework tree grants.
 *
 * This is what FOLD runs on when All Files Access is refused, revoked, or
 * unavailable. It is genuinely less capable -- it can only see inside trees the
 * user has explicitly handed over, and it cannot watch for changes -- and
 * [capabilities] says so, which is what lets the UI hide the controls that would
 * not work instead of failing them at the last moment.
 *
 * Every operation is scoped to a persisted tree grant. There is no path
 * arithmetic here and no traversal risk of the usual kind: a document URI cannot
 * address something outside the tree it came from, because the platform checks
 * the grant on every call. What *is* checked here is the vault, and the document
 * ID's tree prefix, so a URI forged by a caller cannot reach a tree the user
 * never granted.
 */
class SafDocumentProvider(
    private val context: Context,
    private val grantedTrees: List<FsRoot>,
    private val io: CoroutineDispatcher,
) : FileSystemProvider {

    private val resolver: ContentResolver get() = context.contentResolver

    override val capabilities: FsCapabilities = FsCapabilities(
        canListArbitraryPaths = false,
        canWrite = grantedTrees.isNotEmpty(),
        canDelete = grantedTrees.isNotEmpty(),
        // SAF can rename inside a tree and move between two granted trees, but not
        // out of one, so the UI offers move only where both ends are in scope.
        canMove = grantedTrees.isNotEmpty(),
        canObserve = false,
        canIndexWholeDevice = false,
        roots = grantedTrees,
    )

    override suspend fun list(path: FsPath, options: ListOptions): Result<List<FsEntry>> =
        withContext(io) {
            val treeUri = uriOf(path).getOrElse { return@withContext Result.failure(it) }
            val documentId = try {
                if (DocumentsContract.isDocumentUri(context, treeUri)) {
                    DocumentsContract.getDocumentId(treeUri)
                } else {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }
            } catch (e: IllegalArgumentException) {
                return@withContext Result.failure(FsError.OutOfBounds(path))
            }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val entries = ArrayList<FsEntry>()
            try {
                resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val entry = cursor.toEntry(treeUri) ?: continue
                        if (!options.includeHidden && entry.isHidden) continue
                        entries.add(entry)
                    }
                } ?: return@withContext Result.failure(FsError.PermissionDenied(path))
            } catch (e: SecurityException) {
                // The persisted grant was revoked between the last listing and now.
                return@withContext Result.failure(FsError.PermissionDenied(path))
            } catch (e: Exception) {
                return@withContext Result.failure(FsError.Io(e.message ?: "SAF query failed", e))
            }

            Result.success(entries.sortedWith(comparatorFor(options)))
        }

    override suspend fun stat(path: FsPath): Result<FsEntry> = withContext(io) {
        val uri = uriOf(path).getOrElse { return@withContext Result.failure(it) }
        try {
            resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.toEntry(uri)?.let { return@withContext Result.success(it) }
                }
            }
            Result.failure(FsError.NotFound(path))
        } catch (e: SecurityException) {
            Result.failure(FsError.PermissionDenied(path))
        }
    }

    override suspend fun read(path: FsPath): Result<InputStream> = withContext(io) {
        val uri = uriOf(path).getOrElse { return@withContext Result.failure(it) }
        try {
            val stream = resolver.openInputStream(uri)
                ?: return@withContext Result.failure(FsError.NotFound(path))
            Result.success(stream)
        } catch (e: SecurityException) {
            Result.failure(FsError.PermissionDenied(path))
        } catch (e: Exception) {
            Result.failure(FsError.Io(e.message ?: "Cannot read", e))
        }
    }

    override suspend fun write(path: FsPath, append: Boolean): Result<OutputStream> =
        withContext(io) {
            val uri = uriOf(path).getOrElse { return@withContext Result.failure(it) }
            // "wa" is append, "wt" truncates. Plain "w" leaves the tail of a longer
            // previous file in place, which is a classic SAF corruption bug.
            val mode = if (append) "wa" else "wt"
            try {
                val stream = resolver.openOutputStream(uri, mode)
                    ?: return@withContext Result.failure(FsError.NotFound(path))
                Result.success(stream)
            } catch (e: SecurityException) {
                Result.failure(FsError.PermissionDenied(path))
            } catch (e: Exception) {
                Result.failure(FsError.Io(e.message ?: "Cannot write", e))
            }
        }

    override suspend fun createDirectory(path: FsPath): Result<FsEntry> =
        Result.failure(
            FsError.Unsupported(
                "Creating a folder by path. Pick the parent folder first so FOLD can " +
                    "create it inside a tree you granted.",
            )
        )

    /** [to] addresses the destination *directory*. See the interface's note on move. */
    override suspend fun move(from: FsPath, to: FsPath): Result<Unit> = withContext(io) {
        val source = uriOf(from).getOrElse { return@withContext Result.failure(it) }
        val target = uriOf(to).getOrElse { return@withContext Result.failure(it) }
        try {
            val sourceParent = parentOf(source)
                ?: return@withContext Result.failure(FsError.Unsupported("Move out of a granted tree"))
            val moved = DocumentsContract.moveDocument(resolver, source, sourceParent, target)
            if (moved != null) Result.success(Unit) else Result.failure(FsError.Io("Move refused"))
        } catch (e: SecurityException) {
            Result.failure(FsError.PermissionDenied(from))
        } catch (e: UnsupportedOperationException) {
            // Not every DocumentsProvider implements move. Copy-then-delete is the
            // caller's job here, not a silent substitution -- a "move" that leaves
            // the original behind on failure is worse than an honest refusal.
            Result.failure(FsError.Unsupported("This storage provider does not support move"))
        }
    }

    /** [to] addresses the destination *directory*. See the interface's note on move. */
    override suspend fun copy(from: FsPath, to: FsPath): Result<Unit> = withContext(io) {
        val source = uriOf(from).getOrElse { return@withContext Result.failure(it) }
        val target = uriOf(to).getOrElse { return@withContext Result.failure(it) }
        try {
            val copied = DocumentsContract.copyDocument(resolver, source, target)
            if (copied != null) Result.success(Unit) else Result.failure(FsError.Io("Copy refused"))
        } catch (e: SecurityException) {
            Result.failure(FsError.PermissionDenied(from))
        } catch (e: UnsupportedOperationException) {
            Result.failure(FsError.Unsupported("This storage provider does not support copy"))
        }
    }

    override suspend fun delete(path: FsPath): Result<Unit> = withContext(io) {
        val uri = uriOf(path).getOrElse { return@withContext Result.failure(it) }
        try {
            if (DocumentsContract.deleteDocument(resolver, uri)) {
                Result.success(Unit)
            } else {
                Result.failure(FsError.Io("Could not delete ${path.name}"))
            }
        } catch (e: SecurityException) {
            Result.failure(FsError.PermissionDenied(path))
        }
    }

    override suspend fun exists(path: FsPath): Boolean = stat(path).isSuccess

    /**
     * SAF has no change notification worth the name.
     *
     * `ContentResolver.registerContentObserver` on a tree URI fires
     * inconsistently across DocumentsProviders and not at all for some of them,
     * so rather than pretend, this emits a single [FsChange.Overflow] telling the
     * caller to poll. [FsCapabilities.canObserve] is false for the same reason,
     * and the UI shows a manual refresh instead of implying live updates.
     */
    override fun observe(path: FsPath): Flow<FsChange> = flowOf(FsChange.Overflow(path))

    // --- internals -------------------------------------------------------

    private fun uriOf(path: FsPath): Result<Uri> {
        if (path.scheme != FsScheme.SAF) {
            return Result.failure(FsError.OutOfBounds(path))
        }
        val uri = runCatching { Uri.parse(path.value) }.getOrNull()
            ?: return Result.failure(FsError.OutOfBounds(path))

        // A document URI is only in bounds if it descends from a tree the user
        // actually granted. Without this check a caller could hand over any
        // document URI it had obtained elsewhere.
        val withinGrant = grantedTrees.any { root ->
            val rootUri = runCatching { Uri.parse(root.path.value) }.getOrNull() ?: return@any false
            uri.authority == rootUri.authority && documentIdOf(uri)
                ?.startsWith(treeIdOf(rootUri) ?: return@any false) == true
        }
        if (!withinGrant) return Result.failure(FsError.OutOfBounds(path))
        return Result.success(uri)
    }

    private fun documentIdOf(uri: Uri): String? = runCatching {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.getDocumentId(uri)
        } else {
            DocumentsContract.getTreeDocumentId(uri)
        }
    }.getOrNull()

    private fun treeIdOf(uri: Uri): String? =
        runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

    private fun parentOf(uri: Uri): Uri? = runCatching {
        val id = DocumentsContract.getDocumentId(uri)
        val parentId = id.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentId.isEmpty()) null else DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
    }.getOrNull()

    private fun Cursor.toEntry(treeUri: Uri): FsEntry? {
        val documentId = getStringOrNull(DocumentsContract.Document.COLUMN_DOCUMENT_ID) ?: return null
        val name = getStringOrNull(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: return null
        val rawMime = getStringOrNull(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val size = getLongOrNull(DocumentsContract.Document.COLUMN_SIZE) ?: 0L
        val modified = getLongOrNull(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0L
        val flags = getLongOrNull(DocumentsContract.Document.COLUMN_FLAGS) ?: 0L

        val isDirectory = rawMime == DocumentsContract.Document.MIME_TYPE_DIR
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        // DocumentsProviders hand back `application/octet-stream` for anything
        // they do not recognise, which is exactly the `.md` problem again. FOLD
        // re-resolves from the name before accepting that answer.
        val mime = when {
            isDirectory -> RawFileProvider.MIME_DIRECTORY
            rawMime == null || rawMime == MimeResolver.OCTET_STREAM -> MimeResolver.fromName(name)
            else -> rawMime
        }

        return FsEntry(
            path = FsPath.saf(uri.toString()),
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else size,
            lastModifiedMillis = modified,
            mimeType = mime,
            isHidden = name.startsWith('.'),
            canRead = true,
            canWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE.toLong() != 0L,
            childCount = null,
        )
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index)
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
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )

        /** The intent that asks for one more tree. */
        fun openTreeIntent(): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }

        /**
         * Persists a freshly-granted tree so it survives a reboot.
         *
         * Without this the grant dies with the process and the user is asked
         * again on next launch, which is the single most irritating thing a
         * SAF-based file manager can do.
         */
        fun persistGrant(context: Context, treeUri: Uri) {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        /** The trees FOLD still holds, read back from the system on launch. */
        fun persistedRoots(context: Context): List<FsRoot> =
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { permission ->
                    val uri = permission.uri
                    FsRoot(
                        path = FsPath.saf(uri.toString()),
                        label = treeLabel(uri),
                        isPrimary = false,
                    )
                }

        /** A readable name for a tree URI, for the limited-access screen. */
        private fun treeLabel(uri: Uri): String {
            val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                ?: return uri.lastPathSegment.orEmpty()
            return id.substringAfterLast(':').substringAfterLast('/').ifEmpty { id }
        }
    }
}
