package nz.co.doer.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackingDao {

    // ========== Clock Events ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClockEvent(event: PendingClockEventEntity): Long

    @Query("SELECT * FROM pending_clock_events WHERE synced = 0 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getUnsyncedClockEvents(limit: Int = 50): List<PendingClockEventEntity>

    @Query("UPDATE pending_clock_events SET synced = 1 WHERE id = :id")
    suspend fun markClockEventSynced(id: Long)

    @Query("UPDATE pending_clock_events SET syncAttempts = syncAttempts + 1, lastSyncAttempt = :timestamp WHERE id = :id")
    suspend fun incrementClockEventSyncAttempt(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM pending_clock_events WHERE synced = 1 AND createdAt < :olderThan")
    suspend fun deleteOldSyncedClockEvents(olderThan: Long)

    @Query("SELECT COUNT(*) FROM pending_clock_events WHERE synced = 0")
    suspend fun getUnsyncedClockEventCount(): Int

    /** Purge all pending clock events for a shift that no longer exists on the server. */
    @Query("DELETE FROM pending_clock_events WHERE shiftId = :shiftId")
    suspend fun deleteClockEventsForShift(shiftId: Int)

    // ========== Location Points ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationPoint(point: PendingLocationPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationPoints(points: List<PendingLocationPointEntity>)

    @Query("SELECT * FROM pending_location_points WHERE synced = 0 AND shiftId = :shiftId ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getUnsyncedLocationPoints(shiftId: Int, limit: Int = 50): List<PendingLocationPointEntity>

    @Query("UPDATE pending_location_points SET synced = 1 WHERE id IN (:ids)")
    suspend fun markLocationPointsSynced(ids: List<Long>)

    @Query("DELETE FROM pending_location_points WHERE synced = 1 AND createdAt < :olderThan")
    suspend fun deleteOldSyncedLocationPoints(olderThan: Long)

    @Query("SELECT COUNT(*) FROM pending_location_points WHERE synced = 0")
    suspend fun getUnsyncedLocationPointCount(): Int

    /** Purge all pending location points for a shift that no longer exists on the server. */
    @Query("DELETE FROM pending_location_points WHERE shiftId = :shiftId")
    suspend fun deleteLocationPointsForShift(shiftId: Int)

    // ========== Notifications ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: PendingNotificationEntity): Long

    @Query("SELECT * FROM pending_notifications WHERE synced = 0 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getUnsyncedNotifications(limit: Int = 50): List<PendingNotificationEntity>

    @Query("UPDATE pending_notifications SET synced = 1 WHERE id = :id")
    suspend fun markNotificationSynced(id: Long)

    @Query("UPDATE pending_notifications SET syncAttempts = syncAttempts + 1 WHERE id = :id")
    suspend fun incrementNotificationSyncAttempt(id: Long)

    @Query("DELETE FROM pending_notifications WHERE synced = 1 AND createdAt < :olderThan")
    suspend fun deleteOldSyncedNotifications(olderThan: Long)

    // ========== Cleanup ==========

    /** Delete all synced records older than 24 hours */
    @Query("""
        DELETE FROM pending_clock_events WHERE synced = 1 AND createdAt < :cutoff;
    """)
    suspend fun cleanupOldClockEvents(cutoff: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000)

    @Query("""
        DELETE FROM pending_location_points WHERE synced = 1 AND createdAt < :cutoff;
    """)
    suspend fun cleanupOldLocationPoints(cutoff: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000)

    @Query("""
        DELETE FROM pending_notifications WHERE synced = 1 AND createdAt < :cutoff;
    """)
    suspend fun cleanupOldNotifications(cutoff: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000)

    /** Total count of all unsynced items (for UI badge) */
    @Query("""
        SELECT (SELECT COUNT(*) FROM pending_clock_events WHERE synced = 0) +
               (SELECT COUNT(*) FROM pending_location_points WHERE synced = 0) +
               (SELECT COUNT(*) FROM pending_notifications WHERE synced = 0)
    """)
    suspend fun getTotalUnsyncedCount(): Int
}
