package nz.co.doer.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: UserDto? = null,
    val isCaregiver: Boolean = false,
    val isCustomer: Boolean = false,
    val isManager: Boolean = false,
    val isNoDocument: Boolean = false,
    val errorMessage: String? = null,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    // Matching MAUI: show success alert before navigating
    val deleteSuccessMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val isCaregiver = preferencesManager.isCaregiver.first()
            val isCustomer = preferencesManager.isCustomer.first()
            val isManager = preferencesManager.isManager.first()

            when (val result = accountRepository.getUser(userId)) {
                is ApiResult.Success -> {
                    val user = result.data
                    // Matching MAUI: check documents for caregiver
                    val isNoDocument = if (isCaregiver) {
                        user.documents.isNullOrEmpty()
                    } else false

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = user,
                        isCaregiver = isCaregiver,
                        isCustomer = isCustomer,
                        isManager = isManager,
                        isNoDocument = isNoDocument
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Error fetching user details in Profile Page")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message ?: "Error"
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // Matching MAUI DeleteAccount command
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            try {
                val userId = preferencesManager.getUserId()
                when (val result = accountRepository.deleteUserAccount(userId)) {
                    is ApiResult.Success -> {
                        // MAUI: show success alert, then clear session and navigate
                        preferencesManager.clearSession()
                        _uiState.value = _uiState.value.copy(
                            isDeleting = false,
                            deleteSuccessMessage = "Your account has been successfully deleted. We appreciate your time with us and hope to serve you again in the future."
                        )
                    }
                    is ApiResult.Error -> {
                        // MAUI: "Error deleting account. Please try again later."
                        Timber.e("Error deleting account")
                        _uiState.value = _uiState.value.copy(
                            isDeleting = false,
                            errorMessage = "Error deleting account. Please try again later."
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Getting Exception in Deleting User Account in Profile Page")
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    errorMessage = "Error deleting account. Please try again later."
                )
            }
        }
    }

    fun onDeleteSuccessDismissed() {
        _uiState.value = _uiState.value.copy(deleteSuccessMessage = null, isDeleted = true)
    }

    fun refresh() {
        loadProfile()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
