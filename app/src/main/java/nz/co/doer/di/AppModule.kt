package nz.co.doer.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.local.SecureStorageManager
import nz.co.doer.data.local.db.DoerDatabase
import nz.co.doer.data.local.db.TrackingDao
import nz.co.doer.data.repository.LocationTrackingRepository
import nz.co.doer.service.GeofenceManager
import nz.co.doer.service.OfflineSyncManager
import nz.co.doer.service.TrackingManager
import nz.co.doer.service.TrackingNotificationHelper
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager = PreferencesManager(context)

    @Provides
    @Singleton
    fun provideSecureStorageManager(
        @ApplicationContext context: Context
    ): SecureStorageManager = SecureStorageManager(context)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    // Room Database
    @Provides
    @Singleton
    fun provideDoerDatabase(
        @ApplicationContext context: Context
    ): DoerDatabase = Room.databaseBuilder(
        context,
        DoerDatabase::class.java,
        "doer_database"
    ).fallbackToDestructiveMigration().build()

    @Provides
    @Singleton
    fun provideTrackingDao(database: DoerDatabase): TrackingDao = database.trackingDao()

    // Tracking
    @Provides
    @Singleton
    fun provideGeofenceManager(
        @ApplicationContext context: Context
    ): GeofenceManager = GeofenceManager(context)

    @Provides
    @Singleton
    fun provideOfflineSyncManager(
        @ApplicationContext context: Context,
        database: DoerDatabase,
        locationTrackingRepository: LocationTrackingRepository,
        preferencesManager: PreferencesManager
    ): OfflineSyncManager = OfflineSyncManager(
        context, database, locationTrackingRepository, preferencesManager
    )

    @Provides
    @Singleton
    fun provideTrackingNotificationHelper(
        @ApplicationContext context: Context,
        locationTrackingRepository: LocationTrackingRepository,
        preferencesManager: PreferencesManager
    ): TrackingNotificationHelper = TrackingNotificationHelper(
        context, locationTrackingRepository, preferencesManager
    )

    @Provides
    @Singleton
    fun provideTrackingManager(
        @ApplicationContext context: Context,
        geofenceManager: GeofenceManager,
        locationTrackingRepository: LocationTrackingRepository,
        preferencesManager: PreferencesManager,
        trackingNotificationHelper: TrackingNotificationHelper,
        offlineSyncManager: OfflineSyncManager
    ): TrackingManager = TrackingManager(
        context, geofenceManager, locationTrackingRepository, preferencesManager,
        trackingNotificationHelper, offlineSyncManager
    )
}
