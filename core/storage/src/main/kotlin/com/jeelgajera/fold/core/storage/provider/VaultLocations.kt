package com.jeelgajera.fold.core.storage.provider

import android.content.Context
import java.io.File

/**
 * Where the vault's encrypted blobs live, defined in `:core:storage` rather than
 * in `:core:crypto`.
 *
 * That placement is on purpose. The storage layer has to know which directory to
 * refuse *before* the crypto layer exists in the dependency graph, because the
 * refusal is unconditional and must not depend on the vault feature being wired
 * up. `:core:crypto` reads these same paths to write into them; `PathGuard` reads
 * them to deny them.
 */
object VaultLocations {

    /**
     * App-private storage, so no other app can read the blobs even with All Files
     * Access of its own, and nothing here is visible to the media scanner.
     */
    fun blobDir(context: Context): File =
        File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }

    /**
     * A `.nomedia` marker inside the blob directory.
     *
     * App-private storage is not scanned anyway, so this is belt and braces
     * against a future in which the directory moves, or an OEM gallery that
     * indexes more aggressively than the platform does.
     */
    fun ensureNoMedia(context: Context) {
        val marker = File(blobDir(context), ".nomedia")
        if (!marker.exists()) {
            runCatching { marker.createNewFile() }
        }
    }

    /** Every directory `PathGuard` refuses outright. */
    fun deniedRoots(context: Context): List<File> = listOf(
        blobDir(context),
        // The whole app-private tree, not just the vault: FOLD's own database and
        // settings are not the user's files and have no business in a listing or
        // behind an HTTP route.
        context.filesDir,
        context.cacheDir,
        File(context.applicationInfo.dataDir),
    )
}
