package nz.co.doer.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.repository.ClientRepository
import timber.log.Timber
import javax.inject.Inject

data class AddNewClientUiState(
    val name: String = "",
    val email: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AddNewClientViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddNewClientUiState())
    val uiState: StateFlow<AddNewClientUiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    // Matching MAUI AddClient command
    fun addClient() {
        val state = _uiState.value

        // MAUI: ValidateAllProperties() → HasErrors → "Please Enter Required Feilds." (MAUI typo)
        // MAUI [Required] fields: Name, Email
        val hasErrors = state.name.isBlank() || state.email.isBlank()

        if (hasErrors) {
            _uiState.value = state.copy(errorMessage = "Please Enter Required Feilds.")
            return
        }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val userId = preferencesManager.getUserId()
                val basicAuthUid = preferencesManager.getBasicAuthUid()

                // Matching MAUI: sets CreatedBy, ModifiedBy, UserId, BasicAuthUid
                // MAUI does NOT set CreatedDate/ModifiedDate - server manages it
                val client = ClientDto(
                    name = state.name.trim(),
                    email = state.email.trim(),
                    createdBy = userId,
                    modifiedBy = userId,
                    userId = userId,
                    basicAuthUid = basicAuthUid
                )

                when (val result = clientRepository.createNewClient(client)) {
                    is ApiResult.Success -> {
                        // MAUI: "New Client created."
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            successMessage = "New Client created."
                        )
                    }
                    is ApiResult.Error -> {
                        // MAUI: "Error in creating New Client."
                        Timber.e("Failed to create client: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            errorMessage = "Error in creating New Client."
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception creating new client")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Error in creating New Client."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
