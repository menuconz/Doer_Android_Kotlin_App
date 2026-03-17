package nz.co.doer.ui.quotation

import androidx.lifecycle.SavedStateHandle
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
import nz.co.doer.data.remote.dto.JobQuotationDto
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SendQuoteUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val quotedAmount: String = "",
    val notes: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isExistingQuote: Boolean = false,
    val existingQuoteId: Int = 0
)

@HiltViewModel
class SendQuoteViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendQuoteUiState())
    val uiState: StateFlow<SendQuoteUiState> = _uiState.asStateFlow()

    private val shiftId: Int = savedStateHandle.get<String>("shiftId")?.toIntOrNull() ?: 0

    init {
        loadExistingQuote()
    }

    private fun loadExistingQuote() {
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            when (val result = shiftRepository.getJobQuotationByContractorIdAndShiftId(userId, shiftId)) {
                is ApiResult.Success -> {
                    val quote = result.data
                    if (quote.id > 0) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            quotedAmount = if (quote.quotedAmount > 0) quote.quotedAmount.toString() else "",
                            notes = quote.notes,
                            isExistingQuote = true,
                            existingQuoteId = quote.id
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
                is ApiResult.Error -> {
                    // No existing quote found — that's fine
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun updateQuotedAmount(amount: String) {
        _uiState.value = _uiState.value.copy(quotedAmount = amount)
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun submitQuote() {
        val state = _uiState.value
        val amount = state.quotedAmount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _uiState.value = state.copy(errorMessage = "Please enter a valid quoted amount")
            return
        }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val nowUtc = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            val quotation = JobQuotationDto(
                id = if (state.isExistingQuote) state.existingQuoteId else 0,
                shiftId = shiftId,
                caregiverId = userId,
                quotedAmount = amount,
                notes = state.notes,
                quotedDate = nowUtc,
                status = "Created",
                createdBy = userId,
                createdDate = nowUtc,
                modifiedBy = userId,
                modifiedDate = nowUtc
            )

            when (val result = shiftRepository.addJobQuotation(quotation)) {
                is ApiResult.Success -> {
                    // Matching MAUI: "Job Quotation is submitted."
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "Job Quotation is submitted."
                    )
                }
                is ApiResult.Error -> {
                    // Matching MAUI: "Error in submitting Job Quote."
                    Timber.e("Failed to submit quote: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.message ?: "Error in submitting Job Quote."
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
