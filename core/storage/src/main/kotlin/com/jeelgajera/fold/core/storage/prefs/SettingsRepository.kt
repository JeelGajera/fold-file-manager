package com.jeelgajera.fold.core.storage.prefs

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read and write FOLD's preferences.
 *
 * Every mutator is an `update` on the whole object rather than a keyed write, so
 * two screens changing two different settings at the same time cannot lose one
 * of the changes.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<FoldSettings>,
) {
    val settings: Flow<FoldSettings> = store.data

    suspend fun setThemeMode(mode: ThemeMode) = store.updateData { it.copy(themeMode = mode) }

    suspend fun setShowHiddenFiles(show: Boolean) =
        store.updateData { it.copy(showHiddenFiles = show) }

    suspend fun setDefaultSort(sort: com.jeelgajera.fold.core.storage.model.FsSort) =
        store.updateData { it.copy(defaultSort = sort) }

    suspend fun setGridView(grid: Boolean) = store.updateData { it.copy(gridView = grid) }

    suspend fun setServerPort(port: Int) = store.updateData { it.copy(serverPort = port) }

    suspend fun setServerRequirePin(require: Boolean) =
        store.updateData { it.copy(serverRequirePin = require) }

    suspend fun setServerStopOnScreenOff(stop: Boolean) =
        store.updateData { it.copy(serverStopOnScreenOff = stop) }

    suspend fun setServerAllowUpload(allow: Boolean) =
        store.updateData { it.copy(serverAllowUpload = allow) }

    suspend fun setServerAllowDelete(allow: Boolean) =
        store.updateData { it.copy(serverAllowDelete = allow) }

    suspend fun setVaultAutoLockMinutes(minutes: Int) =
        store.updateData { it.copy(vaultAutoLockMinutes = minutes) }

    suspend fun setGlyphMode(mode: GlyphMode) = store.updateData { it.copy(glyphMode = mode) }

    suspend fun toggleGlyphEvent(event: String, enabled: Boolean) = store.updateData { current ->
        val next = if (enabled) current.glyphEvents + event else current.glyphEvents - event
        current.copy(glyphEvents = next)
    }

    suspend fun setOnboardingComplete(complete: Boolean) =
        store.updateData { it.copy(onboardingComplete = complete) }

    suspend fun setAllFilesAccessAsked(asked: Boolean) =
        store.updateData { it.copy(allFilesAccessAsked = asked) }
}
