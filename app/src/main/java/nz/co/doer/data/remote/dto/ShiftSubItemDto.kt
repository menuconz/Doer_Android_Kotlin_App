package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShiftSubItemDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("isDeleteSubItem") val isDeleteSubItem: Boolean = false,
    @SerialName("subitem") val subitem: String = "",
    @SerialName("hsRequired") val hsRequired: Int = 0,
    @SerialName("status") val status: Int = 0,
    @SerialName("isContractor") val isContractor: Boolean = false,
    @SerialName("dateStarted") val dateStarted: String? = null,
    @SerialName("dateCompleted") val dateCompleted: String? = null,
    @SerialName("createdBy") val createdBy: String = "",
    @SerialName("createdByIP") val createdByIp: String = "",
    @SerialName("createdDate") val createdDate: String? = null,
    @SerialName("modifiedBy") val modifiedBy: String = "",
    @SerialName("modifiedByIP") val modifiedByIp: String = "",
    @SerialName("modifiedDate") val modifiedDate: String? = null,
    @SerialName("hsRequiredColour") val hsRequiredColour: String = "",
    @SerialName("statusColour") val statusColour: String = "",
    @SerialName("hsRequiredText") val hsRequiredText: String = "",
    @SerialName("statusText") val statusText: String = "",
    @SerialName("dateStartedString") val dateStartedString: String = "",
    @SerialName("dateCompletedString") val dateCompletedString: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)
