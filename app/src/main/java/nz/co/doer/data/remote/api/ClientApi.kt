package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.remote.dto.ClientJobDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ClientApi {

    @GET("Client/GetAllClients")
    suspend fun getAllClients(): List<ClientDto>

    @POST("Client/CreateClient")
    suspend fun createNewClient(
        @Body client: ClientDto
    ): ClientDto

    @POST("Client/UpdateClient")
    suspend fun updateClient(
        @Body client: ClientDto
    ): ClientDto

    @POST("Client/DeleteClient")
    suspend fun deleteClientById(
        @Query("id") id: Int
    ): Boolean

    @GET("Client/GetUnassignedJobs")
    suspend fun getUnassignedJobs(): List<ClientJobDto>

    @POST("Client/AssignClientToJob")
    suspend fun assignClientToJob(
        @Query("shiftId") shiftId: Int,
        @Query("clientId") clientId: Int?
    ): String
}
