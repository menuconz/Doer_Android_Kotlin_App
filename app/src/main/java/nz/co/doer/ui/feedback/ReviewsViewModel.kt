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
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import javax.inject.Inject

data class ReviewsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val managerFeedback: String = "",
    val hasManagerFeedback: Boolean = false,
    val replyText: String = "",
    val hasExistingReply: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewsUiState())
    val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()

    private val shiftId: Int = savedStateHandle.get<String>("shiftId")?.toIntOrNull() ?: 0
    private var currentShift: ShiftDto? = null

    init {
        loadShift()
    }

    private fun loadShift() {
        viewModelScope.launch {
            when (val result = shiftRepository.getShiftById(shiftId)) {
                is ApiResult.Success -> {
                    val shift = result.data
                    currentShift = shift
                    val existingReply = shift.contractorResponseToReview
                    // MAUI logic: HasReplied = true (show submit) when no existing reply
                    // ReplyText (isReadOnly) = true when existing reply exists
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        managerFeedback = shift.feedback,
                        hasManagerFeedback = shift.feedback.isNotBlank(),
                        replyText = existingReply,
                        hasExistingReply = existingReply.isNotBlank()
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

    fun updateReplyText(text: String) {
        _uiState.value = _uiState.value.copy(replyText = text)
    }

    fun submitReply() {
        val shift = currentShift ?: return
        val reply = _uiState.value.replyText.trim()
        if (reply.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a reply")
            return
        }

        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            // Match MAUI: set modifiedBy to current user, update contractorResponseToReview
            val updatedShift = shift.copy(
                contractorResponseToReview = reply,
                modifiedBy = userId
            )
            when (val result = shiftRepository.updateShift(updatedShift)) {
                is ApiResult.Success -> {
                    currentShift = result.data
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        hasExistingReply = true,
                        successMessage = "Submit Successfully"
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to submit reply: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.message ?: "There was a problem in Submitting Review"
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
