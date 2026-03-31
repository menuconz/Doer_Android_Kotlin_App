package nz.co.doer.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import nz.co.doer.BuildConfig
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.api.AccountApi
import nz.co.doer.data.remote.api.CaregiverLevelApi
import nz.co.doer.data.remote.api.ChatMessageApi
import nz.co.doer.data.remote.api.ClientApi
import nz.co.doer.data.remote.api.EmailMessageApi
import nz.co.doer.data.remote.api.LeadApi
import nz.co.doer.data.remote.api.LocationTrackingApi
import nz.co.doer.data.remote.api.LogsApi
import nz.co.doer.data.remote.api.RestHomeApi
import nz.co.doer.data.remote.api.ShiftApi
import nz.co.doer.data.remote.api.TimeTrackingApi
import nz.co.doer.data.remote.interceptor.AuthInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        preferencesManager: PreferencesManager
    ): AuthInterceptor = AuthInterceptor(preferencesManager)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    // API interfaces

    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi =
        retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun provideShiftApi(retrofit: Retrofit): ShiftApi =
        retrofit.create(ShiftApi::class.java)

    @Provides
    @Singleton
    fun provideLeadApi(retrofit: Retrofit): LeadApi =
        retrofit.create(LeadApi::class.java)

    @Provides
    @Singleton
    fun provideClientApi(retrofit: Retrofit): ClientApi =
        retrofit.create(ClientApi::class.java)

    @Provides
    @Singleton
    fun provideChatMessageApi(retrofit: Retrofit): ChatMessageApi =
        retrofit.create(ChatMessageApi::class.java)

    @Provides
    @Singleton
    fun provideEmailMessageApi(retrofit: Retrofit): EmailMessageApi =
        retrofit.create(EmailMessageApi::class.java)

    @Provides
    @Singleton
    fun provideRestHomeApi(retrofit: Retrofit): RestHomeApi =
        retrofit.create(RestHomeApi::class.java)

    @Provides
    @Singleton
    fun provideCaregiverLevelApi(retrofit: Retrofit): CaregiverLevelApi =
        retrofit.create(CaregiverLevelApi::class.java)

    @Provides
    @Singleton
    fun provideLocationTrackingApi(retrofit: Retrofit): LocationTrackingApi =
        retrofit.create(LocationTrackingApi::class.java)

    @Provides
    @Singleton
    fun provideLogsApi(retrofit: Retrofit): LogsApi =
        retrofit.create(LogsApi::class.java)

    @Provides
    @Singleton
    fun provideTimeTrackingApi(retrofit: Retrofit): TimeTrackingApi =
        retrofit.create(TimeTrackingApi::class.java)
}
