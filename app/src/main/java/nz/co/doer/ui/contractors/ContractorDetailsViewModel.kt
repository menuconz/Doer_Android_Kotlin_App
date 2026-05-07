package nz.co.doer.ui.contractors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.FileModelDto
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ContractorDetailsUiState(
    val isLoading: Boolean = true,
    val contractor: UserDto? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ContractorDetailsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val preferencesManager: nz.co.doer.data.local.PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContractorDetailsUiState())
    val uiState: StateFlow<ContractorDetailsUiState> = _uiState.asStateFlow()

    private val contractorId: String = savedStateHandle.get<String>("contractorId") ?: ""

    private val displayDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    private val parseFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH)
    )

    init {
        loadContractor()
    }

    private fun loadContractor() {
        if (contractorId.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Invalid contractor ID"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = accountRepository.getUser(contractorId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        contractor = result.data
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load contractor: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun formatDateOfBirth(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        for (fmt in parseFormatters) {
            try {
                return LocalDateTime.parse(dateStr.trim(), fmt).format(displayDateFormat)
            } catch (_: Exception) { }
        }
        try {
            val trimmed = dateStr.replace(Regex("\\.\\d+$"), "")
            return LocalDateTime.parse(trimmed, parseFormatters[0]).format(displayDateFormat)
        } catch (_: Exception) { }
        return dateStr
    }

    fun isImageFile(fileModel: FileModelDto): Boolean {
        val ext = fileModel.name.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun isAdmin(): kotlinx.coroutines.flow.Flow<Boolean> = preferencesManager.isAdmin

    // Admin-only: toggle the contractor's IsEmployee flag.
    fun toggleEmployeeFlag(newValue: Boolean) {
        val current = _uiState.value.contractor ?: return
        viewModelScope.launch {
            val adminId = preferencesManager.getUserId()
            when (val result = accountRepository.markAsEmployee(current.id, newValue, adminId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(contractor = result.data)
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to mark as employee: ${result.message}")
                    _uiState.value = _uiState.value.copy(errorMessage = result.message ?: "Failed to update.")
                }
                is ApiResult.Loading -> {}
            }
        }
    }
}
