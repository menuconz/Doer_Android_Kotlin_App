package nz.co.doer.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

data class AddNewLeadUiState(
    val projectName: String = "",
    val jobDescription: String = "",
    val costFromQuote: String = "",
    // Client
    val clients: List<ClientDto> = emptyList(),
    val selectedClient: ClientDto? = null,
    val clientName: String = "",
    val clientEmail: String = "",
    val clientId: Int? = null,
    // Owner
    val owners: List<UserDto> = emptyList(),
    val selectedOwner: UserDto? = null,
    // Contract Type (uses MAUI enum)
    val selectedContractType: Int = 0,
    // Location with Google Places
    val searchAddress: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeList: List<PlacePrediction> = emptyList(),
    val showPlaceList: Boolean = false,
    // State
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isSuccess: Boolean = false
)

// MAUI contract type enum values
val contractTypeList = listOf(
    "ToBeConfirmed",
    "FullContract",
    "SupplyPlaceAndFinish",
    "PlaceAndFinish",
    "LabourSupply",
    "BoxPlaceAndFinish",
    "Remedial",
    "SupplyPlaceFinishAndCut",
    "PlaceFinishAndCut",
    "OtherServices",
    "Meetings"
)

@HiltViewModel
class AddNewLeadViewModel @Inject constructor(
    private val leadRepository: LeadRepository,
    private val clientRepository: ClientRepository,
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddNewLeadUiState())
    val uiState: StateFlow<AddNewLeadUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    private val _events = MutableSharedFlow<AddNewLeadEvent>()
    val events: SharedFlow<AddNewLeadEvent> = _events.asSharedFlow()

    init {
        loadData()
    }

    // Matching MAUI OnNavigatedTo: clear fields, load clients, load owners
    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load clients (matches MAUI LoadClients)
            when (val result = clientRepository.getAllClients()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(clients = result.data)
                }
                is ApiResult.Error -> Timber.e("Error Loading Clients: ${result.message}")
                is ApiResult.Loading -> {}
            }

            // Load owners (matches MAUI: add "Select Owner" default)
            when (val result = accountRepository.getAllManagerAndAdminUsers()) {
                is ApiResult.Success -> {
                    val selectOwner = UserDto(id = "", displayName = "Select Owner")
                    val ownerList = listOf(selectOwner) + result.data
                    _uiState.value = _uiState.value.copy(
                        owners = ownerList,
                        selectedOwner = selectOwner,
                        isLoading = false
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load owners: ${result.message}")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun onProjectNameChange(value: String) {
        _uiState.value = _uiState.value.copy(projectName = value)
    }

    fun onJobDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(jobDescription = value)
    }

    fun onCostChange(value: String) {
        _uiState.value = _uiState.value.copy(costFromQuote = value)
    }

    // Matching MAUI OnSelectedClientChanged: sets clientId, clientName, clientEmail
    fun onClientSelected(client: ClientDto) {
        _uiState.value = _uiState.value.copy(
            selectedClient = client,
            clientId = client.id,
            clientName = client.name,
            clientEmail = client.email
        )
    }

    fun onOwnerSelected(owner: UserDto) {
        _uiState.value = _uiState.value.copy(selectedOwner = owner)
    }

    fun onContractTypeSelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedContractType = index + 1) // 1-based
    }

    // Google Places - matching MAUI OnSearchAddressChanged + GetSearchList
    fun onSearchAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(searchAddress = value)
        searchJob?.cancel()
        if (value.isBlank()) {
            _uiState.value = _uiState.value.copy(placeList = emptyList(), showPlaceList = false)
        } else {
            searchJob = viewModelScope.launch {
                delay(300) // debounce
                val places = googlePlacesService.getPlacesByText(value)
                _uiState.value = _uiState.value.copy(
                    placeList = places,
                    showPlaceList = places.isNotEmpty()
                )
            }
        }
    }

    // Matching MAUI Select + AutoFillSelectedAddress
    fun selectPlace(prediction: PlacePrediction) {
        viewModelScope.launch {
            val place = googlePlacesService.getPlaceDetails(prediction.placeId)
            if (place != null) {
                _uiState.value = _uiState.value.copy(
                    searchAddress = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    placeList = emptyList(),
                    showPlaceList = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    showPlaceList = false,
                    placeList = emptyList()
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

    // Matching MAUI AddLead command
    fun addLead() {
        val state = _uiState.value

        // MAUI: ValidateAllProperties() → HasErrors → "Please Enter Required Fields."
        // MAUI [Required] fields: ProjectName, JobDescription, SearchAddress
        val hasErrors = state.projectName.isBlank() ||
                state.jobDescription.isBlank() ||
                state.searchAddress.isBlank()

        if (hasErrors) {
            _uiState.value = state.copy(errorMessage = "Please Enter Required Fields.")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val userId = preferencesManager.getUserId()
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

                // Matching MAUI lead creation exactly
                val lead = LeadsDto(
                    userId = userId,
                    name = state.projectName,
                    jobDescription = state.jobDescription,
                    clientId = state.clientId,
                    clientName = state.clientName,
                    clientEmail = state.clientEmail,
                    costFromQuote = state.costFromQuote.toDoubleOrNull(),
                    location = state.searchAddress,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    createdDate = now,
                    createdBy = userId,
                    modifiedDate = now,
                    modifiedBy = userId,
                    statusId = 1, // NewLead
                    contractType = if (state.selectedContractType > 0) state.selectedContractType else null,
                    ownerId = state.selectedOwner?.id ?: "",
                    basicAuthUid = preferencesManager.getBasicAuthUid()
                )

                when (val result = leadRepository.createNewLead(lead)) {
                    is ApiResult.Success -> {
                        Timber.d("Lead created successfully")
                        _events.emit(AddNewLeadEvent.ShowMessage("New Lead created."))
                        _uiState.value = _uiState.value.copy(isSubmitting = false, isSuccess = true)
                    }
                    is ApiResult.Error -> {
                        Timber.e("Failed to create lead: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            errorMessage = "Error in creating New Lead."
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Getting Exception To Create New Lead")
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "Error in creating New Lead."
                )
            }
        }
    }
}

sealed class AddNewLeadEvent {
    data class ShowMessage(val message: String) : AddNewLeadEvent()
}
