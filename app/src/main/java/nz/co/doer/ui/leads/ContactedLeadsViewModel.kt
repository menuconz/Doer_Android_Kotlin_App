package nz.co.doer.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.LeadsDto
import nz.co.doer.data.repository.LeadRepository
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ContactedLeadsUiState(
    val isLoading: Boolean = true,
    val leads: List<LeadsDto> = emptyList(),
    val sortColumn: String = "",
    val sortAscending: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class ContactedLeadsViewModel @Inject constructor(
    private val leadRepository: LeadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactedLeadsUiState())
    val uiState: StateFlow<ContactedLeadsUiState> = _uiState.asStateFlow()

    private val parseFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    )
    private val displayDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = leadRepository.getContactedLeads()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        leads = result.data
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load contacted leads: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun sortBy(column: String) {
        val state = _uiState.value
        val ascending = if (state.sortColumn == column) !state.sortAscending else true
        val sorted = state.leads.sortedWith(
            compareBy<LeadsDto> {
                when (column) {
                    "JobDescription" -> it.jobDescription.lowercase()
                    "ClientName" -> it.clientName.lowercase()
                    "ClientEmail" -> it.clientEmail.lowercase()
                    "CostFromQuote" -> (it.costFromQuote ?: 0.0).toString().padStart(20, '0')
                    "Location" -> it.location.lowercase()
                    "CreatedDate" -> it.createdDate ?: ""
                    else -> ""
                }
            }.let { if (ascending) it else it.reversed() }
        )
        _uiState.value = state.copy(
            leads = sorted,
            sortColumn = column,
            sortAscending = ascending
        )
    }

    fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        for (fmt in parseFormatters) {
            try {
                return LocalDateTime.parse(dateStr.trim(), fmt).format(displayDateFormat)
            } catch (_: Exception) {}
        }
        try {
            val trimmed = dateStr.replace(Regex("\\.\\d+$"), "")
            return LocalDateTime.parse(trimmed, parseFormatters[0]).format(displayDateFormat)
        } catch (_: Exception) {}
        return dateStr
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
