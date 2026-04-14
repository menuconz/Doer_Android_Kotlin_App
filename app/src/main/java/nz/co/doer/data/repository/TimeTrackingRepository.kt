package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.TimeTrackingApi
import nz.co.doer.data.remote.dto.SiteHoursSummaryDto
import nz.co.doer.data.remote.dto.TimeTrackingFilterDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeTrackingRepository @Inject constructor(
    private val timeTrackingApi: TimeTrackingApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun getSiteHoursSummary(
        date: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): ApiResult<List<SiteHoursSummaryDto>> = safeApiCall {
        val filter = TimeTrackingFilterDto(
            date = date,
            dateFrom = dateFrom,
            dateTo = dateTo,
            userId = preferencesManager.getUserId(),
            lId = 1,
            siteId = 1,
            basicAuthUid = preferencesManager.getBasicAuthUid()
        )
        timeTrackingApi.getSiteHoursSummary(filter)
    }
}
