package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("createdBy") val createdBy: String = "",
    @SerialName("createdByIP") val createdByIp: String = "",
    @SerialName("createdDate") val createdDate: String? = null,
    @SerialName("modifiedBy") val modifiedBy: String = "",
    @SerialName("modifiedByIP") val modifiedByIp: String = "",
    @SerialName("modifiedDate") val modifiedDate: String? = null,
    @SerialName("jobs") val jobs: List<ClientJobDto> = emptyList(),
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("status") val status: Boolean = false,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class ClientJobDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("projectName") val projectName: String = "",
    @SerialName("isAssigned") val isAssigned: Boolean = false,
    @SerialName("originalIsAssigned") val originalIsAssigned: Boolean = false
)
