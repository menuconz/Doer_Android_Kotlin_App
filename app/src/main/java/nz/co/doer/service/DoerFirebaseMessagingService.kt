package nz.co.doer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nz.co.doer.R
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.api.AccountApi
import nz.co.doer.data.remote.dto.LoginRequestDto
import nz.co.doer.ui.MainActivity
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DoerFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var accountApi: AccountApi

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("FCM message from: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Doer"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

        showNotification(title, body, remoteMessage.data)

        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("FCM data payload: ${remoteMessage.data}")
        }
    }

    // Matching MAUI: When FCM token refreshes, send it to the server
    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed: $token")
        serviceScope.launch {
            try {
                val userId = preferencesManager.getUserId()
                if (userId.isNotBlank()) {
                    // Re-send token to server via login endpoint with stored user info
                    // The server updates the device token for this user
                    val email = preferencesManager.getEmail()
                    if (email.isNotBlank()) {
                        val body = LoginRequestDto(
                            email = email,
                            password = "", // Server will update token regardless for existing session
                            deviceToken = token,
                            deviceTypeId = 2 // Android
                        )
                        accountApi.authenticate(body)
                        Timber.d("FCM token updated on server for user: $userId")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update FCM token on server")
            }
        }
    }

    // Matching MAUI: NotificationTapped navigates to NotificationView
    // Pass data in intent so MainActivity can navigate to the right screen
    private fun showNotification(title: String, body: String, data: Map<String, String> = emptyMap()) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // Pass notification data for navigation
            putExtra("navigate_to", "notifications")
            data["NotificationType"]?.let { putExtra("notification_type", it) }
            data["ShiftId"]?.let { putExtra("shift_id", it) }
            data["shiftId"]?.let { putExtra("shift_id", it) }
            data["notificationId"]?.let { putExtra("notification_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSound)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
