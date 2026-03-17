package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.ChatMessageModelDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatMessageApi {

    @POST("ChatMessage/SendChatMessage")
    suspend fun sendChatMessage(
        @Body message: ChatMessageModelDto
    ): Boolean

    @GET("ChatMessage/GetChatMessages")
    suspend fun getChatMessageByShiftId(
        @Query("shiftId") shiftId: Int
    ): List<ChatMessageModelDto>
}
