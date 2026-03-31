package nz.co.doer.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally queued clock event. Written to Room first, synced to server when online.
 */
@Entity(tableName = "pending_clock_events")
data class PendingClockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val shiftId: Int,
    val eventType: String,
    val locationType: Int,
    val trackingState: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val reasonCode: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long? = null
)

/**
 * Locally queued location point. Batched and synced to server when online.
 */
@Entity(tableName = "pending_location_points")
data class PendingLocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val shiftId: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

/**
 * Locally queued tracking notification. Sent to server when online.
 */
@Entity(tableName = "pending_notifications")
data class PendingNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val shiftId: Int,
    val notificationType: String,
    val title: String,
    val body: String,
    val trackingState: Int,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val hoursOnSite: Double?,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val syncAttempts: Int = 0
)
