package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.CaregiverLevelDto
import retrofit2.http.GET

interface CaregiverLevelApi {

    @GET("CaregiverLevels/GetCaregiverLevels")
    suspend fun getAllCaregiverLevels(): List<CaregiverLevelDto>
}
