package nz.co.doer.data.repository

import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.ActivityLogApi
import nz.co.doer.data.remote.dto.ActivityLogDto
import nz.co.doer.data.remote.dto.ActivityLogPagedDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityLogRepository @Inject constructor(
    private val api: ActivityLogApi
) {
    suspend fun getLogs(
        entityType: String? = null,
        entityId: Int? = null,
        userId: String? = null,
        action: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        skip: Int = 0,
        take: Int = 50
    ): ApiResult<ActivityLogPagedDto> = safeApiCall {
        api.getLogs(entityType, entityId, userId, action, startDate, endDate, skip, take)
    }

    suspend fun getEntityHistory(
        entityType: String,
        entityId: Int
    ): ApiResult<List<ActivityLogDto>> = safeApiCall {
        api.getEntityHistory(entityType, entityId)
    }
}
