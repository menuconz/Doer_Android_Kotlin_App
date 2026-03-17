package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.LeadApi
import nz.co.doer.data.remote.dto.LeadsDto
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeadRepository @Inject constructor(
    private val leadApi: LeadApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun getLeads(): ApiResult<List<LeadsDto>> = safeApiCall { leadApi.getLeads() }
    suspend fun getNewLeads(): ApiResult<List<LeadsDto>> = safeApiCall { leadApi.getNewLeads() }
    suspend fun getQuotedAndWonLeads(): ApiResult<List<LeadsDto>> = safeApiCall { leadApi.getQuotedAndWonLeads() }
    suspend fun getContactedLeads(): ApiResult<List<LeadsDto>> = safeApiCall { leadApi.getContactedLeads() }
    suspend fun createNewLead(lead: LeadsDto): ApiResult<LeadsDto> = safeApiCall { leadApi.createNewLead(lead) }
    suspend fun updateLead(lead: LeadsDto): ApiResult<LeadsDto> = safeApiCall { leadApi.updateLead(lead) }
    suspend fun sendFollowUpMailToClient(id: Int): ApiResult<Int> = safeApiCall { leadApi.sendFollowUpMailToClient(id) }
    suspend fun getShiftById(id: Int): ApiResult<ShiftDto> = safeApiCall { leadApi.getShiftById(id) }
    suspend fun deleteJobById(id: Int): ApiResult<Boolean> = safeApiCall { leadApi.deleteJobById(id) }
}
