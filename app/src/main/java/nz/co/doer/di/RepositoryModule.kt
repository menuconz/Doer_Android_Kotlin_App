package nz.co.doer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.api.AccountApi
import nz.co.doer.data.remote.api.ActivityLogApi
import nz.co.doer.data.remote.api.BoardApi
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
import kotlinx.serialization.json.Json
import nz.co.doer.data.repository.AccountRepository
import nz.co.doer.data.repository.ActivityLogRepository
import nz.co.doer.data.repository.BoardRepository
import nz.co.doer.data.repository.CaregiverLevelRepository
import nz.co.doer.data.repository.ChatMessageRepository
import nz.co.doer.data.repository.ClientRepository
import nz.co.doer.data.repository.EmailMessageRepository
import nz.co.doer.data.repository.LeadRepository
import nz.co.doer.data.repository.LocationTrackingRepository
import nz.co.doer.data.repository.LogsRepository
import nz.co.doer.data.repository.RestHomeRepository
import nz.co.doer.data.repository.ShiftRepository
import nz.co.doer.data.repository.TimeTrackingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAccountRepository(
        accountApi: AccountApi,
        preferencesManager: PreferencesManager,
        json: Json
    ): AccountRepository = AccountRepository(accountApi, preferencesManager, json)

    @Provides
    @Singleton
    fun provideShiftRepository(
        shiftApi: ShiftApi,
        preferencesManager: PreferencesManager
    ): ShiftRepository = ShiftRepository(shiftApi, preferencesManager)

    @Provides
    @Singleton
    fun provideLeadRepository(
        leadApi: LeadApi,
        preferencesManager: PreferencesManager
    ): LeadRepository = LeadRepository(leadApi, preferencesManager)

    @Provides
    @Singleton
    fun provideClientRepository(
        clientApi: ClientApi,
        preferencesManager: PreferencesManager
    ): ClientRepository = ClientRepository(clientApi, preferencesManager)

    @Provides
    @Singleton
    fun provideChatMessageRepository(
        chatMessageApi: ChatMessageApi,
        preferencesManager: PreferencesManager
    ): ChatMessageRepository = ChatMessageRepository(chatMessageApi, preferencesManager)

    @Provides
    @Singleton
    fun provideEmailMessageRepository(
        emailMessageApi: EmailMessageApi,
        preferencesManager: PreferencesManager
    ): EmailMessageRepository = EmailMessageRepository(emailMessageApi, preferencesManager)

    @Provides
    @Singleton
    fun provideRestHomeRepository(
        restHomeApi: RestHomeApi,
        preferencesManager: PreferencesManager
    ): RestHomeRepository = RestHomeRepository(restHomeApi, preferencesManager)

    @Provides
    @Singleton
    fun provideCaregiverLevelRepository(
        caregiverLevelApi: CaregiverLevelApi
    ): CaregiverLevelRepository = CaregiverLevelRepository(caregiverLevelApi)

    @Provides
    @Singleton
    fun provideLocationTrackingRepository(
        locationTrackingApi: LocationTrackingApi,
        preferencesManager: PreferencesManager
    ): LocationTrackingRepository = LocationTrackingRepository(locationTrackingApi, preferencesManager)

    @Provides
    @Singleton
    fun provideLogsRepository(
        logsApi: LogsApi,
        preferencesManager: PreferencesManager
    ): LogsRepository = LogsRepository(logsApi, preferencesManager)

    @Provides
    @Singleton
    fun provideTimeTrackingRepository(
        timeTrackingApi: TimeTrackingApi,
        preferencesManager: PreferencesManager
    ): TimeTrackingRepository = TimeTrackingRepository(timeTrackingApi, preferencesManager)

    @Provides
    @Singleton
    fun provideBoardRepository(
        boardApi: BoardApi
    ): BoardRepository = BoardRepository(boardApi)

    @Provides
    @Singleton
    fun provideActivityLogRepository(
        activityLogApi: ActivityLogApi
    ): ActivityLogRepository = ActivityLogRepository(activityLogApi)
}
