package nz.co.doer.data.repository

import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.BoardApi
import nz.co.doer.data.remote.dto.BoardDto
import nz.co.doer.data.remote.dto.DropdownOptionDto
import nz.co.doer.data.remote.dto.UpdateBoardDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardRepository @Inject constructor(
    private val boardApi: BoardApi
) {
    suspend fun getBoards(): ApiResult<List<BoardDto>> = safeApiCall { boardApi.getBoards() }

    suspend fun getBoardById(id: Int): ApiResult<BoardDto> = safeApiCall { boardApi.getBoardById(id) }

    suspend fun updateBoard(id: Int, name: String): ApiResult<BoardDto> = safeApiCall {
        boardApi.updateBoard(id, UpdateBoardDto(name))
    }

    suspend fun getDropdownOptions(
        boardId: Int,
        columnName: String? = null
    ): ApiResult<List<DropdownOptionDto>> = safeApiCall {
        boardApi.getDropdownOptions(boardId, columnName)
    }

    suspend fun upsertDropdownOption(
        boardId: Int,
        option: DropdownOptionDto
    ): ApiResult<DropdownOptionDto> = safeApiCall {
        boardApi.upsertDropdownOption(boardId, option)
    }
}
