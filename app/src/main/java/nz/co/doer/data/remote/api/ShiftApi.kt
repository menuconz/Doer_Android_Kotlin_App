package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.FileUploadModelDto
import nz.co.doer.data.remote.dto.FileUploadResponseDto
import nz.co.doer.data.remote.dto.JobQuotationDto
import nz.co.doer.data.remote.dto.NotificationsDto
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.remote.dto.ShiftSubItemDto
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
import retrofit2.http.Path
import retrofit2.http.Query

interface ShiftApi {

    // --- Shift Retrieval ---

    @GET("Shift/GetShifts")
    suspend fun getAllShifts(): List<ShiftDto>

    @GET("Shift/GetShiftsByUserId")
    suspend fun getShiftsByUserId(
        @Query("userId") userId: String
    ): List<ShiftDto>

    @GET("Shift/GetShiftsByUserIdMonthAndYear")
    suspend fun getShiftsByUserIdMonth(
        @Query("userId") userId: String,
        @Query("month") month: Int,
        @Query("year") year: Int
    ): List<ShiftDto>

    @GET("Shift/GetShiftsForApp")
    suspend fun getShiftsForApp(
        @Query("month") month: Int,
        @Query("year") year: Int
    ): List<ShiftDto>

    @GET("Shift/GetShiftsByUserIdAndDate")
    suspend fun getShiftsByUserIdAndDate(
        @Query("userId") userId: String,
        @Query("selectedDate") selectedDate: String
    ): List<ShiftDto>

    @GET("Shift/GetShiftsByDate")
    suspend fun getShiftsByDate(
        @Query("selectedDate") selectedDate: String
    ): List<ShiftDto>

    @GET("Shift/GetShiftsByCaregiverId")
    suspend fun getShiftsByCaregiverId(
        @Query("caregiverId") caregiverId: String
    ): List<ShiftDto>

    @GET("Shift/GetAdminJobsByAdminId")
    suspend fun getAdminJobsByAdminId(
        @Query("adminId") userId: String
    ): List<ShiftDto>

    @GET("Shift/GetMonthlyJobsByUserId")
    suspend fun getMonthlyJobsByUserId(
        @Query("userId") userId: String,
        @Query("month") month: Int?,
        @Query("year") year: Int?,
        @Query("skip") skip: Int = 0,
        @Query("take") take: Int = 100
    ): List<ShiftDto>

    // Date-based Kanban: returns jobs whose subitems (or shift itself) fall in [startDate, endDate]
    @GET("Shift/GetJobsByDateRange")
    suspend fun getJobsByDateRange(
        @Query("userId") userId: String,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): List<ShiftDto>

    @GET("Shift/GetMonthlyJobsByCaregiverId")
    suspend fun getMonthlyJobsByCaregiverId(
        @Query("caregiverId") caregiverId: String,
        @Query("month") month: Int?,
        @Query("year") year: Int?,
        @Query("skip") skip: Int = 0,
        @Query("take") take: Int = 100
    ): List<ShiftDto>

    @GET("Shift/GetMonthlyJobsByAdmin")
    suspend fun getMonthlyJobsByAdmin(
        @Query("month") month: Int?,
        @Query("year") year: Int?,
        @Query("skip") skip: Int = 0,
        @Query("take") take: Int = 100
    ): List<ShiftDto>

    @GET("Shift/GetShiftById")
    suspend fun getShiftById(
        @Query("Id") id: Int
    ): ShiftDto

    @GET("Shift/GetFilteredShiftsByUserIdLocationAndTime")
    suspend fun getSortedShiftsByUserIdLocationAndTime(
        @Query("userId") userId: String,
        @Query("searchlatitude") latitude: Double,
        @Query("searchlongitude") longitude: Double,
        @Query("durationFrom") durationFrom: String?,
        @Query("durationTo") durationTo: String?
    ): List<ShiftDto>

    // --- Shift Management ---

