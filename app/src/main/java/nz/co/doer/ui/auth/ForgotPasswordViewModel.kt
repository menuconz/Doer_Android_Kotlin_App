package nz.co.doer.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import javax.inject.Inject

data class ForgotPasswordUiState(
    // Step 1: Generate OTP
    val email: String = "",
    val emailError: String? = null,
    val otpSent: Boolean = false,
    // Step 2: Reset Password
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val otpError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val resetSuccess: Boolean = false
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, emailError = null, errorMessage = null) }
    fun onOtpChange(value: String) { _uiState.value = _uiState.value.copy(otp = value, otpError = null) }
    fun onNewPasswordChange(value: String) { _uiState.value = _uiState.value.copy(newPassword = value, passwordError = null) }
    fun onConfirmPasswordChange(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, confirmPasswordError = null) }

    fun generateOtp() {
        val state = _uiState.value
        if (state.email.isBlank()) {
            _uiState.value = _uiState.value.copy(emailError = "Email is required")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = accountRepository.generateOtp(state.email.trim())) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, otpSent = true)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        var hasError = false

        if (state.otp.isBlank()) { _uiState.value = _uiState.value.copy(otpError = "OTP is required"); hasError = true }
        if (state.newPassword.isBlank()) { _uiState.value = _uiState.value.copy(passwordError = "Password is required"); hasError = true }
        else if (state.newPassword.length < 6) { _uiState.value = _uiState.value.copy(passwordError = "Password must be at least 6 characters"); hasError = true }
        if (state.confirmPassword != state.newPassword) { _uiState.value = _uiState.value.copy(confirmPasswordError = "Passwords do not match"); hasError = true }
        if (hasError) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            when (val result = accountRepository.forgotPassword(state.email.trim(), state.otp.trim(), state.newPassword)) {
                is ApiResult.Success -> {
                    if (result.data) {
                        _uiState.value = _uiState.value.copy(isLoading = false, resetSuccess = true)
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Enter Correct OTP.")
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.Loading -> {}
            }
        }
    }
}
