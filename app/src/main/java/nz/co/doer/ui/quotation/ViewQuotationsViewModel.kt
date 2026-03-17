package nz.co.doer.ui.quotation

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
import nz.co.doer.data.remote.GooglePlacesService
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.JobQuotationDto
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ViewQuotationsUiState(
    val isLoading: Boolean = true,
    val isHiring: Boolean = false,
    val quotations: List<JobQuotationDto> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isHired: Boolean = false,
    val sortColumn: String = "",
    val sortAscending: Boolean = true,
    // Matching MAUI: filter fields
    val searchSkills: String = "",
    val searchName: String = "",
    val searchAddress: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeList: List<PlacePrediction> = emptyList(),
    val showPlaceList: Boolean = false,
    val showFilterSheet: Boolean = false
)

@HiltViewModel
class ViewQuotationsViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewQuotationsUiState())
    val uiState: StateFlow<ViewQuotationsUiState> = _uiState.asStateFlow()

    private val shiftId: Int = savedStateHandle.get<String>("shiftId")?.toIntOrNull() ?: 0
    private var shift: ShiftDto? = null

    private val displayDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.ENGLISH)
    private val parseFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH)
    )

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load shift for hire action
            when (val shiftResult = shiftRepository.getShiftById(shiftId)) {
                is ApiResult.Success -> shift = shiftResult.data
                is ApiResult.Error -> Timber.e("Failed to load shift: ${shiftResult.message}")
                is ApiResult.Loading -> {}
            }

            // Matching MAUI: GetQuotations
            when (val result = shiftRepository.getQuotationsByJobId(shiftId)) {
                is ApiResult.Success -> {
                    val quotations = result.data
                    if (quotations.isEmpty()) {
                        // Matching MAUI: "No Quotation found, Please try again later."
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            quotations = emptyList(),
                            errorMessage = "No Quotation found, Please try again later."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            quotations = quotations
                        )
                    }
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load quotations: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // Matching MAUI: FilterQuotations command — show filter popup
    fun showFilter() {
        _uiState.value = _uiState.value.copy(showFilterSheet = true)
    }

    fun dismissFilter() {
        _uiState.value = _uiState.value.copy(showFilterSheet = false)
    }

    fun onSearchSkillsChanged(value: String) {
        _uiState.value = _uiState.value.copy(searchSkills = value)
    }

    fun onSearchNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(searchName = value)
    }

    // Matching MAUI: Google Places search for location filter (Shell.TitleView search bar)
    fun onSearchAddressChanged(value: String) {
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

    // Matching MAUI: Select + AutoFillSelectedAddress → auto-search quotations
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
                // Matching MAUI: AutoFillSelectedAddress calls GetQuotationsOrderBySearch
                searchQuotations()
            } else {
                _uiState.value = _uiState.value.copy(
                    showPlaceList = false,
                    placeList = emptyList()
                )
            }
        }
    }

    // Matching MAUI: ClearSearchLocation
    fun clearSearchLocation() {
        _uiState.value = _uiState.value.copy(
            searchAddress = "",
            latitude = 0.0,
            longitude = 0.0
        )
        loadData()
    }

    // Matching MAUI: ApplyFilter
    fun applyFilter() {
        _uiState.value = _uiState.value.copy(showFilterSheet = false)
        searchQuotations()
    }

    // Matching MAUI: ClearFilter
    fun clearFilter() {
        _uiState.value = _uiState.value.copy(
            searchSkills = "",
            searchName = "",
            searchAddress = "",
            latitude = 0.0,
            longitude = 0.0,
            showFilterSheet = false
        )
        loadData()
    }

    // Matching MAUI: GetQuotationsOrderBySearch
    private fun searchQuotations() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = shiftRepository.getQuotationsBySearch(
                shiftId = shiftId,
                latitude = state.latitude,
                longitude = state.longitude,
                searchSkills = state.searchSkills,
                searchName = state.searchName
            )) {
                is ApiResult.Success -> {
                    val quotations = result.data
                    if (quotations.isEmpty()) {
                        // Matching MAUI: "No Quotation found."
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            quotations = emptyList(),
                            errorMessage = "No Quotation found."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            quotations = quotations
                        )
                    }
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to search quotations: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // Matching MAUI: SortBy command with PascalCase column keys
    fun sortBy(column: String) {
        val state = _uiState.value
        val ascending = if (state.sortColumn == column) !state.sortAscending else true
        val sorted = state.quotations.sortedWith(
            compareBy<JobQuotationDto> {
                when (column) {
                    "ContractorName" -> it.contractorName.lowercase()
                    "ContractorEmail" -> it.contractorEmail.lowercase()
                    "ContractorPhone" -> it.contractorPhone.lowercase()
                    "ContractorAddress" -> it.contractorAddress.lowercase()
                    "QuotedDate" -> it.quotedDate ?: ""
                    "Notes" -> it.notes.lowercase()
                    "QuotedAmount" -> it.quotedAmount.toString().padStart(20, '0')
                    "Skills" -> it.skills.lowercase()
                    else -> ""
                }
            }.let { if (ascending) it else it.reversed() }
        )
        _uiState.value = state.copy(
            quotations = sorted,
            sortColumn = column,
            sortAscending = ascending
        )
    }

    // Matching MAUI: HireContractor command
    fun hireContractor(quotation: JobQuotationDto) {
        val currentShift = shift ?: return
        _uiState.value = _uiState.value.copy(isHiring = true, errorMessage = null)
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            // Matching MAUI: set CaregiverId, CaregiverName, CaregiverEmail, CaregiverPhone, StatusId=Accepted
            val updated = currentShift.copy(
                caregiverId = quotation.caregiverId,
                caregiverName = quotation.contractorName,
                caregiverEmail = quotation.contractorEmail,
                caregiverPhone = quotation.contractorPhone,
                statusId = 2, // Accepted
                modifiedBy = userId
            )
            when (val result = shiftRepository.updateShift(updated)) {
                is ApiResult.Success -> {
                    // Matching MAUI: "Contractor Hired Sucessfully" (intentional typo from MAUI)
                    _uiState.value = _uiState.value.copy(
                        isHiring = false,
                        successMessage = "Contractor Hired Sucessfully",
                        isHired = true
                    )
                }
                is ApiResult.Error -> {
                    // Matching MAUI: "There was a problem in Hiring Contractor"
                    val errorMsg = if (result.message.isNullOrEmpty()) {
                        "There was a problem in Hiring Contractor"
                    } else {
                        result.message
                    }
                    Timber.e("Failed to hire contractor: $errorMsg")
                    _uiState.value = _uiState.value.copy(
                        isHiring = false,
                        errorMessage = errorMsg
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
            } catch (_: Exception) { }
        }
        try {
            val trimmed = dateStr.replace(Regex("\\.\\d+$"), "")
            return LocalDateTime.parse(trimmed, parseFormatters[0]).format(displayDateFormat)
        } catch (_: Exception) { }
        return dateStr
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
