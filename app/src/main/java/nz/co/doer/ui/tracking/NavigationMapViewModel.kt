package nz.co.doer.ui.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.GoogleDirectionsService
import nz.co.doer.data.remote.NavigationStep
import nz.co.doer.data.remote.RouteInfo
import nz.co.doer.service.TrackingManager
import timber.log.Timber
import javax.inject.Inject

data class NavigationMapUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    // Site info
    val siteLatitude: Double = 0.0,
    val siteLongitude: Double = 0.0,
    val siteAddress: String = "",
    val projectName: String = "",
    val shiftId: Int = 0,

    // Doer live location
    val doerLatitude: Double = 0.0,
    val doerLongitude: Double = 0.0,
    val hasDoerLocation: Boolean = false,

    // Route
    val routePoints: List<LatLng> = emptyList(),
    val hasRoute: Boolean = false,

    // ETA & Distance (live updating)
    val eta: String = "",
    val distance: String = "",
    val durationSeconds: Int = 0,
    val distanceMeters: Int = 0,

    // Status
    val hasArrived: Boolean = false
)

@HiltViewModel
class NavigationMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directionsService: GoogleDirectionsService,
    private val trackingManager: TrackingManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(NavigationMapUiState())
    val uiState: StateFlow<NavigationMapUiState> = _uiState.asStateFlow()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var routeRefreshJob: Job? = null
    private var lastRouteUpdateLocation: LatLng? = null

    // Parse nav arguments
    private val siteLat: Double = savedStateHandle.get<String>("siteLat")?.toDoubleOrNull() ?: 0.0
    private val siteLng: Double = savedStateHandle.get<String>("siteLng")?.toDoubleOrNull() ?: 0.0
    private val siteAddress: String = savedStateHandle.get<String>("siteAddress") ?: ""
    private val projectName: String = savedStateHandle.get<String>("projectName") ?: ""
    private val shiftId: Int = savedStateHandle.get<String>("shiftId")?.toIntOrNull() ?: 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val newLat = location.latitude
            val newLng = location.longitude

            _uiState.value = _uiState.value.copy(
                doerLatitude = newLat,
                doerLongitude = newLng,
                hasDoerLocation = true
            )

            // Check if arrived (within 100m of site)
            val distToSite = TrackingManager.distanceBetween(
                newLat, newLng, siteLat, siteLng
            )
            if (distToSite <= 100f && !_uiState.value.hasArrived) {
                _uiState.value = _uiState.value.copy(
                    hasArrived = true,
                    eta = "Arrived",
                    distance = "0 m"
                )
            }

            // Refresh route if Doer moved > 200m from last route update
            val lastUpdate = lastRouteUpdateLocation
            if (lastUpdate == null || TrackingManager.distanceBetween(
                    newLat, newLng, lastUpdate.latitude, lastUpdate.longitude
                ) > 200f
            ) {
                fetchRoute(newLat, newLng)
            }
        }
    }

    init {
        _uiState.value = _uiState.value.copy(
            siteLatitude = siteLat,
            siteLongitude = siteLng,
            siteAddress = siteAddress,
            projectName = projectName,
            shiftId = shiftId
        )
        startLocationUpdates()
        startPeriodicRouteRefresh()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Location permission required"
            )
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5_000L
        )
            .setMinUpdateIntervalMillis(3_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )

            // Get initial location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    _uiState.value = _uiState.value.copy(
                        doerLatitude = location.latitude,
                        doerLongitude = location.longitude,
                        hasDoerLocation = true,
                        isLoading = false
                    )
                    fetchRoute(location.latitude, location.longitude)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Location permission denied")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Location permission required"
            )
        }
    }

    private fun fetchRoute(fromLat: Double, fromLng: Double) {
        if (siteLat == 0.0 && siteLng == 0.0) return
        if (_uiState.value.hasArrived) return

        viewModelScope.launch {
            val route = directionsService.getRoute(fromLat, fromLng, siteLat, siteLng)
            if (route != null) {
                lastRouteUpdateLocation = LatLng(fromLat, fromLng)
                _uiState.value = _uiState.value.copy(
                    routePoints = route.polylinePoints,
                    hasRoute = true,
                    eta = route.durationText,
                    distance = route.distanceText,
                    durationSeconds = route.durationSeconds,
                    distanceMeters = route.distanceMeters,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Refresh route every 60 seconds for updated ETA with traffic.
     */
    private fun startPeriodicRouteRefresh() {
        routeRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L) // Every 60 seconds
                val state = _uiState.value
                if (state.hasDoerLocation && !state.hasArrived) {
                    fetchRoute(state.doerLatitude, state.doerLongitude)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        routeRefreshJob?.cancel()
        super.onCleared()
    }
}
