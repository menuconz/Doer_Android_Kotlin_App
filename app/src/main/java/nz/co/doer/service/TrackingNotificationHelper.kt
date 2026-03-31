package nz.co.doer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nz.co.doer.R
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.dto.DoerTrackingState
import nz.co.doer.data.remote.dto.TrackingNotificationDto
import nz.co.doer.data.repository.LocationTrackingRepository
import nz.co.doer.ui.MainActivity
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages tracking-related notifications:
 * 1. Local notifications on the Doer's device (status updates)
 * 2. Server-side push notifications to Manager/Admin (via API → FCM)
 *
 * Notification types:
 * - CLOCK_IN: Doer clocked in and started journey
 * - ARRIVED: Doer arrived on-site
 * - LEFT_SITE: Doer left site unexpectedly
 * - THRESHOLD_WARNING: Approaching 12 hours (at 11hr)
 * - THRESHOLD_EXCEEDED: Exceeded 12 hours
 * - CLOCK_OUT: Doer clocked out
 * - GPS_PERMISSION_REVOKED: Doer revoked location permission while tracking
 */
@Singleton
class TrackingNotificationHelper @Inject constructor(
    private val context: Context,
    private val locationTrackingRepository: LocationTrackingRepository,
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        createNotificationChannel()
    }

    // ========== Public notification triggers ==========

    fun onClockIn(shiftId: Int, latitude: Double, longitude: Double, projectName: String) {
        showLocalNotification(
            title = "Clocked In",
            body = "You've clocked in for $projectName. Safe travels!",
            notificationId = NOTIF_ID_CLOCK_IN
        )
        sendToManager(
            shiftId = shiftId,
            type = TYPE_CLOCK_IN,
            title = "Doer Clocked In",
            body = "A Doer has clocked in and is heading to $projectName",
            state = DoerTrackingState.CLOCKED_IN,
            latitude = latitude,
            longitude = longitude
        )
    }

    fun onEnRoute(shiftId: Int, latitude: Double, longitude: Double) {
        showLocalNotification(
            title = "On the Way",
            body = "Navigation active. Your manager can see your progress.",
            notificationId = NOTIF_ID_EN_ROUTE
        )
    }

    fun onArrived(shiftId: Int, latitude: Double, longitude: Double, projectName: String) {
        showLocalNotification(
            title = "Arrived at Site",
            body = "Welcome to $projectName. Time tracking has started.",
            notificationId = NOTIF_ID_ARRIVED
        )
        sendToManager(
            shiftId = shiftId,
            type = TYPE_ARRIVED,
            title = "Doer Arrived",
            body = "A Doer has arrived at $projectName",
            state = DoerTrackingState.ARRIVED,
            latitude = latitude,
            longitude = longitude
        )
    }

    fun onLeftSite(shiftId: Int, latitude: Double, longitude: Double, projectName: String) {
        showLocalNotification(
            title = "Left Site Area",
            body = "You've left $projectName. Auto clock-out in 5 minutes if you don't return.",
            notificationId = NOTIF_ID_LEFT_SITE
        )
        sendToManager(
            shiftId = shiftId,
            type = TYPE_LEFT_SITE,
            title = "Doer Left Site",
            body = "A Doer has left $projectName",
            state = DoerTrackingState.LEAVING,
            latitude = latitude,
            longitude = longitude
        )
    }

    fun onThresholdWarning(shiftId: Int, hoursOnSite: Double, projectName: String) {
        showLocalNotification(
            title = "Approaching 12-Hour Limit",
            body = "You've been at $projectName for ${hoursOnSite.toInt()} hours. 1 hour remaining.",
            notificationId = NOTIF_ID_THRESHOLD_WARNING,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        sendToManager(
            shiftId = shiftId,
            type = TYPE_THRESHOLD_WARNING,
            title = "12-Hour Warning",
            body = "A Doer is approaching 12 hours at $projectName (${hoursOnSite.toInt()}h)",
            state = DoerTrackingState.ON_SITE,
            hoursOnSite = hoursOnSite
        )
    }

    fun onThresholdExceeded(shiftId: Int, hoursOnSite: Double, projectName: String) {
        showLocalNotification(
            title = "12-Hour Limit Exceeded",
            body = "You've exceeded 12 hours at $projectName. Please clock out.",
            notificationId = NOTIF_ID_THRESHOLD_EXCEEDED,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        sendToManager(
            shiftId = shiftId,
            type = TYPE_THRESHOLD_EXCEEDED,
            title = "12-Hour Limit Exceeded!",
            body = "A Doer has exceeded 12 hours at $projectName (${String.format("%.1f", hoursOnSite)}h)",
            state = DoerTrackingState.ON_SITE,
            hoursOnSite = hoursOnSite
        )
    }

    fun onClockOut(shiftId: Int, latitude: Double, longitude: Double, projectName: String) {
        showLocalNotification(
            title = "Clocked Out",
            body = "You've clocked out from $projectName. Great work!",
            notificationId = NOTIF_ID_CLOCK_OUT
        )
        sendToManager(
            shiftId = shiftId,
            type = TYPE_CLOCK_OUT,
            title = "Doer Clocked Out",
            body = "A Doer has clocked out from $projectName",
            state = DoerTrackingState.CLOCKED_OUT,
            latitude = latitude,
            longitude = longitude
        )
    }

    /**
     * Show a suggestion notification when Doer enters a site geofence but isn't clocked in.
     * Tapping the notification opens the app where Doer can clock in.
     */
    fun onAutoClockInSuggestion(shiftId: Int, projectName: String) {
        showLocalNotification(
            title = "You've arrived at $projectName",
            body = "Tap to open the app and clock in.",
            notificationId = NOTIF_ID_AUTO_CLOCK_IN,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    fun onGpsPermissionRevoked(shiftId: Int) {
        sendToManager(
            shiftId = shiftId,
            type = TYPE_GPS_REVOKED,
            title = "GPS Permission Revoked",
            body = "A Doer has revoked location permission while tracking is active",
            state = DoerTrackingState.ON_SITE
        )
    }

    // ========== Internal ==========

    private fun showLocalNotification(
        title: String,
        body: String,
        notificationId: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun sendToManager(
        shiftId: Int,
        type: String,
        title: String,
        body: String,
        state: DoerTrackingState,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        hoursOnSite: Double? = null
    ) {
        scope.launch {
            try {
                val notification = TrackingNotificationDto(
                    userId = preferencesManager.getUserId(),
                    shiftId = shiftId,
                    notificationType = type,
                    title = title,
                    body = body,
                    trackingState = state.value,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = nowUtc(),
                    hoursOnSite = hoursOnSite,
                    lId = 1,
                    siteId = 1,
                    basicAuthUid = preferencesManager.getBasicAuthUid()
                )
                locationTrackingRepository.sendTrackingNotification(notification)
                Timber.d("Tracking notification sent to manager: $type for shift $shiftId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to send tracking notification: $type")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tracking Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for clock-in/out, arrival, and time threshold alerts"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun nowUtc(): String =
        nz.co.doer.util.Constants.nowNz()

    companion object {
        const val CHANNEL_ID = "doer_tracking_alerts"

        // Notification IDs
        const val NOTIF_ID_CLOCK_IN = 2001
        const val NOTIF_ID_EN_ROUTE = 2002
        const val NOTIF_ID_ARRIVED = 2003
        const val NOTIF_ID_LEFT_SITE = 2004
        const val NOTIF_ID_THRESHOLD_WARNING = 2005
        const val NOTIF_ID_THRESHOLD_EXCEEDED = 2006
        const val NOTIF_ID_CLOCK_OUT = 2007
        const val NOTIF_ID_AUTO_CLOCK_IN = 2008

        // Notification types (sent to server)
        const val TYPE_CLOCK_IN = "TRACKING_CLOCK_IN"
        const val TYPE_ARRIVED = "TRACKING_ARRIVED"
        const val TYPE_LEFT_SITE = "TRACKING_LEFT_SITE"
        const val TYPE_THRESHOLD_WARNING = "TRACKING_THRESHOLD_WARNING"
        const val TYPE_THRESHOLD_EXCEEDED = "TRACKING_THRESHOLD_EXCEEDED"
        const val TYPE_CLOCK_OUT = "TRACKING_CLOCK_OUT"
        const val TYPE_GPS_REVOKED = "TRACKING_GPS_REVOKED"
    }
}
