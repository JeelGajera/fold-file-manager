package com.jeelgajera.fold

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jeelgajera.fold.core.storage.di.ApplicationScope
import com.jeelgajera.fold.core.storage.index.FileIndexDao
import com.jeelgajera.fold.core.storage.index.IndexWorker
import com.jeelgajera.fold.core.storage.permission.StorageAccess
import com.jeelgajera.fold.core.storage.provider.VaultLocations
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
class FoldApplication :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var indexDao: FileIndexDao

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

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

            // A fresh install, a cleared cache and a schema change all leave an
            // empty index behind. Reconciliation alone would not touch it until
            // the next idle-and-charging window, which is a home screen that
            // stays blank for hours with no way for the user to know why.
            appScope.launch {
                if (indexDao.fileCount() == 0) IndexWorker.runNow(this@FoldApplication)
            }
        }
    }
}
