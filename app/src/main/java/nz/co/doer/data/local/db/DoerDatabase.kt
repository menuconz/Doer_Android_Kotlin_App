package nz.co.doer.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PendingClockEventEntity::class,
        PendingLocationPointEntity::class,
        PendingNotificationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class DoerDatabase : RoomDatabase() {
    abstract fun trackingDao(): TrackingDao
}
