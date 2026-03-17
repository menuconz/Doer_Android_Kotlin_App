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
import nz.co.doer.data.remote.dto.ShiftSubItemDto
import nz.co.doer.data.repository.ClientRepository
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ===== Data Models =====

data class ContractTypeItem(val id: Int, val name: String, val color: Long)
data class InvoiceStatusItem(val id: Int, val name: String, val color: Long)
data class HSFormStatusItem(val id: Int, val name: String, val color: Long)
data class SubItemStatusItem(val id: Int, val name: String, val color: Long)

data class FilterColumnOption(val propertyName: String, val displayName: String, val isSubItem: Boolean = false)

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

    val availableConditions: List<String>
        get() {
            val col = selectedColumn?.propertyName ?: return defaultConditions
            return when {
                col.contains("Duration", true) || col.contains("Date", true) ->
                    listOf("contains", "doesn't contain", "is", "is not", "is empty", "is not empty")
                col.contains("Status", true) || col.contains("ContractType", true) ||
                        col.contains("InvoiceStatus", true) || col.contains("HSForm", true) ->
                    listOf("is", "is not", "contains", "doesn't contain")
                col.contains("HSRequired", true) ->
                    listOf("is", "is not")
                else -> defaultConditions
            }
        }

    companion object {
        val defaultConditions = listOf("contains", "doesn't contain", "starts with", "is", "is not", "is empty", "is not empty")
    }
}

data class ShiftDisplayRow(
    val shift: ShiftDto,
    val subItems: List<ShiftSubItemDto> = emptyList(),
    val statusMessage: String = "",
    val statusColor: Long = 0xFFFFFFFF,
    val contractTypeText: String = "",
    val contractTypeColor: Long = 0xFFFFFFFF,
    val invoiceStatusText: String = "",
    val invoiceStatusColor: Long = 0xFFFFFFFF,
    val hsFormText: String = "",
    val hsFormColor: Long = 0xFFFFFFFF,
    val durationFromFormatted: String = "",
    val durationToFormatted: String = "",
    val isExpanded: Boolean = false,
    val hasSubItems: Boolean = false,
    val hasQuotations: Boolean = false,
    val isAddSubItem: Boolean = false,
    val isDeleteSubItem: Boolean = false,
    val newSubItemName: String = ""
)

// ===== Dialog Types =====
enum class DayDetailDialog {
    NONE,
    EDITOR,           // Text editor for project name, final measure, job description
    DATE_TIME,        // Date + time picker for duration from/to
    ADDRESS_SEARCH,   // Google Places autocomplete
    CONTRACT_TYPE,    // Select contract type
    CLIENT_SELECT,    // Select client
    INVOICE_STATUS,   // Select invoice status
    HS_FORM_STATUS,   // Select H&S form status
    SUB_ITEM_HS,      // Sub-item H&S required
    SUB_ITEM_STATUS,  // Sub-item status
    SUB_ITEM_DATE,    // Sub-item date started
    DELETE_SUB_ITEM   // Confirm delete sub-item
}

