package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BoardDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("createdDate") val createdDate: String? = null,
    @SerialName("modifiedDate") val modifiedDate: String? = null
)

@Serializable
data class UpdateBoardDto(
    @SerialName("name") val name: String
)

@Serializable
data class DropdownOptionDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("boardId") val boardId: Int = 0,
    @SerialName("columnName") val columnName: String = "",
    @SerialName("value") val value: Int = 0,
    @SerialName("displayName") val displayName: String = "",
    @SerialName("color") val color: String? = null,
    @SerialName("sortOrder") val sortOrder: Int = 0,
    @SerialName("isActive") val isActive: Boolean = true
)
