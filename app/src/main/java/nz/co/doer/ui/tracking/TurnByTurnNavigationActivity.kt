package nz.co.doer.ui.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.SimulationOptions
import com.google.android.libraries.navigation.SupportNavigationFragment
import com.google.android.libraries.navigation.Waypoint
import nz.co.doer.BuildConfig
import nz.co.doer.R
import timber.log.Timber

/**
 * Full-screen turn-by-turn navigation using Google Navigation SDK.
 * Provides voice guidance, real-time traffic rerouting, and lane guidance.
 *
 * Launch with:
 *   TurnByTurnNavigationActivity.start(context, destLat, destLng, projectName)
 */
class TurnByTurnNavigationActivity : AppCompatActivity() {

    private var navigator: Navigator? = null
    private var navFragment: SupportNavigationFragment? = null
    private var arrivalListener: Navigator.ArrivalListener? = null
    private var routeChangedListener: Navigator.RouteChangedListener? = null

    private val autoCloseHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoCloseRunnable = Runnable {
        if (!isFinishing && !isDestroyed) finish()
    }

    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var projectName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, 0.0)
        destLng = intent.getDoubleExtra(EXTRA_DEST_LNG, 0.0)
        projectName = intent.getStringExtra(EXTRA_PROJECT_NAME) ?: ""

        // Set up the navigation fragment
        setContentView(R.layout.activity_turn_by_turn_navigation)
        navFragment = supportFragmentManager
            .findFragmentById(R.id.navigation_fragment) as? SupportNavigationFragment

        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            initNavigationApi()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initNavigationApi()
            } else {
                Toast.makeText(this, "Location permission required for navigation", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initNavigationApi() {
        // In debug mode, set simulated starting location
        if (BuildConfig.DEBUG) {
            NavigationApi.setAbnormalTerminationReportingEnabled(false)
        }

        NavigationApi.getNavigator(this, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(nav: Navigator) {
                navigator = nav
                Timber.d("Navigator ready")

                // Set up listeners
                registerNavigationListeners()

                // Start navigation to destination
                navigateToDestination()
            }

            override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
                Timber.e("Navigation API error: $errorCode")
                when (errorCode) {
                    NavigationApi.ErrorCode.NOT_AUTHORIZED -> {
                        Toast.makeText(
                            this@TurnByTurnNavigationActivity,
                            "Navigation SDK not enabled. Please enable it in Google Cloud Console.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            this@TurnByTurnNavigationActivity,
                            "Error loading navigation: $errorCode",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                finish()
            }
        })
    }

    private fun registerNavigationListeners() {
        arrivalListener = Navigator.ArrivalListener {
            Timber.d("Arrived at destination: $projectName")
            Toast.makeText(this, "You've arrived at $projectName!", Toast.LENGTH_LONG).show()
            navigator?.clearDestinations()
            // Auto-close after 5 seconds if user hasn't pressed back themselves
            autoCloseHandler.removeCallbacks(autoCloseRunnable)
            autoCloseHandler.postDelayed(autoCloseRunnable, 5_000L)
        }
        navigator?.addArrivalListener(arrivalListener)

        routeChangedListener = Navigator.RouteChangedListener {
            Timber.d("Route changed")
        }
        navigator?.addRouteChangedListener(routeChangedListener)
    }

    private fun navigateToDestination() {
        if (destLat == 0.0 && destLng == 0.0) {
            Toast.makeText(this, "Invalid destination coordinates", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            val destination = Waypoint.builder()
                .setLatLng(destLat, destLng)
                .setTitle(projectName.ifBlank { "Destination" })
                .build()

            val pendingRoute = navigator?.setDestination(destination)
            pendingRoute?.setOnResultListener { code ->
                when (code) {
                    Navigator.RouteStatus.OK -> {
                        Timber.d("Route found to $projectName")
                        // Hide action bar for full-screen experience
                        supportActionBar?.hide()
                        // Start turn-by-turn guidance with voice
                        navigator?.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                        navigator?.startGuidance()

                        // In debug builds, simulate driving along the route at normal speed
                        if (BuildConfig.DEBUG) {
                            navigator?.simulator?.simulateLocationsAlongExistingRoute(
                                SimulationOptions().speedMultiplier(10f) // 5x speed for testing
                            )
                            Timber.d("Simulation started at 5x speed")
                        }
                    }
                    Navigator.RouteStatus.NO_ROUTE_FOUND -> {
                        Toast.makeText(this, "No route found to destination", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    Navigator.RouteStatus.NETWORK_ERROR -> {
                        Toast.makeText(this, "Network error. Check your connection.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    else -> {
                        Toast.makeText(this, "Route error: $code", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        } catch (e: Waypoint.UnsupportedPlaceIdException) {
            Timber.e(e, "Invalid waypoint")
            Toast.makeText(this, "Invalid destination", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        // Cancel the auto-close timer if it's still pending
        autoCloseHandler.removeCallbacks(autoCloseRunnable)

        // Stop simulator first (debug builds) so no further simulated fixes arrive
        try {
            if (BuildConfig.DEBUG) navigator?.simulator?.unsetUserLocation()
        } catch (_: Exception) {}

        // Stop voice guidance and clear destinations so nothing keeps running
        try {
            navigator?.setAudioGuidance(Navigator.AudioGuidance.SILENT)
            navigator?.stopGuidance()
            navigator?.clearDestinations()
        } catch (_: Exception) {}

        arrivalListener?.let { navigator?.removeArrivalListener(it) }
        routeChangedListener?.let { navigator?.removeRouteChangedListener(it) }
        navigator?.cleanup()
        navigator = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DEST_LAT = "dest_lat"
        private const val EXTRA_DEST_LNG = "dest_lng"
        private const val EXTRA_PROJECT_NAME = "project_name"
        private const val LOCATION_PERMISSION_REQUEST = 100

        fun start(context: Context, destLat: Double, destLng: Double, projectName: String) {
            val intent = Intent(context, TurnByTurnNavigationActivity::class.java).apply {
                putExtra(EXTRA_DEST_LAT, destLat)
                putExtra(EXTRA_DEST_LNG, destLng)
                putExtra(EXTRA_PROJECT_NAME, projectName)
            }
            context.startActivity(intent)
        }
    }
}
