package com.jeelgajera.fold.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeelgajera.fold.core.storage.index.FileIndexDao
import com.jeelgajera.fold.core.storage.index.FileIndexEntity
import com.jeelgajera.fold.core.storage.mime.FileCategory
import com.jeelgajera.fold.core.storage.model.FsChange
import com.jeelgajera.fold.core.storage.model.FsEntry
import com.jeelgajera.fold.core.storage.model.FsPath
import com.jeelgajera.fold.core.storage.model.FsSort
import com.jeelgajera.fold.core.storage.model.ListOptions
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import com.jeelgajera.fold.core.storage.provider.FileSystemProviderFactory
import com.jeelgajera.fold.core.storage.provider.FsError
import com.jeelgajera.fold.core.storage.stats.StorageSnapshot
import com.jeelgajera.fold.core.storage.stats.VolumeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the browse screen is showing right now. */
data class BrowseUiState(
    val path: FsPath? = null,
    val entries: List<FsEntry> = emptyList(),
    val selection: Set<String> = emptySet(),
    val sort: FsSort = FsSort.DATE,
    val gridView: Boolean = false,
    val showHidden: Boolean = false,
    val loading: Boolean = true,
    /** Non-null when the listing failed. Carries a sentence, not an exception name. */
    val error: String? = null,
    val isLimitedAccess: Boolean = false,
) {
    val hasSelection: Boolean get() = selection.isNotEmpty()
    val totalBytes: Long get() = entries.filterNot { it.isDirectory }.sumOf { it.sizeBytes }
    val crumbs: List<String> get() = path?.segments().orEmpty()
}

/** What the home screen is showing. */
data class HomeUiState(
    val storage: StorageSnapshot? = null,
    val categories: List<CategoryTile> = emptyList(),
    val recents: List<FileIndexEntity> = emptyList(),
    val indexedFiles: Int = 0,
)

