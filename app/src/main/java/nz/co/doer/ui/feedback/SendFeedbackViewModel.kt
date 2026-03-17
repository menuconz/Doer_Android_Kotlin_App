package nz.co.doer.ui.feedback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.remote.dto.ShiftStatus
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import javax.inject.Inject

data class SendFeedbackUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val feedbackText: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SendFeedbackViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendFeedbackUiState())
    val uiState: StateFlow<SendFeedbackUiState> = _uiState.asStateFlow()

    private val shiftId: Int = savedStateHandle.get<String>("shiftId")?.toIntOrNull() ?: 0
    private var currentShift: ShiftDto? = null

    init {
        loadShift()
    }

    private fun loadShift() {
        viewModelScope.launch {
            when (val result = shiftRepository.getShiftById(shiftId)) {
                is ApiResult.Success -> {
                    currentShift = result.data
                    // Matching MAUI: Feedback = string.Empty — always start with empty feedback
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        feedbackText = ""
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun updateFeedback(text: String) {
        _uiState.value = _uiState.value.copy(feedbackText = text)
    }

    fun submitFeedback() {
        val shift = currentShift ?: return
        val feedback = _uiState.value.feedbackText.trim()
        if (feedback.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter feedback")
            return
        }

        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val updatedShift = shift.copy(
                feedback = feedback,
                modifiedBy = userId,
                statusId = ShiftStatus.FinishJob.value
            )
            when (val result = shiftRepository.updateShift(updatedShift)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Job Completed"
                    )
                }
                is ApiResult.Error -> {
                    val errorMsg = if (result.message.isNullOrEmpty()) {
                        "There was a problem in Completing Job"
                    } else {
                        result.message
                    }
                    Timber.e("Failed to submit feedback: $errorMsg")
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = errorMsg
                    )
                }
                is ApiResult.Loading -> {}
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
