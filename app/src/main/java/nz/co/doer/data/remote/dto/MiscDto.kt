package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Login Request ---
@Serializable
data class LoginRequestDto(
    @SerialName("Email") val email: String,
    @SerialName("Password") val password: String,
    @SerialName("DeviceToken") val deviceToken: String = "",
    @SerialName("DeviceTypeId") val deviceTypeId: Int = 2,
    @SerialName("OSID") val osId: Int = 1,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("LId") val lId: Int = 1
)

// --- JobQuotation ---
@Serializable
data class JobQuotationDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("shiftId") val shiftId: Int = 0,
    @SerialName("caregiverId") val caregiverId: String = "",
    @SerialName("quotedAmount") val quotedAmount: Double = 0.0,
    @SerialName("quotedDate") val quotedDate: String? = null,
    @SerialName("status") val status: String = "",
    @SerialName("notes") val notes: String = "",
    @SerialName("createdBy") val createdBy: String = "",
    @SerialName("createdByIP") val createdByIp: String = "",
    @SerialName("createdDate") val createdDate: String? = null,
    @SerialName("modifiedBy") val modifiedBy: String = "",
    @SerialName("modifiedByIP") val modifiedByIp: String = "",
    @SerialName("modifiedDate") val modifiedDate: String? = null,
    @SerialName("contractorName") val contractorName: String = "",
    @SerialName("contractorEmail") val contractorEmail: String = "",
    @SerialName("contractorPhone") val contractorPhone: String = "",
    @SerialName("contractorAddress") val contractorAddress: String = "",
    @SerialName("skills") val skills: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userId") val userId: String = "",
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

// --- RestHome ---
@Serializable
data class RestHomeDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("Address") val address: String = "",
    @SerialName("Mobile") val mobile: String = "",
    @SerialName("NumberOfBeds") val numberOfBeds: Int = 0,
    @SerialName("Latitude") val latitude: Double? = null,
    @SerialName("Longitude") val longitude: Double? = null,
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

// --- CaregiverLevel ---
@Serializable
data class CaregiverLevelDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("LevelName") val levelName: String = ""
)

// --- NotificationsDto ---
@Serializable
data class NotificationsDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("userId") val userId: String = "",
    @SerialName("shiftId") val shiftId: Int? = null,
    @SerialName("projectName") val projectName: String = "",
    @SerialName("shiftStatusId") val shiftStatusId: String = "",
    @SerialName("userDeviceToken") val userDeviceToken: String = "",
    @SerialName("userDeviceType") val userDeviceType: Int = 0,
    @SerialName("title") val title: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("firebaseMessageId") val firebaseMessageId: String = "",
    @SerialName("status") val notificationStatus: Int = 1,
    @SerialName("sentAt") val sentAt: String = "",
    @SerialName("deliveredAt") val deliveredAt: String? = null,
    @SerialName("readAt") val readAt: String? = null,
    @SerialName("errorMessage") val errorMessage: String = "",
    @SerialName("notificationType") val notificationType: String = "",
    @SerialName("isRead") val isRead: Boolean = false,
    @SerialName("emailMessageId") val emailMessageId: Int? = null,
    @SerialName("data") val data: String? = null
)

