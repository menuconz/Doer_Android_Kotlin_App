package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.ContactusDto
import nz.co.doer.data.remote.dto.LoginRequestDto
import nz.co.doer.data.remote.dto.MainMenuDto
import nz.co.doer.data.remote.dto.RegisterUserWithoutDocumentDto
import nz.co.doer.data.remote.dto.UserDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Query

interface AccountApi {

    // --- Authentication ---

    @POST("Account/Login")
    suspend fun authenticate(
        @Body body: LoginRequestDto
    ): Response<ResponseBody>

    @GET("Account/Logout")
    suspend fun logout(
        @Query("userId") userId: String
    ): UserDto

    @GET("Account/GenerateOtp")
    suspend fun generateOtp(
        @Query("emailId") email: String
    ): UserDto

    @GET("Account/ForgetPassword")
    suspend fun forgotPassword(
        @Query("otp") otp: String,
        @Query("emailId") email: String,
        @Query("password") password: String
    ): Boolean

    @GET("Account/emailexists")
    suspend fun checkEmailExists(
        @Query("email") email: String
    ): Boolean

    @POST("Account/ContactTeam")
    suspend fun contactDetails(
        @Body contactus: ContactusDto
    ): Boolean

    // --- Registration ---

    @Multipart
    @POST("Account/Register")
    suspend fun registerContractor(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part documents: List<MultipartBody.Part>
    ): Response<ResponseBody>

    @POST("Account/RegisterManager")
    suspend fun registerManager(
        @Body registerUser: RegisterUserWithoutDocumentDto
    ): Response<ResponseBody>

    @POST("Account/RegisterCustomer")
    suspend fun registerCustomer(
        @Body user: UserDto
    ): UserDto

    // --- User Profile ---

    @GET("User/GetUserById")
    suspend fun getUser(
        @Query("id") id: String
    ): UserDto

    @Multipart
    @POST("User/UpdateUserWithDocument")
    suspend fun updateProfile(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part addDocument: List<MultipartBody.Part>
    ): UserDto

    @POST("User/DeleteDocumentById")
    suspend fun deleteDocument(
        @Query("id") id: Int
    ): Boolean

    @POST("User/DeleteUserAccount")
    suspend fun deleteUserAccount(
        @Query("id") userId: String
    ): Boolean

    // --- User Search ---

    @GET("User/GetCaregiverUsers")
    suspend fun getAllContractors(): List<UserDto>

    @GET("User/GetManagerUsers")
    suspend fun getAllManagers(): List<UserDto>

    @GET("User/SearchCaregiverUsersBySkillsAndLocation")
    suspend fun searchContractorsBySkillsAndLocation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("skill") searchSkills: String,
        @Query("name") searchName: String
    ): List<UserDto>

    @GET("User/GetAllUsersWithAdmin")
    suspend fun getAllUsersWithAdmin(): List<UserDto>

    @GET("User/getAllFiloKretoTeam")
    suspend fun getAllFiloKretoTeam(): List<UserDto>

    @GET("User/GetAllManagerAndAdminUsers")
    suspend fun getAllManagerAndAdminUsers(): List<UserDto>

    // --- Team Management ---

    @POST("user/addToFiloKretoTeam")
    suspend fun addUsersToFiloKretoTeam(
        @Body userIds: List<String>
    ): Boolean

    @POST("user/removeFromFiloKretoTeam")
    suspend fun removeUsersFromFiloKretoTeam(
        @Body userIds: List<String>
    ): Boolean

    // --- Menu ---

    @POST("AccountDetails/GetMainMenuVisibility")
    suspend fun getMainMenuVisibility(
        @Body user: UserDto
    ): MainMenuDto
}
