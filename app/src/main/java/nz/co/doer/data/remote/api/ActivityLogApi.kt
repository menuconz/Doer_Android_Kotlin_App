package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.ActivityLogDto
import nz.co.doer.data.remote.dto.ActivityLogPagedDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ActivityLogApi {

    @GET("ActivityLog")
    suspend fun getLogs(
        @Query("entityType") entityType: String? = null,
        @Query("entityId") entityId: Int? = null,
        @Query("userId") userId: String? = null,
        @Query("action") action: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("skip") skip: Int = 0,
        @Query("take") take: Int = 50
    ): ActivityLogPagedDto

    @GET("ActivityLog/{entityType}/{entityId}")
    suspend fun getEntityHistory(
        @Path("entityType") entityType: String,
        @Path("entityId") entityId: Int
    ): List<ActivityLogDto>
}
