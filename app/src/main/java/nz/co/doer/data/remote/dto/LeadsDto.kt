package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LeadsDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("jobDescription") val jobDescription: String = "",
    @SerialName("contractType") val contractType: Int? = null,
    @SerialName("contractTypeName") val contractTypeName: String = "",
    @SerialName("contractTypeColor") val contractTypeColor: String = "",
    @SerialName("statusId") val statusId: Int = 0,
    @SerialName("statusName") val statusName: String = "",
    @SerialName("statusColor") val statusColor: String = "",
    @SerialName("location") val location: String = "",
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("costFromQuote") val costFromQuote: Double? = null,
    @SerialName("clientName") val clientName: String = "",
    @SerialName("clientEmail") val clientEmail: String = "",
    @SerialName("clientId") val clientId: Int? = null,
    @SerialName("createdBy") val createdBy: String = "",
    @SerialName("createdByIP") val createdByIp: String = "",
    @SerialName("createdDate") val createdDate: String? = null,
    @SerialName("modifiedBy") val modifiedBy: String = "",
    @SerialName("modifiedByIP") val modifiedByIp: String = "",
    @SerialName("modifiedDate") val modifiedDate: String? = null,
    @SerialName("ownerId") val ownerId: String = "",
    @SerialName("ownerName") val ownerName: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("status") val status: Boolean = false,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)