data class DayDetailUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateString: String = "",
    val projectCountText: String = "",
    val pageTitle: String = "Day Detail",
    val shiftRows: List<ShiftDisplayRow> = emptyList(),
    val isLoading: Boolean = true,
    val isManager: Boolean = false,
    val isAdmin: Boolean = false,
    val isCaregiver: Boolean = false,
    val isOwner: Boolean = false,
    val errorMessage: String? = null,
    // Sort
    val sortColumn: String = "",
    val sortAscending: Boolean = true,
    // Filter
    val pendingFilters: List<FilterRow> = emptyList(),
    val activeFilters: List<FilterRow> = emptyList(),
    val filterStatusText: String = "",
    // Dialog
    val activeDialog: DayDetailDialog = DayDetailDialog.NONE,
    val editingShiftId: Int = 0,
    val editingSubItemId: Int = 0,
    val editorTitle: String = "",
    val editorText: String = "",
    val editorFieldName: String = "",
    val editDate: LocalDate = LocalDate.now(),
    val editTime: LocalTime = LocalTime.NOON,
    val editIsAllDay: Boolean = false,
    // Address search
    val addressSearchText: String = "",
    val placeSuggestions: List<PlacePrediction> = emptyList(),
    // Clients
    val clients: List<ClientDto> = emptyList(),
    // Options
    val contractTypes: List<ContractTypeItem> = emptyList(),
    val invoiceStatuses: List<InvoiceStatusItem> = emptyList(),
    val hsFormStatuses: List<HSFormStatusItem> = emptyList(),
    val subItemHsOptions: List<HSFormStatusItem> = emptyList(),
    val subItemStatusOptions: List<SubItemStatusItem> = emptyList(),
    val filterColumns: List<FilterColumnOption> = emptyList(),
    // Delete confirm
    val deleteSubItemName: String = ""
)

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val clientRepository: ClientRepository,
    private val preferencesManager: PreferencesManager,
    private val googlePlacesService: GooglePlacesService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DayDetailUiState())
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    private val dateStr: String = savedStateHandle.get<String>("date") ?: LocalDate.now().toString()
    private val shiftIdParam: Int? = savedStateHandle.get<String>("shiftId")?.toIntOrNull()
    private var originalShifts: List<ShiftDto> = emptyList()
    private var allShiftRows: List<ShiftDisplayRow> = emptyList()
    private var addressSearchJob: Job? = null

    init {
        val date = try { LocalDate.parse(dateStr) } catch (e: Exception) { LocalDate.now() }
        val dateFormatted = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.ENGLISH))
        val pageTitleFormatted = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH))

        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            selectedDateString = dateFormatted,
            pageTitle = pageTitleFormatted,
            contractTypes = contractTypeOptions,
            invoiceStatuses = invoiceStatusOptions,
            hsFormStatuses = hsFormStatusOptions,
            subItemHsOptions = hsRequiredOptions,
            subItemStatusOptions = subItemStatusOpts,
            filterColumns = buildFilterColumns()
        )

        viewModelScope.launch {
            val isAdmin = preferencesManager.isAdmin.first()
            val isManager = preferencesManager.isManager.first()
            val isCaregiver = preferencesManager.isCaregiver.first()
            _uiState.value = _uiState.value.copy(
                isAdmin = isAdmin,
                isManager = isManager,
                isCaregiver = isCaregiver,
                isOwner = isManager || isAdmin
            )
            loadClients()
            loadShifts()
        }
    }

    // ===== Data Loading =====

    private suspend fun loadShifts() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        val shifts: List<ShiftDto> = if (shiftIdParam != null) {
            when (val result = shiftRepository.getShiftById(shiftIdParam)) {
                is ApiResult.Success -> listOf(result.data)
                is ApiResult.Error -> {
                    Timber.e("Failed to load shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                    return
                }
                is ApiResult.Loading -> return
            }
        } else {
            val result = if (_uiState.value.isAdmin) {
                shiftRepository.getShiftsByDate(dateStr)
            } else {
                val userId = preferencesManager.getUserId()
                shiftRepository.getShiftsByUserIdAndDate(userId, dateStr)
            }
            when (result) {
                is ApiResult.Success -> result.data.sortedByDescending { it.id }
                is ApiResult.Error -> {
                    Timber.e("Failed to load shifts: ${result.message}")
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
                    return
                }
                is ApiResult.Loading -> return
            }
        }

        originalShifts = shifts
        val rows = processShifts(shifts)
        allShiftRows = rows
        val countText = when (rows.size) {
            0 -> "No jobs scheduled"
            1 -> "1 job scheduled"
            else -> "${rows.size} jobs scheduled"
        }

        _uiState.value = _uiState.value.copy(
            shiftRows = rows,
            projectCountText = countText,
            isLoading = false,
            filterStatusText = "Showing ${rows.size} of ${originalShifts.size} projects"
        )
    }

    private suspend fun processShifts(shifts: List<ShiftDto>): List<ShiftDisplayRow> {
        val isOwner = _uiState.value.isOwner
        val isCaregiver = _uiState.value.isCaregiver
        val clients = _uiState.value.clients

        return shifts.map { rawShift ->
            // Enrich clientName from loaded clients when API returns null
            val shift = if (rawShift.clientName.isNullOrBlank() && rawShift.clientId != null && rawShift.clientId > 0) {
                val clientName = clients.find { it.id == rawShift.clientId }?.name
                if (!clientName.isNullOrBlank()) rawShift.copy(clientName = clientName) else rawShift
            } else rawShift

            // Load sub-items
            val subItems = when (val result = shiftRepository.getSubItemsByJobId(shift.id)) {
                is ApiResult.Success -> result.data
                else -> emptyList()
            }

            // Check quotations - MAUI only shows "View Quotations" when quotations exist AND status == Created AND user is Manager/Admin
            val quotationsExist = when (val result = shiftRepository.getQuotationsByJobId(shift.id)) {
                is ApiResult.Success -> result.data.isNotEmpty()
                else -> false
            }
            val hasQuotations = quotationsExist && shift.statusId == 1 && isOwner

            ShiftDisplayRow(
                shift = shift,
                subItems = subItems,
                statusMessage = getStatusMessage(shift.statusId, hasQuotations),
                statusColor = CalendarViewModel.getStatusColor(shift.statusId, hasQuotations),
                contractTypeText = getContractTypeText(shift.contractType),
                contractTypeColor = CalendarViewModel.getContractTypeColor(shift.contractType),
                invoiceStatusText = getInvoiceStatusText(shift.invoiceStatus),
                invoiceStatusColor = getInvoiceStatusColor(shift.invoiceStatus),
                hsFormText = getHSFormText(shift.hsForms),
                hsFormColor = getHSFormColor(shift.hsForms),
                durationFromFormatted = formatDateTime(shift.durationFrom),
                durationToFormatted = formatDateTime(shift.durationTo),
                hasSubItems = subItems.isNotEmpty(),
                hasQuotations = hasQuotations,
                isAddSubItem = isOwner,
                isDeleteSubItem = isOwner
            )
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

    // ===== Expand / Collapse =====

    fun toggleExpand(shiftId: Int) {
        fun toggle(row: ShiftDisplayRow): ShiftDisplayRow =
            if (row.shift.id == shiftId) row.copy(isExpanded = !row.isExpanded) else row

        allShiftRows = allShiftRows.map { toggle(it) }
        _uiState.value = _uiState.value.copy(
            shiftRows = _uiState.value.shiftRows.map { toggle(it) }
        )
    }

    // ===== Sorting =====

    fun sortBy(column: String) {
        val state = _uiState.value
        val ascending = if (state.sortColumn == column) !state.sortAscending else true

        val comparator = Comparator<ShiftDisplayRow> { a, b ->
            val valA = getSortValue(a, column)
            val valB = getSortValue(b, column)
            valA.compareTo(valB)
        }
        val effectiveComparator = if (ascending) comparator else comparator.reversed()

        allShiftRows = allShiftRows.sortedWith(effectiveComparator)
        _uiState.value = state.copy(
            shiftRows = state.shiftRows.sortedWith(effectiveComparator),
            sortColumn = column,
            sortAscending = ascending
        )
    }

    private fun getSortValue(row: ShiftDisplayRow, column: String): String {
        return when (column) {
            "ProjectName" -> row.shift.projectName
            "ClientName" -> row.shift.clientName ?: ""
            "Address" -> row.shift.address
            "DurationFromString" -> row.shift.durationFrom
            "DurationToString" -> row.shift.durationTo
            "ContractType" -> row.contractTypeText
            "InvoiceStatus" -> row.invoiceStatusText
            "HSForm" -> row.hsFormText
            "FinalMeasure" -> row.shift.finalMeasure
            "Instructions" -> row.shift.instructions
            "Amount" -> (row.shift.amount ?: 0.0).toString().padStart(15, '0')
            "AcceptedQuoteAmount" -> (row.shift.acceptedQuoteAmount ?: 0.0).toString().padStart(15, '0')
            "StatusMessage" -> row.statusMessage
            else -> ""
        }
    }

    fun getSortIcon(column: String): String {
        val state = _uiState.value
        if (state.sortColumn != column) return ""
        return if (state.sortAscending) "▲" else "▼"
    }

    // ===== Filters =====

    fun addFilter() {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters + FilterRow()
        )
    }

    fun updatePendingFilterColumn(filterId: Long, column: FilterColumnOption) {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters.map {
                if (it.id == filterId) it.copy(selectedColumn = column, selectedCondition = "", value = "") else it
            }
        )
    }

    fun updatePendingFilterCondition(filterId: Long, condition: String) {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters.map {
                if (it.id == filterId) it.copy(selectedCondition = condition) else it
            }
        )
    }

    fun updatePendingFilterValue(filterId: Long, value: String) {
        _uiState.value = _uiState.value.copy(
            pendingFilters = _uiState.value.pendingFilters.map {
                if (it.id == filterId) it.copy(value = value) else it
            }
        )
    }

    fun removeFilter(filterId: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(
            pendingFilters = state.pendingFilters.filter { it.id != filterId },
            activeFilters = state.activeFilters.filter { it.id != filterId }
        )
        applyFiltersToData()
    }

    fun applyFilters() {
        val state = _uiState.value
        val complete = state.pendingFilters.filter { it.isComplete }
        if (complete.isEmpty()) return
        _uiState.value = state.copy(
            activeFilters = state.activeFilters + complete.map { it.copy(isApplied = true) },
            pendingFilters = state.pendingFilters.filter { !it.isComplete }
        )
        applyFiltersToData()
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(
            pendingFilters = emptyList(),
            activeFilters = emptyList()
        )
        applyFiltersToData()
    }

    private fun applyFiltersToData() {
        val active = _uiState.value.activeFilters
        if (active.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                shiftRows = allShiftRows,
                filterStatusText = "Showing ${allShiftRows.size} of ${allShiftRows.size} projects"
            )
            return
        }

        val filtered = allShiftRows.filter { row ->
            active.all { filter -> matchesFilterRow(row, filter) }
        }
        _uiState.value = _uiState.value.copy(
            shiftRows = filtered,
            filterStatusText = "Showing ${filtered.size} of ${allShiftRows.size} projects"
        )
    }

    private fun matchesFilterRow(row: ShiftDisplayRow, filter: FilterRow): Boolean {
        val column = filter.selectedColumn ?: return true
        val condition = filter.selectedCondition
        val filterValue = filter.value.lowercase()

        if (column.isSubItem) {
            if (row.subItems.isEmpty()) {
                return condition == "is empty"
            }
            return row.subItems.any { subItem ->
                val propValue = getSubItemPropertyValue(subItem, column.propertyName).lowercase()
                evaluateCondition(propValue, condition, filterValue)
            }
        }

        val propValue = if (column.propertyName == "StatusMessage") {
            row.statusMessage.lowercase()
        } else {
            getShiftPropertyValue(row.shift, column.propertyName).lowercase()
        }
        return evaluateCondition(propValue, condition, filterValue)
    }

    private fun evaluateCondition(propValue: String, condition: String, filterValue: String): Boolean {
        return when (condition) {
            "contains" -> propValue.contains(filterValue)
            "doesn't contain" -> !propValue.contains(filterValue)
            "starts with" -> propValue.startsWith(filterValue)
            "is" -> propValue == filterValue
            "is not" -> propValue != filterValue
            "is empty" -> propValue.isBlank() || propValue == "not set" || propValue == "not started" || propValue == "not completed"
            "is not empty" -> propValue.isNotBlank() && propValue != "not set" && propValue != "not started" && propValue != "not completed"
            else -> true
        }
    }

    private fun getSubItemPropertyValue(subItem: ShiftSubItemDto, propertyName: String): String {
        return when (propertyName) {
            "SubItem" -> subItem.subitem
            "SubItemHSRequired" -> getSubItemHSText(subItem.hsRequired)
            "SubItemStatus" -> getSubItemStatusText(subItem.status)
            "SubItemDateStarted" -> subItem.dateStartedString
            "SubItemCompleted" -> subItem.dateCompletedString
            else -> ""
        }
    }

    private fun getShiftPropertyValue(shift: ShiftDto, propertyName: String): String {
        return when (propertyName) {
            "ProjectName" -> shift.projectName
            "ClientName" -> shift.clientName ?: ""
            "Address" -> shift.address
            "DurationFrom" -> formatDateTime(shift.durationFrom)
            "DurationTo" -> formatDateTime(shift.durationTo)
            "ContractType" -> getContractTypeText(shift.contractType)
            "InvoiceStatus" -> getInvoiceStatusText(shift.invoiceStatus)
            "HSForm" -> getHSFormText(shift.hsForms)
            "FinalMeasure" -> shift.finalMeasure
            "Instructions" -> shift.instructions
            "StatusMessage" -> getStatusMessage(shift.statusId, shift.hasQuotations)
            else -> ""
        }
    }

    // ===== Inline Editing - Open Dialogs =====

    fun editProjectName(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val shift = findShift(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.EDITOR,
            editingShiftId = shiftId,
            editorTitle = "Edit Project Name",
            editorText = shift.projectName,
            editorFieldName = "ProjectName"
        )
    }

    fun editFinalMeasure(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val shift = findShift(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.EDITOR,
            editingShiftId = shiftId,
            editorTitle = "Edit Final Measure",
            editorText = shift.finalMeasure,
            editorFieldName = "FinalMeasure"
        )
    }

    fun editJobDescription(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val shift = findShift(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.EDITOR,
            editingShiftId = shiftId,
            editorTitle = "Edit Job Description",
            editorText = shift.instructions,
            editorFieldName = "Instructions"
        )
    }

    fun editDurationFrom(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val shift = findShift(shiftId) ?: return
        val (date, time) = parseDateTimeParts(shift.durationFrom)
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.DATE_TIME,
            editingShiftId = shiftId,
            editorFieldName = "DurationFrom",
            editDate = date,
            editTime = time,
            editIsAllDay = shift.isAllDay
        )
    }

    fun editDurationTo(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val shift = findShift(shiftId) ?: return
        val (date, time) = parseDateTimeParts(shift.durationTo)
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.DATE_TIME,
            editingShiftId = shiftId,
            editorFieldName = "DurationTo",
            editDate = date,
            editTime = time,
            editIsAllDay = shift.isAllDay
        )
    }

    fun editAddress(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        val shift = findShift(shiftId) ?: return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.ADDRESS_SEARCH,
            editingShiftId = shiftId,
            addressSearchText = shift.address,
            placeSuggestions = emptyList()
        )
    }

    fun openContractType(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.CONTRACT_TYPE,
            editingShiftId = shiftId
        )
    }

    fun openClientSelect(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.CLIENT_SELECT,
            editingShiftId = shiftId
        )
    }

    fun openInvoiceStatus(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.INVOICE_STATUS,
            editingShiftId = shiftId
        )
    }

    fun openHSFormStatus(shiftId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.HS_FORM_STATUS,
            editingShiftId = shiftId
        )
    }

    fun openSubItemHS(shiftId: Int, subItemId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.SUB_ITEM_HS,
            editingShiftId = shiftId,
            editingSubItemId = subItemId
        )
    }

    fun openSubItemStatus(shiftId: Int, subItemId: Int) {
        if (_uiState.value.isCaregiver) return
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.SUB_ITEM_STATUS,
            editingShiftId = shiftId,
            editingSubItemId = subItemId
        )
    }

    fun openSubItemDateStarted(shiftId: Int, subItemId: Int) {
        if (_uiState.value.isCaregiver) return
        val subItem = findSubItem(shiftId, subItemId) ?: return
        val (date, time) = if (!subItem.dateStarted.isNullOrBlank()) {
            parseDateTimeParts(subItem.dateStarted)
        } else {
            Pair(LocalDate.now(), LocalTime.NOON)
        }
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.SUB_ITEM_DATE,
            editingShiftId = shiftId,
            editingSubItemId = subItemId,
            editDate = date,
            editTime = time
        )
    }

    fun confirmDeleteSubItem(shiftId: Int, subItemId: Int) {
        val subItem = findSubItem(shiftId, subItemId)
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.DELETE_SUB_ITEM,
            editingShiftId = shiftId,
            editingSubItemId = subItemId,
            deleteSubItemName = subItem?.subitem ?: "this sub-item"
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            activeDialog = DayDetailDialog.NONE,
            placeSuggestions = emptyList()
        )
    }

    // ===== Inline Editing - Save Actions =====

    fun saveEditorText(text: String) {
        val state = _uiState.value
        val shift = findShift(state.editingShiftId) ?: return
        val updated = when (state.editorFieldName) {
            "ProjectName" -> shift.copy(projectName = text)
            "FinalMeasure" -> shift.copy(finalMeasure = text)
            "Instructions" -> shift.copy(instructions = text)
            else -> shift
        }
        dismissDialog()
        updateShift(updated)
    }

    fun saveDateTime(date: LocalDate, time: LocalTime) {
        val state = _uiState.value
        val shift = findShift(state.editingShiftId) ?: return
        val dateTimeStr = "${date}T${time.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
        val updated = when (state.editorFieldName) {
            "DurationFrom" -> shift.copy(durationFrom = dateTimeStr)
            "DurationTo" -> shift.copy(durationTo = dateTimeStr)
            else -> shift
        }
        dismissDialog()
        updateShift(updated)
    }

    fun saveAddress(address: String, latitude: Double, longitude: Double) {
        val shift = findShift(_uiState.value.editingShiftId) ?: return
        val updated = shift.copy(address = address, latitude = latitude, longitude = longitude)
        dismissDialog()
        updateShift(updated)
    }

    fun selectContractType(item: ContractTypeItem) {
        val shift = findShift(_uiState.value.editingShiftId) ?: return
        val updated = shift.copy(contractType = item.id)
        dismissDialog()
        updateShift(updated)
    }

    fun selectClient(client: ClientDto) {
        val shift = findShift(_uiState.value.editingShiftId) ?: return
        val updated = shift.copy(
            clientId = if (client.id > 0) client.id else null,
            clientName = if (client.id > 0) client.name else ""
        )
        dismissDialog()
        updateShift(updated)
    }

    fun selectInvoiceStatus(item: InvoiceStatusItem) {
        val shift = findShift(_uiState.value.editingShiftId) ?: return
        val updated = shift.copy(invoiceStatus = item.id)
        dismissDialog()
        updateShift(updated)
    }

    fun selectHSFormStatus(item: HSFormStatusItem) {
        val shift = findShift(_uiState.value.editingShiftId) ?: return
        val updated = shift.copy(hsForms = item.id)
        dismissDialog()
        updateShift(updated)
    }

    fun selectSubItemHS(item: HSFormStatusItem) {
        val state = _uiState.value
        val subItem = findSubItem(state.editingShiftId, state.editingSubItemId) ?: return
        val updated = subItem.copy(hsRequired = item.id)
        dismissDialog()
        updateSubItem(state.editingShiftId, updated)
    }

    fun selectSubItemStatus(item: SubItemStatusItem) {
        val state = _uiState.value
        val subItem = findSubItem(state.editingShiftId, state.editingSubItemId) ?: return
        val updated = subItem.copy(status = item.id)
        dismissDialog()
        updateSubItem(state.editingShiftId, updated)
    }

    fun saveSubItemDate(date: LocalDate, time: LocalTime) {
        val state = _uiState.value
        val subItem = findSubItem(state.editingShiftId, state.editingSubItemId) ?: return
        val dateTimeStr = "${date}T${time.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
        val updated = subItem.copy(dateStarted = dateTimeStr)
        dismissDialog()
        updateSubItem(state.editingShiftId, updated)
    }

    // ===== Address Search =====

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

    // ===== Sub-Item CRUD =====

    fun updateNewSubItemName(shiftId: Int, name: String) {
        _uiState.value = _uiState.value.copy(
            shiftRows = _uiState.value.shiftRows.map {
                if (it.shift.id == shiftId) it.copy(newSubItemName = name) else it
            }
        )
    }

    fun addSubItem(shiftId: Int) {
        val row = _uiState.value.shiftRows.find { it.shift.id == shiftId } ?: return
        val name = row.newSubItemName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
            val newSubItem = ShiftSubItemDto(
                shiftId = shiftId,
                subitem = name,
                createdBy = userId,
                createdDate = now,
                modifiedBy = userId,
                modifiedDate = now
            )

            when (val result = shiftRepository.addSubItems(newSubItem)) {
                is ApiResult.Success -> {
                    Timber.d("Sub-item added: ${result.data.id}")
                    val addedSubItem = result.data
                    fun addToRow(row: ShiftDisplayRow): ShiftDisplayRow {
                        if (row.shift.id != shiftId) return row
                        val updatedSubItems = row.subItems + addedSubItem
                        return row.copy(
                            subItems = updatedSubItems,
                            hasSubItems = true,
                            newSubItemName = ""
                        )
                    }
                    allShiftRows = allShiftRows.map { addToRow(it) }
                    _uiState.value = _uiState.value.copy(
                        shiftRows = _uiState.value.shiftRows.map { addToRow(it) }
                    )
                    checkAndUpdateMainHSStatusInPlace(shiftId)
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to add sub-item: ${result.message}")
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun deleteSubItem() {
        val state = _uiState.value
        val subItemId = state.editingSubItemId
        dismissDialog()

        viewModelScope.launch {
            when (val result = shiftRepository.deleteSubItem(subItemId)) {
                is ApiResult.Success -> {
                    Timber.d("Sub-item deleted")
                    fun removeFromRow(row: ShiftDisplayRow): ShiftDisplayRow {
                        val updatedSubItems = row.subItems.filter { it.id != subItemId }
                        if (updatedSubItems.size == row.subItems.size) return row
                        return row.copy(
                            subItems = updatedSubItems,
                            hasSubItems = updatedSubItems.isNotEmpty()
                        )
                    }
                    allShiftRows = allShiftRows.map { removeFromRow(it) }
                    _uiState.value = _uiState.value.copy(
                        shiftRows = _uiState.value.shiftRows.map { removeFromRow(it) }
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to delete sub-item: ${result.message}")
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // ===== API Update Calls =====

    private fun updateShift(shift: ShiftDto) {
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val shiftToUpdate = shift.copy(modifiedBy = userId)
            when (val result = shiftRepository.updateShift(shiftToUpdate)) {
                is ApiResult.Success -> {
                    Timber.d("Shift updated")
                    updateShiftInPlace(result.data)
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to update shift: ${result.message}")
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    private fun updateShiftInPlace(updatedShift: ShiftDto) {
        val isOwner = _uiState.value.isOwner
        val clients = _uiState.value.clients

        // Enrich clientName if needed
        val enrichedShift = if (updatedShift.clientName.isNullOrBlank() && updatedShift.clientId != null && updatedShift.clientId > 0) {
            val clientName = clients.find { it.id == updatedShift.clientId }?.name
            if (!clientName.isNullOrBlank()) updatedShift.copy(clientName = clientName) else updatedShift
        } else updatedShift

        fun updateRow(row: ShiftDisplayRow): ShiftDisplayRow {
            if (row.shift.id != enrichedShift.id) return row
            val hasQuotations = row.hasQuotations // preserve existing quotation state
            return row.copy(
                shift = enrichedShift,
                statusMessage = getStatusMessage(enrichedShift.statusId, hasQuotations),
                statusColor = CalendarViewModel.getStatusColor(enrichedShift.statusId, hasQuotations),
                contractTypeText = getContractTypeText(enrichedShift.contractType),
                contractTypeColor = CalendarViewModel.getContractTypeColor(enrichedShift.contractType),
                invoiceStatusText = getInvoiceStatusText(enrichedShift.invoiceStatus),
                invoiceStatusColor = getInvoiceStatusColor(enrichedShift.invoiceStatus),
                hsFormText = getHSFormText(enrichedShift.hsForms),
                hsFormColor = getHSFormColor(enrichedShift.hsForms),
                durationFromFormatted = formatDateTime(enrichedShift.durationFrom),
                durationToFormatted = formatDateTime(enrichedShift.durationTo)
            )
        }

        // Update originalShifts and allShiftRows
        originalShifts = originalShifts.map { if (it.id == enrichedShift.id) enrichedShift else it }
        allShiftRows = allShiftRows.map { updateRow(it) }

        _uiState.value = _uiState.value.copy(
            shiftRows = _uiState.value.shiftRows.map { updateRow(it) }
        )
    }

    private fun updateSubItem(shiftId: Int, subItem: ShiftSubItemDto) {
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val subItemToUpdate = subItem.copy(modifiedBy = userId)
            when (val result = shiftRepository.editSubItems(subItemToUpdate)) {
                is ApiResult.Success -> {
                    Timber.d("Sub-item updated")
                    val returnedSubItem = result.data
                    updateSubItemInPlace(shiftId, returnedSubItem)
                    checkAndUpdateMainHSStatusInPlace(shiftId)
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to update sub-item: ${result.message}")
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    private fun updateSubItemInPlace(shiftId: Int, updatedSubItem: ShiftSubItemDto) {
        fun updateRow(row: ShiftDisplayRow): ShiftDisplayRow {
            if (row.shift.id != shiftId) return row
            val updatedSubItems = row.subItems.map { sub ->
                if (sub.id == updatedSubItem.id) updatedSubItem else sub
            }
            return row.copy(subItems = updatedSubItems)
        }

        allShiftRows = allShiftRows.map { updateRow(it) }
        _uiState.value = _uiState.value.copy(
            shiftRows = _uiState.value.shiftRows.map { updateRow(it) }
        )
    }

    private suspend fun checkAndUpdateMainHSStatusInPlace(shiftId: Int) {
        val row = allShiftRows.find { it.shift.id == shiftId } ?: return
        val subItems = row.subItems
        if (subItems.isEmpty()) return

        val allDoneOrNoHS = subItems.all { it.hsRequired == 4 || it.hsRequired == 0 }
        if (allDoneOrNoHS && row.shift.hsForms != 4) {
            val userId = preferencesManager.getUserId()
            val updatedShift = row.shift.copy(hsForms = 4, modifiedBy = userId)
            shiftRepository.updateShift(updatedShift)
            updateShiftInPlace(updatedShift)
        }
    }


    // ===== Editor State Updates =====

    fun updateEditorText(text: String) {
        _uiState.value = _uiState.value.copy(editorText = text)
    }

    fun updateEditDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(editDate = date)
    }

    fun updateEditTime(time: LocalTime) {
        _uiState.value = _uiState.value.copy(editTime = time)
    }

    // ===== Helpers =====

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refresh() {
        viewModelScope.launch { loadShifts() }
    }

    private fun findShift(shiftId: Int): ShiftDto? {
        return _uiState.value.shiftRows.find { it.shift.id == shiftId }?.shift
    }

    private fun findSubItem(shiftId: Int, subItemId: Int): ShiftSubItemDto? {
        return _uiState.value.shiftRows
            .find { it.shift.id == shiftId }
            ?.subItems
            ?.find { it.id == subItemId }
    }

    private fun parseDateTimeParts(dateStr: String): Pair<LocalDate, LocalTime> {
        return try {
            val date = LocalDate.parse(dateStr.substring(0, 10))
            val time = if (dateStr.length >= 19) {
                LocalTime.parse(dateStr.substring(11, 19))
            } else LocalTime.NOON
            Pair(date, time)
        } catch (e: Exception) {
            Pair(LocalDate.now(), LocalTime.NOON)
        }
    }

    // ===== Static Text/Color Helpers =====

    companion object {
        fun getStatusMessage(statusId: Int, hasQuotations: Boolean): String {
            return when (statusId) {
                1 -> if (hasQuotations) "Quoted" else "Created"
                2 -> "Accepted"
                3 -> "Started"
                4 -> "End"
                5 -> "Not Completed"
                6 -> "Completed"
                else -> ""
            }
        }

        fun getContractTypeText(contractType: Int?): String {
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
                else -> ""
            }
        }

        fun getInvoiceStatusText(invoiceStatus: Int?): String {
            return when (invoiceStatus) {
                1 -> "Not Yet Created"
                2 -> "To Be Invoiced"
                3 -> "Invoice Drafted"
                4 -> "Invoice Sent"
                else -> ""
            }
        }

        fun getHSFormText(hsForms: Int?): String {
            return when (hsForms) {
                0 -> "No H&S"
                1 -> "SSSP"
                2 -> "JSA"
                3 -> "Take 5"
                4 -> "Done"
                5 -> "Missing H&S"
                else -> ""
            }
        }

        fun getHSFormColor(hsForms: Int?): Long {
            return when (hsForms) {
                0 -> 0xFFC4C4C4
                1 -> 0xFF00C875
                2 -> 0xFF007EB5
                3 -> 0xFFFF0000
                4 -> 0xFFFFCB00
                5 -> 0xFF808080
                else -> 0xFFFFFFFF
            }
        }

        fun getInvoiceStatusColor(invoiceStatus: Int?): Long {
            return when (invoiceStatus) {
                1 -> 0xFFC4C4C4
                2 -> 0xFFFF6D3B
                3 -> 0xFFFF0000
                4 -> 0xFFFFCB00
                else -> 0xFFFFFFFF
            }
        }

        fun getSubItemHSColor(hsRequired: Int): Long {
            return when (hsRequired) {
                0 -> 0xFFC4C4C4
                1 -> 0xFF00C875
                2 -> 0xFF007EB5
                3 -> 0xFFFF0000
                4 -> 0xFFFFCB00
                5 -> 0xFF808080
                else -> 0xFFFFFFFF
            }
        }

        fun getSubItemHSText(hsRequired: Int): String {
            return when (hsRequired) {
                0 -> "No H&S"
                1 -> "SSSP"
                2 -> "JSA"
                3 -> "Take 5"
                4 -> "Done"
                5 -> "Missing H&S"
                else -> ""
            }
        }

        fun getSubItemStatusColor(status: Int): Long {
            return when (status) {
                1 -> 0xFF9D50DD
                2 -> 0xFF00C875
                3 -> 0xFFFF0000
                4 -> 0xFFFFCB00
                else -> 0xFFFFFFFF
            }
        }

        fun getSubItemStatusText(status: Int): String {
            return when (status) {
                1 -> "Awaiting previous"
                2 -> "Working on it"
                3 -> "Stuck"
                4 -> "Done"
                else -> ""
            }
        }

        fun formatDateTime(dateStr: String): String {
            if (dateStr.length < 16) return dateStr
            return try {
                val date = LocalDate.parse(dateStr.substring(0, 10))
                val time = LocalTime.parse(dateStr.substring(11, minOf(19, dateStr.length)))
                val dateFmt = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                val timeFmt = time.format(DateTimeFormatter.ofPattern("h:mm a"))
                "$dateFmt $timeFmt"
            } catch (e: Exception) { dateStr }
        }

        // Option lists
        val contractTypeOptions = listOf(
            ContractTypeItem(1, "To Be Confirmed", 0xFFC4C4C4),
            ContractTypeItem(2, "Full Contract", 0xFFBCA58A),
            ContractTypeItem(3, "Supply Place And Finish", 0xFF74AFCC),
            ContractTypeItem(4, "Place And Finish", 0xFFCAB641),
            ContractTypeItem(5, "Labour Supply", 0xFF175A63),
            ContractTypeItem(6, "Box Place And Finish", 0xFF333333),
            ContractTypeItem(7, "Remedial", 0xFFFF0000),
            ContractTypeItem(8, "Supply Place Finish And Cut", 0xFF037F4C),
            ContractTypeItem(9, "Place Finish And Cut", 0xFF7F5347),
            ContractTypeItem(10, "Other Services", 0xFF7F00FF),
            ContractTypeItem(11, "Meetings", 0xFFFF8DA1)
        )

        val invoiceStatusOptions = listOf(
            InvoiceStatusItem(1, "Not Yet Created", 0xFFC4C4C4),
            InvoiceStatusItem(2, "To Be Invoiced", 0xFFFF6D3B),
            InvoiceStatusItem(3, "Invoice Drafted", 0xFFFF0000),
            InvoiceStatusItem(4, "Invoice Sent", 0xFFFFCB00)
        )

        val hsFormStatusOptions = listOf(
            HSFormStatusItem(0, "No H&S", 0xFFC4C4C4),
            HSFormStatusItem(1, "SSSP", 0xFF00C875),
            HSFormStatusItem(2, "JSA", 0xFF007EB5),
            HSFormStatusItem(3, "Take 5", 0xFFFF0000),
            HSFormStatusItem(4, "Done", 0xFFFFCB00),
            HSFormStatusItem(5, "Missing H&S", 0xFF808080)
        )

        val hsRequiredOptions = listOf(
            HSFormStatusItem(0, "No H&S", 0xFFC4C4C4),
            HSFormStatusItem(1, "SSSP", 0xFF00C875),
            HSFormStatusItem(2, "JSA", 0xFF007EB5),
            HSFormStatusItem(3, "Take 5", 0xFFFF0000),
            HSFormStatusItem(4, "Done", 0xFFFFCB00),
            HSFormStatusItem(5, "Missing H&S", 0xFF808080)
        )

        val subItemStatusOpts = listOf(
            SubItemStatusItem(1, "Awaiting previous", 0xFF9D50DD),
            SubItemStatusItem(2, "Working on it", 0xFF00C875),
            SubItemStatusItem(3, "Stuck", 0xFFFF0000),
            SubItemStatusItem(4, "Done", 0xFFFFCB00)
        )

        fun buildFilterColumns(): List<FilterColumnOption> {
            return listOf(
                // Item columns
                FilterColumnOption("ProjectName", "Project Name"),
                FilterColumnOption("ClientName", "Client Name"),
                FilterColumnOption("Address", "Address"),
                FilterColumnOption("DurationFrom", "Duration From"),
                FilterColumnOption("DurationTo", "Duration To"),
                FilterColumnOption("ContractType", "Contract Type"),
                FilterColumnOption("InvoiceStatus", "Invoice Status"),
                FilterColumnOption("HSForm", "H&S Forms"),
                FilterColumnOption("FinalMeasure", "Final Measure"),
                FilterColumnOption("Instructions", "Job Description"),
                FilterColumnOption("StatusMessage", "Status"),
                // Subitem columns
                FilterColumnOption("SubItem", "Sub Item", isSubItem = true),
                FilterColumnOption("SubItemHSRequired", "H&S Required", isSubItem = true),
                FilterColumnOption("SubItemStatus", "Status (Sub Item)", isSubItem = true),
                FilterColumnOption("SubItemDateStarted", "Date Started (Sub Item)", isSubItem = true),
                FilterColumnOption("SubItemCompleted", "Completed (Sub Item)", isSubItem = true)
            )
        }
    }
}
