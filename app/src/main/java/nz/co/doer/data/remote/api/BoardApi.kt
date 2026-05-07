package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.BoardDto
import nz.co.doer.data.remote.dto.DropdownOptionDto
import nz.co.doer.data.remote.dto.UpdateBoardDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface BoardApi {

    @GET("Board")
    suspend fun getBoards(): List<BoardDto>

    @GET("Board/{id}")
    suspend fun getBoardById(@Path("id") id: Int): BoardDto

    @PUT("Board/{id}")
    suspend fun updateBoard(
        @Path("id") id: Int,
        @Body body: UpdateBoardDto
    ): BoardDto

    @GET("Board/{boardId}/dropdown-options")
    suspend fun getDropdownOptions(
        @Path("boardId") boardId: Int,
        @Query("columnName") columnName: String? = null
    ): List<DropdownOptionDto>

    @POST("Board/{boardId}/dropdown-options")
    suspend fun upsertDropdownOption(
        @Path("boardId") boardId: Int,
        @Body option: DropdownOptionDto
    ): DropdownOptionDto
}
