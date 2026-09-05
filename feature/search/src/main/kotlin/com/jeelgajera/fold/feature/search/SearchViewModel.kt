package com.jeelgajera.fold.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeelgajera.fold.core.storage.index.ContentMatch
import com.jeelgajera.fold.core.storage.index.ContentSearcher
import com.jeelgajera.fold.core.storage.index.FileIndexDao
import com.jeelgajera.fold.core.storage.index.FileIndexEntity
import com.jeelgajera.fold.core.storage.mime.FileCategory
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.measureTimeMillis

/** Where a search looks. */
enum class SearchScope { ALL_STORAGE, THIS_FOLDER, VAULT }

/** One result row. A name match, or a name match with a line from inside the file. */
data class SearchResult(
    val entry: FileIndexEntity,
    val contentMatch: ContentMatch? = null,
)

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL_STORAGE,
    val searchContents: Boolean = false,
    val categories: Set<FileCategory> = emptySet(),
    val minSizeBytes: Long = 0,
    val results: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    /** How long the last search took. Reported, not decorated. */
    val elapsedMillis: Long = 0,
    val indexedFiles: Int = 0,
)

/**
 * Search over the index, with an optional live pass through file contents.
 *
 * Two honest details drive the design here:
 *
 * **The timing readout is real.** The design mock shows "41 ms · 218 431 files".
 * That number is measured, not written in. A name search over the index genuinely
 * lands in that range; a contents search does not, and when contents are on the
 * readout shows the larger number it actually took. Faking it would train the
 * user to distrust the one part of the screen that is a measurement.
 *
 * **Contents are read, never indexed.** Turning CONTENTS on opens text-like files
 * under a size ceiling and streams them. Nothing is written anywhere. The cost is
 * latency, which the readout shows.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val indexDao: FileIndexDao,
    private val contentSearcher: ContentSearcher,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var scopePath: String = ""

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(indexedFiles = indexDao.fileCount())
        }
    }

    /** The folder BROWSE is currently in, for the THIS FOLDER scope. */
    fun setScopePath(path: String) {
        scopePath = path
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        restartSearch()
    }

    fun clearQuery() = setQuery("")

    fun setScope(scope: SearchScope) {
        _state.value = _state.value.copy(scope = scope)
        restartSearch()
    }

    fun toggleContents(enabled: Boolean) {
        _state.value = _state.value.copy(searchContents = enabled)
        restartSearch()
    }

    fun toggleCategory(category: FileCategory, enabled: Boolean) {
        val next = if (enabled) {
            _state.value.categories + category
        } else {
            _state.value.categories - category
        }
        _state.value = _state.value.copy(categories = next)
        restartSearch()
    }

    fun setMinSize(bytes: Long) {
        _state.value = _state.value.copy(minSizeBytes = bytes)
        restartSearch()
    }

    private fun restartSearch() {
        // Cancelling first is what makes the searcher's cancellation checks worth
        // having: a new keystroke abandons the previous pass inside a line rather
        // than at the end of a 200-file run.
        searchJob?.cancel()

        val current = _state.value
        if (current.query.isBlank()) {
            _state.value = current.copy(results = emptyList(), searching = false, elapsedMillis = 0)
            return
        }

        searchJob = viewModelScope.launch {
            // Debounce. Typing "firmware" should run one search, not eight.
            delay(DEBOUNCE_MILLIS)
            _state.value = _state.value.copy(searching = true)

            val preferences = settings.settings.first()
            val results = ArrayList<SearchResult>()
            var elapsed: Long

            elapsed = measureTimeMillis {
                val byName = indexDao.searchByName(
                    query = current.query.lowercase(),
                    scopePrefix = if (current.scope == SearchScope.THIS_FOLDER) scopePath else "",
                    minSize = current.minSizeBytes,
                    includeHidden = preferences.showHiddenFiles,
                    categories = current.categories.map { it.name },
                    categoryCount = current.categories.size,
                    limit = NAME_RESULT_LIMIT,
                )
                results.addAll(byName.map { SearchResult(it) })
            }

            // Name results appear immediately; the contents pass streams in after.
            _state.value = _state.value.copy(
                results = results.toList(),
                elapsedMillis = elapsed,
                searching = current.searchContents,
            )

            if (!current.searchContents) {
                _state.value = _state.value.copy(searching = false)
                return@launch
            }

            val startedAt = System.currentTimeMillis()
            val candidates = indexDao.textCandidates(
                scopePrefix = if (current.scope == SearchScope.THIS_FOLDER) scopePath else "",
                maxBytes = ContentSearcher.DEFAULT_MAX_FILE_BYTES,
                includeHidden = preferences.showHiddenFiles,
                textMimeTypes = TEXT_MIME_TYPES,
                limit = CONTENT_CANDIDATE_LIMIT,
            )
            val byPath = candidates.associateBy { it.path }

            contentSearcher.search(current.query, candidates).collect { match ->
                byPath[match.path.value]?.let { entry ->
                    results.add(SearchResult(entry, match))
                    _state.value = _state.value.copy(
                        results = results.toList(),
                        // The readout keeps climbing while contents stream, which
                        // is the honest thing for it to do.
                        elapsedMillis = System.currentTimeMillis() - startedAt + elapsed,
                    )
                }
            }

            _state.value = _state.value.copy(searching = false)
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 180L
        const val NAME_RESULT_LIMIT = 200
        const val CONTENT_CANDIDATE_LIMIT = 400

        /**
         * The types the contents pass will open.
         *
         * Kept in step with `FileCategory.isTextLike` -- the SQL needs a literal
         * list, and a mismatch would mean either opening a binary or silently
         * skipping a text file.
         */
        val TEXT_MIME_TYPES = listOf(
            "text/plain",
            "text/markdown",
            "text/csv",
            "text/html",
            "text/xml",
            "text/css",
            "text/javascript",
            "text/x-kotlin",
            "text/x-java-source",
            "text/x-typescript",
            "text/x-python",
            "text/x-ruby",
            "text/x-go",
            "text/x-rust",
            "text/x-c",
            "text/x-c++src",
            "text/x-c++hdr",
            "text/x-csharp",
            "text/x-swift",
            "text/x-diff",
            "text/tab-separated-values",
            "text/vtt",
            "application/json",
            "application/yaml",
            "application/toml",
            "application/sql",
            "application/x-sh",
            "application/xml",
        )
    }
}
