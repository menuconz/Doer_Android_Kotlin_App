package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.ClientApi
import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.remote.dto.ClientJobDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val clientApi: ClientApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun getAllClients(): ApiResult<List<ClientDto>> = safeApiCall { clientApi.getAllClients() }
    suspend fun createNewClient(client: ClientDto): ApiResult<ClientDto> = safeApiCall { clientApi.createNewClient(client) }
    suspend fun updateClient(client: ClientDto): ApiResult<ClientDto> = safeApiCall { clientApi.updateClient(client) }
    suspend fun deleteClientById(id: Int): ApiResult<Boolean> = safeApiCall { clientApi.deleteClientById(id) }
    suspend fun getUnassignedJobs(): ApiResult<List<ClientJobDto>> = safeApiCall { clientApi.getUnassignedJobs() }
    suspend fun assignClientToJob(shiftId: Int, clientId: Int?): ApiResult<String> = safeApiCall { clientApi.assignClientToJob(shiftId, clientId) }
}
