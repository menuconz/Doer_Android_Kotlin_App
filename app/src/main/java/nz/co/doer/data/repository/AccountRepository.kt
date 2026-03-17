package nz.co.doer.data.repository

import kotlinx.serialization.json.Json
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.AccountApi
import nz.co.doer.data.remote.dto.ContactusDto
import nz.co.doer.data.remote.dto.LoginRequestDto
import nz.co.doer.data.remote.dto.MainMenuDto
import nz.co.doer.data.remote.dto.RegisterUserWithoutDocumentDto
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.remote.safeApiCall
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountApi: AccountApi,
    private val preferencesManager: PreferencesManager,
    private val json: Json
) {

    suspend fun authenticate(
        userName: String,
        password: String,
        deviceToken: String,
        deviceTypeId: Int = 2 // Android
    ): ApiResult<UserDto> {
        return try {
            val body = LoginRequestDto(
                email = userName,
                password = password,
                deviceToken = deviceToken,
                deviceTypeId = deviceTypeId
            )
            val response = accountApi.authenticate(body)
            parseUserResponse(response)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Login failed. Please try again.", e)
        }
    }

    suspend fun logout(userId: String): ApiResult<UserDto> = safeApiCall {
        accountApi.logout(userId)
    }

    suspend fun generateOtp(email: String): ApiResult<UserDto> = safeApiCall {
        accountApi.generateOtp(email)
    }

    suspend fun forgotPassword(
        email: String,
        otp: String,
        password: String
    ): ApiResult<Boolean> = safeApiCall {
        accountApi.forgotPassword(otp, email, password)
    }

    suspend fun checkEmailExists(email: String): ApiResult<Boolean> = safeApiCall {
        accountApi.checkEmailExists(email)
    }

    suspend fun contactDetails(contactus: ContactusDto): ApiResult<Boolean> = safeApiCall {
        accountApi.contactDetails(contactus)
    }

    suspend fun registerManager(
        registerUser: RegisterUserWithoutDocumentDto
    ): ApiResult<UserDto> {
        return try {
            val response = accountApi.registerManager(registerUser)
            parseUserResponse(response)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Registration failed. Please try again.", e)
        }
    }

    suspend fun registerCustomer(user: UserDto): ApiResult<UserDto> = safeApiCall {
        accountApi.registerCustomer(user)
    }

    suspend fun registerContractor(
        user: UserDto,
        documentFiles: List<File>
    ): ApiResult<UserDto> {
        return try {
            val fields = mutableMapOf<String, RequestBody>()
            fields["DisplayName"] = user.displayName.toRequestBody("text/plain".toMediaTypeOrNull())
            fields["Email"] = user.email.toRequestBody("text/plain".toMediaTypeOrNull())
            fields["Password"] = user.password.toRequestBody("text/plain".toMediaTypeOrNull())
            fields["PhoneNumber"] = user.phoneNumber.toRequestBody("text/plain".toMediaTypeOrNull())
            fields["DeviceToken"] = user.deviceToken.toRequestBody("text/plain".toMediaTypeOrNull())
            fields["DeviceTypeId"] = "2".toRequestBody("text/plain".toMediaTypeOrNull())
            fields["Address"] = user.address.toRequestBody("text/plain".toMediaTypeOrNull())
            fields["DateofBirth"] = user.dateOfBirthString.toRequestBody("text/plain".toMediaTypeOrNull())
            fields["SiteId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())
            fields["LId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())

            val documents = documentFiles.map { file ->
                val mediaType = getMimeType(file.name).toMediaTypeOrNull()
                val requestFile = file.asRequestBody(mediaType)
                MultipartBody.Part.createFormData("Documents", file.name, requestFile)
            }

            val response = accountApi.registerContractor(fields, documents)
            parseUserResponse(response)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Registration failed. Please try again.", e)
        }
    }

    suspend fun getUser(id: String): ApiResult<UserDto> = safeApiCall {
        accountApi.getUser(id)
    }

    suspend fun updateProfile(
        user: UserDto,
        newDocuments: List<File>
    ): ApiResult<UserDto> = safeApiCall {
        val textPlain = "text/plain".toMediaTypeOrNull()
        val fields = mutableMapOf<String, RequestBody>()
        fields["Id"] = user.id.toRequestBody(textPlain)
        fields["DisplayName"] = user.displayName.toRequestBody(textPlain)
        fields["Email"] = user.email.toRequestBody(textPlain)
        // MAUI: Username = Emailid.Trim()
        fields["Username"] = user.email.toRequestBody(textPlain)
        fields["PhoneNumber"] = user.phoneNumber.toRequestBody(textPlain)
        fields["Address"] = user.address.toRequestBody(textPlain)
        // MAUI: DateofBirth, Latitude, Longitude
        if (!user.dateOfBirth.isNullOrBlank()) {
            fields["DateofBirth"] = user.dateOfBirth.toRequestBody(textPlain)
        }
        user.latitude?.let {
            fields["Latitude"] = it.toString().toRequestBody(textPlain)
        }
        user.longitude?.let {
            fields["Longitude"] = it.toString().toRequestBody(textPlain)
        }
        // MAUI: WorkExperience, Skills (caregiver fields)
        if (user.workExperience.isNotBlank()) {
            fields["WorkExperience"] = user.workExperience.toRequestBody(textPlain)
        }
        if (user.skills.isNotBlank()) {
            fields["Skills"] = user.skills.toRequestBody(textPlain)
        }
        fields["SiteId"] = "1".toRequestBody(textPlain)
        fields["LId"] = "1".toRequestBody(textPlain)
        fields["UserID"] = preferencesManager.getUserId().toRequestBody(textPlain)
        fields["BasicAuthUid"] = preferencesManager.getBasicAuthUid().toRequestBody(textPlain)

        val documents = newDocuments.map { file ->
            val mediaType = getMimeType(file.name).toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            MultipartBody.Part.createFormData("AddDocument", file.name, requestFile)
        }

        accountApi.updateProfile(fields, documents)
    }

    suspend fun deleteDocument(id: Int): ApiResult<Boolean> = safeApiCall {
        accountApi.deleteDocument(id)
    }

    suspend fun deleteUserAccount(userId: String): ApiResult<Boolean> = safeApiCall {
        accountApi.deleteUserAccount(userId)
    }

    suspend fun getAllContractors(): ApiResult<List<UserDto>> = safeApiCall {
        accountApi.getAllContractors()
    }

    suspend fun getAllManagers(): ApiResult<List<UserDto>> = safeApiCall {
        accountApi.getAllManagers()
    }

    suspend fun searchContractorsBySkillsAndLocation(
        latitude: Double,
        longitude: Double,
        searchSkills: String,
        searchName: String
    ): ApiResult<List<UserDto>> = safeApiCall {
        accountApi.searchContractorsBySkillsAndLocation(latitude, longitude, searchSkills, searchName)
    }

    suspend fun getAllUsersWithAdmin(): ApiResult<List<UserDto>> = safeApiCall {
        accountApi.getAllUsersWithAdmin()
    }

    suspend fun getAllFiloKretoTeam(): ApiResult<List<UserDto>> = safeApiCall {
        accountApi.getAllFiloKretoTeam()
    }

    suspend fun getAllManagerAndAdminUsers(): ApiResult<List<UserDto>> = safeApiCall {
        accountApi.getAllManagerAndAdminUsers()
    }

    suspend fun addUsersToFiloKretoTeam(userIds: List<String>): ApiResult<Boolean> = safeApiCall {
        accountApi.addUsersToFiloKretoTeam(userIds)
    }

    suspend fun removeUsersFromFiloKretoTeam(userIds: List<String>): ApiResult<Boolean> = safeApiCall {
        accountApi.removeUsersFromFiloKretoTeam(userIds)
    }

    suspend fun getMainMenuVisibility(user: UserDto): ApiResult<MainMenuDto> = safeApiCall {
        accountApi.getMainMenuVisibility(user)
    }

    private fun parseUserResponse(response: retrofit2.Response<okhttp3.ResponseBody>): ApiResult<UserDto> {
        val responseString = response.body()?.string() ?: ""
        Timber.d("API raw response: $responseString")
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()?.trim()?.removeSurrounding("\"") ?: ""
            Timber.d("API error response: $errorBody")
            return ApiResult.Error(errorBody.ifBlank { "Please enter correct email or password" })
        }
        if (responseString.isBlank()) {
            return ApiResult.Error("Please enter correct email or password")
        }
        return try {
            val user = json.decodeFromString<UserDto>(responseString)
            Timber.d("Parsed user - Role: '${user.role}', AdminVerified: ${user.adminVerified}")
            ApiResult.Success(user)
        } catch (e: Exception) {
            val errorMsg = responseString.trim().removeSurrounding("\"")
            ApiResult.Error(errorMsg)
        }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".pdf", true) -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
