package nz.co.doer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import nz.co.doer.R
import nz.co.doer.data.remote.dto.DoerTrackingState
import nz.co.doer.data.remote.dto.LocationPointDto
import nz.co.doer.ui.MainActivity
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Foreground service for tracking Doer location during active shifts.
 *
 * Supports two tracking modes:
 * - EN_ROUTE: High frequency (10s), high accuracy, 50m displacement filter
 * - ON_SITE: Low frequency (5min), balanced accuracy, 100m displacement filter
 *
 * Handles geofence transition intents and grace period management.
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var trackingManager: TrackingManager

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentMode: TrackingMode = TrackingMode.EN_ROUTE
    private var activeShiftId: Int = 0

    // Grace period handler for LEAVING state
    private val graceHandler = android.os.Handler(Looper.getMainLooper())
    private val graceRunnable = Runnable {
        Timber.d("Grace period expired — confirming departure")
        trackingManager.confirmDeparture()
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return

            val point = LocationPointDto(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = nowUtc(),
                accuracy = location.accuracy,
                speed = location.speed,
                bearing = location.bearing
            )
            trackingManager.addLocationPoint(point)

            // Update notification with current state
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                val modeName = intent.getStringExtra(EXTRA_TRACKING_MODE) ?: TrackingMode.EN_ROUTE.name
                currentMode = TrackingMode.valueOf(modeName)
                activeShiftId = intent.getIntExtra(EXTRA_SHIFT_ID, 0)

                Timber.d("Starting tracking: mode=$currentMode, shiftId=$activeShiftId")
                startForegroundWithType()
                acquireWakeLock()
                startLocationUpdates(currentMode)
            }

            ACTION_UPDATE_MODE -> {
                val modeName = intent.getStringExtra(EXTRA_TRACKING_MODE) ?: return START_STICKY
                val newMode = TrackingMode.valueOf(modeName)
                if (newMode != currentMode) {
                    Timber.d("Updating tracking mode: $currentMode → $newMode")
                    currentMode = newMode
                    stopLocationUpdates()
                    startLocationUpdates(newMode)
                    updateNotification()
                }
            }

            ACTION_STOP_TRACKING -> {
                Timber.d("Stopping tracking service")
                cancelGraceTimer()
                stopLocationUpdates()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            // Geofence transition intents from GeofenceBroadcastReceiver
            GeofenceBroadcastReceiver.ACTION_GEOFENCE_ENTER -> {
                val shiftId = intent.getIntExtra(GeofenceBroadcastReceiver.EXTRA_SHIFT_ID, 0)
                val lat = intent.getDoubleExtra(GeofenceBroadcastReceiver.EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(GeofenceBroadcastReceiver.EXTRA_LONGITUDE, 0.0)
                cancelGraceTimer()
                trackingManager.onGeofenceEnter(shiftId, lat, lng)
                updateNotification()
            }

            GeofenceBroadcastReceiver.ACTION_GEOFENCE_DWELL -> {
                val shiftId = intent.getIntExtra(GeofenceBroadcastReceiver.EXTRA_SHIFT_ID, 0)
                val lat = intent.getDoubleExtra(GeofenceBroadcastReceiver.EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(GeofenceBroadcastReceiver.EXTRA_LONGITUDE, 0.0)
                trackingManager.onGeofenceDwell(shiftId, lat, lng)
                updateNotification()
            }

            GeofenceBroadcastReceiver.ACTION_GEOFENCE_EXIT -> {
                val shiftId = intent.getIntExtra(GeofenceBroadcastReceiver.EXTRA_SHIFT_ID, 0)
                val lat = intent.getDoubleExtra(GeofenceBroadcastReceiver.EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(GeofenceBroadcastReceiver.EXTRA_LONGITUDE, 0.0)
                trackingManager.onGeofenceExit(shiftId, lat, lng)
                startGraceTimer()
                updateNotification()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        cancelGraceTimer()
        stopLocationUpdates()
        releaseWakeLock()
        trackingManager.flushLocationBatch()
        Timber.d("LocationTrackingService destroyed")
        super.onDestroy()
    }

    private fun startLocationUpdates(mode: TrackingMode) {
        if (!hasLocationPermission()) {
            Timber.w("Cannot start location updates: permission not granted")
            showPermissionRevokedNotification()
            return
        }

        val locationRequest = when (mode) {
            TrackingMode.EN_ROUTE -> LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                EN_ROUTE_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(EN_ROUTE_FASTEST_INTERVAL_MS)
                .setMinUpdateDistanceMeters(EN_ROUTE_DISPLACEMENT_M)
                .setWaitForAccurateLocation(true)
                .build()

            TrackingMode.ON_SITE -> LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                ON_SITE_INTERVAL_MS
            )
                .setMinUpdateIntervalMillis(ON_SITE_FASTEST_INTERVAL_MS)
                .setMinUpdateDistanceMeters(ON_SITE_DISPLACEMENT_M)
                .build()
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Timber.d("Location updates started: mode=$mode")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException starting location updates")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Timber.d("Location updates stopped")
    }

    private fun startForegroundWithType() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Doer location is being tracked during active shifts"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val state = trackingManager.trackingState.value
        val (title, text) = getNotificationContent(state)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getNotificationContent(state: DoerTrackingState): Pair<String, String> {
        return when (state) {
            DoerTrackingState.IDLE -> "Doer" to "Ready"
            DoerTrackingState.CLOCKED_IN -> "Clocked In" to "Preparing to head to site..."
            DoerTrackingState.EN_ROUTE -> "On the Way" to "Navigating to site..."
            DoerTrackingState.ARRIVED -> "Arrived" to "You've reached the site area"
            DoerTrackingState.ON_SITE -> "On Site" to "Working — time is being tracked"
            DoerTrackingState.LEAVING -> "Leaving Site" to "Left site area — will auto clock-out in 5 min"
            DoerTrackingState.CLOCKED_OUT -> "Clocked Out" to "Shift tracking complete"
        }
    }

    private fun startGraceTimer() {
        cancelGraceTimer()
        graceHandler.postDelayed(graceRunnable, TrackingManager.GRACE_PERIOD_MS)
        Timber.d("Grace timer started: ${TrackingManager.GRACE_PERIOD_MS / 1000}s")
    }

    private fun cancelGraceTimer() {
        graceHandler.removeCallbacks(graceRunnable)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Doer::LocationTrackingWakeLock"
        ).apply {
            acquire(12 * 60 * 60 * 1000L) // 12 hours max (matches site time allowance)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionRevokedNotification() {
        val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TrackingNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Location Tracking Paused")
            .setContentText("Location permission is required. Tap to enable.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Location tracking is paused because permission was revoked. Tap to open settings and enable location access to continue tracking."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(PERMISSION_NOTIFICATION_ID, notification)

        // Also notify manager
        val shiftId = activeShiftId
        if (shiftId > 0) {
            trackingManager.notifyGpsPermissionRevoked(shiftId)
        }
    }

    private fun nowUtc(): String =
        nz.co.doer.util.Constants.nowNz()

    companion object {
        const val NOTIFICATION_ID = 1001
        const val PERMISSION_NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "doer_location_tracking"

        // Actions
        const val ACTION_START_TRACKING = "nz.co.doer.START_TRACKING"
        const val ACTION_STOP_TRACKING = "nz.co.doer.STOP_TRACKING"
        const val ACTION_UPDATE_MODE = "nz.co.doer.UPDATE_MODE"

        // Extras
        const val EXTRA_TRACKING_MODE = "extra_tracking_mode"
        const val EXTRA_SHIFT_ID = "extra_shift_id"

        // EN_ROUTE: High frequency for live map tracking
        const val EN_ROUTE_INTERVAL_MS = 10_000L           // 10 seconds
        const val EN_ROUTE_FASTEST_INTERVAL_MS = 5_000L     // 5 seconds minimum
        const val EN_ROUTE_DISPLACEMENT_M = 50f             // 50m displacement filter

        // ON_SITE: Low frequency, just confirming presence
        const val ON_SITE_INTERVAL_MS = 5 * 60_000L        // 5 minutes
        const val ON_SITE_FASTEST_INTERVAL_MS = 2 * 60_000L // 2 minutes minimum
        const val ON_SITE_DISPLACEMENT_M = 100f             // 100m displacement filter
    }
}
