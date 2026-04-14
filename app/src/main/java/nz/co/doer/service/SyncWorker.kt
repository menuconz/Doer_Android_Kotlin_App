package nz.co.doer.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * WorkManager worker that syncs all pending offline tracking data to the server.
 * Triggered when network becomes available after being offline.
 * Uses exponential backoff on failure.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val offlineSyncManager: OfflineSyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker started — syncing offline tracking data")

        return try {
            val allSynced = offlineSyncManager.syncAll()

            if (allSynced) {
                Timber.d("SyncWorker complete — all data synced")
                Result.success()
            } else {
                val pending = offlineSyncManager.getPendingCount()
                Timber.w("SyncWorker partial — $pending items still pending, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker failed")
            Result.retry()
        }
    }
}
