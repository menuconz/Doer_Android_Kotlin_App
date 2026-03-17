package nz.co.doer.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.GooglePlacesService
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class RegisterContractorUiState(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val dateOfBirth: String = "",
    val searchAddress: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val documents: List<File> = emptyList(),
    val placeSuggestions: List<PlacePrediction> = emptyList(),
    val showSuggestions: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class RegisterContractorViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val firebaseMessaging: FirebaseMessaging,
    private val googlePlacesService: GooglePlacesService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterContractorUiState())
    val uiState: StateFlow<RegisterContractorUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onNameChange(value: String) { _uiState.value = _uiState.value.copy(fullName = value, nameError = null) }
    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, emailError = null) }
    fun onPhoneChange(value: String) { _uiState.value = _uiState.value.copy(phone = value) }
    fun onDateOfBirthChange(value: String) { _uiState.value = _uiState.value.copy(dateOfBirth = value) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, passwordError = null) }
    fun onConfirmPasswordChange(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value, confirmPasswordError = null) }

    fun onSearchAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(searchAddress = value)
        if (value.length >= 3 && value != _uiState.value.address) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                delay(300) // debounce
                val predictions = googlePlacesService.getPlacesByText(value)
                _uiState.value = _uiState.value.copy(
                    placeSuggestions = predictions,
                    showSuggestions = predictions.isNotEmpty()
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(showSuggestions = false, placeSuggestions = emptyList())
        }
    }

    fun onPlaceSelected(prediction: PlacePrediction) {
        viewModelScope.launch {
            val place = googlePlacesService.getPlaceDetails(prediction.placeId)
            if (place != null) {
                _uiState.value = _uiState.value.copy(
                    address = place.address,
                    searchAddress = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    showSuggestions = false,
                    placeSuggestions = emptyList()
                )
            }
        }
    }

    fun addDocument(file: File) {
        _uiState.value = _uiState.value.copy(documents = _uiState.value.documents + file)
    }

    fun removeDocument(file: File) {
        _uiState.value = _uiState.value.copy(documents = _uiState.value.documents - file)
    }

    fun register() {
        val state = _uiState.value
        var hasError = false

        if (state.fullName.isBlank()) {
            _uiState.value = _uiState.value.copy(nameError = "Name is required"); hasError = true
        }
        if (state.email.isBlank()) {
            _uiState.value = _uiState.value.copy(emailError = "Email is required"); hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.value = _uiState.value.copy(emailError = "Invalid email format"); hasError = true
        }
        if (state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = "Password is required"); hasError = true
        } else if (state.password.length < 6) {
            _uiState.value = _uiState.value.copy(passwordError = "Password must be at least 6 characters"); hasError = true
        }
        if (state.confirmPassword != state.password) {
            _uiState.value = _uiState.value.copy(confirmPasswordError = "Passwords do not match"); hasError = true
        }
        if (hasError) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // Check if email exists
                when (val emailCheck = accountRepository.checkEmailExists(state.email.trim())) {
                    is ApiResult.Success -> {
                        if (emailCheck.data) {
                            _uiState.value = _uiState.value.copy(isLoading = false, emailError = "Email already registered")
                            return@launch
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = emailCheck.message)
                        return@launch
                    }
                    is ApiResult.Loading -> {}
                }

                val deviceToken = try { firebaseMessaging.token.await() } catch (e: Exception) { "" }

                val user = UserDto(
                    displayName = state.fullName.trim(),
                    email = state.email.trim(),
                    password = state.password,
                    phoneNumber = state.phone.trim(),
                    address = state.searchAddress,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    dateOfBirthString = state.dateOfBirth,
                    deviceToken = deviceToken,
                    deviceTypeId = 2
                )

                when (val result = accountRepository.registerContractor(user, state.documents)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            successMessage = "Thank you for registering with us! Your account as Contractor has been successfully created and is pending approval from an administrator. You will receive an email notification once your account has been approved. Thank you for your patience."
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Registration failed")
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Registration failed. Please try again.")
            }
        }
    }
}
