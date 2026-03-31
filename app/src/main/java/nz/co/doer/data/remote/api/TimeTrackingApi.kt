package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.SiteHoursSummaryDto
import nz.co.doer.data.remote.dto.TimeTrackingFilterDto
import retrofit2.http.Body
import retrofit2.http.POST

interface TimeTrackingApi {

    @POST("Tracking/SiteHoursSummary")
    suspend fun getSiteHoursSummary(
        @Body filter: TimeTrackingFilterDto
    ): List<SiteHoursSummaryDto>
}
