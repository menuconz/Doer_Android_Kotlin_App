package nz.co.doer.ui.contractors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.GooglePlacesService
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class AllContractorsUiState(
    val isLoading: Boolean = true,
    val contractors: List<UserDto> = emptyList(),
    val errorMessage: String? = null,
    // Matching MAUI: sort column keys use PascalCase (DisplayName, Email, etc.)
    val sortColumn: String = "",
    val sortAscending: Boolean = true,
    // Matching MAUI: filter fields
    val searchName: String = "",
    val searchSkills: String = "",
    val searchAddress: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeList: List<PlacePrediction> = emptyList(),
    val showPlaceList: Boolean = false,
    val isFiltered: Boolean = false,
    val showFilterSheet: Boolean = false
)

@HiltViewModel
class AllContractorsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val googlePlacesService: GooglePlacesService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllContractorsUiState())
    val uiState: StateFlow<AllContractorsUiState> = _uiState.asStateFlow()

    private val displayDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    private val parseFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH)
    )

    init {
        loadAllContractors()
    }

    // Matching MAUI: GetAllContractors — ordered by Id descending, only AdminVerified
    private fun loadAllContractors() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = accountRepository.getAllContractors()) {
                is ApiResult.Success -> {
                    val verified = result.data.filter { it.adminVerified }
                    val sorted = verified.sortedByDescending { it.id }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        contractors = sorted,
                        sortColumn = "",
                        sortAscending = true,
                        isFiltered = false
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load contractors: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // Matching MAUI: FilterJobs command — show filter popup
    fun showFilter() {
        _uiState.value = _uiState.value.copy(showFilterSheet = true)
    }

    fun dismissFilter() {
        _uiState.value = _uiState.value.copy(showFilterSheet = false)
    }

    fun onSearchNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(searchName = value)
    }

    fun onSearchSkillsChanged(value: String) {
        _uiState.value = _uiState.value.copy(searchSkills = value)
    }

    // Matching MAUI: Google Places search for location filter
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

    // Matching MAUI: Select + AutoFillSelectedAddress
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

    // Matching MAUI: ClearSearchLocation
    fun clearSearchLocation() {
        _uiState.value = _uiState.value.copy(
            searchAddress = "",
            latitude = 0.0,
            longitude = 0.0
        )
        loadAllContractors()
    }

    // Matching MAUI: ApplyFilter
    fun applyFilter() {
        val state = _uiState.value
        _uiState.value = state.copy(showFilterSheet = false)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = accountRepository.searchContractorsBySkillsAndLocation(
                latitude = state.latitude,
                longitude = state.longitude,
                searchSkills = state.searchSkills,
                searchName = state.searchName
            )) {
                is ApiResult.Success -> {
                    val verified = result.data.filter { it.adminVerified }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        contractors = verified,
                        sortColumn = "",
                        sortAscending = true,
                        isFiltered = true
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to search contractors: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // Matching MAUI: ClearFilter
    fun clearFilter() {
        _uiState.value = _uiState.value.copy(
            searchName = "",
            searchSkills = "",
            searchAddress = "",
            latitude = 0.0,
            longitude = 0.0,
            isFiltered = false,
            showFilterSheet = false
        )
        loadAllContractors()
    }

    // Matching MAUI: SortBy command with PascalCase column keys
    fun sortBy(column: String) {
        val state = _uiState.value
        val ascending = if (state.sortColumn == column) !state.sortAscending else true
        val sorted = state.contractors.sortedWith(
            compareBy<UserDto> {
                when (column) {
                    "DisplayName" -> it.displayName.lowercase()
                    "Email" -> it.email.lowercase()
                    "PhoneNumber" -> it.phoneNumber.lowercase()
                    "DateofBirthString" -> it.dateOfBirth ?: ""
                    "Address" -> it.address.lowercase()
                    "WorkExperience" -> it.workExperience.lowercase()
                    "Skills" -> it.skills.lowercase()
                    else -> ""
                }
            }.let { if (ascending) it else it.reversed() }
        )
        _uiState.value = state.copy(
            contractors = sorted,
            sortColumn = column,
            sortAscending = ascending
        )
    }

    // Matching MAUI: DateofBirthString format dd/MM/yyyy
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
