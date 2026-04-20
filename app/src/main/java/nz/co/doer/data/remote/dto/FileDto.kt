package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileModelDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("size") val size: Long = 0,
    @SerialName("type") val type: String = "",
    @SerialName("fileURL") val fileUrl: String = "",
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
data class FileUploadModelDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("shiftId") val shiftId: String = "",
    @SerialName("shiftSubItemId") val shiftSubItemId: String? = null,
    @SerialName("fileName") val fileName: String = "",
    @SerialName("fileUrl") val fileUrl: String = "",
    @SerialName("createdBy") val createdBy: String = "",
    @SerialName("createdByName") val createdByName: String = "",
    @SerialName("createdDate") val createdDate: String = "",
    @SerialName("fileSize") val fileSize: String = "",
    @SerialName("fileExtension") val fileExtension: String = "",
    @SerialName("thumbnailUrl") val thumbnailUrl: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("status") val status: Boolean = false,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
) {
    val isImage: Boolean
        get() = fileName.substringAfterLast('.', "").lowercase() in
            setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
}

@Serializable
data class FileUploadResponseDto(
    @SerialName("Success") val success: Boolean = false,
    @SerialName("Message") val message: String = "",
    @SerialName("UploadedFiles") val uploadedFiles: List<FileUploadModelDto> = emptyList(),
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)
