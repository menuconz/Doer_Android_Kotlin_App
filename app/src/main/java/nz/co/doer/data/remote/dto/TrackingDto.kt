package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClockEventDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("userId") val userId: String = "",
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("eventType") val eventType: String = "",
    @SerialName("locationType") val locationType: Int = 1,
    @SerialName("trackingState") val trackingState: Int = 0,
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0,
    @SerialName("timestamp") val timestamp: String = "",
    @SerialName("reasonCode") val reasonCode: String? = null,
    @SerialName("isOffline") val isOffline: Boolean = false,
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class LocationBatchDto(
    @SerialName("userId") val userId: String = "",
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("points") val points: List<LocationPointDto> = emptyList(),
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class LocationPointDto(
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0,
    @SerialName("timestamp") val timestamp: String = "",
    @SerialName("accuracy") val accuracy: Float = 0f,
    @SerialName("speed") val speed: Float = 0f,
    @SerialName("bearing") val bearing: Float = 0f
)

@Serializable
data class TrackingNotificationDto(
    @SerialName("userId") val userId: String = "",
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("notificationType") val notificationType: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("trackingState") val trackingState: Int = 0,
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0,
    @SerialName("timestamp") val timestamp: String = "",
    @SerialName("hoursOnSite") val hoursOnSite: Double? = null,
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class EditTimeEntryDto(
    @SerialName("userId") val userId: String = "",
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("clockInTime") val clockInTime: String? = null,
    @SerialName("clockOutTime") val clockOutTime: String? = null,
    @SerialName("reasonCode") val reasonCode: String = "",
    @SerialName("editedBy") val editedBy: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class TrackingStatusDto(
    @SerialName("userId") val userId: String = "",
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("trackingState") val trackingState: Int = 0,
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0,
    @SerialName("timestamp") val timestamp: String = "",
    @SerialName("eta") val eta: String? = null,
    @SerialName("distanceRemaining") val distanceRemaining: Double? = null,
    // Server-populated fields for live map
    @SerialName("displayName") val displayName: String = "",
    @SerialName("projectName") val projectName: String = "",
    @SerialName("siteName") val siteName: String = "",
    @SerialName("siteLatitude") val siteLatitude: Double? = null,
    @SerialName("siteLongitude") val siteLongitude: Double? = null,
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)
