package nz.co.doer.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.RegisterUserWithoutDocumentDto
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import javax.inject.Inject

data class RegisterManagerUiState(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val dateOfBirth: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class RegisterManagerViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterManagerUiState())
    val uiState: StateFlow<RegisterManagerUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(fullName = value, nameError = null) }
    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, emailError = null) }
    fun onPhoneChange(value: String) { _uiState.value = _uiState.value.copy(phone = value) }
    fun onDateOfBirthChange(value: String) { _uiState.value = _uiState.value.copy(dateOfBirth = value) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, passwordError = null) }
    fun onConfirmPasswordChange(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, confirmPasswordError = null) }

    fun register() {
        val state = _uiState.value
        var hasError = false

        if (state.fullName.isBlank()) { _uiState.value = _uiState.value.copy(nameError = "Name is required"); hasError = true }
        if (state.email.isBlank()) { _uiState.value = _uiState.value.copy(emailError = "Email is required"); hasError = true }
        else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) { _uiState.value = _uiState.value.copy(emailError = "Invalid email format"); hasError = true }
        if (state.password.isBlank()) { _uiState.value = _uiState.value.copy(passwordError = "Password is required"); hasError = true }
        else if (state.password.length < 6) { _uiState.value = _uiState.value.copy(passwordError = "Password must be at least 6 characters"); hasError = true }
        if (state.confirmPassword != state.password) { _uiState.value = _uiState.value.copy(confirmPasswordError = "Passwords do not match"); hasError = true }
        if (hasError) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                when (val emailCheck = accountRepository.checkEmailExists(state.email.trim())) {
                    is ApiResult.Success -> {
                        if (emailCheck.data) {
                            _uiState.value = _uiState.value.copy(isLoading = false, emailError = "Email already registered")
                            return@launch
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = emailCheck.message)
                        return@launch
                    }
                    is ApiResult.Loading -> {}
                }

                // Convert dd/MM/yyyy to ISO format for API
                val isoDateOfBirth = if (state.dateOfBirth.isNotBlank()) {
                    val parts = state.dateOfBirth.split("/")
                    "${parts[2]}-${parts[1]}-${parts[0]}T00:00:00"
                } else null

                val registerUser = RegisterUserWithoutDocumentDto(
                    displayName = state.fullName.trim(),
                    email = state.email.trim(),
                    password = state.password,
                    phoneNumber = state.phone.trim(),
                    dateOfBirth = isoDateOfBirth,
                    restHomeId = 0
                )

                when (val result = accountRepository.registerManager(registerUser)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Your account as Manager has been successfully created and is pending approval"
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Registration failed")
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Registration failed. Please try again.")
            }
        }
    }
}
