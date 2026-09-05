package com.jeelgajera.fold.core.storage.provider

import android.content.Context
import android.os.Environment
import com.jeelgajera.fold.core.storage.model.FsRoot
import com.jeelgajera.fold.core.storage.permission.StorageAccess
import com.jeelgajera.fold.core.storage.permission.StorageAccessLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Picks the provider that matches the access FOLD currently holds, and swaps it
 * when that changes.
 *
 * Permission can be revoked from system settings while the app is in the
 * background, so this is not a one-shot decision made at launch. [refresh] is
 * called on every resume; if the level changed, a new provider is installed and
 * everything observing [provider] re-renders against the new capabilities. That
 * is the whole point of the abstraction: the app degrades to SAF mid-session
 * instead of throwing SecurityExceptions from screens that were built assuming
 * raw access.
 */
class FileSystemProviderFactory(
    private val context: Context,
    private val io: CoroutineDispatcher,
) {
    private val state = MutableStateFlow(build())

    /** The provider in force. Collect it -- do not cache the value. */
    val provider: StateFlow<FileSystemProvider> = state.asStateFlow()

    val current: FileSystemProvider get() = state.value

    /**
     * Re-reads the granted permissions and swaps the provider if they changed.
     *
     * @return true when the provider was replaced.
     */
    fun refresh(): Boolean {
        val next = build()
        val changed = next::class != state.value::class ||
            next.capabilities != state.value.capabilities
        if (changed) state.value = next
        return changed
    }

    /**
     * The guard for the current access level.
     *
     * Exposed because the LAN server and the indexer need the same allowlist the
     * provider is using, and building a second one by hand is exactly how the two
     * would drift apart.
     */
    fun guard(): PathGuard = buildGuard()

    private fun build(): FileSystemProvider = when (StorageAccess.level(context)) {
        StorageAccessLevel.ALL_FILES -> RawFileProvider(
            guard = buildGuard(),
            roots = RawFileProvider.defaultRoots(),
            io = io,
        )

        StorageAccessLevel.LIMITED, StorageAccessLevel.NONE -> SafDocumentProvider(
            context = context,
            grantedTrees = SafDocumentProvider.persistedRoots(context),
            io = io,
        )
    }

    private fun buildGuard(): PathGuard = PathGuard(
        allowedRoots = allowedRoots(),
        deniedRoots = VaultLocations.deniedRoots(context),
    )

    /**
     * What raw-mode browsing is allowed to reach.
     *
     * `/` is included because "every file on your device" has to mean it -- a file
     * manager that quietly refuses `/system` while claiming full access is doing
     * the same thing to the user that MediaStore does. The denied list still wins,
     * so FOLD's own private data stays out regardless.
     */
    private fun allowedRoots(): List<File> = listOf(
        Environment.getExternalStorageDirectory(),
        File("/"),
    )

    /** Roots for the UI's root picker. Empty in [StorageAccessLevel.NONE]. */
    fun roots(): List<FsRoot> = current.capabilities.roots
}
