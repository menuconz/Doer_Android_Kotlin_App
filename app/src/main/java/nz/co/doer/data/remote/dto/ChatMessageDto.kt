package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageModelDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("ShiftId") val shiftId: Int = 0,
    @SerialName("SentById") val sentById: String = "",
    @SerialName("SenderName") val senderName: String = "",
    @SerialName("SentToId") val sentToId: String = "",
    @SerialName("Message") val message: String = "",
    @SerialName("SentAtUtc") val sentAtUtc: String = "",
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)
