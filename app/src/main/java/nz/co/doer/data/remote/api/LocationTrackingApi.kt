package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.UserLocationDto
import retrofit2.http.Body
import retrofit2.http.POST

interface LocationTrackingApi {

    @POST("User/UpdateUserLocation")
    suspend fun updateCaregiverLocation(
        @Body userLocation: UserLocationDto
    ): String
}
