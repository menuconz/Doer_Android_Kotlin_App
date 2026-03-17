package nz.co.doer.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import nz.co.doer.R
import timber.log.Timber

/**
 * Foreground service for tracking contractor location during active shifts.
 * Mirrors the MAUI WakeLock + location tracking behavior.
 */
class LocationTrackingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("LocationTrackingService started")
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        // TODO: Start location updates using FusedLocationProviderClient
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        // TODO: Stop location updates
        Timber.d("LocationTrackingService stopped")
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Doer")
            .setContentText("Tracking your location for the active shift")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Doer::LocationTrackingWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes timeout
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
