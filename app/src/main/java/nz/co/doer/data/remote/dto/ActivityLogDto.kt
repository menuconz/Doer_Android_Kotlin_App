package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityLogDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("userId") val userId: String = "",
    @SerialName("userName") val userName: String = "",
    @SerialName("entityType") val entityType: String = "",
    @SerialName("entityId") val entityId: Int = 0,
    @SerialName("action") val action: String = "",
    @SerialName("fieldName") val fieldName: String? = null,
    @SerialName("oldValue") val oldValue: String? = null,
    @SerialName("newValue") val newValue: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("ipAddress") val ipAddress: String? = null,
    @SerialName("timestamp") val timestamp: String = ""
)

@Serializable
data class ActivityLogPagedDto(
    @SerialName("totalCount") val totalCount: Int = 0,
    @SerialName("logs") val logs: List<ActivityLogDto> = emptyList()
)
