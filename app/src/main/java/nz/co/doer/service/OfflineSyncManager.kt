package nz.co.doer.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.local.db.DoerDatabase
import nz.co.doer.data.local.db.PendingClockEventEntity
import nz.co.doer.data.local.db.PendingLocationPointEntity
import nz.co.doer.data.local.db.PendingNotificationEntity
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.ClockEventDto
import nz.co.doer.data.remote.dto.LocationBatchDto
import nz.co.doer.data.remote.dto.LocationPointDto
import nz.co.doer.data.remote.dto.TrackingNotificationDto
import nz.co.doer.data.repository.LocationTrackingRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first sync manager. All tracking events are written to Room first,
 * then synced to the server when network is available.
 *
 * Flow:
 * 1. Event occurs → write to Room (immediate, zero network dependency)
 * 2. If online → sync immediately in background
 * 3. If offline → enqueue WorkManager job (runs when network restores)
 * 4. WorkManager syncs all pending items with exponential backoff
 * 5. Synced items flagged, cleaned up after 24 hours
 */
@Singleton
class OfflineSyncManager @Inject constructor(
    private val context: Context,
    private val database: DoerDatabase,
    private val locationTrackingRepository: LocationTrackingRepository,
    private val preferencesManager: PreferencesManager
) {
    private val dao = database.trackingDao()

    // ========== Queue Events (offline-first write) ==========

    /**
     * Queue a clock event for sync. Writes to Room immediately.
     * If online, also triggers immediate sync.
     */
    suspend fun queueClockEvent(
        userId: String,
        shiftId: Int,
        eventType: String,
        locationType: Int,
        trackingState: Int,
        latitude: Double,
        longitude: Double,
        timestamp: String,
        reasonCode: String? = null
    ) {
        val entity = PendingClockEventEntity(
            userId = userId,
            shiftId = shiftId,
            eventType = eventType,
            locationType = locationType,
            trackingState = trackingState,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            reasonCode = reasonCode
        )
        dao.insertClockEvent(entity)
        Timber.d("Clock event queued: $eventType for shift $shiftId")

        if (isOnline()) {
            syncClockEvents()
        } else {
            scheduleSync()
        }
    }

    /**
     * Queue location points for sync.
     */
    suspend fun queueLocationPoints(
        userId: String,
        shiftId: Int,
        points: List<LocationPointDto>
    ) {
        val entities = points.map { point ->
            PendingLocationPointEntity(
                userId = userId,
                shiftId = shiftId,
                latitude = point.latitude,
                longitude = point.longitude,
                timestamp = point.timestamp,
                accuracy = point.accuracy,
                speed = point.speed,
                bearing = point.bearing
            )
        }
        dao.insertLocationPoints(entities)
        Timber.d("${points.size} location points queued for shift $shiftId")

        if (isOnline()) {
            syncLocationPoints(shiftId)
        } else {
            scheduleSync()
        }
    }

    /**
     * Queue a tracking notification for sync.
     */
    suspend fun queueNotification(
        userId: String,
        shiftId: Int,
        notificationType: String,
        title: String,
        body: String,
        trackingState: Int,
        latitude: Double,
        longitude: Double,
        timestamp: String,
        hoursOnSite: Double? = null
    ) {
        val entity = PendingNotificationEntity(
            userId = userId,
            shiftId = shiftId,
            notificationType = notificationType,
            title = title,
            body = body,
            trackingState = trackingState,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            hoursOnSite = hoursOnSite
        )
        dao.insertNotification(entity)

        if (isOnline()) {
            syncNotifications()
        } else {
            scheduleSync()
        }
    }

    // ========== Sync Logic ==========

    /**
     * Sync all pending items. Called by SyncWorker or directly when online.
     * Returns true if all items synced successfully.
     */
    suspend fun syncAll(): Boolean {
        var allSuccess = true

        if (!syncClockEvents()) allSuccess = false
        if (!syncAllLocationPoints()) allSuccess = false
        if (!syncNotifications()) allSuccess = false

        // Cleanup old synced records
        dao.cleanupOldClockEvents()
        dao.cleanupOldLocationPoints()
        dao.cleanupOldNotifications()

        val remaining = dao.getTotalUnsyncedCount()
        if (remaining > 0) {
            Timber.d("Sync complete. $remaining items still pending.")
        } else {
            Timber.d("Sync complete. All items synced.")
        }

        return allSuccess
    }

    private suspend fun syncClockEvents(): Boolean {
        val events = dao.getUnsyncedClockEvents()
        if (events.isEmpty()) return true

        var allSuccess = true
        val basicAuthUid = preferencesManager.getBasicAuthUid()

        for (event in events) {
            if (event.syncAttempts >= MAX_SYNC_ATTEMPTS) continue

            try {
                val dto = ClockEventDto(
                    userId = event.userId,
                    shiftId = event.shiftId,
                    eventType = event.eventType,
                    locationType = event.locationType,
                    trackingState = event.trackingState,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    timestamp = event.timestamp,
                    reasonCode = event.reasonCode,
                    isOffline = true,
                    lId = 1,
                    siteId = 1,
                    basicAuthUid = basicAuthUid
                )
                when (locationTrackingRepository.recordClockEvent(dto)) {
                    is ApiResult.Success -> {
                        dao.markClockEventSynced(event.id)
                        Timber.d("Synced clock event ${event.id}: ${event.eventType}")
                    }
                    is ApiResult.Error -> {
                        dao.incrementClockEventSyncAttempt(event.id)
                        allSuccess = false
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                dao.incrementClockEventSyncAttempt(event.id)
                allSuccess = false
                Timber.e(e, "Failed to sync clock event ${event.id}")
            }
        }
        return allSuccess
    }

    private suspend fun syncAllLocationPoints(): Boolean {
        // Get distinct shift IDs with unsynced points
        val events = dao.getUnsyncedClockEvents(limit = 1)
        // Sync points for all shifts — use a broad query
        return syncLocationPointsGeneric()
    }

    private suspend fun syncLocationPoints(shiftId: Int): Boolean {
        val points = dao.getUnsyncedLocationPoints(shiftId)
        if (points.isEmpty()) return true

        val basicAuthUid = preferencesManager.getBasicAuthUid()
        val userId = preferencesManager.getUserId()

        // Batch into groups of 10
        val batches = points.chunked(10)
        var allSuccess = true

        for (batch in batches) {
            try {
                val dto = LocationBatchDto(
                    userId = userId,
                    shiftId = shiftId,
                    points = batch.map { p ->
                        LocationPointDto(
                            latitude = p.latitude,
                            longitude = p.longitude,
                            timestamp = p.timestamp,
                            accuracy = p.accuracy,
                            speed = p.speed,
                            bearing = p.bearing
                        )
                    },
                    lId = 1,
                    siteId = 1,
                    basicAuthUid = basicAuthUid
                )
                when (locationTrackingRepository.sendLocationBatch(dto)) {
                    is ApiResult.Success -> {
                        dao.markLocationPointsSynced(batch.map { it.id })
                        Timber.d("Synced ${batch.size} location points for shift $shiftId")
                    }
                    is ApiResult.Error -> {
                        allSuccess = false
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                allSuccess = false
                Timber.e(e, "Failed to sync location batch for shift $shiftId")
            }
        }
        return allSuccess
    }

    private suspend fun syncLocationPointsGeneric(): Boolean {
        // Simple approach: get all unsynced points grouped by shift
        // For now, try syncing with shiftId=0 which gets all
        val count = dao.getUnsyncedLocationPointCount()
        if (count == 0) return true
        // We'll sync per-shift in the worker which has full context
        return count == 0
    }

    private suspend fun syncNotifications(): Boolean {
        val notifications = dao.getUnsyncedNotifications()
        if (notifications.isEmpty()) return true

        var allSuccess = true
        val basicAuthUid = preferencesManager.getBasicAuthUid()

        for (notif in notifications) {
            if (notif.syncAttempts >= MAX_SYNC_ATTEMPTS) continue

            try {
                val dto = TrackingNotificationDto(
                    userId = notif.userId,
                    shiftId = notif.shiftId,
                    notificationType = notif.notificationType,
                    title = notif.title,
                    body = notif.body,
                    trackingState = notif.trackingState,
                    latitude = notif.latitude,
                    longitude = notif.longitude,
                    timestamp = notif.timestamp,
                    hoursOnSite = notif.hoursOnSite,
                    lId = 1,
                    siteId = 1,
                    basicAuthUid = basicAuthUid
                )
                when (locationTrackingRepository.sendTrackingNotification(dto)) {
                    is ApiResult.Success -> {
                        dao.markNotificationSynced(notif.id)
                    }
                    is ApiResult.Error -> {
                        dao.incrementNotificationSyncAttempt(notif.id)
                        allSuccess = false
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                dao.incrementNotificationSyncAttempt(notif.id)
                allSuccess = false
                Timber.e(e, "Failed to sync notification ${notif.id}")
            }
        }
        return allSuccess
    }

    // ========== WorkManager Scheduling ==========

    /**
     * Schedule a sync job that runs when network becomes available.
     * Uses unique work to avoid duplicate jobs.
     */
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP, // Don't replace existing pending work
            syncWork
        )
        Timber.d("Sync work scheduled (will run when network available)")
    }

    // ========== Utilities ==========

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun getPendingCount(): Int = dao.getTotalUnsyncedCount()

    companion object {
        const val SYNC_WORK_NAME = "doer_tracking_sync"
        const val MAX_SYNC_ATTEMPTS = 5
    }
}
