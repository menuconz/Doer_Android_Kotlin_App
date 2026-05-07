package nz.co.doer.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.local.SecureStorageManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager,
    private val secureStorageManager: SecureStorageManager,
    private val firebaseMessaging: FirebaseMessaging
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, emailError = null, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, passwordError = null, errorMessage = null)
    }

    fun login() {
        val state = _uiState.value
        var hasError = false

        if (state.email.isBlank()) {
            _uiState.value = _uiState.value.copy(emailError = "Email is required")
            hasError = true
        }
        if (state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = "Password is required")
            hasError = true
        } else if (state.password.length < 6) {
            _uiState.value = _uiState.value.copy(passwordError = "Password must be at least 6 characters")
            hasError = true
        }
        if (hasError) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val deviceToken = try {
                    firebaseMessaging.token.await()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get FCM token")
                    ""
                }

                when (val result = accountRepository.authenticate(
                    userName = state.email.trim(),
                    password = state.password,
                    deviceToken = deviceToken
                )) {
                    is ApiResult.Success -> {
                        val user = result.data
                        Timber.d("Login response - Role: '${user.role}', AdminVerified: ${user.adminVerified}, DisplayName: ${user.displayName}")
                        if (!user.errorMessage.isNullOrEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = user.errorMessage
                            )
                            return@launch
                        }

                        val role = user.role

                        // Only Contractor and Manager roles need admin approval (matching MAUI exact checks)
                        if (role == "Contractor" && !user.adminVerified) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = "Your Account is not approved by the Administrator."
                            )
                            return@launch
                        }
                        if (role == "Manager" && !user.adminVerified) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = "Your Account is not approved by the Administrator."
                            )
                            return@launch
                        }
                        val isAdmin = role.equals("Administrator", ignoreCase = true)
                        val isManager = role.equals("Manager", ignoreCase = true)
                        val isCaregiver = role.equals("Contractor", ignoreCase = true) || role.equals("Caregiver", ignoreCase = true)
                        val isCustomer = role.equals("Customer", ignoreCase = true)

                        preferencesManager.saveUserSession(
                            fullName = user.displayName,
                            phone = user.phoneNumber,
                            email = user.email,
                            userId = user.id,
                            contactId = user.contactId,
                            basicAuthUid = user.token,
                            role = role,
                            isManager = isManager,
                            isCaregiver = isCaregiver,
                            isCustomer = isCustomer,
                            isAdmin = isAdmin,
                            isContractor = isCaregiver,
                            isEmployee = user.isEmployee
                        )

                        // Login response from the deployed API doesn't always carry IsEmployee
                        // correctly; GetUserById does. Refetch and overwrite once the token
                        // is in prefs so the auth interceptor can attach the bearer.
                        if (isCaregiver) {
                            when (val r = accountRepository.getUser(user.id)) {
                                is ApiResult.Success -> preferencesManager.setIsEmployee(r.data.isEmployee)
                                else -> {}
                            }
                        }

                        secureStorageManager.isLoggedIn = true

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loginSuccess = true
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Login failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Login failed. Please try again."
                )
            }
        }
    }
}
