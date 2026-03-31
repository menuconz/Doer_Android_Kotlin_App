package nz.co.doer.ui.calendar

import androidx.lifecycle.SavedStateHandle
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
import nz.co.doer.data.remote.dto.ClockLocationType
import nz.co.doer.data.remote.dto.DoerTrackingState
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.remote.dto.ShiftSubItemDto
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import nz.co.doer.data.repository.ClientRepository
import nz.co.doer.data.repository.ShiftRepository
import nz.co.doer.service.TrackingManager
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ShiftDetailsUiState(
    val shift: ShiftDto? = null,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
    val isDeleted: Boolean = false,
    val successMessage: String? = null,

    // Status display
    val statusMessage: String = "",
    val statusColor: Long = 0xFF777777,

    // H&S Form display
    val hsFormText: String = "",
    val hsFormColor: Long = 0xFFC4C4C4,
    val showHsForm: Boolean = false,

    // Contract / Invoice display
    val contractTypeText: String = "",
    val invoiceStatusText: String = "",

    // Formatted dates
    val durationFromFormatted: String = "",
    val durationToFormatted: String = "",
    val shiftStartTimeFormatted: String = "",
    val shiftEndTimeFormatted: String = "",
    val showShiftStartTime: Boolean = false,
    val showShiftEndTime: Boolean = false,

    // Role flags
    val isManager: Boolean = false,
    val isCaregiver: Boolean = false,
    val isAdmin: Boolean = false,
    val isCustomer: Boolean = false,

    // Button visibility
    val editShiftButton: Boolean = false,
    val isDeleteButton: Boolean = false,
    val isAllDayEditable: Boolean = false,
    val isAllDay: Boolean = false,
    val quotationButton: Boolean = false,
    val viewQuotationsButton: Boolean = false,
    val startButton: Boolean = false,
    val rejectButton: Boolean = false,
    val endButton: Boolean = false,
    val completeButton: Boolean = false,
    val reviewsButton: Boolean = false,
    val showFeedback: Boolean = false,

    // Contractor details (shown to manager/admin/customer)
    val contractorName: String = "",
    val contractorEmail: String = "",
    val contractorPhone: String = "",

    // Manager details (shown to admin)
    val managerName: String = "",
    val managerEmail: String = "",
    val managerPhone: String = "",

    // Managers list for admin re-assignment
    val managersList: List<UserDto> = emptyList(),
    val selectedManagerId: String? = null,
    val originalManagerId: String = "",

    // Reminder
    val reminderOptions: List<ReminderOption> = defaultReminderOptions,
    val selectedReminderLabel: String = "None",
    val canEditReminder: Boolean = false,
    val canViewReminderOnly: Boolean = false,
    val hasReminderSet: Boolean = false,
    val showReminderSection: Boolean = true,
    val navigateToFeedbackShiftId: Int? = null,
    val navigateToReviewsShiftId: Int? = null,

    // Tracking state
    val trackingState: DoerTrackingState = DoerTrackingState.IDLE,
    val showNavigateButton: Boolean = false,
    val showClockInButton: Boolean = false,
    val showClockOutButton: Boolean = false,
    val markCompleteButton: Boolean = false,
    val isTrackingActive: Boolean = false,
    val selectedClockLocationType: ClockLocationType = ClockLocationType.SITE,
    val needsLocationPermission: Boolean = false,

    // Stage selection for clock-in
    val availableStages: List<ShiftSubItemDto> = emptyList(),
    val selectedStageName: String = "",

    // Multi-site warning
    val showMultiSiteWarning: Boolean = false,
    val activeShiftProjectName: String = ""
) {
    companion object {
        val defaultReminderOptions = listOf(
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
    }
}

@HiltViewModel
class ShiftDetailsViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val accountRepository: AccountRepository,
    private val clientRepository: ClientRepository,
    private val preferencesManager: PreferencesManager,
    private val trackingManager: TrackingManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftDetailsUiState())
    val uiState: StateFlow<ShiftDetailsUiState> = _uiState.asStateFlow()

    private val shiftId: Int = savedStateHandle.get<String>("shiftId")?.toIntOrNull() ?: 0

    private val displayDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.ENGLISH)

    // Parse formats the API may return
    private val parseFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)
    )

    init {
        loadShiftDetails()
        observeTrackingState()
    }

    private fun observeTrackingState() {
        viewModelScope.launch {
            trackingManager.trackingState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    trackingState = state,
                    isTrackingActive = state != DoerTrackingState.IDLE && state != DoerTrackingState.CLOCKED_OUT
                )
            }
        }
    }

    fun refresh() {
        loadShiftDetails()
    }

    private fun loadShiftDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Gather role info from preferences
            val prefIsAdmin = preferencesManager.isAdmin.first()
            val prefIsManager = preferencesManager.isManager.first()
            val prefIsCaregiver = preferencesManager.isCaregiver.first()
            val prefIsCustomer = preferencesManager.isCustomer.first()
            val currentUserId = preferencesManager.getUserId()
            val currentUserFullName = preferencesManager.fullName.first()

            when (val result = shiftRepository.getShiftById(shiftId)) {
                is ApiResult.Success -> {
                    var shift = result.data

                    // Enrich clientName from client API if null but clientId present
                    if (shift.clientName.isNullOrBlank() && shift.clientId != null && shift.clientId > 0) {
                        when (val clientResult = clientRepository.getAllClients()) {
                            is ApiResult.Success -> {
                                val clientName = clientResult.data.find { it.id == shift.clientId }?.name
                                if (!clientName.isNullOrBlank()) {
                                    shift = shift.copy(clientName = clientName)
                                }
                            }
                            else -> { /* ignore */ }
                        }
                    }

                    val statusId = shift.statusId

                    // --- Status message & color (matches MAUI DayDetailViewModel colors) ---
                    val (statusMsg, statusClr) = getStatusDisplay(statusId, shift.hasQuotations)

                    // --- H&S Form status (null when hsForms is null, matching MAUI) ---
                    val hsDisplay = getHsFormDisplay(shift.hsForms)
                    val hsText = hsDisplay?.first ?: ""
                    val hsClr = hsDisplay?.second ?: 0xFFC4C4C4

                    // --- Contract type & invoice status ---
                    val contractText = getContractTypeText(shift.contractType)
                    val invoiceText = getInvoiceStatusText(shift.invoiceStatus)

                    // --- Date formatting ---
                    val durationFromFmt = formatDateTime(shift.durationFrom)
                    val durationToFmt = formatDateTime(shift.durationTo)
                    val startTimeFmt = if (!shift.shiftStartTime.isNullOrBlank()) formatDateTime(shift.shiftStartTime) else ""
                    val endTimeFmt = if (!shift.shiftEndTime.isNullOrBlank()) formatDateTime(shift.shiftEndTime) else ""

                    // --- Show shift start/end times ---
                    val showStart = statusId == 3 || statusId == 4 || statusId == 6
                    val showEnd = statusId == 4 || statusId == 6

                    // --- Role-based visibility ---
                    val editShift = !prefIsCaregiver &&
                            statusId != 4 && statusId != 6 && statusId != 5
                    val deleteBtn = prefIsManager || prefIsAdmin
                    val allDayEditable = prefIsManager || prefIsAdmin
                    val quotationBtn = prefIsCaregiver && statusId == 1
                    val viewQuotationsBtn = (prefIsManager || prefIsAdmin) && statusId == 1 && shift.hasQuotations
                    val isManagerSection = (prefIsManager || prefIsCustomer || prefIsAdmin) && statusId != 1
                    val startBtn = prefIsCaregiver && statusId == 2
                    val rejectBtn = prefIsCaregiver && (statusId == 2 || statusId == 3)
                    val endBtn = prefIsCaregiver && statusId == 3
                    val completeBtn = prefIsManager && statusId == 4
                    val reviewsBtn = prefIsCaregiver && statusId == 6
                    val feedback = statusId == 6 && shift.feedback.isNotBlank()

                    // --- Reminder ---
                    val reminderOptions = ShiftDetailsUiState.defaultReminderOptions
                    val selectedLabel = resolveReminderLabel(shift.reminderOffset, reminderOptions)
                    val hasReminder = shift.isReminderScheduled

                    // Editable: Manager/Admin when (editShift || status==Created || status==Accepted)
                    //           OR Caregiver when (status==Accepted || status==Ongoing)
                    val canEdit = if (prefIsManager || prefIsAdmin) {
                        editShift || statusId == 1 || statusId == 2
                    } else if (prefIsCaregiver) {
                        statusId == 2 || statusId == 3
                    } else {
                        false
                    }
                    val canViewOnly = !canEdit && hasReminder

                    // Tracking button visibility
                    val showNavigate = prefIsCaregiver && statusId >= 2 && statusId <= 3
                            && shift.latitude != null && shift.longitude != null
                            && shift.latitude != 0.0 && shift.longitude != 0.0
                    // Show Clock In when: Accepted (first time) OR Ongoing but not currently clocked in (multi-day)
                    val showClockIn = prefIsCaregiver
                            && (statusId == 2 || statusId == 3)
                            && trackingManager.trackingState.value == DoerTrackingState.IDLE
                    val showClockOut = prefIsCaregiver
                            && trackingManager.activeShiftId.value == shift.id
                            && trackingManager.trackingState.value != DoerTrackingState.IDLE
                            && trackingManager.trackingState.value != DoerTrackingState.CLOCKED_OUT
                    // Show Mark Complete when: Ongoing AND not currently clocked in at this shift
                    val showMarkComplete = prefIsCaregiver && statusId == 3
                            && (trackingManager.activeShiftId.value != shift.id
                            || trackingManager.trackingState.value == DoerTrackingState.IDLE)

                    _uiState.value = _uiState.value.copy(
                        shift = shift,
                        isLoading = false,
                        statusMessage = statusMsg,
                        statusColor = statusClr,
                        hsFormText = hsText,
                        hsFormColor = hsClr,
                        showHsForm = hsDisplay != null,
                        contractTypeText = contractText,
                        invoiceStatusText = invoiceText,
                        durationFromFormatted = durationFromFmt,
                        durationToFormatted = durationToFmt,
                        shiftStartTimeFormatted = startTimeFmt,
                        shiftEndTimeFormatted = endTimeFmt,
                        showShiftStartTime = showStart,
                        showShiftEndTime = showEnd,
                        isManager = isManagerSection,
                        isCaregiver = prefIsCaregiver,
                        isAdmin = prefIsAdmin,
                        isCustomer = prefIsCustomer,
                        editShiftButton = editShift,
                        isDeleteButton = deleteBtn,
                        isAllDayEditable = allDayEditable,
                        isAllDay = shift.isAllDay,
                        quotationButton = quotationBtn,
                        viewQuotationsButton = viewQuotationsBtn,
                        startButton = startBtn,
                        rejectButton = rejectBtn,
                        endButton = endBtn,
                        completeButton = completeBtn,
                        reviewsButton = reviewsBtn,
                        showFeedback = feedback,
                        reminderOptions = reminderOptions,
                        selectedReminderLabel = selectedLabel,
                        canEditReminder = canEdit,
                        canViewReminderOnly = canViewOnly,
                        hasReminderSet = hasReminder,
                        selectedManagerId = shift.userId?.ifBlank { null },
                        originalManagerId = shift.userId ?: "",
                        showNavigateButton = showNavigate,
                        showClockInButton = showClockIn,
                        showClockOutButton = showClockOut,
                        markCompleteButton = showMarkComplete,
                        trackingState = trackingManager.trackingState.value,
                        isTrackingActive = trackingManager.trackingState.value != DoerTrackingState.IDLE
                                && trackingManager.trackingState.value != DoerTrackingState.CLOCKED_OUT
                    )

                    // Load available stages (sub-items) for stage selection on clock-in
                    if (prefIsCaregiver && (statusId == 2 || statusId == 3)) {
                        var stages = shift.shiftSubItems ?: emptyList()
                        // If sub-items not included in shift response, fetch separately
                        if (stages.isEmpty()) {
                            when (val subResult = shiftRepository.getSubItemsByJobId(shift.id)) {
                                is ApiResult.Success -> stages = subResult.data
                                else -> {}
                            }
                        }
                        _uiState.value = _uiState.value.copy(
                            availableStages = stages,
                            selectedStageName = stages.firstOrNull()?.subitem ?: ""
                        )
                    }

                    // Check multi-site: if Doer is already clocked in at another shift
                    val activeTrackingShiftId = trackingManager.activeShiftId.value
                    if (activeTrackingShiftId != null && activeTrackingShiftId != shift.id
                        && trackingManager.trackingState.value != DoerTrackingState.IDLE
                        && trackingManager.trackingState.value != DoerTrackingState.CLOCKED_OUT
                    ) {
                        _uiState.value = _uiState.value.copy(
                            showMultiSiteWarning = true,
                            showClockInButton = false,
                            activeShiftProjectName = "Shift #$activeTrackingShiftId"
                        )
                    }

                    // Fetch contractor details if manager/admin/customer and status != Created
                    if (isManagerSection && shift.caregiverId.isNotBlank()) {
                        loadContractorDetails(shift.caregiverId)
                    }

                    // Fetch manager details and managers list if admin
                    if (prefIsAdmin) {
                        if (!shift.userId.isNullOrBlank()) {
                            loadManagerDetails(shift.userId)
                        }
                        loadManagersList()
                    }
                }

                is ApiResult.Error -> {
                    Timber.e("Failed to load shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }

                is ApiResult.Loading -> {}
            }
        }
    }

    private suspend fun loadContractorDetails(caregiverId: String) {
        when (val result = accountRepository.getUser(caregiverId)) {
            is ApiResult.Success -> {
                val user = result.data
                _uiState.value = _uiState.value.copy(
                    contractorName = user.displayName,
                    contractorEmail = user.email,
                    contractorPhone = user.phoneNumber
                )
            }
            is ApiResult.Error -> {
                Timber.e("Failed to load contractor details: ${result.message}")
            }
            is ApiResult.Loading -> {}
        }
    }

    private suspend fun loadManagerDetails(userId: String) {
        when (val result = accountRepository.getUser(userId)) {
            is ApiResult.Success -> {
                val user = result.data
                _uiState.value = _uiState.value.copy(
                    managerName = user.displayName,
                    managerEmail = user.email,
                    managerPhone = user.phoneNumber
                )
            }
            is ApiResult.Error -> {
                Timber.e("Failed to load manager details: ${result.message}")
            }
            is ApiResult.Loading -> {}
        }
    }

    private suspend fun loadManagersList() {
        when (val result = accountRepository.getAllManagers()) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(managersList = result.data)
            }
            is ApiResult.Error -> {
                Timber.e("Failed to load managers list: ${result.message}")
            }
            is ApiResult.Loading -> {}
        }
    }

    // ========== Actions ==========

    fun selectClockLocationType(type: ClockLocationType) {
        _uiState.value = _uiState.value.copy(selectedClockLocationType = type)
    }

    fun selectStage(stageName: String) {
        _uiState.value = _uiState.value.copy(selectedStageName = stageName)
    }

    fun dismissMultiSiteWarning() {
        _uiState.value = _uiState.value.copy(showMultiSiteWarning = false)
    }

    /** Clock out from the other active shift so Doer can clock in here */
    fun clockOutOtherShift() {
        trackingManager.clockOut()
        _uiState.value = _uiState.value.copy(
            showMultiSiteWarning = false,
            showClockInButton = true
        )
    }

    /**
     * Clock in with tracking. Starts the state machine + geofencing + location service.
     * @param currentLatitude Doer's current GPS latitude
     * @param currentLongitude Doer's current GPS longitude
     */
    fun clockInWithTracking(currentLatitude: Double, currentLongitude: Double) {
        val shift = _uiState.value.shift ?: return
        val locationType = _uiState.value.selectedClockLocationType

        // Start tracking state machine
        trackingManager.clockIn(
            shiftId = shift.id,
            locationType = locationType,
            siteLatitude = shift.latitude,
            siteLongitude = shift.longitude,
            currentLatitude = currentLatitude,
            currentLongitude = currentLongitude,
            projectName = shift.projectName.ifBlank { "Site #${shift.id}" }
        )

        // Also update shift status on the server (existing logic)
        startShift()

        // Refresh UI to show Clock Out button instead of Clock In
        refresh()
    }

    /**
     * Clock out with tracking. Stops all tracking services.
     * @param currentLatitude Doer's current GPS latitude
     * @param currentLongitude Doer's current GPS longitude
     * @param reasonCode Optional reason for manual clock-out
     */
    fun clockOutWithTracking(
        currentLatitude: Double = 0.0,
        currentLongitude: Double = 0.0,
        reasonCode: String? = null
    ) {
        trackingManager.clockOut(currentLatitude, currentLongitude, reasonCode)

        // Don't change shift status — stays Ongoing (3) for multi-day projects
        // Contractor uses "Mark Complete" when all work is done
        _uiState.value = _uiState.value.copy(
            successMessage = "Clocked out successfully"
        )
        refresh()
    }

    /**
     * Contractor marks the shift as complete (all work done across all days).
     * Sets StatusId = 4 (Completed). Manager then reviews and finishes (StatusId = 6).
     */
    fun markShiftComplete() {
        val shift = _uiState.value.shift ?: return
        _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val nowUtc = nz.co.doer.util.Constants.nowNz()
            val updated = shift.copy(
                modifiedBy = userId,
                modifiedDate = nowUtc,
                shiftEndTime = nowUtc,
                statusId = 4
            )
            when (val result = shiftRepository.updateShift(updated)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Shift marked as complete"
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to mark shift complete: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun requestLocationPermission() {
        _uiState.value = _uiState.value.copy(needsLocationPermission = true)
    }

    fun onLocationPermissionHandled() {
        _uiState.value = _uiState.value.copy(needsLocationPermission = false)
    }

    fun startShift() {
        val shift = _uiState.value.shift ?: return
        // Only update status to Ongoing on FIRST clock-in (Accepted → Ongoing)
        // For subsequent clock-ins (already Ongoing), just record the clock event
        if (shift.statusId == 3) {
            // Already Ongoing — no need to update shift status again
            _uiState.value = _uiState.value.copy(successMessage = "Clocked in")
            return
        }
        _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val nowUtc = nz.co.doer.util.Constants.nowNz()
            val updated = shift.copy(
                modifiedBy = userId,
                modifiedDate = nowUtc,
                shiftStartTime = nowUtc,
                statusId = 3
            )
            when (val result = shiftRepository.updateShift(updated)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Shift started"
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to start shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun endShift() {
        val shift = _uiState.value.shift ?: return
        _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val nowUtc = nz.co.doer.util.Constants.nowNz()
            val updated = shift.copy(
                modifiedBy = userId,
                modifiedDate = nowUtc,
                shiftEndTime = nowUtc,
                statusId = 4
            )
            when (val result = shiftRepository.updateShift(updated)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Shift ended"
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to end shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun rejectShift() {
        val shift = _uiState.value.shift ?: return
        _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val nowUtc = nz.co.doer.util.Constants.nowNz()
            val updated = shift.copy(
                modifiedBy = userId,
                modifiedDate = nowUtc,
                statusId = 5
            )
            when (val result = shiftRepository.updateShift(updated)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Shift marked as not completed"
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to reject shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun deleteShift() {
        _uiState.value = _uiState.value.copy(isUpdating = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = shiftRepository.deleteJob(shiftId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        isDeleted = true
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to delete shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun completeShift() {
        _uiState.value = _uiState.value.copy(navigateToFeedbackShiftId = shiftId)
    }

    fun onFeedbackNavigated() {
        _uiState.value = _uiState.value.copy(navigateToFeedbackShiftId = null)
    }

    fun sendQuotes() {
        // Navigate to SendQuote screen - not implemented yet
        _uiState.value = _uiState.value.copy(
            successMessage = "Send Quote screen not yet implemented"
        )
    }

    fun viewReviews() {
        _uiState.value = _uiState.value.copy(navigateToReviewsShiftId = shiftId)
    }

    fun onReviewsNavigated() {
        _uiState.value = _uiState.value.copy(navigateToReviewsShiftId = null)
    }

    fun updateIsAllDay(isAllDay: Boolean) {
        _uiState.value = _uiState.value.copy(isAllDay = isAllDay)
    }

    fun selectManager(managerId: String) {
        _uiState.value = _uiState.value.copy(selectedManagerId = managerId)
    }

    /**
     * Save manager reassignment and allDay changes via updateShiftsExtraHour.
     */
    fun updateShift() {
        val shift = _uiState.value.shift ?: return
        val state = _uiState.value
        _uiState.value = state.copy(isUpdating = true, errorMessage = null)
        viewModelScope.launch {
            val currentUserId = preferencesManager.getUserId()
            // Only change isAllDay and modifiedBy; only touch userId if admin explicitly picked a manager
            var updated = shift.copy(
                isAllDay = state.isAllDay,
                modifiedBy = currentUserId
            )
            if (state.selectedManagerId != null) {
                updated = updated.copy(userId = state.selectedManagerId)
            }
            when (val result = shiftRepository.updateShiftsExtraHour(updated)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Shift updated"
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to update shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun selectReminder(label: String) {
        _uiState.value = _uiState.value.copy(selectedReminderLabel = label)
    }

    /**
     * Add or clear a reminder via the addTaskReminder API.
     */
    fun addReminder() {
        val shift = _uiState.value.shift ?: return
        val state = _uiState.value
        val selectedOption = state.reminderOptions.find { it.label == state.selectedReminderLabel }
            ?: return

        _uiState.value = state.copy(isUpdating = true, errorMessage = null)
        viewModelScope.launch {
            val updated: ShiftDto
            if (selectedOption.offsetMinutes == 0L) {
                // None selected - clear reminder
                updated = shift.copy(
                    isReminderScheduled = false,
                    reminderOffset = null,
                    reminderTime = null
                )
            } else {
                // Calculate reminder time: durationFrom minus offset
                val reminderOffsetStr = minutesToTimeSpan(selectedOption.offsetMinutes)
                val reminderTime = calculateReminderTime(
                    shift.durationFrom,
                    selectedOption.offsetMinutes
                )
                updated = shift.copy(
                    isReminderScheduled = true,
                    reminderOffset = reminderOffsetStr,
                    reminderTime = reminderTime
                )
            }

            when (val result = shiftRepository.addTaskReminder(updated)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        successMessage = "Reminder updated",
                        hasReminderSet = updated.isReminderScheduled,
                        shift = result.data
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to update reminder: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    // ========== Helpers ==========

    private fun getStatusDisplay(statusId: Int, hasQuotations: Boolean = false): Pair<String, Long> {
        // Colors match MAUI DayDetailViewModel StatusColor assignments
        return when (statusId) {
            1 -> if (hasQuotations) "Quoted" to 0xFF007AFF else "Created" to 0xFFFF9500
            2 -> "Accepted" to 0xFF9D50DD
            3 -> "Started" to 0xFF00C875
            4 -> "End" to 0xFF74AFCC
            5 -> "Not Completed" to 0xFFFF3B30
            6 -> "Completed" to 0xFFFFCB00
            else -> "Unknown" to 0xFFC4C4C4
        }
    }

    private fun getHsFormDisplay(hsForms: Int?): Pair<String, Long>? {
        // Returns null when hsForms is null (MAUI shows nothing in this case)
        return when (hsForms) {
            0 -> "No H&S" to 0xFFC4C4C4
            1 -> "SSSP" to 0xFF007AFF
            2 -> "JSA" to 0xFFFF9500
            3 -> "Take 5" to 0xFFFF3B30
            4 -> "Done" to 0xFF34C759
            5 -> "Missing H&S" to 0xFF808080
            else -> null
        }
    }

    private fun getContractTypeText(contractType: Int?): String {
        return when (contractType) {
            1 -> "To Be Confirmed"
            2 -> "Full Contract"
            3 -> "Supply Place And Finish"
            4 -> "Place And Finish"
            5 -> "Labour Supply"
            6 -> "Box Place And Finish"
            7 -> "Remedial"
            8 -> "Supply Place Finish And Cut"
            9 -> "Place Finish And Cut"
            10 -> "Other Services"
            11 -> "Meetings"
            else -> "Not Set"
        }
    }

    private fun getInvoiceStatusText(invoiceStatus: Int?): String {
        return when (invoiceStatus) {
            1 -> "Not Yet Created"
            2 -> "To Be Invoiced"
            3 -> "Invoice Drafted"
            4 -> "Invoice Sent"
            else -> "Not Set"
        }
    }

    private fun formatDateTime(dateStr: String): String {
        if (dateStr.isBlank()) return ""
        for (formatter in parseFormatters) {
            try {
                val parsed = LocalDateTime.parse(dateStr.trim(), formatter)
                return parsed.format(displayDateFormat)
            } catch (_: Exception) {
                // try next
            }
        }
        // Fallback: try trimming fractional seconds of arbitrary length
        try {
            val trimmed = dateStr.replace(Regex("\\.\\d+$"), "")
            val parsed = LocalDateTime.parse(trimmed, parseFormatters[0])
            return parsed.format(displayDateFormat)
        } catch (_: Exception) {
            // ignore
        }
        return dateStr
    }

    /**
     * Parse reminderOffset string (e.g. "00:05:00" for 5 minutes, "1.00:00:00" for 1 day)
     * and match it to a reminder option label.
     */
    private fun resolveReminderLabel(
        reminderOffset: String?,
        options: List<ReminderOption>
    ): String {
        if (reminderOffset.isNullOrBlank()) return "None"
        val totalMinutes = parseTimeSpanToMinutes(reminderOffset)
        return options.find { it.offsetMinutes == totalMinutes }?.label ?: "None"
    }

    /**
     * Parse a .NET TimeSpan string to total minutes.
     * Formats: "HH:mm:ss", "d.HH:mm:ss", "HH:mm:ss.fffffff"
     */
    private fun parseTimeSpanToMinutes(timeSpan: String): Long {
        try {
            val cleaned = timeSpan.trim()
            var days = 0L
            var timePart = cleaned

            // Check for days portion: "d.HH:mm:ss"
            if (cleaned.contains('.')) {
                val dotIndex = cleaned.indexOf('.')
                // Could be "1.02:03:04" (days.time) or "00:05:00.0000000" (time.fractional)
                val beforeDot = cleaned.substring(0, dotIndex)
                val afterDot = cleaned.substring(dotIndex + 1)

                if (beforeDot.contains(':')) {
                    // Fractional seconds: "00:05:00.0000000" -> time is before dot
                    timePart = beforeDot
                } else {
                    // Days: "1.02:03:04"
                    days = beforeDot.toLongOrNull() ?: 0L
                    timePart = afterDot
                    // Remove any trailing fractional seconds from timePart
                    val fracIndex = timePart.indexOf('.')
                    if (fracIndex >= 0) {
                        timePart = timePart.substring(0, fracIndex)
                    }
                }
            }

            val parts = timePart.split(':')
            val hours = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val minutes = parts.getOrNull(1)?.toLongOrNull() ?: 0L

            return days * 1440 + hours * 60 + minutes
        } catch (e: Exception) {
            Timber.e("Failed to parse reminderOffset: $timeSpan")
            return 0L
        }
    }

    /**
     * Convert offset minutes to a .NET TimeSpan string (e.g. "00:05:00").
     */
    private fun minutesToTimeSpan(totalMinutes: Long): String {
        val days = totalMinutes / 1440
        val remaining = totalMinutes % 1440
        val hours = remaining / 60
        val mins = remaining % 60
        return if (days > 0) {
            String.format(Locale.US, "%d.%02d:%02d:00", days, hours, mins)
        } else {
            String.format(Locale.US, "%02d:%02d:00", hours, mins)
        }
    }

    /**
     * Calculate the reminder time by subtracting offsetMinutes from the shift's durationFrom.
     */
    private fun calculateReminderTime(durationFrom: String, offsetMinutes: Long): String? {
        for (formatter in parseFormatters) {
            try {
                val parsed = LocalDateTime.parse(durationFrom.trim(), formatter)
                val reminderDt = parsed.minusMinutes(offsetMinutes)
                return reminderDt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: Exception) {
                // try next
            }
        }
        // Fallback for arbitrary fractional seconds
        try {
            val trimmed = durationFrom.replace(Regex("\\.\\d+$"), "")
            val parsed = LocalDateTime.parse(trimmed, parseFormatters[0])
            val reminderDt = parsed.minusMinutes(offsetMinutes)
            return reminderDt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: Exception) {
            // ignore
        }
        return null
    }
}
