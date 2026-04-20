package nz.co.doer.service

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.dto.ClockEventDto
import nz.co.doer.data.remote.dto.ClockEventType
import nz.co.doer.data.remote.dto.ClockLocationType
import nz.co.doer.data.remote.dto.DoerTrackingState
import nz.co.doer.data.remote.dto.LocationBatchDto
import nz.co.doer.data.remote.dto.LocationPointDto
import nz.co.doer.data.remote.dto.TrackingStatusDto
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.repository.LocationTrackingRepository
import retrofit2.HttpException
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core tracking orchestrator. Manages the finite state machine for Doer tracking,
 * coordinates geofencing, location updates, notifications, and 12hr threshold monitoring.
 *
 * State transitions:
 * IDLE → CLOCKED_IN → EN_ROUTE → ARRIVED → ON_SITE → LEAVING → CLOCKED_OUT
 *                   └→ ON_SITE (if at yard/office)
 *                                                    └→ ON_SITE (re-entry within grace)
 */
@Singleton
class TrackingManager @Inject constructor(
    private val context: Context,
    private val geofenceManager: GeofenceManager,
    private val locationTrackingRepository: LocationTrackingRepository,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: TrackingNotificationHelper,
    private val offlineSyncManager: OfflineSyncManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Current tracking state
    private val _trackingState = MutableStateFlow(DoerTrackingState.IDLE)
    val trackingState: StateFlow<DoerTrackingState> = _trackingState.asStateFlow()

    // Active shift being tracked
    private val _activeShiftId = MutableStateFlow<Int?>(null)
    val activeShiftId: StateFlow<Int?> = _activeShiftId.asStateFlow()

    // Location type where Doer clocked in
    private val _clockLocationType = MutableStateFlow(ClockLocationType.SITE)
    val clockLocationType: StateFlow<ClockLocationType> = _clockLocationType.asStateFlow()

    // Batched location points waiting to be sent
    private val locationBatch = mutableListOf<LocationPointDto>()
    private val batchLock = Any()

    // Grace period tracking for LEAVING state
    private var leaveTimestamp: Long = 0L

    // 12-hour threshold monitor
    private var thresholdMonitorJob: Job? = null
    private var onSiteStartTime: Long = 0L
    private var warningFired: Boolean = false
    private var exceededFired: Boolean = false

    // Project name for notification messages
    private var activeProjectName: String = ""

    // Last known location — used for status pushes between GPS samples
    private var lastKnownLat: Double = 0.0
    private var lastKnownLng: Double = 0.0

    // Site coordinates for distance-remaining calculation during EN_ROUTE
    private var activeSiteLat: Double? = null
    private var activeSiteLng: Double? = null

    // Selected sub-item / stage for the active shift (for per-stage hours reporting)
    private var activeSubItemId: Int? = null
    private var activeSubItemName: String? = null

    init {
        // Listen for "shift was deleted on server" signals from the sync layer so a
        // stale tracker (possibly persisted across app restarts) heals on the first
        // failed sync attempt.
        scope.launch {
            offlineSyncManager.shiftDeletedEvents.collect { deletedShiftId ->
                if (_activeShiftId.value == deletedShiftId) {
                    handleShiftDeleted(deletedShiftId)
                }
            }
        }
    }

    /**
     * Clock in at a location. This is the entry point for tracking.
     */
    fun clockIn(
        shiftId: Int,
        locationType: ClockLocationType,
        siteLatitude: Double?,
        siteLongitude: Double?,
        currentLatitude: Double,
        currentLongitude: Double,
        projectName: String = "",
        subItemId: Int? = null,
        subItemName: String? = null
    ) {
        // Stage session state before transition so status pushes have context
        _activeShiftId.value = shiftId
        _clockLocationType.value = locationType
        activeProjectName = projectName
        activeSiteLat = siteLatitude
        activeSiteLng = siteLongitude
        activeSubItemId = subItemId?.takeIf { it != 0 }
        activeSubItemName = subItemName?.takeIf { it.isNotBlank() }
        updateLastKnownLocation(currentLatitude, currentLongitude)

        if (!transitionTo(DoerTrackingState.CLOCKED_IN)) return
        // Note: do NOT push status here — we'll transition immediately to EN_ROUTE or
        // ON_SITE below and push the FINAL state once. Pushing twice in quick succession
        // races on the server and can leave Live Tracking stuck on "Clocked In".

        // Record clock-in event
        recordClockEvent(
            shiftId = shiftId,
            eventType = ClockEventType.CLOCK_IN,
            latitude = currentLatitude,
            longitude = currentLongitude
        )

        // Notify manager
        notificationHelper.onClockIn(shiftId, currentLatitude, currentLongitude, projectName)

        // Register geofence at the shift's location regardless of Site/Yard/Office —
        // the shift's coordinates are always the destination the worker is travelling to.
        if (siteLatitude != null && siteLongitude != null
            && siteLatitude != 0.0 && siteLongitude != 0.0
        ) {
            geofenceManager.registerSiteGeofence(shiftId, siteLatitude, siteLongitude)

            // Check if Doer is already at the destination (within geofence radius)
            val distanceToSite = distanceBetween(
                currentLatitude, currentLongitude, siteLatitude, siteLongitude
            )
            if (distanceToSite <= GeofenceManager.DEFAULT_GEOFENCE_RADIUS) {
                // Already on-site — go directly to ON_SITE
                transitionTo(DoerTrackingState.ON_SITE)
                pushTrackingStatus()
                startOnSiteMonitoring()
                startLocationService(TrackingMode.ON_SITE)
            } else {
                // Need to travel — go to EN_ROUTE
                transitionTo(DoerTrackingState.EN_ROUTE)
                pushTrackingStatus()
                notificationHelper.onEnRoute(shiftId, currentLatitude, currentLongitude)
                startLocationService(TrackingMode.EN_ROUTE)
            }
        } else {
            // No coordinates available on the shift — cannot register a geofence.
            // Fall through to ON_SITE directly (trust-based).
            transitionTo(DoerTrackingState.ON_SITE)
            pushTrackingStatus()
            startOnSiteMonitoring()
            startLocationService(TrackingMode.ON_SITE)
        }
    }

    /**
     * Clock out from current shift. Stops all tracking.
     */
    fun clockOut(
        currentLatitude: Double = 0.0,
        currentLongitude: Double = 0.0,
        reasonCode: String? = null
    ) {
        val shiftId = _activeShiftId.value ?: return

        if (currentLatitude != 0.0 || currentLongitude != 0.0) {
            updateLastKnownLocation(currentLatitude, currentLongitude)
        }

        transitionTo(DoerTrackingState.CLOCKED_OUT)
        pushTrackingStatus()

        // Stop threshold monitor
        stopOnSiteMonitoring()

        // Record clock-out event
        recordClockEvent(
            shiftId = shiftId,
            eventType = ClockEventType.CLOCK_OUT,
            latitude = currentLatitude,
            longitude = currentLongitude,
            reasonCode = reasonCode
        )

        // Notify
        notificationHelper.onClockOut(shiftId, currentLatitude, currentLongitude, activeProjectName)

        // Flush any remaining location batch
        flushLocationBatch()

        // Clean up
        geofenceManager.removeGeofence(shiftId)
        stopLocationService()

        _activeShiftId.value = null
        activeProjectName = ""
        activeSiteLat = null
        activeSiteLng = null
        activeSubItemId = null
        activeSubItemName = null
        _trackingState.value = DoerTrackingState.IDLE
    }

    /**
     * Handle geofence ENTER — Doer has entered the site radius.
     */
    fun onGeofenceEnter(shiftId: Int, latitude: Double, longitude: Double) {
        if (_activeShiftId.value != shiftId) return
        updateLastKnownLocation(latitude, longitude)
        val currentState = _trackingState.value

        if (currentState == DoerTrackingState.EN_ROUTE) {
            transitionTo(DoerTrackingState.ARRIVED)
            pushTrackingStatus()
            recordClockEvent(shiftId, ClockEventType.GEOFENCE_ENTER, latitude, longitude)
            notificationHelper.onArrived(shiftId, latitude, longitude, activeProjectName)
            updateLocationServiceMode(TrackingMode.ON_SITE)
        } else if (currentState == DoerTrackingState.LEAVING) {
            // Re-entered within grace period — back to ON_SITE
            transitionTo(DoerTrackingState.ON_SITE)
            pushTrackingStatus()
            leaveTimestamp = 0L
            // Resume threshold monitoring (don't reset timer — time continues)
        }
    }

    /**
     * Handle geofence DWELL — Doer has been at site for 2+ minutes (confirmed arrival).
     */
    fun onGeofenceDwell(shiftId: Int, latitude: Double, longitude: Double) {
        if (_activeShiftId.value != shiftId) return
        updateLastKnownLocation(latitude, longitude)

        if (_trackingState.value == DoerTrackingState.ARRIVED) {
            transitionTo(DoerTrackingState.ON_SITE)
            pushTrackingStatus()
            recordClockEvent(shiftId, ClockEventType.STATE_CHANGE, latitude, longitude)
            startOnSiteMonitoring()
        }
    }

    /**
     * Handle geofence EXIT — Doer has left the site radius.
     */
    fun onGeofenceExit(shiftId: Int, latitude: Double, longitude: Double) {
        if (_activeShiftId.value != shiftId) return
        updateLastKnownLocation(latitude, longitude)
        val currentState = _trackingState.value

        if (currentState == DoerTrackingState.ON_SITE || currentState == DoerTrackingState.ARRIVED) {
            transitionTo(DoerTrackingState.LEAVING)
            pushTrackingStatus()
            leaveTimestamp = System.currentTimeMillis()
            recordClockEvent(shiftId, ClockEventType.GEOFENCE_EXIT, latitude, longitude)
            notificationHelper.onLeftSite(shiftId, latitude, longitude, activeProjectName)
        }
    }

    /**
     * Called when GPS permission is revoked while tracking is active.
     */
    fun notifyGpsPermissionRevoked(shiftId: Int) {
        notificationHelper.onGpsPermissionRevoked(shiftId)
    }

    /**
     * Called by LocationTrackingService when grace period expires and Doer hasn't re-entered.
     */
    fun confirmDeparture() {
        if (_trackingState.value == DoerTrackingState.LEAVING) {
            clockOut()
        }
    }

    /**
     * Add a location point to the batch. Called by LocationTrackingService.
     */
    fun addLocationPoint(point: LocationPointDto) {
        updateLastKnownLocation(point.latitude, point.longitude)
        synchronized(batchLock) {
            locationBatch.add(point)
            if (locationBatch.size >= BATCH_SIZE) {
                flushLocationBatch()
            }
        }
    }

    private fun updateLastKnownLocation(latitude: Double, longitude: Double) {
        if (latitude == 0.0 && longitude == 0.0) return
        lastKnownLat = latitude
        lastKnownLng = longitude
    }

    /**
     * Push a snapshot of the current tracking state to the server so the manager
     * live-map reflects state transitions (IDLE → EN_ROUTE → ARRIVED → ON_SITE …)
     * in near real time. Called after every state change in this manager.
     */
    private fun pushTrackingStatus() {
        val shiftId = _activeShiftId.value ?: return
        val state = _trackingState.value
        val siteLat = activeSiteLat
        val siteLng = activeSiteLng
        val lat = lastKnownLat
        val lng = lastKnownLng

        val distanceRemainingMeters: Double? =
            if (state == DoerTrackingState.EN_ROUTE && siteLat != null && siteLng != null
                && lat != 0.0 && lng != 0.0
            ) {
                distanceBetween(lat, lng, siteLat, siteLng).toDouble()
            } else null

        scope.launch {
            try {
                val dto = TrackingStatusDto(
                    userId = preferencesManager.getUserId(),
                    shiftId = shiftId,
                    trackingState = state.value,
                    latitude = lat,
                    longitude = lng,
                    timestamp = nowUtc(),
                    eta = null,
                    distanceRemaining = distanceRemainingMeters,
                    siteLatitude = siteLat,
                    siteLongitude = siteLng,
                    basicAuthUid = preferencesManager.getBasicAuthUid()
                )
                val result = locationTrackingRepository.updateTrackingStatus(dto)
                if (result is ApiResult.Error &&
                    (result.exception as? HttpException)?.code() == 404
                ) {
                    Timber.w("Active shift $shiftId no longer exists on server — stopping tracker")
                    handleShiftDeleted(shiftId)
                    return@launch
                }
                Timber.d("Pushed tracking status: state=$state, shiftId=$shiftId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to push tracking status for shift $shiftId (state=$state)")
            }
        }
    }

    /**
     * Called when the server reports (404) that the active shift has been deleted.
     * Tears down all local tracking state without attempting further server writes
     * for this shift.
     */
    private fun handleShiftDeleted(shiftId: Int) {
        stopOnSiteMonitoring()
        synchronized(batchLock) { locationBatch.clear() }
        geofenceManager.removeGeofence(shiftId)
        stopLocationService()

        notificationHelper.onShiftDeleted(shiftId, activeProjectName)

        _activeShiftId.value = null
        activeProjectName = ""
        activeSiteLat = null
        activeSiteLng = null
        activeSubItemId = null
        activeSubItemName = null
        _trackingState.value = DoerTrackingState.IDLE
    }

    /**
     * Flush accumulated location points to the server.
     */
    fun flushLocationBatch() {
        val pointsToSend: List<LocationPointDto>
        synchronized(batchLock) {
            if (locationBatch.isEmpty()) return
            pointsToSend = locationBatch.toList()
            locationBatch.clear()
        }

        val shiftId = _activeShiftId.value ?: return

        scope.launch {
            try {
                // Offline-first: write to Room, then sync
                offlineSyncManager.queueLocationPoints(
                    userId = preferencesManager.getUserId(),
                    shiftId = shiftId,
                    points = pointsToSend
                )
                Timber.d("Flushed ${pointsToSend.size} location points to offline queue for shift $shiftId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to queue location batch, re-queuing in memory")
                synchronized(batchLock) {
                    locationBatch.addAll(0, pointsToSend)
                }
            }
        }
    }

    // ========== 12-Hour Threshold Monitor ==========

    /**
     * Start monitoring time on site. Fires warnings at 11hr and alerts at 12hr.
     * Checks every 5 minutes to balance accuracy vs battery.
     */
    private fun startOnSiteMonitoring() {
        if (thresholdMonitorJob?.isActive == true) return

        onSiteStartTime = System.currentTimeMillis()
        warningFired = false
        exceededFired = false

        thresholdMonitorJob = scope.launch {
            while (isActive) {
                delay(THRESHOLD_CHECK_INTERVAL_MS)

                val hoursOnSite = getHoursOnSite()
                val shiftId = _activeShiftId.value ?: break

                // 11-hour warning (1 hour before threshold)
                if (hoursOnSite >= WARNING_HOURS && !warningFired) {
                    warningFired = true
                    Timber.w("Threshold WARNING: $hoursOnSite hours on site for shift $shiftId")
                    notificationHelper.onThresholdWarning(shiftId, hoursOnSite, activeProjectName)
                }

                // 12-hour exceeded
                if (hoursOnSite >= THRESHOLD_HOURS && !exceededFired) {
                    exceededFired = true
                    Timber.w("Threshold EXCEEDED: $hoursOnSite hours on site for shift $shiftId")
                    notificationHelper.onThresholdExceeded(shiftId, hoursOnSite, activeProjectName)
                }
            }
        }
        Timber.d("On-site threshold monitoring started")
    }

    private fun stopOnSiteMonitoring() {
        thresholdMonitorJob?.cancel()
        thresholdMonitorJob = null
        onSiteStartTime = 0L
        warningFired = false
        exceededFired = false
        Timber.d("On-site threshold monitoring stopped")
    }

    /** Get hours the Doer has been on site. */
    fun getHoursOnSite(): Double {
        if (onSiteStartTime == 0L) return 0.0
        val elapsedMs = System.currentTimeMillis() - onSiteStartTime
        return elapsedMs / (1000.0 * 60.0 * 60.0)
    }

    // ========== State Machine ==========

    private fun transitionTo(newState: DoerTrackingState): Boolean {
        val currentState = _trackingState.value
        val validTransitions = DoerTrackingState.validTransitions(currentState)

        if (newState !in validTransitions) {
            Timber.w("Invalid state transition: $currentState → $newState. Valid: $validTransitions")
            return false
        }

        Timber.d("State transition: $currentState → $newState")
        _trackingState.value = newState
        return true
    }

    private fun recordClockEvent(
        shiftId: Int,
        eventType: ClockEventType,
        latitude: Double,
        longitude: Double,
        reasonCode: String? = null
    ) {
        scope.launch {
            try {
                // Offline-first: write to Room, then sync.
                // Stage is attached to every event for this shift so the server can
                // fall back to non-CLOCK_IN events if needed.
                offlineSyncManager.queueClockEvent(
                    userId = preferencesManager.getUserId(),
                    shiftId = shiftId,
                    eventType = eventType.value,
                    locationType = _clockLocationType.value.value,
                    trackingState = _trackingState.value.value,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = nowUtc(),
                    reasonCode = reasonCode,
                    subItemId = activeSubItemId,
                    subItemName = activeSubItemName
                )
                Timber.d("Clock event queued: ${eventType.value} for shift $shiftId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to queue clock event")
            }
        }
    }

    // ========== Service Control ==========

    private fun startLocationService(mode: TrackingMode) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
            putExtra(LocationTrackingService.EXTRA_TRACKING_MODE, mode.name)
            putExtra(LocationTrackingService.EXTRA_SHIFT_ID, _activeShiftId.value ?: 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateLocationServiceMode(mode: TrackingMode) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_UPDATE_MODE
            putExtra(LocationTrackingService.EXTRA_TRACKING_MODE, mode.name)
        }
        context.startService(intent)
    }

    private fun stopLocationService() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        context.startService(intent)
    }

    private fun nowUtc(): String =
        nz.co.doer.util.Constants.nowNz()

    companion object {
        const val BATCH_SIZE = 5
        const val GRACE_PERIOD_MS = 5 * 60 * 1000L // 5 minutes
        const val THRESHOLD_HOURS = 12.0
        const val WARNING_HOURS = 11.0
        const val THRESHOLD_CHECK_INTERVAL_MS = 5 * 60 * 1000L // Check every 5 minutes

        fun distanceBetween(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Float {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }
    }
}

/**
 * Tracking mode that determines GPS update frequency and accuracy.
 */
enum class TrackingMode {
    /** High frequency tracking while traveling to site. 10s interval, high accuracy. */
    EN_ROUTE,
    /** Low frequency tracking while working on site. 5min interval, balanced accuracy. */
    ON_SITE
}
