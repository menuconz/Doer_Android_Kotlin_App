package nz.co.doer.data.repository

import kotlinx.coroutines.flow.first
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
        // Managers and admins see all workers' hours for the site, not just their own.
        // Contractors / caregivers see only their own hours.
        val isManager = preferencesManager.isManager.first()
        val isAdmin = preferencesManager.isAdmin.first()
        val userIdFilter = if (isManager || isAdmin) "" else preferencesManager.getUserId()

        val filter = TimeTrackingFilterDto(
            date = date,
            dateFrom = dateFrom,
            dateTo = dateTo,
            userId = userIdFilter,
            lId = 1,
            siteId = 1,
            basicAuthUid = preferencesManager.getBasicAuthUid()
        )
        timeTrackingApi.getSiteHoursSummary(filter)
    }
}
