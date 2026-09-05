package com.jeelgajera.fold.core.storage.index

import com.jeelgajera.fold.core.storage.mime.FileCategory
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.provider.FileSystemProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

/** A line inside a file that matched a contents search. */
data class ContentMatch(
    val path: FsPath,
    val name: String,
    val lineNumber: Int,
    val line: String,
    /** Where the query starts in [line], for highlighting. */
    val matchStart: Int,
    val matchLength: Int,
)

/**
 * Full-text search inside text-like files.
 *
 * Contents are searched live rather than from an index. The reason is a
 * privacy one before it is a technical one: a content index would be a second,
 * durable copy of everything the user has written, sitting in a database that
 * outlives the files themselves. FOLD reads the file, matches, and forgets.
 *
 * Three limits keep that honest:
 *
 * - **Type.** Only types [FileCategory.isTextLike] accepts are opened. FOLD does
 *   not grep an unknown binary hoping for the best -- that is how a file manager
 *   ends up reading a wallet file.
 * - **Size.** Files over [maxFileBytes] are skipped. A 2GB log is not worth six
 *   seconds of the user's time and a battery spike.
 * - **Cancellation.** Every file and every line checks for it, so a new keystroke
 *   abandons the previous search within a line rather than at the end of the run.
 *
 * Results stream as they are found: the first match appears while the rest of the
 * candidates are still being read.
 */
class ContentSearcher(
    private val provider: FileSystemProvider,
    private val io: CoroutineDispatcher,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxLineLength: Int = DEFAULT_MAX_LINE_LENGTH,
) {

    fun search(
        query: String,
        candidates: List<FileIndexEntity>,
        maxMatchesPerFile: Int = 3,
        maxTotalMatches: Int = 200,
    ): Flow<ContentMatch> = flow {
        if (query.isBlank()) return@flow
        val needle = query.lowercase()
        var emitted = 0

        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            if (emitted >= maxTotalMatches) return@flow
            if (candidate.isDirectory) continue
            if (candidate.sizeBytes > maxFileBytes) continue
            if (!FileCategory.isTextLike(candidate.mimeType)) continue

            val path = FsPath.raw(candidate.path)
            val stream = provider.read(path).getOrNull() ?: continue

            var inFile = 0
            try {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var lineNumber = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        lineNumber++
                        if (line.length > maxLineLength) continue

                        val at = line.lowercase().indexOf(needle)
                        if (at < 0) continue

                        emit(
                            ContentMatch(
                                path = path,
                                name = candidate.name,
                                lineNumber = lineNumber,
                                line = line.trim(),
                                // trim() shifts the match; recompute against the
                                // trimmed string so highlighting lands correctly.
                                matchStart = line.trim().lowercase().indexOf(needle).coerceAtLeast(0),
                                matchLength = query.length,
                            )
                        )
                        emitted++
                        inFile++
                        if (inFile >= maxMatchesPerFile || emitted >= maxTotalMatches) break
                    }
                }
            } catch (e: Exception) {
                // An unreadable or mis-encoded file is skipped, not surfaced. The
                // user asked for matches, not for a list of what could not be read.
                continue
            }
        }
    }.flowOn(io)

    companion object {
        /**
         * The size ceiling. Generous enough for a day of logcat, small enough that
         * the worst case is a fraction of a second per file.
         */
        const val DEFAULT_MAX_FILE_BYTES = 8L * 1024 * 1024

        /** Minified bundles and base64 blobs are one enormous line. Skip them. */
        const val DEFAULT_MAX_LINE_LENGTH = 4_000
    }
}
