package nz.co.doer.ui.mainleads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.GooglePlacesService
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.remote.dto.ContractType
import nz.co.doer.data.remote.dto.HSRequiredStatus
import nz.co.doer.data.remote.dto.Invoice
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.remote.dto.ShiftStatus
import nz.co.doer.data.remote.dto.ShiftSubItemDto
import nz.co.doer.data.remote.dto.SubItemStatus
import nz.co.doer.data.repository.ClientRepository
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

// ──────────────── Data models ────────────────

data class JobRowItem(
    val shift: ShiftDto,
    val statusDisplayText: String,
    val statusColor: Long,
    val invoiceDisplayText: String,
    val invoiceColor: Long,
    val contractTypeDisplayText: String,
    val contractTypeColor: Long,
    val hsFormText: String,
    val hsFormColor: Long,
    val subItems: List<ShiftSubItemDto> = emptyList(),
    val isExpanded: Boolean = false,
    val isOwner: Boolean = false,
    val isAddSubItem: Boolean = false,
    val newSubItemName: String = ""
)

enum class FilterColumnType { ItemColumn, SubItemColumn }

data class FilterColumnOption(
    val displayName: String,
    val propertyName: String,
    val columnType: FilterColumnType = FilterColumnType.ItemColumn,
    val icon: String = "",
    val availableConditions: List<String> = emptyList()
)

data class FilterRow(
    val id: Long = System.nanoTime(),
    val selectedColumn: FilterColumnOption? = null,
    val selectedCondition: String = "",
    val value: String = "",
    val isApplied: Boolean = false
) {
    val isComplete: Boolean
        get() = selectedColumn != null && selectedCondition.isNotBlank() &&
                (value.isNotBlank() || selectedCondition == "is empty" || selectedCondition == "is not empty")
}

data class KanbanColumn(
    val title: String,
    val headerColor: Long,
    val items: List<JobRowItem>
)

// Edit dialog types
enum class EditDialogType {
    None, TextEditor, DateTimePicker, ContractTypePicker, InvoiceStatusPicker,
    HSFormStatusPicker, SubItemHSPicker, SubItemStatusPicker, ClientPicker,
    AddressSearch, SubItemDatePicker
}

data class EditDialogState(
    val type: EditDialogType = EditDialogType.None,
    val title: String = "",
    val fieldName: String = "",
    val textValue: String = "",
    val selectedShiftId: Int = 0,
    val selectedSubItemId: Int = 0,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedHour: Int = 0,
    val selectedMinute: Int = 0
)

data class StatusOption(
    val name: String,
    val value: Int,
    val color: Long
)

data class MainLeadsJobsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val jobs: List<JobRowItem> = emptyList(),
    val currentMonth: Int = LocalDate.now().monthValue,
    val currentYear: Int = LocalDate.now().year,
    val monthDisplayText: String = "",
    val sortColumn: String = "",
    val sortAscending: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Role flags
    val isOwner: Boolean = false,
    val isCaregiver: Boolean = false,
    // View mode
    val selectedViewMode: String = "Main Table",
    val isListView: Boolean = true,
    val kanbanColumns: List<KanbanColumn> = emptyList(),
    // Filter
    val filterColumns: List<FilterColumnOption> = emptyList(),
    val pendingFilters: List<FilterRow> = emptyList(),
    val activeFilters: List<FilterRow> = emptyList(),
    val filterStatusText: String = "Showing all of 0 projects",
    val hasActiveFilters: Boolean = false,
    val hasPendingFilters: Boolean = false,
    val hasFiltersToApply: Boolean = false,
    val showEmptyState: Boolean = true,
    // Edit dialog
    val editDialog: EditDialogState = EditDialogState(),
    // Address search
    val addressSearchText: String = "",
    val placeSuggestions: List<PlacePrediction> = emptyList(),
    // Clients
    val clients: List<ClientDto> = emptyList(),
    // Pagination
    val canLoadMore: Boolean = true
)

