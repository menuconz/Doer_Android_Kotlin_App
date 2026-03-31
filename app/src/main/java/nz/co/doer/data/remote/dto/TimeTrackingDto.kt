package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary of total hours for a single site across all Doers and stages.
 */
@Serializable
data class SiteHoursSummaryDto(
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("projectName") val projectName: String = "",
    @SerialName("address") val address: String = "",
    @SerialName("clientName") val clientName: String? = null,
    @SerialName("totalHours") val totalHours: Double = 0.0,
    @SerialName("doerCount") val doerCount: Int = 0,
    @SerialName("stages") val stages: List<StageHoursDto> = emptyList(),
    @SerialName("doerHours") val doerHours: List<DoerHoursDto> = emptyList()
)

/**
 * Hours a single Doer has worked at a specific site.
 */
@Serializable
data class DoerHoursDto(
    @SerialName("userId") val userId: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("clockInTime") val clockInTime: String? = null,
    @SerialName("clockOutTime") val clockOutTime: String? = null,
    @SerialName("totalHours") val totalHours: Double = 0.0,
    @SerialName("stage") val stage: String = "",
    @SerialName("isActive") val isActive: Boolean = false
)

/**
 * Hours per work stage at a site (e.g., Prep, Place & Finish, Saw Cutting).
 */
@Serializable
data class StageHoursDto(
    @SerialName("stageName") val stageName: String = "",
    @SerialName("totalHours") val totalHours: Double = 0.0,
    @SerialName("doerCount") val doerCount: Int = 0
)

/**
 * Request filter for time tracking dashboard data.
 */
@Serializable
data class TimeTrackingFilterDto(
    @SerialName("date") val date: String? = null,
    @SerialName("dateFrom") val dateFrom: String? = null,
    @SerialName("dateTo") val dateTo: String? = null,
    @SerialName("userId") val userId: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)