data class CategoryTile(
    val category: FileCategory,
    val label: String,
    val count: Int,
    val bytes: Long,
) {
    /**
     * How full this category is relative to the largest one, as 0..3 dots.
     *
     * A relative measure rather than an absolute one: three dots means "this is
     * where your storage went", which is the question the home screen answers.
     */
    fun dots(largest: Long): Int = when {
        largest <= 0 -> 0
        bytes >= largest * 2 / 3 -> 3
        bytes >= largest / 3 -> 2
        bytes > 0 -> 1
        else -> 0
    }
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val providerFactory: FileSystemProviderFactory,
    private val settings: SettingsRepository,
    private val indexDao: FileIndexDao,
) : ViewModel() {

    private val _browse = MutableStateFlow(BrowseUiState())
    val browse: StateFlow<BrowseUiState> = _browse.asStateFlow()

    private var watcher: Job? = null

    /**
     * The home screen, assembled from the index.
     *
     * Category counts come from FOLD's own index rather than from MediaStore, so
     * a `.md` file counts as a document instead of not existing.
     */
    val home: StateFlow<HomeUiState> = combine(
        indexDao.observeCategoryTotals(),
        indexDao.observeRecent(limit = 4, includeHidden = false),
        indexDao.observeFileCount(),
    ) { totals, recents, count ->
        val snapshot = VolumeStats.snapshot(totals)
        val tiles = FileCategory.entries
            .filter { it != FileCategory.OTHER || snapshot.byCategory.containsKey(it) }
            .map { category ->
                val slice = snapshot.byCategory[category]
                CategoryTile(
                    category = category,
                    label = category.label(),
                    count = slice?.count ?: 0,
                    bytes = slice?.bytes ?: 0,
                )
            }
        HomeUiState(
            storage = snapshot,
            categories = tiles,
            recents = recents,
            indexedFiles = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            settings.settings.map { it.showHiddenFiles to it.gridView }.collect { (hidden, grid) ->
                val changed = _browse.value.showHidden != hidden
                _browse.value = _browse.value.copy(showHidden = hidden, gridView = grid)
                // The hidden-files toggle changes what a listing contains, so the
                // current folder is re-read rather than left stale.
                if (changed) _browse.value.path?.let { open(it) }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { preferences ->
                if (_browse.value.path == null) {
                    _browse.value = _browse.value.copy(sort = preferences.defaultSort)
                    providerFactory.current.capabilities.roots.firstOrNull()?.let { open(it.path) }
                }
            }
        }
    }

    /** Opens a directory and starts watching it for changes. */
    fun open(path: FsPath) {
        _browse.value = _browse.value.copy(loading = true, error = null, selection = emptySet())
        viewModelScope.launch {
            val provider = providerFactory.current
            val options = ListOptions(
                sort = _browse.value.sort,
                includeHidden = _browse.value.showHidden,
            )
            provider.list(path, options).fold(
                onSuccess = { entries ->
                    _browse.value = _browse.value.copy(
                        path = path,
                        entries = entries,
                        loading = false,
                        isLimitedAccess = provider.capabilities.isLimited,
                    )
                },
                onFailure = { failure ->
                    _browse.value = _browse.value.copy(
                        path = path,
                        entries = emptyList(),
                        loading = false,
                        error = failure.userMessage(),
                        isLimitedAccess = provider.capabilities.isLimited,
                    )
                },
            )
            watch(path)
        }
    }

    /** Up one level, stopping at the provider's root. */
    fun goUp(): Boolean {
        val current = _browse.value.path ?: return false
        val roots = providerFactory.current.capabilities.roots.map { it.path }
        if (roots.any { it.value == current.value }) return false
        val parent = current.parent ?: return false
        open(parent)
        return true
    }

    fun toggleSelection(entry: FsEntry) {
        val selection = _browse.value.selection
        _browse.value = _browse.value.copy(
            selection = if (entry.path.value in selection) {
                selection - entry.path.value
            } else {
                selection + entry.path.value
            },
        )
    }

    fun clearSelection() {
        _browse.value = _browse.value.copy(selection = emptySet())
    }

    fun selectedPaths(): List<FsPath> =
        _browse.value.entries.filter { it.path.value in _browse.value.selection }.map { it.path }

    /** Cycles DATE -> SIZE -> NAME, matching the sort chip. */
    fun cycleSort() {
        val next = when (_browse.value.sort) {
            FsSort.DATE -> FsSort.SIZE
            FsSort.SIZE -> FsSort.NAME
            FsSort.NAME -> FsSort.DATE
        }
        _browse.value = _browse.value.copy(sort = next)
        _browse.value.path?.let { open(it) }
        viewModelScope.launch { settings.setDefaultSort(next) }
    }

    fun toggleView() {
        viewModelScope.launch { settings.setGridView(!_browse.value.gridView) }
    }

    fun setShowHidden(show: Boolean) {
        viewModelScope.launch { settings.setShowHiddenFiles(show) }
    }

    fun delete(paths: List<FsPath>) {
        viewModelScope.launch {
            val provider = providerFactory.current
            paths.forEach { provider.delete(it) }
            clearSelection()
            _browse.value.path?.let { open(it) }
        }
    }

    /** Called on resume: a permission revoked in system settings must not be missed. */
    fun refreshAccess() {
        if (providerFactory.refresh()) {
            _browse.value = BrowseUiState()
            providerFactory.current.capabilities.roots.firstOrNull()?.let { open(it.path) }
        }
    }

    private fun watch(path: FsPath) {
        watcher?.cancel()
        if (!providerFactory.current.capabilities.canObserve) return
        watcher = viewModelScope.launch {
            providerFactory.current.observe(path).collect { change ->
                // Overflow means the watcher lost track, so the listing is re-read
                // rather than patched from an event that may be one of several
                // that were dropped.
                if (change is FsChange.Overflow) open(path) else open(path)
            }
        }
    }
}

/** A sentence for the user, never an exception class name. */
internal fun Throwable.userMessage(): String = when (this) {
    is FsError.PermissionDenied -> "FOLD cannot read this folder with the access it has."
    is FsError.NotFound -> "That folder is no longer there."
    is FsError.OutOfBounds -> "That location is not available."
    is FsError.NotADirectory -> "That is a file, not a folder."
    is FsError.Unsupported -> operation
    else -> message ?: "Something went wrong reading this folder."
}

internal fun FileCategory.label(): String = when (this) {
    FileCategory.DOWNLOADS -> "Downloads"
    FileCategory.DOCUMENTS -> "Documents"
    FileCategory.IMAGES -> "Images"
    FileCategory.VIDEO -> "Video"
    FileCategory.AUDIO -> "Audio"
    FileCategory.ARCHIVES -> "Archives"
    FileCategory.OTHER -> "Other"
}