    @POST("Shift/CreateShift")
    suspend fun createShift(
        @Body shiftDetail: ShiftDto
    ): ShiftDto

    @POST("Shift/UpdateShiftAsync")
    suspend fun updateShift(
        @Body shiftDetail: ShiftDto
    ): ShiftDto

    @POST("Shift/UpdateShiftsExtraHour")
    suspend fun updateShiftsExtraHour(
        @Body shiftDetail: ShiftDto
    ): ShiftDto

    @POST("Shift/RequestForOverTime")
    suspend fun requestOverTime(
        @Body shift: ShiftDto
    ): Response<ResponseBody>

    @POST("Shift/DeleteShift")
    suspend fun deleteJob(
        @Query("Id") id: Int
    ): Boolean

    @POST("Shift/AddTaskReminderAsync")
    suspend fun addTaskReminder(
        @Body shiftDetail: ShiftDto
    ): ShiftDto

    // --- Sub-Items ---

    @POST("Shift/CreateShiftSubItems")
    suspend fun addSubItems(
        @Body shiftSubItem: ShiftSubItemDto
    ): ShiftSubItemDto

    @POST("Shift/UpdateShiftSubItemAsync")
    suspend fun editSubItems(
        @Body shiftSubItem: ShiftSubItemDto
    ): ShiftSubItemDto

    @GET("Shift/GetSubItemsByShiftId")
    suspend fun getSubItemsByJobId(
        @Query("shiftId") id: Int
    ): List<ShiftSubItemDto>

    @GET("Shift/GetShiftSubItemsById")
    suspend fun getSubItemById(
        @Query("id") id: Int
    ): ShiftSubItemDto

    @POST("Shift/DeleteSubItem")
    suspend fun deleteSubItem(
        @Query("subItemId") id: Int
    ): Boolean

    // --- Quotations ---

    @GET("Shift/GetJobQuotations")
    suspend fun getQuotationsByJobId(
        @Query("shiftId") id: Int
    ): List<JobQuotationDto>

    @GET("Shift/SearchJobQuotationsByLocationandSkills")
    suspend fun getQuotationsBySearch(
        @Query("shiftId") id: Int,
        @Query("searchlatitude") latitude: Double,
        @Query("searchlongitude") longitude: Double,
        @Query("skill") searchSkills: String,
        @Query("name") searchName: String
    ): List<JobQuotationDto>

    @POST("Shift/AddJobQuotation")
    suspend fun addJobQuotation(
        @Body jobQuotation: JobQuotationDto
    ): JobQuotationDto

    @GET("Shift/GetJobQuotationByContractorIdAndShifId")
    suspend fun getJobQuotationByContractorIdAndShiftId(
        @Query("contractorId") contractorId: String,
        @Query("shiftId") shiftId: Int
    ): JobQuotationDto

    // --- Files ---

    @GET("Shift/GetFilesByShiftId/{shiftId}")
    suspend fun getShiftFiles(
        @Path("shiftId") shiftId: String
    ): List<FileUploadModelDto>

    @GET("Shift/GetFiles")
    suspend fun getSubItemFiles(
        @Query("shiftId") shiftId: String,
        @Query("subItemId") subItemId: String
    ): List<FileUploadModelDto>

    @Multipart
    @POST("Shift/UploadFilesToShift")
    suspend fun uploadFile(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part files: List<MultipartBody.Part>
    ): FileUploadResponseDto

    // --- Notifications ---

    @GET("Shift/getUserAllNotificationsById")
    suspend fun getUserAllNotificationsById(
        @Query("id") userId: String
    ): List<NotificationsDto>

    @GET("Shift/MarkNotificationAsRead")
    suspend fun markNotificationAsRead(
        @Query("id") id: Int
    ): NotificationsDto

    // --- Users by Job ---

    @GET("Shift/GetAllUsersByJobId")
    suspend fun getAllUsersByJobId(
        @Query("jobId") jobId: Int
    ): List<UserDto>
}
