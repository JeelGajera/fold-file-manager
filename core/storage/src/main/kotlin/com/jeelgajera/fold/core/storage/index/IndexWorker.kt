package com.jeelgajera.fold.core.storage.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jeelgajera.fold.core.storage.provider.RawFileProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Periodic reconciliation of the index against the real filesystem.
 *
 * `FileObserver` catches changes while FOLD is running, but it watches one
 * directory at a time, drops events under load, and sees nothing at all while the
 * process is dead. Everything it misses -- a file another app wrote overnight, a
 * folder deleted from a computer over the LAN server -- shows up here.
 *
 * Constrained to charging and idle: a full walk of a 256GB device is not
 * something to do on battery while someone is using the phone.
 */
@HiltWorker
class IndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val indexer: FileIndexer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Without All Files Access there is no whole-device tree to walk. SAF
        // trees are indexed on visit instead, so this simply has nothing to do.
        if (!RawFileProvider.hasAllFilesAccess()) return Result.success()

        val root = File(android.os.Environment.getExternalStorageDirectory().path)
        return try {
            indexer.indexTree(root)
            Result.success()
        } catch (e: Exception) {
            // A retry costs a scheduled wake-up, not a user-visible failure.
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "fold-index-reconcile"
        private const val ONE_SHOT_NAME = "fold-index-now"

        /**
         * Schedules the recurring reconciliation.
         *
         * `KEEP` rather than `UPDATE` so a relaunch does not reset the interval
         * and push the next run another twelve hours out.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<IndexWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Runs a walk now. Used right after All Files Access is first granted. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<IndexWorker>().build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
