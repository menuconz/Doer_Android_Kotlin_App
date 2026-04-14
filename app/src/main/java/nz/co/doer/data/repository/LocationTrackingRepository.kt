package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.LocationTrackingApi
import nz.co.doer.data.remote.dto.ClockEventDto
import nz.co.doer.data.remote.dto.EditTimeEntryDto
import nz.co.doer.data.remote.dto.LocationBatchDto
import nz.co.doer.data.remote.dto.TrackingNotificationDto
import nz.co.doer.data.remote.dto.TrackingStatusDto
import nz.co.doer.data.remote.dto.UserLocationDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationTrackingRepository @Inject constructor(
    private val locationTrackingApi: LocationTrackingApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun updateCaregiverLocation(
        latitude: Double,
        longitude: Double,
        timestamp: String
    ): ApiResult<String> = safeApiCall {
        val userLocation = UserLocationDto(
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            userId = preferencesManager.getUserId(),
            siteId = 1,
            lId = 1,
            basicAuthUid = preferencesManager.getBasicAuthUid()
        )
        locationTrackingApi.updateCaregiverLocation(userLocation)
    }

    suspend fun recordClockEvent(event: ClockEventDto): ApiResult<String> = safeApiCall {
        locationTrackingApi.recordClockEvent(event)
    }

    suspend fun sendLocationBatch(batch: LocationBatchDto): ApiResult<String> = safeApiCall {
        locationTrackingApi.sendLocationBatch(batch)
    }

    suspend fun updateTrackingStatus(status: TrackingStatusDto): ApiResult<String> = safeApiCall {
        locationTrackingApi.updateTrackingStatus(status)
    }

    suspend fun getActiveDoers(): ApiResult<List<TrackingStatusDto>> = safeApiCall {
        locationTrackingApi.getActiveDoers()
    }

    suspend fun sendTrackingNotification(
        notification: TrackingNotificationDto
    ): ApiResult<String> = safeApiCall {
        locationTrackingApi.sendTrackingNotification(notification)
    }

    suspend fun editTimeEntry(request: EditTimeEntryDto): ApiResult<String> = safeApiCall {
        locationTrackingApi.editTimeEntry(request)
    }
}
