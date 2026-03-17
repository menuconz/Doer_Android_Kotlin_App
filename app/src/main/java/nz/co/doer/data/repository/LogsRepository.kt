package nz.co.doer.data.repository

import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.api.LogsApi
import nz.co.doer.data.remote.dto.LogsDto
import nz.co.doer.data.remote.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogsRepository @Inject constructor(
    private val logsApi: LogsApi,
    private val preferencesManager: PreferencesManager
) {
    suspend fun enterLogs(logs: LogsDto): ApiResult<LogsDto> = safeApiCall {
        logsApi.enterLogs(logs)
    }
}
