package nz.co.doer.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.DoerTrackingState
import nz.co.doer.data.remote.dto.TrackingStatusDto
import nz.co.doer.data.repository.LocationTrackingRepository
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ActiveDoerUi(
    val userId: String,
    val displayName: String = "",
    val shiftId: Int,
    val trackingState: DoerTrackingState,
    val latitude: Double,
    val longitude: Double,
    val eta: String?,
    val distanceRemaining: Double?,
    val siteName: String = "",
    val projectName: String = "",
    val siteLatitude: Double? = null,
    val siteLongitude: Double? = null,
    val timeOnSite: String = "",
    val timestamp: String = ""
) {
    val statusLabel: String get() = when (trackingState) {
        DoerTrackingState.IDLE -> "Idle"
        DoerTrackingState.CLOCKED_IN -> "Clocked In"
        DoerTrackingState.EN_ROUTE -> "On the Way"
        DoerTrackingState.ARRIVED -> "Arrived"
        DoerTrackingState.ON_SITE -> "On Site"
        DoerTrackingState.LEAVING -> "Leaving"
        DoerTrackingState.CLOCKED_OUT -> "Clocked Out"
    }

    val markerColor: Long get() = when (trackingState) {
        DoerTrackingState.IDLE -> 0xFF9CA3AF
        DoerTrackingState.CLOCKED_IN -> 0xFF007AFF
        DoerTrackingState.EN_ROUTE -> 0xFF3B82F6
        DoerTrackingState.ARRIVED -> 0xFF10B981
        DoerTrackingState.ON_SITE -> 0xFF00C875
        DoerTrackingState.LEAVING -> 0xFFF59E0B
        DoerTrackingState.CLOCKED_OUT -> 0xFF6B7280
    }
}

data class LiveTrackingUiState(
    val activeDoers: List<ActiveDoerUi> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isPolling: Boolean = false,
    val lastUpdated: String = "",
    // Stats
    val totalActiveDoers: Int = 0,
    val enRouteCount: Int = 0,
    val onSiteCount: Int = 0,
    val arrivedCount: Int = 0,
    // Selected Doer route
    val selectedDoerUserId: String? = null,
    val selectedDoerRoute: List<com.google.android.gms.maps.model.LatLng> = emptyList()
)

@HiltViewModel
class LiveTrackingViewModel @Inject constructor(
    private val locationTrackingRepository: LocationTrackingRepository,
    private val directionsService: nz.co.doer.data.remote.GoogleDirectionsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTrackingUiState())
    val uiState: StateFlow<LiveTrackingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    private val parseFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH)
    )

    init {
        fetchActiveDoers()
    }

    /**
     * Start polling for active Doer locations every 15 seconds.
     * Called when the screen becomes visible.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPolling = true)
            while (isActive) {
                fetchActiveDoers()
                delay(POLL_INTERVAL_MS)
            }
        }
        Timber.d("Live tracking polling started")
    }

    /**
     * Stop polling. Called when the screen is no longer visible.
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.value = _uiState.value.copy(isPolling = false)
        Timber.d("Live tracking polling stopped")
    }

    fun refresh() {
        fetchActiveDoers()
    }

    /** Manager taps a Doer card — show route from Doer to site */
    fun selectDoer(doer: ActiveDoerUi) {
        if (doer.siteLatitude == null || doer.siteLongitude == null ||
            doer.siteLatitude == 0.0 || doer.siteLongitude == 0.0) {
            _uiState.value = _uiState.value.copy(selectedDoerUserId = doer.userId, selectedDoerRoute = emptyList())
            return
        }
        _uiState.value = _uiState.value.copy(selectedDoerUserId = doer.userId, selectedDoerRoute = emptyList())
        viewModelScope.launch {
            val route = directionsService.getRoute(
                doer.latitude, doer.longitude,
                doer.siteLatitude, doer.siteLongitude
            )
            if (route != null) {
                _uiState.value = _uiState.value.copy(selectedDoerRoute = route.polylinePoints)
            }
        }
    }

    fun clearSelectedDoer() {
        _uiState.value = _uiState.value.copy(selectedDoerUserId = null, selectedDoerRoute = emptyList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun fetchActiveDoers() {
        viewModelScope.launch {
            when (val result = locationTrackingRepository.getActiveDoers()) {
                is ApiResult.Success -> {
                    val doers = result.data.map { it.toUi() }
                    val now = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH))

                    _uiState.value = _uiState.value.copy(
                        activeDoers = doers,
                        isLoading = false,
                        errorMessage = null,
                        lastUpdated = now,
                        totalActiveDoers = doers.size,
                        enRouteCount = doers.count { it.trackingState == DoerTrackingState.EN_ROUTE },
                        onSiteCount = doers.count {
                            it.trackingState == DoerTrackingState.ON_SITE ||
                            it.trackingState == DoerTrackingState.ARRIVED
                        },
                        arrivedCount = doers.count { it.trackingState == DoerTrackingState.ARRIVED }
                    )
                }

                is ApiResult.Error -> {
                    Timber.e("Failed to fetch active doers: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }

                is ApiResult.Loading -> {}
            }
        }
    }

    private fun TrackingStatusDto.toUi(): ActiveDoerUi {
        val state = DoerTrackingState.fromValue(trackingState)
        // Only show time on site when actually ON_SITE or ARRIVED, not when CLOCKED_IN or EN_ROUTE
        val timeStr = if (state == DoerTrackingState.ON_SITE || state == DoerTrackingState.ARRIVED) {
            computeTimeOnSite(timestamp)
        } else {
            ""
        }

        return ActiveDoerUi(
            userId = userId,
            displayName = displayName,
            shiftId = shiftId,
            trackingState = state,
            latitude = latitude,
            longitude = longitude,
            eta = eta,
            distanceRemaining = distanceRemaining,
            siteName = siteName,
            projectName = projectName,
            siteLatitude = siteLatitude,
            siteLongitude = siteLongitude,
            timeOnSite = timeStr,
            timestamp = timestamp
        )
    }

    private fun computeTimeOnSite(timestamp: String): String {
        if (timestamp.isBlank()) return ""
        for (formatter in parseFormatters) {
            try {
                val clockInTime = LocalDateTime.parse(timestamp.trim(), formatter)
                val now = LocalDateTime.now()
                val duration = Duration.between(clockInTime, now)
                val hours = duration.toHours()
                val minutes = duration.toMinutes() % 60
                return "${hours}h ${minutes}m"
            } catch (_: Exception) {
                // try next
            }
        }
        return ""
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        const val POLL_INTERVAL_MS = 15_000L // 15 seconds
    }
}
