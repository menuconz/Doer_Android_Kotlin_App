package nz.co.doer.ui.profile

import android.net.Uri
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
import nz.co.doer.data.remote.GooglePlacesService
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.FileModelDto
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val dateOfBirth: String = "",
    val dateOfBirthRaw: String? = null, // ISO format from API, for sending back
    val address: String = "",
    val workExperience: String = "",
    val skills: String = "",
    val isCaregiver: Boolean = false,
    val isCustomer: Boolean = false,
    val isManager: Boolean = false,
    val isNoDocument: Boolean = false,
    val hasDocument: Boolean = false,
    val existingDocuments: List<FileModelDto> = emptyList(),
    val newDocumentUris: List<Uri> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val userId: String = "",
    // Google Places - matching MAUI SearchAddress, PlaceList, SearchListVisibility
    val searchAddress: String = "",
    val placeList: List<PlacePrediction> = emptyList(),
    val showPlaceList: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val restHomeId: Int? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    // Matching MAUI OnNavigatedTo: GetUser, set role flags
    private fun loadProfile() {
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val isCaregiver = preferencesManager.isCaregiver.first()
            val isCustomer = preferencesManager.isCustomer.first()
            val isManager = preferencesManager.isManager.first()

            when (val result = accountRepository.getUser(userId)) {
                is ApiResult.Success -> {
                    val user = result.data
                    // Matching MAUI: Name = detail.DisplayName
                    val isNoDoc = if (isCaregiver) user.documents.isNullOrEmpty() else false
                    val hasDocs = if (isCaregiver) !user.documents.isNullOrEmpty() else false

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        name = user.displayName,
                        email = user.email,
                        phone = user.phoneNumber,
                        dateOfBirth = formatDobForDisplay(user.dateOfBirth),
                        dateOfBirthRaw = user.dateOfBirth,
                        // MAUI: SearchAddress = detail.Address
                        searchAddress = user.address,
                        address = user.address,
                        latitude = user.latitude ?: 0.0,
                        longitude = user.longitude ?: 0.0,
                        workExperience = user.workExperience,
                        skills = user.skills,
                        isCaregiver = isCaregiver,
                        isCustomer = isCustomer,
                        isManager = isManager,
                        isNoDocument = isNoDoc,
                        hasDocument = hasDocs,
                        existingDocuments = user.documents ?: emptyList(),
                        userId = userId,
                        restHomeId = user.restHomeId
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Getting Exception To Fetch User Details in EditProfile Screen")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message ?: "Error"
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun updatePhone(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone)
    }

    fun updateWorkExperience(exp: String) {
        _uiState.value = _uiState.value.copy(workExperience = exp)
    }

    fun updateSkills(skills: String) {
        _uiState.value = _uiState.value.copy(skills = skills)
    }

    fun updateDateOfBirth(dob: String) {
        // Convert dd/MM/yyyy display format to ISO format for API
        val isoDate = try {
            val parsed = java.time.LocalDate.parse(dob, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            parsed.atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        } catch (_: Exception) {
            dob
        }
        _uiState.value = _uiState.value.copy(dateOfBirth = dob, dateOfBirthRaw = isoDate)
    }

    // Google Places - matching MAUI OnSearchAddressChanged + GetSearchList
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

    // Matching MAUI Select + AutoFillSelectedAddress
    fun selectPlace(prediction: PlacePrediction) {
        viewModelScope.launch {
            val place = googlePlacesService.getPlaceDetails(prediction.placeId)
            if (place != null) {
                _uiState.value = _uiState.value.copy(
                    address = place.address,
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

    fun addDocumentUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            newDocumentUris = _uiState.value.newDocumentUris + uri
        )
    }

    fun removeNewDocument(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            newDocumentUris = _uiState.value.newDocumentUris - uri
        )
    }

    // Matching MAUI DeleteDocument command
    // Confirm: "Are you sure you want to delete this document?" Yes/No
    // Success: "Document Deleted Sucessfully" (MAUI typo)
    // Error: "There was a problem in Deleting Document"
    fun deleteExistingDocument(doc: FileModelDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                when (accountRepository.deleteDocument(doc.id)) {
                    is ApiResult.Success -> {
                        val updatedDocs = _uiState.value.existingDocuments.filter { it.id != doc.id }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            existingDocuments = updatedDocs,
                            isNoDocument = updatedDocs.isEmpty(),
                            hasDocument = updatedDocs.isNotEmpty(),
                            successMessage = "Document Deleted Sucessfully"
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "There was a problem in Deleting Document"
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Getting Exception To Delete Document")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "There was a problem in Deleting Document"
                )
            }
        }
    }

    // Matching MAUI UpdateProfilePage command
    // Success: "Profile Updated Sucessfully" (MAUI typo)
    // Error: "There was a problem in Updating Profile"
    fun saveProfile(documentFiles: List<File>) {
        val state = _uiState.value

        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                // Matching MAUI User object creation
                val user = UserDto(
                    id = state.userId,
                    displayName = state.name,
                    email = state.email.trim(),
                    username = state.email.trim(), // MAUI: Username = Emailid.Trim()
                    phoneNumber = state.phone,
                    address = state.searchAddress,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    workExperience = if (state.isCaregiver) state.workExperience else "",
                    skills = if (state.isCaregiver) state.skills else "",
                    dateOfBirth = state.dateOfBirthRaw
                )

                when (val result = accountRepository.updateProfile(user, documentFiles)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            successMessage = "Profile Updated Sucessfully"
                        )
                    }
                    is ApiResult.Error -> {
                        val errorMsg = result.message ?: "There was a problem in Updating Profile"
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            errorMessage = errorMsg
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Getting Exception To Update Profile")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "There was a problem in Updating Profile"
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

    private fun formatDobForDisplay(dob: String?): String {
        if (dob.isNullOrBlank()) return ""
        return try {
            val parsed = java.time.LocalDate.parse(dob.substringBefore("T"))
            parsed.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            dob
        }
    }
}
