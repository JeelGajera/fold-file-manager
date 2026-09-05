package com.jeelgajera.fold

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jeelgajera.fold.core.storage.index.IndexWorker
import com.jeelgajera.fold.core.storage.permission.StorageAccess
import com.jeelgajera.fold.core.storage.provider.VaultLocations
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The application object.
 *
 * Three things happen here and nothing else: the vault's directory is created so
 * `PathGuard` can canonicalise it (a denied root that does not exist yet cannot
 * be resolved, and would silently drop out of the deny list), WorkManager is
 * given Hilt's factory, and index reconciliation is scheduled.
 *
 * Notably absent: any analytics initialiser, any crash reporter, any advertising
 * id. That absence is the feature -- see PRIVACY.md.
 */
@HiltAndroidApp
class FoldApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        VaultLocations.ensureNoMedia(this)

        // Only meaningful with All Files Access; the worker no-ops otherwise, but
        // there is no point waking the device to find that out.
        if (StorageAccess.hasAllFilesAccess()) {
            IndexWorker.schedulePeriodic(this)
        }
    }
}
