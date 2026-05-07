package nz.co.doer.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.GooglePlacesService
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.remote.dto.LeadsDto
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import nz.co.doer.data.repository.ClientRepository
import nz.co.doer.data.repository.LeadRepository
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SectionState(
    val leads: List<LeadsDto> = emptyList(),
    val isExpanded: Boolean = true,
    val sortColumn: String = "",
    val sortAscending: Boolean = true
)

data class QuotedLeadsUiState(
    val isLoading: Boolean = true,
    val quoteNotAccepted: SectionState = SectionState(),   // statusId = 2
    val closedDeal: SectionState = SectionState(),          // statusId = 3
    val quoteExpired: SectionState = SectionState(),        // statusId = 5
    val drafted: SectionState = SectionState(),             // statusId = 6
    val clients: List<ClientDto> = emptyList(),
    val owners: List<UserDto> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isUpdating: Boolean = false,
    // Edit dialog state
    val editingLead: LeadsDto? = null,
    val editField: EditField? = null,
    val editValue: String = "",
    // Location search
    val searchAddress: String = "",
    val placeList: List<PlacePrediction> = emptyList(),
    val showPlaceList: Boolean = false
)

enum class QuotedSection {
    QuoteNotAccepted, ClosedDeal, QuoteExpired, Drafted
}

