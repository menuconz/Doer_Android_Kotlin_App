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
    @SerialName("ShiftId") val shiftId: String = "",
    @SerialName("FileName") val fileName: String = "",
    @SerialName("FileUrl") val fileUrl: String = "",
    @SerialName("CreatedBy") val createdBy: String = "",
    @SerialName("CreatedByName") val createdByName: String = "",
    @SerialName("CreatedDate") val createdDate: String = "",
    @SerialName("FileSize") val fileSize: String = "",
    @SerialName("FileExtension") val fileExtension: String = "",
    @SerialName("IsImage") val isImage: Boolean = false,
    @SerialName("ThumbnailUrl") val thumbnailUrl: String = "",
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

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
