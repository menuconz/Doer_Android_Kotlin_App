package nz.co.doer.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.GooglePlacesService
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.repository.ClientRepository
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ContractTypeOption(val id: Int, val name: String)

data class ReminderOption(val label: String, val offsetMinutes: Long)

data class AddShiftUiState(
    val projectName: String = "",
    val selectedClientIndex: Int = 0,
    val clients: List<ClientDto> = emptyList(),
    val isAllDay: Boolean = false,
    val durationFromDate: LocalDate = LocalDate.now(),
    val durationFromTime: LocalTime = LocalTime.of(12, 0),
    val durationToDate: LocalDate = LocalDate.now(),
    val durationToTime: LocalTime = LocalTime.of(13, 0),
    val searchAddress: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeSuggestions: List<PlacePrediction> = emptyList(),
    val showSuggestions: Boolean = false,
    val selectedContractTypeIndex: Int = 0,
    val selectedReminderIndex: Int = 0,
    val instructions: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AddShiftViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val clientRepository: ClientRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(AddShiftUiState())
    val uiState: StateFlow<AddShiftUiState> = _uiState.asStateFlow()

    val contractTypes = listOf(
        ContractTypeOption(0, "Select Contract Type"),
        ContractTypeOption(1, "To Be Confirmed"),
        ContractTypeOption(2, "Full Contract"),
        ContractTypeOption(3, "Supply Place And Finish"),
        ContractTypeOption(4, "Place And Finish"),
        ContractTypeOption(5, "Labour Supply"),
        ContractTypeOption(6, "Box Place And Finish"),
        ContractTypeOption(7, "Remedial"),
        ContractTypeOption(8, "Supply Place Finish And Cut"),
        ContractTypeOption(9, "Place Finish And Cut"),
        ContractTypeOption(10, "Other Services"),
        ContractTypeOption(11, "Meetings")
    )

    val reminderOptions = listOf(
        ReminderOption("None", 0),
        ReminderOption("5 minutes before", 5),
        ReminderOption("15 minutes before", 15),
        ReminderOption("30 minutes before", 30),
        ReminderOption("1 hour before", 60),
        ReminderOption("2 hours before", 120),
        ReminderOption("4 hours before", 240),
        ReminderOption("1 day before", 1440),
        ReminderOption("2 days before", 2880)
    )

    init {
        val dateStr = savedStateHandle.get<String>("date")
        val hourStr = savedStateHandle.get<String>("hour")
        val date = try {
            dateStr?.let { LocalDate.parse(it) } ?: LocalDate.now()
        } catch (e: Exception) { LocalDate.now() }
        val hour = hourStr?.toIntOrNull() ?: 12
        val fromTime = LocalTime.of(hour, 0)
        val toTime = LocalTime.of((hour + 1).coerceAtMost(23), 0)

        _uiState.value = _uiState.value.copy(
            durationFromDate = date,
            durationToDate = date,
            durationFromTime = fromTime,
            durationToTime = toTime
        )

        viewModelScope.launch {
            loadClients()
        }
    }

    private suspend fun loadClients() {
        when (val result = clientRepository.getAllClients()) {
            is ApiResult.Success -> {
                val selectClient = ClientDto(id = 0, name = "Select Client")
                _uiState.value = _uiState.value.copy(
                    clients = listOf(selectClient) + result.data
                )
            }
            is ApiResult.Error -> Timber.e("Failed to load clients: ${result.message}")
            is ApiResult.Loading -> {}
        }
    }

    fun onProjectNameChange(value: String) {
        _uiState.value = _uiState.value.copy(projectName = value)
    }

    fun onClientChange(index: Int) {
        _uiState.value = _uiState.value.copy(selectedClientIndex = index)
    }

    fun onAllDayChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(isAllDay = value)
    }

    fun onDurationFromDateChange(date: LocalDate) {
        _uiState.value = _uiState.value.copy(durationFromDate = date)
    }

    fun onDurationFromTimeChange(time: LocalTime) {
        _uiState.value = _uiState.value.copy(durationFromTime = time)
    }

    fun onDurationToDateChange(date: LocalDate) {
        _uiState.value = _uiState.value.copy(durationToDate = date)
    }

    fun onDurationToTimeChange(time: LocalTime) {
        _uiState.value = _uiState.value.copy(durationToTime = time)
    }

    fun onSearchAddressChange(value: String) {
        _uiState.value = _uiState.value.copy(searchAddress = value)
        searchJob?.cancel()
        if (value.length >= 3) {
            searchJob = viewModelScope.launch {
                delay(300)
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

    fun onContractTypeChange(index: Int) {
        _uiState.value = _uiState.value.copy(selectedContractTypeIndex = index)
    }

    fun onReminderChange(index: Int) {
        _uiState.value = _uiState.value.copy(selectedReminderIndex = index)
    }

    fun onInstructionsChange(value: String) {
        _uiState.value = _uiState.value.copy(instructions = value)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun createShift() {
        val state = _uiState.value

        // Validation
        if (state.address.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter the job address")
            return
        }

        val fromDateTime: String
        val toDateTime: String

        if (state.isAllDay) {
            fromDateTime = "${state.durationFromDate}T00:00:00"
            toDateTime = "${state.durationToDate}T23:59:59"
        } else {
            fromDateTime = "${state.durationFromDate}T${state.durationFromTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
            toDateTime = "${state.durationToDate}T${state.durationToTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
        }

        if (toDateTime <= fromDateTime) {
            _uiState.value = state.copy(errorMessage = "Please ensure Duration To is later than Duration From")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val now = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
            val selectedClient = state.clients.getOrNull(state.selectedClientIndex)
            val clientId = if (selectedClient != null && selectedClient.id > 0) selectedClient.id else null
            val contractTypeId = contractTypes.getOrNull(state.selectedContractTypeIndex)?.id
            val contractType = if (contractTypeId != null && contractTypeId > 0) contractTypeId else null
            val reminder = reminderOptions.getOrNull(state.selectedReminderIndex)
            val isReminderScheduled = reminder != null && reminder.offsetMinutes > 0

            val shift = ShiftDto(
                userId = userId,
                projectName = state.projectName,
                durationFrom = fromDateTime,
                durationTo = toDateTime,
                address = state.address,
                latitude = if (state.latitude != 0.0) state.latitude else null,
                longitude = if (state.longitude != 0.0) state.longitude else null,
                instructions = state.instructions,
                statusId = 1,
                contractType = contractType,
                invoiceStatus = 1,
                isAllDay = state.isAllDay,
                isReminderScheduled = isReminderScheduled,
                reminderOffset = if (isReminderScheduled) {
                    // Convert total minutes → HH:mm:ss (minutes/seconds must stay 0-59 for C# TimeSpan.Parse)
                    val totalMin = reminder!!.offsetMinutes
                    String.format("%02d:%02d:00", totalMin / 60, totalMin % 60)
                } else null,
                clientId = clientId,
                createdBy = userId,
                createdDate = now,
                modifiedBy = userId,
                modifiedDate = now
            )

            when (val result = shiftRepository.createShift(shift)) {
                is ApiResult.Success -> {
                    Timber.d("Shift created successfully: ${result.data.id}")
                    _uiState.value = _uiState.value.copy(isSubmitting = false, isSuccess = true)
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to create shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }
}
