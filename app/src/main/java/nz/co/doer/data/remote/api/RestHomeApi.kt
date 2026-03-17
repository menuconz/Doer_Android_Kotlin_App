package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.RestHomeDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RestHomeApi {

    @POST("RestHome/RegisterRestHome")
    suspend fun registerRestHome(
        @Body restHome: RestHomeDto
    ): RestHomeDto

    @GET("RestHome/GetRestHomes")
    suspend fun getRestHomes(): List<RestHomeDto>

    @GET("RestHome/GetRestHomeById")
    suspend fun getRestHomeById(
        @Query("Id") id: Int
    ): RestHomeDto
}
