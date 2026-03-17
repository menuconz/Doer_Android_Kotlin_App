package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.EmailMessageApi
import nz.co.doer.data.remote.dto.EmailMessageDto
import nz.co.doer.data.remote.dto.EmailThreadDto
import nz.co.doer.data.remote.dto.NewEmailRequestDto
import nz.co.doer.data.remote.dto.SendEmailReplyRequestDto
import nz.co.doer.data.remote.safeApiCall
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailMessageRepository @Inject constructor(
    private val emailMessageApi: EmailMessageApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun getEmailThreadById(emailMessageId: Int): ApiResult<EmailThreadDto> = safeApiCall {
        emailMessageApi.getEmailThreadById(emailMessageId)
    }

    suspend fun markEmailAsRead(emailMessageId: Int): ApiResult<EmailThreadDto> = safeApiCall {
        emailMessageApi.markEmailAsRead(emailMessageId)
    }

    suspend fun getAllEmailMessageByShiftId(shiftId: Int): ApiResult<List<EmailThreadDto>> = safeApiCall {
        emailMessageApi.getAllEmailMessageByShiftId(shiftId)
    }

    suspend fun getSubItemAllEmailMessage(shiftId: Int, subItemId: Int): ApiResult<List<EmailThreadDto>> = safeApiCall {
        emailMessageApi.getSubItemAllEmailMessage(shiftId, subItemId)
    }

    suspend fun sendEmailReply(request: SendEmailReplyRequestDto): ApiResult<EmailMessageDto> = safeApiCall {
        emailMessageApi.sendEmailReply(request)
    }

    suspend fun sendSubItemEmailReply(request: SendEmailReplyRequestDto): ApiResult<EmailMessageDto> = safeApiCall {
        emailMessageApi.sendSubItemEmailReply(request)
    }

    suspend fun sendNewEmail(request: NewEmailRequestDto): ApiResult<EmailMessageDto> = safeApiCall {
        emailMessageApi.sendNewEmail(request)
    }

    suspend fun sendNewEmailWithAttachments(
        jobId: Int,
        toEmail: String,
        subject: String,
        body: String,
        ccEmail: String,
        files: List<File>
    ): ApiResult<EmailMessageDto> = safeApiCall {
        val fields = mutableMapOf<String, RequestBody>()
        fields["JobId"] = jobId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        fields["ToEmail"] = toEmail.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["Subject"] = subject.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["Body"] = body.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["CcEmail"] = ccEmail.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["SiteId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())
        fields["LId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())
        fields["UserID"] = preferencesManager.getUserId().toRequestBody("text/plain".toMediaTypeOrNull())
        fields["BasicAuthUid"] = preferencesManager.getBasicAuthUid().toRequestBody("text/plain".toMediaTypeOrNull())

        val attachments = files.map { file ->
            val mediaType = getMimeType(file.name).toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            MultipartBody.Part.createFormData("attachments", file.name, requestFile)
        }

        emailMessageApi.sendNewEmailWithAttachments(fields, attachments)
    }

    suspend fun sendNewSubItemEmailWithAttachments(
        jobId: Int,
        subItemId: Int,
        toEmail: String,
        subject: String,
        body: String,
        ccEmail: String,
        files: List<File>
    ): ApiResult<EmailMessageDto> = safeApiCall {
        val fields = mutableMapOf<String, RequestBody>()
        fields["JobId"] = jobId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        fields["SubItemId"] = subItemId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        fields["ToEmail"] = toEmail.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["Subject"] = subject.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["Body"] = body.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["CcEmail"] = ccEmail.toRequestBody("text/plain".toMediaTypeOrNull())
        fields["SiteId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())
        fields["LId"] = "1".toRequestBody("text/plain".toMediaTypeOrNull())
        fields["UserID"] = preferencesManager.getUserId().toRequestBody("text/plain".toMediaTypeOrNull())
        fields["BasicAuthUid"] = preferencesManager.getBasicAuthUid().toRequestBody("text/plain".toMediaTypeOrNull())

        val attachments = files.map { file ->
            val mediaType = getMimeType(file.name).toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            MultipartBody.Part.createFormData("attachments", file.name, requestFile)
        }

        emailMessageApi.sendNewSubItemEmailWithAttachments(fields, attachments)
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