@HiltViewModel
class QuotedLeadsViewModel @Inject constructor(
    private val leadRepository: LeadRepository,
    private val clientRepository: ClientRepository,
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService,
    private val boardConfigCache: nz.co.doer.data.local.BoardConfigCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuotedLeadsUiState())
    val uiState: StateFlow<QuotedLeadsUiState> = _uiState.asStateFlow()

    fun leadStatusColor(statusId: Int): Long =
        boardConfigCache.color("LeadStatus", statusId) { NewLeadsViewModel.getLeadStatusColor(statusId) }

    fun contractTypeColorDynamic(contractType: Int?): Long =
        boardConfigCache.color("ContractType", contractType ?: -1) { NewLeadsViewModel.getContractTypeColor(contractType) }

    fun dynamicQuotedLeadStatuses(): List<Pair<Int, String>> {
        val cached = boardConfigCache.getOptions("LeadStatus")
        if (cached.isEmpty()) return quotedLeadStatuses
        val allowed = quotedLeadStatuses.map { it.first }.toSet()
        return cached.filter { it.value in allowed }.map { it.value to it.displayName }
    }

    fun dynamicContractTypes(): List<Pair<Int, String>> {
        val cached = boardConfigCache.getOptions("ContractType")
        return if (cached.isEmpty()) NewLeadsViewModel.contractTypes
        else cached.map { it.value to it.displayName }
    }

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

            // Load leads
            when (val result = leadRepository.getQuotedAndWonLeads()) {
                is ApiResult.Success -> {
                    val allLeads = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        quoteNotAccepted = _uiState.value.quoteNotAccepted.copy(
                            leads = allLeads.filter { it.statusId == 2 }
                        ),
                        closedDeal = _uiState.value.closedDeal.copy(
                            leads = allLeads.filter { it.statusId == 3 }
                        ),
                        quoteExpired = _uiState.value.quoteExpired.copy(
                            leads = allLeads.filter { it.statusId == 5 }
                        ),
                        drafted = _uiState.value.drafted.copy(
                            leads = allLeads.filter { it.statusId == 6 }
                        )
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load quoted leads: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }

            // Load clients
            when (val result = clientRepository.getAllClients()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(clients = result.data)
                }
                is ApiResult.Error -> Timber.e("Failed to load clients: ${result.message}")
                is ApiResult.Loading -> {}
            }

            // Load owners
            when (val result = accountRepository.getAllManagerAndAdminUsers()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(owners = result.data)
                }
                is ApiResult.Error -> Timber.e("Failed to load owners: ${result.message}")
                is ApiResult.Loading -> {}
            }
        }
    }

    fun toggleSection(section: QuotedSection) {
        val state = _uiState.value
        _uiState.value = when (section) {
            QuotedSection.QuoteNotAccepted -> state.copy(
                quoteNotAccepted = state.quoteNotAccepted.copy(isExpanded = !state.quoteNotAccepted.isExpanded)
            )
            QuotedSection.ClosedDeal -> state.copy(
                closedDeal = state.closedDeal.copy(isExpanded = !state.closedDeal.isExpanded)
            )
            QuotedSection.QuoteExpired -> state.copy(
                quoteExpired = state.quoteExpired.copy(isExpanded = !state.quoteExpired.isExpanded)
            )
            QuotedSection.Drafted -> state.copy(
                drafted = state.drafted.copy(isExpanded = !state.drafted.isExpanded)
            )
        }
    }

    fun sortSection(section: QuotedSection, column: String) {
        val state = _uiState.value
        val sectionState = when (section) {
            QuotedSection.QuoteNotAccepted -> state.quoteNotAccepted
            QuotedSection.ClosedDeal -> state.closedDeal
            QuotedSection.QuoteExpired -> state.quoteExpired
            QuotedSection.Drafted -> state.drafted
        }

        val ascending = if (sectionState.sortColumn == column) !sectionState.sortAscending else true
        val sorted = sectionState.leads.sortedWith(
            compareBy<LeadsDto> {
                when (column) {
                    "JobDescription" -> it.jobDescription.lowercase()
                    "OwnerName" -> it.ownerName.lowercase()
                    "StatusName" -> it.statusName.lowercase()
                    "CostFromQuote" -> (it.costFromQuote ?: 0.0).toString().padStart(20, '0')
                    "ClientName" -> it.clientName.lowercase()
                    "Location" -> it.location.lowercase()
                    "ContractTypeName" -> it.contractTypeName.lowercase()
                    "CreatedDate" -> it.createdDate ?: ""
                    else -> ""
                }
            }.let { if (ascending) it else it.reversed() }
        )

        val updatedSection = sectionState.copy(
            leads = sorted,
            sortColumn = column,
            sortAscending = ascending
        )

        _uiState.value = when (section) {
            QuotedSection.QuoteNotAccepted -> state.copy(quoteNotAccepted = updatedSection)
            QuotedSection.ClosedDeal -> state.copy(closedDeal = updatedSection)
            QuotedSection.QuoteExpired -> state.copy(quoteExpired = updatedSection)
            QuotedSection.Drafted -> state.copy(drafted = updatedSection)
        }
    }

    fun startEdit(lead: LeadsDto, field: EditField) {
        val currentValue = when (field) {
            EditField.ProjectDescription -> lead.jobDescription
            EditField.Owner -> lead.ownerId
            EditField.Status -> lead.statusId.toString()
            EditField.Cost -> (lead.costFromQuote ?: 0.0).toString()
            EditField.Client -> (lead.clientId ?: 0).toString()
            EditField.Location -> lead.location
            EditField.ContractType -> (lead.contractType ?: 0).toString()
        }
        _uiState.value = _uiState.value.copy(
            editingLead = lead,
            editField = field,
            editValue = currentValue,
            searchAddress = if (field == EditField.Location) lead.location else "",
            placeList = emptyList(),
            showPlaceList = false
        )
    }

    fun onEditValueChange(value: String) {
        _uiState.value = _uiState.value.copy(editValue = value)
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(
            editingLead = null,
            editField = null,
            editValue = "",
            searchAddress = "",
            placeList = emptyList(),
            showPlaceList = false
        )
    }

    fun saveEditorField() {
        val state = _uiState.value
        val lead = state.editingLead ?: return
        val field = state.editField ?: return
        val value = state.editValue

        val updatedLead = when (field) {
            EditField.ProjectDescription -> lead.copy(jobDescription = value)
            EditField.Cost -> lead.copy(costFromQuote = value.toDoubleOrNull())
            else -> return
        }
        updateLead(updatedLead)
    }

    fun selectLeadStatus(statusId: Int) {
        val lead = _uiState.value.editingLead ?: return
        val updatedLead = lead.copy(statusId = statusId)
        updateLead(updatedLead)
    }

    fun selectOwner(owner: UserDto) {
        val lead = _uiState.value.editingLead ?: return
        val updatedLead = lead.copy(ownerId = owner.id, ownerName = owner.displayName)
        updateLead(updatedLead)
    }

    fun selectClient(client: ClientDto) {
        val lead = _uiState.value.editingLead ?: return
        val updatedLead = lead.copy(
            clientId = client.id,
            clientName = client.name,
            clientEmail = client.email
        )
        updateLead(updatedLead)
    }

    fun selectContractType(contractTypeId: Int) {
        val lead = _uiState.value.editingLead ?: return
        val updatedLead = lead.copy(contractType = contractTypeId)
        updateLead(updatedLead)
    }

    // Location search
    fun onSearchAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(searchAddress = value)
        if (value.isBlank()) {
            _uiState.value = _uiState.value.copy(placeList = emptyList(), showPlaceList = false)
        } else {
            viewModelScope.launch {
                val places = googlePlacesService.getPlacesByText(value)
                _uiState.value = _uiState.value.copy(
                    placeList = places,
                    showPlaceList = places.isNotEmpty()
                )
            }
        }
    }

    fun clearSearchAddress() {
        _uiState.value = _uiState.value.copy(
            searchAddress = "",
            placeList = emptyList(),
            showPlaceList = false
        )
    }

    fun selectPlace(prediction: PlacePrediction) {
        viewModelScope.launch {
            val lead = _uiState.value.editingLead ?: return@launch
            val place = googlePlacesService.getPlaceDetails(prediction.placeId)
            if (place != null) {
                val updatedLead = lead.copy(
                    location = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude
                )
                _uiState.value = _uiState.value.copy(
                    searchAddress = place.address,
                    placeList = emptyList(),
                    showPlaceList = false
                )
                updateLead(updatedLead)
            }
        }
    }

    private fun updateLead(lead: LeadsDto) {
        _uiState.value = _uiState.value.copy(
            isUpdating = true,
            editingLead = null,
            editField = null,
            editValue = "",
            searchAddress = "",
            placeList = emptyList(),
            showPlaceList = false
        )
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val updatedLead = lead.copy(
                modifiedBy = userId,
                userId = userId,
                basicAuthUid = preferencesManager.getBasicAuthUid()
            )

            when (val result = leadRepository.updateLead(updatedLead)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Lead updated successfully"
                    )
                    refresh()
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to update lead: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun sendFollowUp(leadId: Int) {
        _uiState.value = _uiState.value.copy(isUpdating = true)
        viewModelScope.launch {
            when (val result = leadRepository.sendFollowUpMailToClient(leadId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Follow up email sent successfully."
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to send follow-up: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message ?: "Failed to send follow up email. Please try again."
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
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

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    companion object {
        // MAUI Quoted leads has Quote Expired included but Drafted commented out
        val quotedLeadStatuses = listOf(
            1 to "New Lead",
            2 to "Quote Sent",
            3 to "Won",
            4 to "Contacted",
            5 to "Quote Expired"
        )
    }
}
