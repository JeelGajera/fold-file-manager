package com.jeelgajera.fold.feature.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeelgajera.fold.core.design.component.FoldSettingRow
import com.jeelgajera.fold.core.design.component.FoldToggleRow
import com.jeelgajera.fold.core.design.component.SectionHeading
import com.jeelgajera.fold.core.design.component.bottomRule
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.storage.index.FileIndexDao
import com.jeelgajera.fold.core.storage.model.FsSort
import com.jeelgajera.fold.core.storage.permission.StorageAccess
import com.jeelgajera.fold.core.storage.permission.StorageAccessLevel
import com.jeelgajera.fold.core.storage.prefs.FoldSettings
import com.jeelgajera.fold.core.storage.prefs.SettingsRepository
import com.jeelgajera.fold.core.storage.stats.VolumeStats
import com.jeelgajera.fold.core.storage.util.Formatting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val indexDao: FileIndexDao,
) : ViewModel() {

    val preferences: StateFlow<FoldSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldSettings())

    val indexedFiles: StateFlow<Int> = indexDao.observeFileCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _storageUsed = MutableStateFlow(0L to 0L)
    val storageUsed: StateFlow<Pair<Long, Long>> = _storageUsed.asStateFlow()

    init {
        _storageUsed.value = VolumeStats.primaryVolume()
    }

    fun setShowHidden(show: Boolean) = viewModelScope.launch { settings.setShowHiddenFiles(show) }
    fun setRequirePin(require: Boolean) = viewModelScope.launch { settings.setServerRequirePin(require) }
    fun setStopOnScreenOff(stop: Boolean) = viewModelScope.launch { settings.setServerStopOnScreenOff(stop) }
    fun setAllowUpload(allow: Boolean) = viewModelScope.launch { settings.setServerAllowUpload(allow) }
    fun setAllowDelete(allow: Boolean) = viewModelScope.launch { settings.setServerAllowDelete(allow) }

    fun cycleDefaultSort() = viewModelScope.launch {
        val next = when (preferences.value.defaultSort) {
            FsSort.DATE -> FsSort.SIZE
            FsSort.SIZE -> FsSort.NAME
            FsSort.NAME -> FsSort.DATE
        }
        settings.setDefaultSort(next)
    }

    /**
     * Throws the index away.
     *
     * Safe by construction: the index is a cache of the filesystem and holds
     * nothing the device cannot regenerate. It is offered because a user who
     * wants FOLD to stop remembering anything should be able to make that true
     * in one tap.
     */
    fun clearIndex() = viewModelScope.launch { indexDao.clear() }
}

/**
 * Settings.
 *
 * Grouped the way the design lays it out, and ending with the claim the whole
 * app is built around -- no analytics, no network calls -- placed where a
 * sceptical user will actually look for it rather than in a store listing.
 */
@Composable
fun SettingsScreen(
    versionName: String,
    versionCode: String,
    onOpenAllFilesAccess: () -> Unit,
    onOpenLimited: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenGlyph: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val indexed by viewModel.indexedFiles.collectAsStateWithLifecycle()
    val storage by viewModel.storageUsed.collectAsStateWithLifecycle()
    val colors = FoldTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val accessLevel = StorageAccess.level(context)

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = FoldSpacing.dockClearance),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .bottomRule(colors.dividerStrong, FoldRules.sectionDivider)
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = FoldTheme.typography.titleM,
                    color = colors.onBackground,
                )
            }
        }

        // --- Access ---
        item { SectionHeading(stringResource(R.string.settings_group_access)) }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_all_files),
                help = stringResource(R.string.settings_all_files_help),
                value = if (accessLevel == StorageAccessLevel.ALL_FILES) {
                    stringResource(R.string.settings_on)
                } else {
                    stringResource(R.string.settings_off)
                },
                valueAccent = accessLevel == StorageAccessLevel.ALL_FILES,
                onClick = onOpenAllFilesAccess,
            )
        }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_limited),
                help = stringResource(R.string.settings_limited_help),
                value = StorageAccess.grantedTreeCount(context).toString(),
                onClick = onOpenLimited,
            )
        }
        item {
            FoldToggleRow(
                label = stringResource(R.string.settings_hidden),
                help = stringResource(R.string.settings_hidden_help),
                checked = preferences.showHiddenFiles,
                onCheckedChange = viewModel::setShowHidden,
            )
        }

        // --- Storage ---
        item { SectionHeading(stringResource(R.string.settings_group_storage)) }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_internal),
                help = "${Formatting.bytes(storage.first)} / ${Formatting.bytes(storage.second)}",
                value = if (storage.second > 0) {
                    "${(storage.first * 100 / storage.second)}%"
                } else {
                    "--"
                },
                onClick = {},
            )
        }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_cache),
                help = stringResource(R.string.settings_cache_help),
                value = Formatting.count(indexed),
                onClick = viewModel::clearIndex,
            )
        }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_default_sort),
                help = stringResource(R.string.settings_default_sort_help),
                value = preferences.defaultSort.name,
                onClick = viewModel::cycleDefaultSort,
            )
        }

        // --- Share over Wi-Fi ---
        item { SectionHeading(stringResource(R.string.settings_group_share)) }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_port),
                help = stringResource(R.string.settings_port_help),
                value = preferences.serverPort.toString(),
                onClick = {},
            )
        }
        item {
            FoldToggleRow(
                label = stringResource(R.string.settings_require_pin),
                help = stringResource(R.string.settings_require_pin_help),
                checked = preferences.serverRequirePin,
                onCheckedChange = viewModel::setRequirePin,
            )
        }
        item {
            FoldToggleRow(
                label = stringResource(R.string.settings_stop_on_lock),
                help = stringResource(R.string.settings_stop_on_lock_help),
                checked = preferences.serverStopOnScreenOff,
                onCheckedChange = viewModel::setStopOnScreenOff,
            )
        }
        item {
            FoldToggleRow(
                label = stringResource(R.string.settings_allow_upload),
                help = stringResource(R.string.settings_allow_upload_help),
                checked = preferences.serverAllowUpload,
                onCheckedChange = viewModel::setAllowUpload,
            )
        }
        item {
            FoldToggleRow(
                label = stringResource(R.string.settings_allow_delete),
                help = stringResource(R.string.settings_allow_delete_help),
                checked = preferences.serverAllowDelete,
                onCheckedChange = viewModel::setAllowDelete,
            )
        }

        // --- Appearance ---
        item { SectionHeading(stringResource(R.string.settings_group_appearance)) }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_theme),
                help = stringResource(R.string.settings_theme_help),
                value = preferences.themeMode.name,
                onClick = onOpenTheme,
            )
        }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_glyph),
                help = stringResource(R.string.settings_glyph_help),
                value = preferences.glyphMode.name,
                onClick = onOpenGlyph,
            )
        }
        item {
            FoldSettingRow(
                label = stringResource(R.string.settings_widgets),
                help = stringResource(R.string.settings_widgets_help),
                value = "2",
                onClick = onOpenWidgets,
            )
        }

        item {
            Text(
                stringResource(R.string.settings_footer, versionName, versionCode),
                style = FoldTheme.typography.meta,
                color = colors.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
