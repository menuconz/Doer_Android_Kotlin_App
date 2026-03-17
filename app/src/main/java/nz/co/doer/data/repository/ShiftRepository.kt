package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.ShiftApi
import nz.co.doer.data.remote.dto.FileUploadModelDto
import nz.co.doer.data.remote.dto.FileUploadResponseDto
import nz.co.doer.data.remote.dto.JobQuotationDto
import nz.co.doer.data.remote.dto.NotificationsDto
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.remote.dto.ShiftSubItemDto
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.remote.safeApiCall
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftApi: ShiftApi,
    private val preferencesManager: PreferencesManager
) {

    suspend fun getAllShifts(): ApiResult<List<ShiftDto>> = safeApiCall { shiftApi.getAllShifts() }

    suspend fun getShiftsByUserId(userId: String): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getShiftsByUserId(userId)
    }

    suspend fun getShiftsByUserIdMonth(userId: String, month: Int, year: Int): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getShiftsByUserIdMonth(userId, month, year)
    }

    suspend fun getShiftsForApp(month: Int, year: Int): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getShiftsForApp(month, year)
    }

    suspend fun getShiftsByUserIdAndDate(userId: String, selectedDate: String): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getShiftsByUserIdAndDate(userId, selectedDate)
    }

    suspend fun getShiftsByDate(selectedDate: String): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getShiftsByDate(selectedDate)
    }

    suspend fun getShiftsByCaregiverId(caregiverId: String): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getShiftsByCaregiverId(caregiverId)
    }

    suspend fun getAdminJobsByAdminId(userId: String): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getAdminJobsByAdminId(userId)
    }

    suspend fun getMonthlyJobsByUserId(userId: String, month: Int?, year: Int?, skip: Int = 0, take: Int = 100): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getMonthlyJobsByUserId(userId, month, year, skip, take)
    }

    suspend fun getMonthlyJobsByCaregiverId(caregiverId: String, month: Int?, year: Int?, skip: Int = 0, take: Int = 100): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getMonthlyJobsByCaregiverId(caregiverId, month, year, skip, take)
    }

    suspend fun getMonthlyJobsByAdmin(month: Int?, year: Int?, skip: Int = 0, take: Int = 100): ApiResult<List<ShiftDto>> = safeApiCall {
        shiftApi.getMonthlyJobsByAdmin(month, year, skip, take)
    }

    suspend fun getShiftById(id: Int): ApiResult<ShiftDto> = safeApiCall { shiftApi.getShiftById(id) }

    suspend fun createShift(shiftDetail: ShiftDto): ApiResult<ShiftDto> = safeApiCall { shiftApi.createShift(shiftDetail) }

    suspend fun updateShift(shiftDetail: ShiftDto): ApiResult<ShiftDto> = safeApiCall { shiftApi.updateShift(shiftDetail) }

    suspend fun updateShiftsExtraHour(shiftDetail: ShiftDto): ApiResult<ShiftDto> = safeApiCall { shiftApi.updateShiftsExtraHour(shiftDetail) }

    suspend fun requestOverTime(shift: ShiftDto): ApiResult<Response<ResponseBody>> = safeApiCall { shiftApi.requestOverTime(shift) }

    suspend fun deleteJob(id: Int): ApiResult<Boolean> = safeApiCall { shiftApi.deleteJob(id) }

    suspend fun addTaskReminder(shiftDetail: ShiftDto): ApiResult<ShiftDto> = safeApiCall { shiftApi.addTaskReminder(shiftDetail) }

    // Sub-Items
    suspend fun addSubItems(subItem: ShiftSubItemDto): ApiResult<ShiftSubItemDto> = safeApiCall { shiftApi.addSubItems(subItem) }

    suspend fun editSubItems(subItem: ShiftSubItemDto): ApiResult<ShiftSubItemDto> = safeApiCall { shiftApi.editSubItems(subItem) }

    suspend fun getSubItemsByJobId(id: Int): ApiResult<List<ShiftSubItemDto>> = safeApiCall { shiftApi.getSubItemsByJobId(id) }

    suspend fun getSubItemById(id: Int): ApiResult<ShiftSubItemDto> = safeApiCall { shiftApi.getSubItemById(id) }

    suspend fun deleteSubItem(id: Int): ApiResult<Boolean> = safeApiCall { shiftApi.deleteSubItem(id) }

    // Quotations
    suspend fun getQuotationsByJobId(id: Int): ApiResult<List<JobQuotationDto>> = safeApiCall { shiftApi.getQuotationsByJobId(id) }

    suspend fun getQuotationsBySearch(shiftId: Int, latitude: Double, longitude: Double, searchSkills: String, searchName: String): ApiResult<List<JobQuotationDto>> = safeApiCall {
        shiftApi.getQuotationsBySearch(shiftId, latitude, longitude, searchSkills, searchName)
    }

    suspend fun addJobQuotation(quotation: JobQuotationDto): ApiResult<JobQuotationDto> = safeApiCall { shiftApi.addJobQuotation(quotation) }

    suspend fun getJobQuotationByContractorIdAndShiftId(contractorId: String, shiftId: Int): ApiResult<JobQuotationDto> = safeApiCall {
        shiftApi.getJobQuotationByContractorIdAndShiftId(contractorId, shiftId)
    }

    // Files
    suspend fun getShiftFiles(shiftId: String): ApiResult<List<FileUploadModelDto>> = safeApiCall { shiftApi.getShiftFiles(shiftId) }

    suspend fun getSubItemFiles(shiftId: String, subItemId: String): ApiResult<List<FileUploadModelDto>> = safeApiCall {
        shiftApi.getSubItemFiles(shiftId, subItemId)
    }

    suspend fun uploadFile(
        shiftId: String,
        subItemId: String?,
        files: List<File>
    ): ApiResult<FileUploadResponseDto> = safeApiCall {
        val fields = mutableMapOf<String, RequestBody>()
        fields["ShiftId"] = shiftId.toRequestBody("text/plain".toMediaTypeOrNull())
        if (!subItemId.isNullOrEmpty()) {
            fields["ShiftSubItemId"] = subItemId.toRequestBody("text/plain".toMediaTypeOrNull())
        }
        fields["CreatedBy"] = preferencesManager.getUserId().toRequestBody("text/plain".toMediaTypeOrNull())
        fields["SiteId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())
        fields["LId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())
        fields["UserID"] = preferencesManager.getUserId().toRequestBody("text/plain".toMediaTypeOrNull())
        fields["BasicAuthUid"] = preferencesManager.getBasicAuthUid().toRequestBody("text/plain".toMediaTypeOrNull())

        val fileParts = files.map { file ->
            val mediaType = getMimeType(file.name).toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            MultipartBody.Part.createFormData("Files", file.name, requestFile)
        }

        shiftApi.uploadFile(fields, fileParts)
    }

    // Notifications
    suspend fun getUserAllNotificationsById(userId: String): ApiResult<List<NotificationsDto>> = safeApiCall {
        shiftApi.getUserAllNotificationsById(userId)
    }

    suspend fun markNotificationAsRead(id: Int): ApiResult<NotificationsDto> = safeApiCall {
        shiftApi.markNotificationAsRead(id)
    }

    // Users by Job
    suspend fun getAllUsersByJobId(jobId: Int): ApiResult<List<UserDto>> = safeApiCall {
        shiftApi.getAllUsersByJobId(jobId)
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
