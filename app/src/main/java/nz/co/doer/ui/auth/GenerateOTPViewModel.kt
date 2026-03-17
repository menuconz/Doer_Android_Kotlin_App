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

data class GenerateOTPUiState(
    val email: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val otpGenerated: Boolean = false
)

@HiltViewModel
class GenerateOTPViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerateOTPUiState())
    val uiState: StateFlow<GenerateOTPUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(
            email = value,
            emailError = null,
            errorMessage = null
        )
    }

    fun generateOtp() {
        val state = _uiState.value
        val email = state.email.trim()

        if (email.isBlank()) {
            _uiState.value = state.copy(emailError = "Email is required")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = state.copy(emailError = "Please enter a valid email address")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null, emailError = null)

        viewModelScope.launch {
            when (val result = accountRepository.generateOtp(email)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpGenerated = true
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to generate OTP: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
