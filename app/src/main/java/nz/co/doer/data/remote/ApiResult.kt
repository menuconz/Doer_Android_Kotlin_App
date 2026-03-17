package nz.co.doer.data.remote

/**
 * Wrapper for API call results, replacing the MAUI pattern of
 * try/catch in every service method.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}

/**
 * Safely executes an API call and wraps the result.
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Unknown error", e)
    }
}