// --- MainMenu ---
@Serializable
data class MainMenuDto(
    @SerialName("IsMyTeamVisible") val isMyTeamVisible: Boolean = false,
    @SerialName("IsAgreement") val isAgreement: Boolean = false,
    @SerialName("IsInformation") val isInformation: Boolean = false,
    @SerialName("FullSizeProfileImage") val fullSizeProfileImage: String = "",
    @SerialName("ThumbnailProfileImage") val thumbnailProfileImage: String = "",
    @SerialName("IsConsent") val isConsent: Boolean = false,
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

// --- UserLocation ---
@Serializable
data class UserLocationDto(
    @SerialName("Latitude") val latitude: Double = 0.0,
    @SerialName("Longitude") val longitude: Double = 0.0,
    @SerialName("Timestamp") val timestamp: String = "",
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

// --- Logs ---
@Serializable
data class LogsDto(
    @SerialName("Id") val id: Int = 0,
    @SerialName("Timestamp") val timestamp: String = "",
    @SerialName("Level") val level: String = "",
    @SerialName("Template") val template: String = "",
    @SerialName("Message") val message: String = "",
    @SerialName("Exception") val exception: String = "",
    @SerialName("Properties") val properties: String = "",
    @SerialName("_ts") val ts: String = "",
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

// --- Contactus ---
@Serializable
data class ContactusDto(
    @SerialName("Name") val name: String = "",
    @SerialName("Email") val email: String = "",
    @SerialName("PhoneNumber") val phoneNumber: String = "",
    @SerialName("Message") val message: String = "",
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

// --- RegisterUserWithoutDocument ---
@Serializable
data class RegisterUserWithoutDocumentDto(
    @SerialName("CaregiverLevelId") val caregiverLevelId: Int = 0,
    @SerialName("DisplayName") val displayName: String = "",
    @SerialName("Email") val email: String = "",
    @SerialName("Password") val password: String = "",
    @SerialName("RestHomeId") val restHomeId: Int? = null,
    @SerialName("DateOfBirth") val dateOfBirth: String? = null,
    @SerialName("PhoneNumber") val phoneNumber: String = "",
    @SerialName("DeviceToken") val deviceToken: String = "",
    @SerialName("DeviceTypeId") val deviceTypeId: Int = 2, // Android
    @SerialName("Address") val address: String = "",
    @SerialName("Latitude") val latitude: Double? = null,
    @SerialName("Longitude") val longitude: Double? = null,
    @SerialName("PerHourCharges") val perHourCharges: Double? = null,
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

// --- ForgotPasswordModel ---
@Serializable
data class ForgotPasswordModelDto(
    @SerialName("FirstName") val firstName: String = "",
    @SerialName("LastName") val lastName: String = "",
    @SerialName("EmailId") val emailId: String = "",
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("ErrorMessage") val errorMessage: String? = null,
    @SerialName("Status") val status: Boolean = false,
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

// --- PaymarkPayment ---
@Serializable
data class PaymarkPaymentRequestDto(
    @SerialName("username") val username: String = "",
    @SerialName("password") val password: String = "",
    @SerialName("account_id") val accountId: Int = 0,
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("cmd") val cmd: String = "",
    @SerialName("display_customer_email") val displayCustomerEmail: String = "",
    @SerialName("particular") val particular: String = "",
    @SerialName("reference") val reference: String = "",
    @SerialName("store_payment_token") val storePaymentToken: String = "",
    @SerialName("token_reference") val tokenReference: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("return_url") val returnUrl: String = "",
    // EntityBase fields
    @SerialName("LId") val lId: Int = 0,
    @SerialName("SiteId") val siteId: Int = 1,
    @SerialName("ContactID") val contactId: Int = 0,
    @SerialName("UserID") val userId: String = "",
    @SerialName("BasicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class PaymarkPaymentResponseDto(
    @SerialName("success") val success: String = "",
    @SerialName("failure") val failure: String = "",
    @SerialName("code") val code: Int = 0,
    @SerialName("message") val message: Int = 0
)

// --- GooglePlace ---
@Serializable
data class GooglePlaceAutoCompletePrediction(
    @SerialName("description") val description: String = "",
    @SerialName("id") val id: String = "",
    @SerialName("place_id") val placeId: String = "",
    @SerialName("reference") val reference: String = "",
    @SerialName("structured_formatting") val structuredFormatting: StructuredFormatting? = null
)

@Serializable
data class StructuredFormatting(
    @SerialName("main_text") val mainText: String = "",
    @SerialName("secondary_text") val secondaryText: String = ""
)

@Serializable
data class GooglePlaceAutoCompleteResult(
    @SerialName("status") val status: String = "",
    @SerialName("predictions") val autoCompletePlaces: List<GooglePlaceAutoCompletePrediction> = emptyList()
)
