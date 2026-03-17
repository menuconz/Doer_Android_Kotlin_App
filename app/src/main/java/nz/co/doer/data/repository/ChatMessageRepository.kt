package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.ChatMessageApi
import nz.co.doer.data.remote.dto.ChatMessageModelDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMessageRepository @Inject constructor(
    private val chatMessageApi: ChatMessageApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun sendChatMessage(message: ChatMessageModelDto): ApiResult<Boolean> = safeApiCall {
        chatMessageApi.sendChatMessage(message)
    }

    suspend fun getChatMessageByShiftId(shiftId: Int): ApiResult<List<ChatMessageModelDto>> = safeApiCall {
        chatMessageApi.getChatMessageByShiftId(shiftId)
    }
}
