package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.RestHomeApi
import nz.co.doer.data.remote.dto.RestHomeDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestHomeRepository @Inject constructor(
    private val restHomeApi: RestHomeApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun registerRestHome(restHome: RestHomeDto): ApiResult<RestHomeDto> = safeApiCall {
        restHomeApi.registerRestHome(restHome)
    }

    suspend fun getRestHomes(): ApiResult<List<RestHomeDto>> = safeApiCall {
        restHomeApi.getRestHomes()
    }

    suspend fun getRestHomeById(id: Int): ApiResult<RestHomeDto> = safeApiCall {
        restHomeApi.getRestHomeById(id)
    }
}
