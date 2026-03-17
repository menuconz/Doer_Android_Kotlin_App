package nz.co.doer.data.remote.interceptor

import kotlinx.coroutines.runBlocking
import nz.co.doer.data.local.PreferencesManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { preferencesManager.getBasicAuthUid() }

        val request = chain.request().newBuilder().apply {
            if (token.isNotEmpty()) {
                addHeader("Authorization", "Bearer $token")
            }
            addHeader("Content-Type", "application/json")
            addHeader("Accept", "application/json")
        }.build()

        return chain.proceed(request)
    }
}
