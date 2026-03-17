package nz.co.doer.data.remote.api

import nz.co.doer.data.remote.dto.LogsDto
import retrofit2.http.Body
import retrofit2.http.POST

interface LogsApi {

    @POST("Common/SaveLog")
    suspend fun enterLogs(
        @Body logs: LogsDto
    ): LogsDto
}
