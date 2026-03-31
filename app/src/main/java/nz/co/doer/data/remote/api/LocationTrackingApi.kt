package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.ClockEventDto
import nz.co.doer.data.remote.dto.EditTimeEntryDto
import nz.co.doer.data.remote.dto.LocationBatchDto
import nz.co.doer.data.remote.dto.TrackingNotificationDto
import nz.co.doer.data.remote.dto.TrackingStatusDto
import nz.co.doer.data.remote.dto.UserLocationDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LocationTrackingApi {

    @POST("User/UpdateUserLocation")
    suspend fun updateCaregiverLocation(
        @Body userLocation: UserLocationDto
    ): String

    @POST("Tracking/ClockEvent")
    suspend fun recordClockEvent(
        @Body clockEvent: ClockEventDto
    ): String

    @POST("Tracking/LocationBatch")
    suspend fun sendLocationBatch(
        @Body batch: LocationBatchDto
    ): String

    @POST("Tracking/UpdateStatus")
    suspend fun updateTrackingStatus(
        @Body status: TrackingStatusDto
    ): String

    @GET("Tracking/ActiveDoers")
    suspend fun getActiveDoers(
        @Query("siteId") siteId: Int = 1,
        @Query("lId") lId: Int = 1
    ): List<TrackingStatusDto>

    @POST("Tracking/SendNotification")
    suspend fun sendTrackingNotification(
        @Body notification: TrackingNotificationDto
    ): String

    @POST("Tracking/EditTimeEntry")
    suspend fun editTimeEntry(
        @Body request: EditTimeEntryDto
    ): String
}