@HiltViewModel
class MainLeadsJobsViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val clientRepository: ClientRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainLeadsJobsUiState())
    val uiState: StateFlow<MainLeadsJobsUiState> = _uiState.asStateFlow()

    private var originalJobs: List<JobRowItem> = emptyList()
    private var currentSkip: Int = 0
    private val pageSize: Int = 100
    private var addressSearchJob: Job? = null

    // ──────────────── Resume refresh guard ────────────────
    // Prevents a double-load on first launch (init already calls loadJobs).
    private var isFirstResume = true

    /**
     * Call this from the screen's ON_RESUME lifecycle event.
     * Refreshes job statuses silently when the user returns from another screen
     * (e.g. after a contractor marks a job as Started/Ended in ShiftDetails).
     */
    fun refreshOnResume() {
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        loadJobs()
    }

    init {
        viewModelScope.launch {
            val isManager = preferencesManager.isManager.first()
            val isAdmin = preferencesManager.isAdmin.first()
            val isCaregiver = preferencesManager.isCaregiver.first()
            val isOwner = isManager || isAdmin
            _uiState.value = _uiState.value.copy(
                isOwner = isOwner,
                isCaregiver = isCaregiver,
                filterColumns = buildFilterColumns()
            )
            loadClients()
            updateMonthDisplay()
            loadJobs()
        }
    }

    private suspend fun loadClients() {
        when (val result = clientRepository.getAllClients()) {
            is ApiResult.Success -> {
                val selectClient = ClientDto(id = 0, name = "Select Client")
                _uiState.value = _uiState.value.copy(clients = listOf(selectClient) + result.data)
            }
            is ApiResult.Error -> Timber.e("Failed to load clients: ${result.message}")
            is ApiResult.Loading -> {}
        }
    }

    private fun updateMonthDisplay() {
        val state = _uiState.value
        val monthName = java.time.Month.of(state.currentMonth)
            .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        _uiState.value = state.copy(monthDisplayText = "$monthName ${state.currentYear}")
    }

    fun previousMonth() {
        val state = _uiState.value
        var month = state.currentMonth - 1
        var year = state.currentYear
        if (month < 1) { month = 12; year-- }
        _uiState.value = state.copy(currentMonth = month, currentYear = year)
        updateMonthDisplay()
        loadJobs()
    }

    fun nextMonth() {
        val state = _uiState.value
        var month = state.currentMonth + 1
        var year = state.currentYear
        if (month > 12) { month = 1; year++ }
        _uiState.value = state.copy(currentMonth = month, currentYear = year)
        updateMonthDisplay()
        loadJobs()
    }

    // ──────────────── Load Jobs (Paginated) ────────────────

    fun loadJobs() {
        currentSkip = 0
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, canLoadMore = true)

        viewModelScope.launch {
            val isManager = preferencesManager.isManager.first()
            val isCaregiver = preferencesManager.isCaregiver.first()
            val isAdmin = preferencesManager.isAdmin.first()
            val userId = preferencesManager.getUserId()
            val isOwner = isManager || isAdmin
            val state = _uiState.value

            val result = fetchPage(isAdmin, isCaregiver, userId, state.currentMonth, state.currentYear, 0, pageSize)

            when (result) {
                is ApiResult.Success -> {
                    val jobRows = mapShiftsToRows(result.data, isOwner)
                    originalJobs = jobRows
                    currentSkip = jobRows.size
                    val canLoadMore = result.data.size >= pageSize
                    val sorted = sortJobs(jobRows, state.sortColumn, state.sortAscending)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        jobs = sorted,
                        isOwner = isOwner,
                        isCaregiver = isCaregiver,
                        canLoadMore = canLoadMore
                    )
                    applyFiltersToData()
                    buildKanban()
                    updateFilterStatus()
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load jobs: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.canLoadMore || state.isLoading) return

        _uiState.value = state.copy(isLoadingMore = true)
        val skipAtStart = currentSkip

        viewModelScope.launch {
            val isManager = preferencesManager.isManager.first()
            val isCaregiver = preferencesManager.isCaregiver.first()
            val isAdmin = preferencesManager.isAdmin.first()
            val userId = preferencesManager.getUserId()
            val isOwner = isManager || isAdmin

            val result = fetchPage(isAdmin, isCaregiver, userId, state.currentMonth, state.currentYear, skipAtStart, pageSize)

            when (result) {
                is ApiResult.Success -> {
                    // If loadJobs reset while we were loading, discard stale results
                    if (currentSkip != skipAtStart) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                        return@launch
                    }
                    val newRows = mapShiftsToRows(result.data, isOwner)
                    originalJobs = originalJobs + newRows
                    currentSkip += newRows.size
                    val canLoadMore = result.data.size >= pageSize
                    val sorted = sortJobs(originalJobs, state.sortColumn, state.sortAscending)
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        jobs = sorted,
                        canLoadMore = canLoadMore
                    )
                    applyFiltersToData()
                    buildKanban()
                    updateFilterStatus()
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load more jobs: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    private suspend fun fetchPage(
        isAdmin: Boolean, isCaregiver: Boolean, userId: String,
        month: Int, year: Int, skip: Int, take: Int
    ): ApiResult<List<ShiftDto>> {
        return when {
            isAdmin -> shiftRepository.getMonthlyJobsByAdmin(month, year, skip, take)
            isCaregiver -> shiftRepository.getMonthlyJobsByCaregiverId(userId, month, year, skip, take)
            else -> shiftRepository.getMonthlyJobsByUserId(userId, month, year, skip, take)
        }
    }

    private suspend fun mapShiftsToRows(shifts: List<ShiftDto>, isOwner: Boolean): List<JobRowItem> {
        return shifts.map { shift ->
            // Fetch quotations for this shift to get accepted quote amount
            val quotations = when (val result = shiftRepository.getQuotationsByJobId(shift.id)) {
                is ApiResult.Success -> result.data
                else -> emptyList()
            }
            val hasQuotations = quotations.isNotEmpty() &&
                    shift.statusId == ShiftStatus.Created.value &&
                    isOwner
            val acceptedQuote = quotations.firstOrNull { it.status.equals("Accepted", ignoreCase = true) }
            val enrichedShift = shift.copy(
                hasQuotations = hasQuotations,
                acceptedQuoteAmount = acceptedQuote?.quotedAmount ?: shift.acceptedQuoteAmount,
                acceptedQuotationId = acceptedQuote?.id ?: shift.acceptedQuotationId
            )
            JobRowItem(
                shift = enrichedShift,
                statusDisplayText = getStatusText(shift.statusId, hasQuotations),
                statusColor = getStatusColor(shift.statusId, hasQuotations),
                invoiceDisplayText = getInvoiceText(shift.invoiceStatus),
                invoiceColor = getInvoiceColor(shift.invoiceStatus),
                contractTypeDisplayText = getContractTypeText(shift.contractType),
                contractTypeColor = getContractTypeColor(shift.contractType),
                hsFormText = getHSFormText(shift.hsForms),
                hsFormColor = getHSFormColor(shift.hsForms),
                subItems = shift.shiftSubItems ?: emptyList(),
                isOwner = isOwner,
                isAddSubItem = isOwner
            )
        }
    }

    // ──────────────── Toggle Expand ────────────────

    fun toggleExpanded(shiftId: Int) {
        fun toggle(row: JobRowItem): JobRowItem =
            if (row.shift.id == shiftId) row.copy(isExpanded = !row.isExpanded) else row

        originalJobs = originalJobs.map { toggle(it) }
        _uiState.value = _uiState.value.copy(
            jobs = _uiState.value.jobs.map { toggle(it) }
        )
    }

    // ──────────────── Sorting ────────────────

    fun sortBy(column: String) {
        val state = _uiState.value
        val ascending = if (state.sortColumn == column) !state.sortAscending else true
        _uiState.value = state.copy(
            sortColumn = column,
            sortAscending = ascending,
            jobs = sortJobs(state.jobs, column, ascending)
        )
    }

    private fun sortJobs(jobs: List<JobRowItem>, column: String, ascending: Boolean): List<JobRowItem> {
        if (column.isBlank()) return jobs
        val comparator: Comparator<JobRowItem> = when (column) {
            "ProjectName" -> compareBy { it.shift.projectName.lowercase() }
            "ClientName" -> compareBy { (it.shift.clientName ?: "").lowercase() }
            "Address" -> compareBy { it.shift.address.lowercase() }
            "DurationFromString" -> compareBy { it.shift.durationFrom }
            "DurationToString" -> compareBy { it.shift.durationTo }
            "ContractType" -> compareBy { it.contractTypeDisplayText.lowercase() }
            "InvoiceStatus" -> compareBy { it.invoiceDisplayText.lowercase() }
            "HSForm" -> compareBy { it.shift.hsForms ?: 0 }
            "FinalMeasure" -> compareBy { (it.shift.finalMeasure).lowercase() }
            "Instructions" -> compareBy { (it.shift.instructions).lowercase() }
            "Amount" -> compareBy { it.shift.amount ?: 0.0 }
            "AcceptedQuoteAmount" -> compareBy { it.shift.acceptedQuoteAmount ?: 0.0 }
            "StatusMessage" -> compareBy { it.statusDisplayText.lowercase() }
            else -> compareBy { it.shift.projectName.lowercase() }
        }
        return if (ascending) jobs.sortedWith(comparator) else jobs.sortedWith(comparator.reversed())
    }

    // ──────────────── View Mode ────────────────

    fun setViewMode(mode: String) {
        _uiState.value = _uiState.value.copy(
            selectedViewMode = mode,
            isListView = mode == "Main Table"
        )
        if (mode == "Kanban" && _uiState.value.canLoadMore) {
            loadAllRemaining()
        }
    }

    private fun loadAllRemaining() {
        viewModelScope.launch {
            val isManager = preferencesManager.isManager.first()
            val isCaregiver = preferencesManager.isCaregiver.first()
            val isAdmin = preferencesManager.isAdmin.first()
            val userId = preferencesManager.getUserId()
            val isOwner = isManager || isAdmin
            val state = _uiState.value

            while (_uiState.value.canLoadMore) {
                val result = fetchPage(isAdmin, isCaregiver, userId, state.currentMonth, state.currentYear, currentSkip, pageSize)
                when (result) {
                    is ApiResult.Success -> {
                        if (result.data.isEmpty()) {
                            _uiState.value = _uiState.value.copy(canLoadMore = false)
                            break
                        }
                        val newRows = mapShiftsToRows(result.data, isOwner)
                        originalJobs = originalJobs + newRows
                        currentSkip += newRows.size
                        val canLoadMore = result.data.size >= pageSize
                        val sorted = sortJobs(originalJobs, _uiState.value.sortColumn, _uiState.value.sortAscending)
                        _uiState.value = _uiState.value.copy(
                            jobs = sorted,
                            canLoadMore = canLoadMore
                        )
                        applyFiltersToData()
                        buildKanban()
                        updateFilterStatus()
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(canLoadMore = false)
                        break
                    }
                    is ApiResult.Loading -> break
                }
            }
        }
    }

    private fun buildKanban() {
        val jobs = _uiState.value.jobs
        if (jobs.isEmpty()) {
            _uiState.value = _uiState.value.copy(kanbanColumns = emptyList())
            return
        }
        val columns = jobs
            .groupBy { it.shift.contractType ?: 0 }
            .toSortedMap()
            .map { (_, groupJobs) ->
                val first = groupJobs.first()
                KanbanColumn(
                    title = "${first.contractTypeDisplayText} / ${groupJobs.size}",
                    headerColor = first.contractTypeColor,
                    items = groupJobs
                )
            }
        _uiState.value = _uiState.value.copy(kanbanColumns = columns)
    }

    // ──────────────── Inline Edit Dialogs ────────────────

    fun editProjectName(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val job = findJob(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.TextEditor,
                title = "Project Name",
                fieldName = "ProjectName",
                textValue = job.shift.projectName,
                selectedShiftId = shiftId
            )
        )
    }

    fun editFinalMeasure(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val job = findJob(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.TextEditor,
                title = "Final Measure",
                fieldName = "FinalMeasure",
                textValue = job.shift.finalMeasure,
                selectedShiftId = shiftId
            )
        )
    }

    fun editJobDescription(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val job = findJob(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.TextEditor,
                title = "Job Description",
                fieldName = "Instructions",
                textValue = job.shift.instructions,
                selectedShiftId = shiftId
            )
        )
    }

    fun editDurationFrom(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val job = findJob(shiftId) ?: return
        val dt = parseDateTimeOrNow(job.shift.durationFrom)
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.DateTimePicker,
                title = "Duration From",
                fieldName = "DurationFrom",
                selectedShiftId = shiftId,
                selectedDate = dt.toLocalDate(),
                selectedHour = dt.hour,
                selectedMinute = dt.minute
            )
        )
    }

    fun editDurationTo(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val job = findJob(shiftId) ?: return
        val dt = parseDateTimeOrNow(job.shift.durationTo)
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.DateTimePicker,
                title = "Duration To",
                fieldName = "DurationTo",
                selectedShiftId = shiftId,
                selectedDate = dt.toLocalDate(),
                selectedHour = dt.hour,
                selectedMinute = dt.minute
            )
        )
    }

    fun editContractType(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.ContractTypePicker,
                title = "Contract Type",
                selectedShiftId = shiftId
            )
        )
    }

    fun editInvoiceStatus(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.InvoiceStatusPicker,
                title = "Invoice Status",
                selectedShiftId = shiftId
            )
        )
    }

    fun editHSFormStatus(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.HSFormStatusPicker,
                title = "H&S Form Status",
                selectedShiftId = shiftId
            )
        )
    }

    fun editSubItemHSStatus(subItemId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.SubItemHSPicker,
                title = "H&S Status",
                selectedSubItemId = subItemId
            )
        )
    }

    fun editSubItemStatus(subItemId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.SubItemStatusPicker,
                title = "Sub-Item Status",
                selectedSubItemId = subItemId
            )
        )
    }

    fun editAddress(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val job = findJob(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.AddressSearch,
                title = "Search Address",
                selectedShiftId = shiftId
            ),
            addressSearchText = job.shift.address,
            placeSuggestions = emptyList()
        )
    }

    fun editClient(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.ClientPicker,
                title = "Select Client",
                selectedShiftId = shiftId
            )
        )
    }

    fun editSubItemDateStarted(shiftId: Int, subItemId: Int) {
        if (_uiState.value.isCaregiver) return
        val subItem = findSubItem(subItemId) ?: return
        val (date, hour, minute) = if (!subItem.dateStarted.isNullOrBlank()) {
            try {
                val dt = LocalDateTime.parse(subItem.dateStarted.replace("Z", ""))
                Triple(dt.toLocalDate(), dt.hour, dt.minute)
            } catch (_: Exception) {
                Triple(LocalDate.now(), 12, 0)
            }
        } else {
            Triple(LocalDate.now(), 12, 0)
        }
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(
                type = EditDialogType.SubItemDatePicker,
                title = "Date Started",
                selectedShiftId = shiftId,
                selectedSubItemId = subItemId,
                selectedDate = date,
                selectedHour = hour,
                selectedMinute = minute
            )
        )
    }

    // ──────────────── Address Search ────────────────

    fun onAddressSearchChange(text: String) {
        _uiState.value = _uiState.value.copy(addressSearchText = text)
        addressSearchJob?.cancel()
        if (text.length >= 3) {
            addressSearchJob = viewModelScope.launch {
                delay(300)
                val predictions = googlePlacesService.getPlacesByText(text)
                _uiState.value = _uiState.value.copy(placeSuggestions = predictions)
            }
        } else {
            _uiState.value = _uiState.value.copy(placeSuggestions = emptyList())
        }
    }

    fun onPlaceSelected(prediction: PlacePrediction) {
        viewModelScope.launch {
            val place = googlePlacesService.getPlaceDetails(prediction.placeId)
            if (place != null) {
                saveAddress(place.address, place.latitude, place.longitude)
            }
        }
    }

    private fun saveAddress(address: String, latitude: Double, longitude: Double) {
        val dialog = _uiState.value.editDialog
        val job = findJob(dialog.selectedShiftId) ?: return
        val updatedShift = job.shift.copy(address = address, latitude = latitude, longitude = longitude)
        dismissEditDialog()
        updateShift(updatedShift)
    }

    // ──────────────── Save Actions ────────────────

    fun selectClient(clientId: Int, clientName: String) {
        val dialog = _uiState.value.editDialog
        val job = findJob(dialog.selectedShiftId) ?: return
        val updatedShift = job.shift.copy(
            clientId = if (clientId > 0) clientId else null,
            clientName = if (clientId > 0) clientName else ""
        )
        dismissEditDialog()
        updateShift(updatedShift)
    }

    fun saveSubItemDate(date: LocalDate, time: LocalTime) {
        val dialog = _uiState.value.editDialog
        val subItem = findSubItem(dialog.selectedSubItemId) ?: return
        val dateTimeStr = "${date}T${time.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
        val updated = subItem.copy(dateStarted = dateTimeStr)
        dismissEditDialog()
        updateSubItem(updated)
    }

    fun updateEditDialogText(text: String) {
        _uiState.value = _uiState.value.copy(
            editDialog = _uiState.value.editDialog.copy(textValue = text)
        )
    }

    fun updateEditDialogDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            editDialog = _uiState.value.editDialog.copy(selectedDate = date)
        )
    }

    fun updateEditDialogTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(
            editDialog = _uiState.value.editDialog.copy(selectedHour = hour, selectedMinute = minute)
        )
    }

    fun dismissEditDialog() {
        _uiState.value = _uiState.value.copy(
            editDialog = EditDialogState(),
            placeSuggestions = emptyList()
        )
    }

    fun saveTextEdit() {
        val dialog = _uiState.value.editDialog
        val job = findJob(dialog.selectedShiftId) ?: return
        val updatedShift = when (dialog.fieldName) {
            "ProjectName" -> job.shift.copy(projectName = dialog.textValue)
            "FinalMeasure" -> job.shift.copy(finalMeasure = dialog.textValue)
            "Instructions" -> job.shift.copy(instructions = dialog.textValue)
            else -> return
        }
        dismissEditDialog()
        updateShift(updatedShift)
    }

    fun saveDateTimeEdit() {
        val dialog = _uiState.value.editDialog
        val job = findJob(dialog.selectedShiftId) ?: return
        val dateTime = LocalDateTime.of(dialog.selectedDate, java.time.LocalTime.of(dialog.selectedHour, dialog.selectedMinute))
        val formatted = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val updatedShift = when (dialog.fieldName) {
            "DurationFrom" -> job.shift.copy(durationFrom = formatted)
            "DurationTo" -> job.shift.copy(durationTo = formatted)
            else -> return
        }
        dismissEditDialog()
        updateShift(updatedShift)
    }

    fun selectContractType(value: Int) {
        val dialog = _uiState.value.editDialog
        val job = findJob(dialog.selectedShiftId) ?: return
        dismissEditDialog()
        updateShift(job.shift.copy(contractType = value))
    }

    fun selectInvoiceStatus(value: Int) {
        val dialog = _uiState.value.editDialog
        val job = findJob(dialog.selectedShiftId) ?: return
        dismissEditDialog()
        updateShift(job.shift.copy(invoiceStatus = value))
    }

    fun selectHSFormStatus(value: Int) {
        val dialog = _uiState.value.editDialog
        val job = findJob(dialog.selectedShiftId) ?: return
        dismissEditDialog()
        updateShift(job.shift.copy(hsForms = value))
    }

    fun selectSubItemHSStatus(value: Int) {
        val dialog = _uiState.value.editDialog
        val subItem = findSubItem(dialog.selectedSubItemId) ?: return
        dismissEditDialog()
        updateSubItem(subItem.copy(hsRequired = value))
    }

    fun selectSubItemStatus(value: Int) {
        val dialog = _uiState.value.editDialog
        val subItem = findSubItem(dialog.selectedSubItemId) ?: return
        dismissEditDialog()
        updateSubItem(subItem.copy(status = value))
    }

    private fun updateShift(shift: ShiftDto) {
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val updated = shift.copy(modifiedBy = userId)
            when (val result = shiftRepository.updateShift(updated)) {
                is ApiResult.Success -> {
                    val returnedShift = result.data
                    updateShiftInPlace(returnedShift)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message ?: "Failed to update")
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    private fun updateShiftInPlace(updatedShift: ShiftDto) {
        val isOwner = _uiState.value.isOwner
        val clients = _uiState.value.clients

        // Enrich clientName from local clients list when API returns null (same as DayDetailView)
        val enrichedShift = if (updatedShift.clientName.isNullOrBlank() && updatedShift.clientId != null && updatedShift.clientId > 0) {
            val clientName = clients.find { it.id == updatedShift.clientId }?.name
            if (!clientName.isNullOrBlank()) updatedShift.copy(clientName = clientName) else updatedShift
        } else updatedShift

        val hasQuotations = !enrichedShift.jobQuotations.isNullOrEmpty() &&
                enrichedShift.statusId == ShiftStatus.Created.value &&
                isOwner
        val shiftWithQuotations = enrichedShift.copy(hasQuotations = hasQuotations)

        fun updateRow(row: JobRowItem): JobRowItem {
            if (row.shift.id != enrichedShift.id) return row
            return row.copy(
                shift = shiftWithQuotations.copy(shiftSubItems = row.shift.shiftSubItems),
                statusDisplayText = getStatusText(enrichedShift.statusId, hasQuotations),
                statusColor = getStatusColor(enrichedShift.statusId, hasQuotations),
                invoiceDisplayText = getInvoiceText(enrichedShift.invoiceStatus),
                invoiceColor = getInvoiceColor(enrichedShift.invoiceStatus),
                contractTypeDisplayText = getContractTypeText(enrichedShift.contractType),
                contractTypeColor = getContractTypeColor(enrichedShift.contractType),
                hsFormText = getHSFormText(enrichedShift.hsForms),
                hsFormColor = getHSFormColor(enrichedShift.hsForms)
            )
        }

        originalJobs = originalJobs.map { updateRow(it) }
        _uiState.value = _uiState.value.copy(
            jobs = _uiState.value.jobs.map { updateRow(it) }
        )
        buildKanban()
    }

    private fun updateSubItem(subItem: ShiftSubItemDto) {
        viewModelScope.launch {
            when (val result = shiftRepository.editSubItems(subItem)) {
                is ApiResult.Success -> {
                    val returnedSubItem = result.data
                    updateSubItemInPlace(returnedSubItem)
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message ?: "Failed to update sub-item")
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    private fun updateSubItemInPlace(updatedSubItem: ShiftSubItemDto) {
        fun updateRow(row: JobRowItem): JobRowItem {
            if (row.shift.id != updatedSubItem.shiftId) return row
            val updatedSubItems = row.subItems.map { sub ->
                if (sub.id == updatedSubItem.id) updatedSubItem else sub
            }
            return row.copy(
                subItems = updatedSubItems,
                shift = row.shift.copy(shiftSubItems = updatedSubItems)
            )
        }

        originalJobs = originalJobs.map { updateRow(it) }
        _uiState.value = _uiState.value.copy(
            jobs = _uiState.value.jobs.map { updateRow(it) }
        )
    }

    // ──────────────── Sub-item Add / Delete ────────────────

    fun updateNewSubItemName(shiftId: Int, name: String) {
        _uiState.value = _uiState.value.copy(
            jobs = _uiState.value.jobs.map { row ->
                if (row.shift.id == shiftId) row.copy(newSubItemName = name) else row
            }
        )
    }

    fun addNewSubItem(shiftId: Int) {
        val job = findJob(shiftId) ?: return
        val name = job.newSubItemName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a sub-item name.")
            return
        }

        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val newSubItem = ShiftSubItemDto(
                id = 0,
                shiftId = shiftId,
                subitem = name,
                hsRequired = HSRequiredStatus.NoHS.value,
                status = 0,
                createdBy = userId
            )
            when (val result = shiftRepository.addSubItems(newSubItem)) {
                is ApiResult.Success -> {
                    val addedSubItem = result.data
                    fun addToRow(row: JobRowItem): JobRowItem {
                        if (row.shift.id != shiftId) return row
                        val updatedSubItems = row.subItems + addedSubItem
                        return row.copy(
                            subItems = updatedSubItems,
                            shift = row.shift.copy(shiftSubItems = updatedSubItems),
                            newSubItemName = ""
                        )
                    }
                    originalJobs = originalJobs.map { addToRow(it) }
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Sub-item added successfully.",
                        jobs = _uiState.value.jobs.map { addToRow(it) }
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message ?: "Failed to add sub-item.")
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun deleteSubItem(subItemId: Int) {
        viewModelScope.launch {
            when (val result = shiftRepository.deleteSubItem(subItemId)) {
                is ApiResult.Success -> {
                    fun removeFromRow(row: JobRowItem): JobRowItem {
                        val updatedSubItems = row.subItems.filter { it.id != subItemId }
                        if (updatedSubItems.size == row.subItems.size) return row
                        return row.copy(
                            subItems = updatedSubItems,
                            shift = row.shift.copy(shiftSubItems = updatedSubItems)
                        )
                    }
                    originalJobs = originalJobs.map { removeFromRow(it) }
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Sub-item deleted.",
                        jobs = _uiState.value.jobs.map { removeFromRow(it) }
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message ?: "Failed to delete sub-item.")
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // ──────────────── Filter System ────────────────

    private fun buildFilterColumns(): List<FilterColumnOption> {
        val columns = mutableListOf<FilterColumnOption>()
        // Item columns
        columns.add(FilterColumnOption("Item columns 🔧", "", FilterColumnType.ItemColumn, "", emptyList()))
        val itemCols = listOf(
            "Project Name" to "ProjectName",
            "Client Name" to "ClientName",
            "Address" to "Address",
            "Duration From" to "DurationFromString",
            "Duration To" to "DurationToString",
            "Contract Type" to "ContractTypeText",
            "Invoice Status" to "InvoiceStatusText",
            "H&S Forms" to "HSForms",
            "Final Measure" to "FinalMeasure",
            "Job Description" to "Instructions",
            "Status" to "StatusMessage"
        )
        itemCols.forEach { (display, prop) ->
            columns.add(FilterColumnOption(display, prop, FilterColumnType.ItemColumn, "", getConditions(display)))
        }
        // Sub-item columns
        columns.add(FilterColumnOption("Subitem columns 🔧", "", FilterColumnType.SubItemColumn, "", emptyList()))
        val subCols = listOf(
            "Sub Item" to "SubItem.Subitem",
            "H&S Required" to "SubItem.HSRequiredText",
            "Status" to "SubItem.StatusText",
            "Date Started" to "SubItem.DateStartedString",
            "Completed" to "SubItem.DateCompletedString"
        )
        subCols.forEach { (display, prop) ->
            columns.add(FilterColumnOption(display, prop, FilterColumnType.SubItemColumn, "", getConditions(display)))
        }
        return columns
    }

    private fun getConditions(colName: String): List<String> {
        val lower = colName.lowercase()
        return when {
            lower.contains("date") || lower.contains("duration") ->
                listOf("contains", "doesn't contain", "is", "is not", "is empty", "is not empty")
            lower.contains("status") || lower.contains("type") || lower.contains("h&s forms") ->
                listOf("is", "is not", "contains", "doesn't contain")
            lower.contains("h&s") && lower.contains("required") ->
                listOf("is", "is not")
            else ->
                listOf("contains", "doesn't contain", "starts with", "is", "is not", "is empty", "is not empty")
        }
    }

    fun addFilter() {
        val filterableCols = _uiState.value.filterColumns.filter { it.propertyName.isNotBlank() }
        if (filterableCols.isEmpty()) return
        val newFilter = FilterRow(selectedColumn = filterableCols.firstOrNull())
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters + newFilter
        )
        updateFilterStatus()
    }

    fun updatePendingFilterColumn(filterId: Long, column: FilterColumnOption) {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters.map {
                if (it.id == filterId) it.copy(selectedColumn = column, selectedCondition = "", value = "") else it
            }
        )
        updateFilterStatus()
    }

    fun updatePendingFilterCondition(filterId: Long, condition: String) {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters.map {
                if (it.id == filterId) it.copy(selectedCondition = condition) else it
            }
        )
        updateFilterStatus()
    }

    fun updatePendingFilterValue(filterId: Long, value: String) {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters.map {
                if (it.id == filterId) it.copy(value = value) else it
            }
        )
        updateFilterStatus()
    }

    fun removePendingFilter(filterId: Long) {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters.filter { it.id != filterId },
            activeFilters = _uiState.value.activeFilters.filter { it.id != filterId }
        )
        applyFiltersToData()
        updateFilterStatus()
    }

    fun applyFilters() {
        val complete = _uiState.value.pendingFilters.filter { it.isComplete }
        if (complete.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            activeFilters = _uiState.value.activeFilters + complete.map { it.copy(isApplied = true) },
            pendingFilters = _uiState.value.pendingFilters.filter { !it.isComplete }
        )
        applyFiltersToData()
        updateFilterStatus()
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(
            pendingFilters = emptyList(),
            activeFilters = emptyList()
        )
        applyFiltersToData()
        updateFilterStatus()
    }

    private fun applyFiltersToData() {
        val activeFilters = _uiState.value.activeFilters
        if (activeFilters.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                jobs = sortJobs(originalJobs, _uiState.value.sortColumn, _uiState.value.sortAscending)
            )
            buildKanban()
            return
        }
        val filtered = originalJobs.filter { job ->
            activeFilters.all { filter -> evaluateFilter(job, filter) }
        }
        _uiState.value = _uiState.value.copy(
            jobs = sortJobs(filtered, _uiState.value.sortColumn, _uiState.value.sortAscending)
        )
        buildKanban()
    }

    private fun evaluateFilter(job: JobRowItem, filter: FilterRow): Boolean {
        val col = filter.selectedColumn ?: return true
        val condition = filter.selectedCondition
        val filterValue = filter.value.lowercase()

        if (col.columnType == FilterColumnType.SubItemColumn) {
            if (job.subItems.isEmpty()) return condition == "is empty"
            return job.subItems.any { evaluateSubItemFilter(it, col.propertyName, condition, filterValue) }
        }

        val propValue = getShiftPropertyValue(job, col.propertyName)?.lowercase() ?: ""
        return evaluateCondition(propValue, condition, filterValue)
    }

    private fun evaluateSubItemFilter(subItem: ShiftSubItemDto, propName: String, condition: String, value: String): Boolean {
        val propValue = when (propName) {
            "SubItem.Subitem" -> subItem.subitem.lowercase()
            "SubItem.HSRequiredText" -> (subItem.hsRequiredText).lowercase()
            "SubItem.StatusText" -> (subItem.statusText).lowercase()
            "SubItem.DateStartedString" -> (subItem.dateStartedString).lowercase()
            "SubItem.DateCompletedString" -> (subItem.dateCompletedString).lowercase()
            else -> ""
        }
        return evaluateCondition(propValue, condition, value)
    }

    private fun evaluateCondition(propValue: String, condition: String, value: String): Boolean {
        return when (condition) {
            "contains" -> propValue.contains(value)
            "doesn't contain" -> !propValue.contains(value)
            "starts with" -> propValue.startsWith(value)
            "is" -> propValue == value
            "is not" -> propValue != value
            "is empty" -> propValue.isBlank() || propValue == "not set" || propValue == "not started" || propValue == "not completed"
            "is not empty" -> propValue.isNotBlank() && propValue != "not set" && propValue != "not started" && propValue != "not completed"
            else -> true
        }
    }

    private fun getShiftPropertyValue(job: JobRowItem, propName: String): String? {
        return when (propName) {
            "ProjectName" -> job.shift.projectName
            "ClientName" -> job.shift.clientName
            "Address" -> job.shift.address
            "DurationFromString" -> job.shift.durationFromString.ifBlank { job.shift.durationFrom }
            "DurationToString" -> job.shift.durationToString.ifBlank { job.shift.durationTo }
            "ContractTypeText" -> job.contractTypeDisplayText
            "InvoiceStatusText" -> job.invoiceDisplayText
            "HSForms" -> job.hsFormText
            "FinalMeasure" -> job.shift.finalMeasure
            "Instructions" -> job.shift.instructions
            "StatusMessage" -> job.statusDisplayText
            else -> null
        }
    }

    private fun updateFilterStatus() {
        val state = _uiState.value
        val hasActive = state.activeFilters.isNotEmpty()
        val hasPending = state.pendingFilters.isNotEmpty()
        val hasFiltersToApply = state.pendingFilters.any { it.isComplete }
        val showEmpty = !hasActive && !hasPending
        val totalCount = originalJobs.size
        val currentCount = state.jobs.size
        val statusText = if (!hasActive) "Showing all of $totalCount projects"
        else "Showing $currentCount of $totalCount projects"

        _uiState.value = state.copy(
            hasActiveFilters = hasActive,
            hasPendingFilters = hasPending,
            hasFiltersToApply = hasFiltersToApply,
            showEmptyState = showEmpty,
            filterStatusText = statusText
        )
    }

    // ──────────────── Helpers ────────────────

    private fun findJob(shiftId: Int): JobRowItem? = _uiState.value.jobs.find { it.shift.id == shiftId }

    private fun findSubItem(subItemId: Int): ShiftSubItemDto? {
        for (job in _uiState.value.jobs) {
            val sub = job.subItems.find { it.id == subItemId }
            if (sub != null) return sub
        }
        return null
    }

    private fun parseDateTimeOrNow(dateStr: String): LocalDateTime {
        return try {
            LocalDateTime.parse(dateStr.replace("Z", ""))
        } catch (_: Exception) {
            LocalDateTime.now()
        }
    }

    fun formatTimestamp(dateStr: String): String {
        return try {
            val dt = LocalDateTime.parse(dateStr.replace("Z", ""))
            dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"))
        } catch (_: Exception) {
            dateStr
        }
    }

    fun formatAmount(amount: Double?): String {
        return if (amount != null) String.format("$%.2f", amount) else ""
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

    // ──────────────── Status/Color maps matching MAUI exactly ────────────────

    companion object {
        fun getStatusText(statusId: Int, hasQuotations: Boolean = false): String {
            if (statusId == ShiftStatus.Created.value && hasQuotations) return "Quoted"
            return when (ShiftStatus.fromValue(statusId)) {
                ShiftStatus.Created -> "Created"
                ShiftStatus.Accepted -> "Accepted"
                ShiftStatus.Ongoing -> "Started"
                ShiftStatus.Completed -> "End"
                ShiftStatus.NotCompleted -> "Not Completed"
                ShiftStatus.FinishJob -> "Completed"
            }
        }

        fun getStatusColor(statusId: Int, hasQuotations: Boolean = false): Long {
            if (statusId == ShiftStatus.Created.value && hasQuotations) return 0xFF007AFF
            return when (ShiftStatus.fromValue(statusId)) {
                ShiftStatus.Created -> 0xFFFF9500
                ShiftStatus.Accepted -> 0xFF9D50DD
                ShiftStatus.Ongoing -> 0xFF00C875
                ShiftStatus.Completed -> 0xFF74AFCC
                ShiftStatus.NotCompleted -> 0xFFFF3B30
                ShiftStatus.FinishJob -> 0xFFFFCB00
            }
        }

        fun getInvoiceText(invoiceStatus: Int?): String = when {
            invoiceStatus == null -> ""
            else -> when (Invoice.fromValue(invoiceStatus)) {
                Invoice.NotYetCreated -> "Not Yet Created"
                Invoice.ToBeInvoiced -> "To Be Invoiced"
                Invoice.InvoiceDrafted -> "Invoice Drafted"
                Invoice.InvoiceSent -> "Invoice Sent"
            }
        }

        fun getInvoiceColor(invoiceStatus: Int?): Long = when {
            invoiceStatus == null -> 0xFFFFFFFF
            else -> when (Invoice.fromValue(invoiceStatus)) {
                Invoice.NotYetCreated -> 0xFFC4C4C4
                Invoice.ToBeInvoiced -> 0xFFFF6D3B
                Invoice.InvoiceDrafted -> 0xFFFF0000
                Invoice.InvoiceSent -> 0xFFFFCB00
            }
        }

        fun getContractTypeText(contractType: Int?): String = when {
            contractType == null -> ""
            else -> when (ContractType.fromValue(contractType)) {
                ContractType.ToBeConfirmed -> "To Be Confirmed"
                ContractType.FullContract -> "Full Contract"
                ContractType.SupplyPlaceAndFinish -> "Supply Place And Finish"
                ContractType.PlaceAndFinish -> "Place And Finish"
                ContractType.LabourSupply -> "Labour Supply"
                ContractType.BoxPlaceAndFinish -> "Box Place And Finish"
                ContractType.Remedial -> "Remedial"
                ContractType.SupplyPlaceFinishAndCut -> "Supply Place Finish And Cut"
                ContractType.PlaceFinishAndCut -> "Place Finish And Cut"
                ContractType.OtherServices -> "Other Services"
                ContractType.Meetings -> "Meetings"
            }
        }

        fun getContractTypeColor(contractType: Int?): Long = when {
            contractType == null -> 0xFFFFFFFF
            else -> when (ContractType.fromValue(contractType)) {
                ContractType.ToBeConfirmed -> 0xFFC4C4C4
                ContractType.FullContract -> 0xFFBCA58A
                ContractType.SupplyPlaceAndFinish -> 0xFF74AFCC
                ContractType.PlaceAndFinish -> 0xFFCAB641
                ContractType.LabourSupply -> 0xFF175A63
                ContractType.BoxPlaceAndFinish -> 0xFF333333
                ContractType.Remedial -> 0xFFFF0000
                ContractType.SupplyPlaceFinishAndCut -> 0xFF037F4C
                ContractType.PlaceFinishAndCut -> 0xFF7F5347
                ContractType.OtherServices -> 0xFF7F00FF
                ContractType.Meetings -> 0xFFFF8DA1
            }
        }

        fun getHSFormText(hsForms: Int?): String = when (hsForms) {
            HSRequiredStatus.NoHS.value -> "No H&S"
            HSRequiredStatus.SSSP.value -> "SSSP"
            HSRequiredStatus.JSA.value -> "JSA"
            HSRequiredStatus.Take5.value -> "Take 5"
            HSRequiredStatus.Done.value -> "Done"
            HSRequiredStatus.MissingHS.value -> "Missing H&S"
            else -> ""
        }

        fun getHSFormColor(hsForms: Int?): Long = when (hsForms) {
            HSRequiredStatus.NoHS.value -> 0xFFC4C4C4
            HSRequiredStatus.SSSP.value -> 0xFF00C875
            HSRequiredStatus.JSA.value -> 0xFF007EB5
            HSRequiredStatus.Take5.value -> 0xFFFF0000
            HSRequiredStatus.Done.value -> 0xFFFFCB00
            HSRequiredStatus.MissingHS.value -> 0xFF808080
            else -> 0xFFFFFFFF
        }

        fun getSubItemStatusText(status: Int): String = when (status) {
            SubItemStatus.AwaitingPrevious.value -> "Awaiting previous"
            SubItemStatus.WorkingOnIt.value -> "Working on it"
            SubItemStatus.Stuck.value -> "Stuck"
            SubItemStatus.Done.value -> "Done"
            else -> ""
        }

        fun getSubItemStatusColor(status: Int): Long = when (status) {
            SubItemStatus.AwaitingPrevious.value -> 0xFF9D50DD
            SubItemStatus.WorkingOnIt.value -> 0xFF00C875
            SubItemStatus.Stuck.value -> 0xFFFF0000
            SubItemStatus.Done.value -> 0xFFFFCB00
            else -> 0xFFFFFFFF
        }

        val contractTypeOptions: List<StatusOption> = listOf(
            StatusOption("To Be Confirmed", ContractType.ToBeConfirmed.value, 0xFFC4C4C4),
            StatusOption("Full Contract", ContractType.FullContract.value, 0xFFBCA58A),
            StatusOption("Supply Place And Finish", ContractType.SupplyPlaceAndFinish.value, 0xFF74AFCC),
            StatusOption("Place And Finish", ContractType.PlaceAndFinish.value, 0xFFCAB641),
            StatusOption("Labour Supply", ContractType.LabourSupply.value, 0xFF175A63),
            StatusOption("Box Place And Finish", ContractType.BoxPlaceAndFinish.value, 0xFF333333),
            StatusOption("Remedial", ContractType.Remedial.value, 0xFFFF0000),
            StatusOption("Supply Place Finish And Cut", ContractType.SupplyPlaceFinishAndCut.value, 0xFF037F4C),
            StatusOption("Place Finish And Cut", ContractType.PlaceFinishAndCut.value, 0xFF7F5347),
            StatusOption("Other Services", ContractType.OtherServices.value, 0xFF7F00FF),
            StatusOption("Meetings", ContractType.Meetings.value, 0xFFFF8DA1)
        )

        val invoiceStatusOptions: List<StatusOption> = listOf(
            StatusOption("Not Yet Created", Invoice.NotYetCreated.value, 0xFFC4C4C4),
            StatusOption("To Be Invoiced", Invoice.ToBeInvoiced.value, 0xFFFF6D3B),
            StatusOption("Invoice Drafted", Invoice.InvoiceDrafted.value, 0xFFFF0000),
            StatusOption("Invoice Sent", Invoice.InvoiceSent.value, 0xFFFFCB00)
        )

        val hsFormOptions: List<StatusOption> = listOf(
            StatusOption("No H&S", HSRequiredStatus.NoHS.value, 0xFFC4C4C4),
            StatusOption("SSSP", HSRequiredStatus.SSSP.value, 0xFF00C875),
            StatusOption("JSA", HSRequiredStatus.JSA.value, 0xFF007EB5),
            StatusOption("Take 5", HSRequiredStatus.Take5.value, 0xFFFF0000),
            StatusOption("Done", HSRequiredStatus.Done.value, 0xFFFFCB00),
            StatusOption("Missing H&S", HSRequiredStatus.MissingHS.value, 0xFF808080)
        )

        val subItemStatusOptions: List<StatusOption> = listOf(
            StatusOption("Awaiting previous", SubItemStatus.AwaitingPrevious.value, 0xFF9D50DD),
            StatusOption("Working on it", SubItemStatus.WorkingOnIt.value, 0xFF00C875),
            StatusOption("Stuck", SubItemStatus.Stuck.value, 0xFFFF0000),
            StatusOption("Done", SubItemStatus.Done.value, 0xFFFFCB00)
        )
    }
}