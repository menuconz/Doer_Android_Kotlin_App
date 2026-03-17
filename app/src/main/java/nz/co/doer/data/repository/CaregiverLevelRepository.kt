package nz.co.doer.data.repository

import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.CaregiverLevelApi
import nz.co.doer.data.remote.dto.CaregiverLevelDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaregiverLevelRepository @Inject constructor(
    private val caregiverLevelApi: CaregiverLevelApi
) {
    suspend fun getAllCaregiverLevels(): ApiResult<List<CaregiverLevelDto>> = safeApiCall {
        caregiverLevelApi.getAllCaregiverLevels()
    }
}
