package nz.co.doer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doer_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {

    private object Keys {
        val FULL_NAME = stringPreferencesKey("full_name")
        val PHONE = stringPreferencesKey("phone")
        val EMAIL = stringPreferencesKey("email")
        val DISCIPLINE = stringPreferencesKey("discipline")
        val USER_ID = stringPreferencesKey("user_id")
        val CONTACT_ID = intPreferencesKey("contact_id")
        val BASIC_AUTH_UID = stringPreferencesKey("basic_auth_uid")
        val ROLE = stringPreferencesKey("role")
        val IS_MANAGER = booleanPreferencesKey("is_manager")
        val IS_CAREGIVER = booleanPreferencesKey("is_caregiver")
        val IS_CUSTOMER = booleanPreferencesKey("is_customer")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
        val IS_CONTRACTOR = booleanPreferencesKey("is_contractor")
        val IS_EMPLOYEE = booleanPreferencesKey("is_employee")
    }

    private val dataStore = context.dataStore

    // --- Read flows ---

    val fullName: Flow<String> = dataStore.data.map { it[Keys.FULL_NAME] ?: "" }
    val phone: Flow<String> = dataStore.data.map { it[Keys.PHONE] ?: "" }
    val email: Flow<String> = dataStore.data.map { it[Keys.EMAIL] ?: "" }
    val discipline: Flow<String> = dataStore.data.map { it[Keys.DISCIPLINE] ?: "" }
    val userId: Flow<String> = dataStore.data.map { it[Keys.USER_ID] ?: "" }
    val contactId: Flow<Int> = dataStore.data.map { it[Keys.CONTACT_ID] ?: 0 }
    val basicAuthUid: Flow<String> = dataStore.data.map { it[Keys.BASIC_AUTH_UID] ?: "" }
    val role: Flow<String> = dataStore.data.map { it[Keys.ROLE] ?: "" }
    val isManager: Flow<Boolean> = dataStore.data.map { it[Keys.IS_MANAGER] ?: false }
    val isCaregiver: Flow<Boolean> = dataStore.data.map { it[Keys.IS_CAREGIVER] ?: false }
    val isCustomer: Flow<Boolean> = dataStore.data.map { it[Keys.IS_CUSTOMER] ?: false }
    val isAdmin: Flow<Boolean> = dataStore.data.map { it[Keys.IS_ADMIN] ?: false }
    val isContractor: Flow<Boolean> = dataStore.data.map { it[Keys.IS_CONTRACTOR] ?: false }
    val isEmployee: Flow<Boolean> = dataStore.data.map { it[Keys.IS_EMPLOYEE] ?: false }

    // --- Synchronous getters (for interceptor etc.) ---

    suspend fun getBasicAuthUid(): String = dataStore.data.first()[Keys.BASIC_AUTH_UID] ?: ""
    suspend fun getUserId(): String = dataStore.data.first()[Keys.USER_ID] ?: ""
    suspend fun getEmail(): String = dataStore.data.first()[Keys.EMAIL] ?: ""
    suspend fun getContactId(): Int = dataStore.data.first()[Keys.CONTACT_ID] ?: 0

    // --- Save user session ---

    suspend fun saveUserSession(
        fullName: String,
        phone: String,
        email: String,
        userId: String,
        contactId: Int,
        basicAuthUid: String,
        role: String,
        isManager: Boolean,
        isCaregiver: Boolean,
        isCustomer: Boolean,
        isAdmin: Boolean,
        isContractor: Boolean,
        isEmployee: Boolean = false,
        discipline: String = ""
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.FULL_NAME] = fullName
            prefs[Keys.PHONE] = phone
            prefs[Keys.EMAIL] = email
            prefs[Keys.USER_ID] = userId
            prefs[Keys.CONTACT_ID] = contactId
            prefs[Keys.BASIC_AUTH_UID] = basicAuthUid
            prefs[Keys.ROLE] = role
            prefs[Keys.IS_MANAGER] = isManager
            prefs[Keys.IS_CAREGIVER] = isCaregiver
            prefs[Keys.IS_CUSTOMER] = isCustomer
            prefs[Keys.IS_ADMIN] = isAdmin
            prefs[Keys.IS_CONTRACTOR] = isContractor
            prefs[Keys.IS_EMPLOYEE] = isEmployee
            prefs[Keys.DISCIPLINE] = discipline
        }
    }

    suspend fun setIsEmployee(isEmployee: Boolean) {
        dataStore.edit { it[Keys.IS_EMPLOYEE] = isEmployee }
    }

    // --- Clear session (logout) ---

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }
}
