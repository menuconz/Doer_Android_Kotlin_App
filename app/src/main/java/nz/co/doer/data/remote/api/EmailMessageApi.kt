package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.EmailMessageDto
import nz.co.doer.data.remote.dto.EmailThreadDto
import nz.co.doer.data.remote.dto.NewEmailRequestDto
import nz.co.doer.data.remote.dto.SendEmailReplyRequestDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query

interface EmailMessageApi {

    @GET("EmailMessages/getEmailThread")
    suspend fun getEmailThreadById(
        @Query("emailMessageId") emailMessageId: Int
    ): EmailThreadDto

    @GET("EmailMessages/mark-read")
    suspend fun markEmailAsRead(
        @Query("emailMessageId") emailMessageId: Int
    ): EmailThreadDto

    @GET("EmailMessages/getShiftEmailsWithThreads/{shiftId}")
    suspend fun getAllEmailMessageByShiftId(
        @Path("shiftId") shiftId: Int
    ): List<EmailThreadDto>

    @GET("EmailMessages/getSubItemEmailsWithThreads/{shiftId}/{subItemId}")
    suspend fun getSubItemAllEmailMessage(
        @Path("shiftId") shiftId: Int,
        @Path("subItemId") subItemId: Int
    ): List<EmailThreadDto>

    @POST("EmailMessages/reply")
    suspend fun sendEmailReply(
        @Body request: SendEmailReplyRequestDto
    ): EmailMessageDto

    @POST("EmailMessages/reply-subitem")
    suspend fun sendSubItemEmailReply(
        @Body request: SendEmailReplyRequestDto
    ): EmailMessageDto

    @POST("EmailMessages/send-new")
    suspend fun sendNewEmail(
        @Body request: NewEmailRequestDto
    ): EmailMessageDto

    @Multipart
    @POST("EmailMessages/send-new-emailwithattachment")
    suspend fun sendNewEmailWithAttachments(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part attachments: List<MultipartBody.Part>
    ): EmailMessageDto

    @Multipart
    @POST("EmailMessages/send-new-subitem-emailwithattachment")
    suspend fun sendNewSubItemEmailWithAttachments(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part attachments: List<MultipartBody.Part>
    ): EmailMessageDto
}
