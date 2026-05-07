package nz.co.doer.ui.leads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.LeadStatus
import nz.co.doer.data.remote.dto.LeadsDto
import nz.co.doer.data.repository.LeadRepository
import timber.log.Timber
import javax.inject.Inject

data class ViewLeadUiState(
    val isLoading: Boolean = true,
    val lead: LeadsDto? = null,
    val errorMessage: String? = null,
    val isNew: Boolean = false,
    val isQuoted: Boolean = false,
    val isWon: Boolean = false,
    val isContacted: Boolean = false,
    val canUpdate: Boolean = false,
    val isReadOnly: Boolean = true,
    val isActionLoading: Boolean = false
)

sealed class ViewLeadEvent {
    data class ShowMessage(val message: String) : ViewLeadEvent()
    object NavigateBack : ViewLeadEvent()
}

@HiltViewModel
class ViewLeadViewModel @Inject constructor(
    private val leadRepository: LeadRepository,
    private val preferencesManager: PreferencesManager,
    private val boardConfigCache: nz.co.doer.data.local.BoardConfigCache,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewLeadUiState())
    val uiState: StateFlow<ViewLeadUiState> = _uiState.asStateFlow()

    fun leadStatusColor(statusId: Int): Long =
        boardConfigCache.color("LeadStatus", statusId) { NewLeadsViewModel.getLeadStatusColor(statusId) }

    private val _events = MutableSharedFlow<ViewLeadEvent>()
    val events: SharedFlow<ViewLeadEvent> = _events.asSharedFlow()

    private val leadId: Int = savedStateHandle.get<String>("leadId")?.toIntOrNull() ?: 0

    init {
        loadLead()
    }

    private fun loadLead() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val lead = findLeadById(leadId)

            if (lead != null) {
                // Matching MAUI OnNavigatedTo() status flag logic exactly
                var isNew = false
                var isQuoted = false
                var isWon = false
                var isContacted = false
                var canUpdate = false
                var isReadOnly = true

                when (lead.statusId) {
                    LeadStatus.NewLead.value -> {
                        isNew = true
                        canUpdate = true
                        isReadOnly = false
                    }
                    LeadStatus.QuoteSent.value -> {
                        isQuoted = true
                    }
                    LeadStatus.Won.value -> {
                        isWon = true
                    }
                    LeadStatus.Contacted.value -> {
                        isContacted = true
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lead = lead,
                    isNew = isNew,
                    isQuoted = isQuoted,
                    isWon = isWon,
                    isContacted = isContacted,
                    canUpdate = canUpdate,
                    isReadOnly = isReadOnly
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Lead not found"
                )
            }
        }
    }

    private suspend fun findLeadById(id: Int): LeadsDto? {
        // Try new leads first
        when (val result = leadRepository.getNewLeads()) {
            is ApiResult.Success -> {
                result.data.find { it.id == id }?.let { return it }
            }
            is ApiResult.Error -> Timber.e("Failed to load new leads: ${result.message}")
            is ApiResult.Loading -> {}
        }

        // Try quoted and won leads
        when (val result = leadRepository.getQuotedAndWonLeads()) {
            is ApiResult.Success -> {
                result.data.find { it.id == id }?.let { return it }
            }
            is ApiResult.Error -> Timber.e("Failed to load quoted leads: ${result.message}")
            is ApiResult.Loading -> {}
        }

        // Try contacted leads
        when (val result = leadRepository.getContactedLeads()) {
            is ApiResult.Success -> {
                result.data.find { it.id == id }?.let { return it }
            }
            is ApiResult.Error -> Timber.e("Failed to load contacted leads: ${result.message}")
            is ApiResult.Loading -> {}
        }

        return null
    }

    /**
     * Send Quote: validates required fields, sets status to QuoteSent, calls updateLead API.
     * Matches MAUI SendQuote command.
     */
    fun sendQuote() {
        val lead = _uiState.value.lead ?: return
        viewModelScope.launch {
            // Validate required fields (matches MAUI validation)
            val missingFields = mutableListOf<String>()
            if (lead.clientName.isBlank()) missingFields.add("Client Name")
            if (lead.clientEmail.isBlank()) missingFields.add("Client Email")
            if (lead.costFromQuote == null || lead.costFromQuote <= 0) missingFields.add("Cost From Quote")

            if (missingFields.isNotEmpty()) {
                _events.emit(ViewLeadEvent.ShowMessage(
                    "Please enter ${missingFields.joinToString(", ")} before sending the quote"
                ))
                return@launch
            }

            _uiState.value = _uiState.value.copy(isActionLoading = true)
            try {
                val userId = preferencesManager.userId.first()
                val updatedLead = lead.copy(
                    statusId = LeadStatus.QuoteSent.value,
                    modifiedBy = userId
                )
                when (val result = leadRepository.updateLead(updatedLead)) {
                    is ApiResult.Success -> {
                        // MAUI: "Quote Sent Sucessfully" (matches MAUI typo)
                        _events.emit(ViewLeadEvent.ShowMessage("Quote Sent Sucessfully"))
                        _events.emit(ViewLeadEvent.NavigateBack)
                    }
                    is ApiResult.Error -> {
                        val errorMsg = result.message ?: "There was a problem in Sent Quote"
                        _events.emit(ViewLeadEvent.ShowMessage(errorMsg))
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception in Send Quote")
                _events.emit(ViewLeadEvent.ShowMessage("There was a problem in Sent Quote"))
            } finally {
                _uiState.value = _uiState.value.copy(isActionLoading = false)
            }
        }
    }

    /**
     * Close Deal (Won): validates required fields, sets status to Won, calls updateLead API.
     * Matches MAUI WonLead command.
     */
    fun closeDeal() {
        val lead = _uiState.value.lead ?: return
        viewModelScope.launch {
            // Validate required fields (matches MAUI validation)
            val missingFields = mutableListOf<String>()
            if (lead.clientName.isBlank()) missingFields.add("Client Name")
            if (lead.clientEmail.isBlank()) missingFields.add("Client Email")
            if (lead.costFromQuote == null || lead.costFromQuote <= 0) missingFields.add("Cost From Quote")

            if (missingFields.isNotEmpty()) {
                _events.emit(ViewLeadEvent.ShowMessage(
                    "Please enter ${missingFields.joinToString(", ")} before sending the quote"
                ))
                return@launch
            }

            _uiState.value = _uiState.value.copy(isActionLoading = true)
            try {
                val userId = preferencesManager.userId.first()
                val updatedLead = lead.copy(
                    statusId = LeadStatus.Won.value,
                    modifiedBy = userId
                )
                when (val result = leadRepository.updateLead(updatedLead)) {
                    is ApiResult.Success -> {
                        _events.emit(ViewLeadEvent.ShowMessage("Status Updated to Won"))
                        _events.emit(ViewLeadEvent.NavigateBack)
                    }
                    is ApiResult.Error -> {
                        val errorMsg = result.message ?: "There was a problem in Update Status to Won"
                        _events.emit(ViewLeadEvent.ShowMessage(errorMsg))
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception in Close Deal")
                _events.emit(ViewLeadEvent.ShowMessage("There was a problem in Update Status to Won"))
            } finally {
                _uiState.value = _uiState.value.copy(isActionLoading = false)
            }
        }
    }

    /**
     * Move to Contacts: sets status to Contacted, calls updateLead API.
     * Matches MAUI Contacted command. Note: MAUI has NO field validation for this action.
     */
    fun moveToContacts() {
        val lead = _uiState.value.lead ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true)
            try {
                val userId = preferencesManager.userId.first()
                val updatedLead = lead.copy(
                    statusId = LeadStatus.Contacted.value,
                    modifiedBy = userId
                )
                when (val result = leadRepository.updateLead(updatedLead)) {
                    is ApiResult.Success -> {
                        _events.emit(ViewLeadEvent.ShowMessage("Status Updated to Contacted"))
                        _events.emit(ViewLeadEvent.NavigateBack)
                    }
                    is ApiResult.Error -> {
                        val errorMsg = result.message ?: "There was a problem in Update Status to Contacted"
                        _events.emit(ViewLeadEvent.ShowMessage(errorMsg))
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception in Move to Contacts")
                _events.emit(ViewLeadEvent.ShowMessage("There was a problem in Update Status to Contacted"))
            } finally {
                _uiState.value = _uiState.value.copy(isActionLoading = false)
            }
        }
    }

    /**
     * Update Lead: updates the lead details via API.
     * Matches MAUI UpdateLead command. Only available when status is NewLead (CanUpdate = true).
     */
    fun updateLead() {
        val lead = _uiState.value.lead ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true)
            try {
                val userId = preferencesManager.userId.first()
                val updatedLead = lead.copy(modifiedBy = userId)
                when (val result = leadRepository.updateLead(updatedLead)) {
                    is ApiResult.Success -> {
                        // MAUI: "Lead Details Updated Sucessfully" (matches MAUI typo)
                        _events.emit(ViewLeadEvent.ShowMessage("Lead Details Updated Sucessfully"))
                        _events.emit(ViewLeadEvent.NavigateBack)
                    }
                    is ApiResult.Error -> {
                        val errorMsg = result.message ?: "There was a problem in Updating Lead Details"
                        _events.emit(ViewLeadEvent.ShowMessage(errorMsg))
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception in Update Lead")
                _events.emit(ViewLeadEvent.ShowMessage("There was a problem in Updating Lead Details"))
            } finally {
                _uiState.value = _uiState.value.copy(isActionLoading = false)
            }
        }
    }
}
