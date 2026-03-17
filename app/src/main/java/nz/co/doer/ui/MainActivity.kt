package nz.co.doer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.local.SecureStorageManager
import nz.co.doer.data.repository.ShiftRepository
import nz.co.doer.ui.theme.DoerTheme
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var secureStorageManager: SecureStorageManager
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var shiftRepository: ShiftRepository

    // Matching MAUI: NotificationTapped navigates to NotificationView
    // Exposed as StateFlow so DoerNavHost can observe and navigate
    private val _pendingNavigation = MutableStateFlow<String?>(null)
    val pendingNavigation: StateFlow<String?> = _pendingNavigation.asStateFlow()

    fun clearPendingNavigation() {
        _pendingNavigation.value = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Timber.d("Notification permission granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        handleIntent(intent)

        setContent {
            DoerTheme {
                DoerNavHost(
                    secureStorageManager = secureStorageManager,
                    preferencesManager = preferencesManager,
                    shiftRepository = shiftRepository,
                    activity = this
                )
            }
        }
    }

    // Handle notification tap when app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            Timber.d("Notification tap: navigate to $navigateTo")
            _pendingNavigation.value = navigateTo
            // Clear the extra so it's not re-processed
            intent?.removeExtra("navigate_to")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
