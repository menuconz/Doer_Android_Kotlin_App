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

data class NewLeadsUiState(
    val isLoading: Boolean = true,
    val leads: List<LeadsDto> = emptyList(),
    val clients: List<ClientDto> = emptyList(),
    val owners: List<UserDto> = emptyList(),
    val sortColumn: String = "",
    val sortAscending: Boolean = true,
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

enum class EditField {
    ProjectDescription, Owner, Status, Cost, Client, Location, ContractType
}

@HiltViewModel
class NewLeadsViewModel @Inject constructor(
    private val leadRepository: LeadRepository,
    private val clientRepository: ClientRepository,
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService,
    private val boardConfigCache: nz.co.doer.data.local.BoardConfigCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewLeadsUiState())
    val uiState: StateFlow<NewLeadsUiState> = _uiState.asStateFlow()

    // ──────────────── Cache-aware label/color helpers (instance) ────────────────
    fun leadStatusColor(statusId: Int): Long =
        boardConfigCache.color("LeadStatus", statusId) { getLeadStatusColor(statusId) }

    fun leadStatusInfo(statusId: Int): Pair<String, String> {
        val cached = boardConfigCache.getOptions("LeadStatus").firstOrNull { it.value == statusId }
        return if (cached != null) {
            cached.displayName to (cached.color ?: getLeadStatusInfo(statusId).second)
        } else {
            getLeadStatusInfo(statusId)
        }
    }

    fun contractTypeColorDynamic(contractType: Int?): Long =
        boardConfigCache.color("ContractType", contractType ?: -1) { getContractTypeColor(contractType) }

    fun contractTypeInfoDynamic(contractType: Int?): Pair<String, String> {
        val cached = boardConfigCache.getOptions("ContractType").firstOrNull { it.value == contractType }
        return if (cached != null) {
            cached.displayName to (cached.color ?: getContractTypeInfo(contractType).second)
        } else {
            getContractTypeInfo(contractType)
        }
    }

    fun dynamicLeadStatuses(): List<Pair<Int, String>> {
        val cached = boardConfigCache.getOptions("LeadStatus")
        return if (cached.isEmpty()) leadStatuses
        else cached.map { it.value to it.displayName }
    }

    fun dynamicContractTypes(): List<Pair<Int, String>> {
        val cached = boardConfigCache.getOptions("ContractType")
        return if (cached.isEmpty()) contractTypes
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
            when (val result = leadRepository.getNewLeads()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        leads = result.data
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load new leads: ${result.message}")
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

    fun sortBy(column: String) {
        val state = _uiState.value
        val ascending = if (state.sortColumn == column) !state.sortAscending else true
        val sorted = state.leads.sortedWith(
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
        _uiState.value = state.copy(
            leads = sorted,
            sortColumn = column,
            sortAscending = ascending
        )
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
        // Lead status colors matching MAUI exactly
        fun getLeadStatusColor(statusId: Int): Long {
            return when (statusId) {
                1 -> 0xFFFF9500  // New Lead
                2 -> 0xFF5659E1  // Quote Sent
                3 -> 0xFF00C874  // Won
                4 -> 0xFFFFCB00  // Contacted
                5 -> 0xFF808080  // Quote Expired
                6 -> 0xFF800080  // Drafted
                else -> 0xFF808080
            }
        }

        fun getLeadStatusInfo(statusId: Int): Pair<String, String> {
            return when (statusId) {
                1 -> "New Lead" to "#FF9500"
                2 -> "Quote Sent" to "#5659E1"
                3 -> "Won" to "#00C874"
                4 -> "Contacted" to "#FFCB00"
                5 -> "Quote Expired" to "#808080"
                6 -> "Drafted" to "#800080"
                else -> "Unknown" to "#808080"
            }
        }

        // Contract type colors matching MAUI exactly
        fun getContractTypeColor(contractType: Int?): Long {
            return when (contractType) {
                1 -> 0xFFC4C4C4   // ToBeConfirmed
                2 -> 0xFFBCA58A   // FullContract
                3 -> 0xFF74AFCC   // SupplyPlaceAndFinish
                4 -> 0xFFCAB641   // PlaceAndFinish
                5 -> 0xFF175A63   // LabourSupply
                6 -> 0xFF333333   // BoxPlaceAndFinish
                7 -> 0xFFFF0000   // Remedial
                8 -> 0xFF037F4C   // SupplyPlaceFinishAndCut
                9 -> 0xFF7F5347   // PlaceFinishAndCut
                10 -> 0xFF7F00FF  // OtherServices
                11 -> 0xFFFF8DA1  // Meetings
                else -> 0xFFC4C4C4
            }
        }

        fun getContractTypeInfo(contractType: Int?): Pair<String, String> {
            return when (contractType) {
                1 -> "To Be Confirmed" to "#C4C4C4"
                2 -> "Full Contract" to "#BCA58A"
                3 -> "Supply Place And Finish" to "#74AFCC"
                4 -> "Place And Finish" to "#CAB641"
                5 -> "Labour Supply" to "#175A63"
                6 -> "Box Place And Finish" to "#333333"
                7 -> "Remedial" to "#FF0000"
                8 -> "Supply Place Finish And Cut" to "#037F4C"
                9 -> "Place Finish And Cut" to "#7F5347"
                10 -> "Other Services" to "#7F00FF"
                11 -> "Meetings" to "#FF8DA1"
                else -> "Unknown" to "#C4C4C4"
            }
        }

        // MAUI has Quote Expired commented out in the status list
        val leadStatuses = listOf(
            1 to "New Lead",
            2 to "Quote Sent",
            3 to "Won",
            4 to "Contacted",
            6 to "Drafted"
        )

        val contractTypes = listOf(
            1 to "To Be Confirmed",
            2 to "Full Contract",
            3 to "Supply Place And Finish",
            4 to "Place And Finish",
            5 to "Labour Supply",
            6 to "Box Place And Finish",
            7 to "Remedial",
            8 to "Supply Place Finish And Cut",
            9 to "Place Finish And Cut",
            10 to "Other Services",
            11 to "Meetings"
        )
    }
}
