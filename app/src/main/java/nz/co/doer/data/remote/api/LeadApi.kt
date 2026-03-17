package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.LeadsDto
import nz.co.doer.data.remote.dto.ShiftDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LeadApi {

    @GET("Lead/GetLeads")
    suspend fun getLeads(): List<LeadsDto>

    @GET("Lead/GetNewLeads")
    suspend fun getNewLeads(): List<LeadsDto>

    @GET("Lead/GetQuotedAndWonLeads")
    suspend fun getQuotedAndWonLeads(): List<LeadsDto>

    @GET("Lead/GetContactedLeads")
    suspend fun getContactedLeads(): List<LeadsDto>

    @POST("Lead/CreateLead")
    suspend fun createNewLead(
        @Body leadDetail: LeadsDto
    ): LeadsDto

    @POST("Lead/UpdateLead")
    suspend fun updateLead(
        @Body leadDetail: LeadsDto
    ): LeadsDto

    @GET("Lead/SendFollowUPMailToClient")
    suspend fun sendFollowUpMailToClient(
        @Query("leadId") id: Int
    ): Int

    @GET("Shift/GetShiftById")
    suspend fun getShiftById(
        @Query("Id") id: Int
    ): ShiftDto

    @POST("Shift/DeleteShift")
    suspend fun deleteJobById(
        @Query("Id") id: Int
    ): Boolean
}
