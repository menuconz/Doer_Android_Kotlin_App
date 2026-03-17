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
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

data class CalendarDayItem(
    val date: LocalDate,
    val dayLetter: String,
    val dayNumber: String,
    val monthName: String,
    val isSelected: Boolean,
    val isToday: Boolean
)

data class DayShiftItem(
    val shift: ShiftDto,
    val projectName: String,
    val address: String,
    val durationText: String,
    val statusMessage: String,
    val contractColor: Long
)

data class TimelineBlock(
    val shift: ShiftDto,
    val projectName: String,
    val address: String,
    val durationText: String,
    val contractColor: Long,
    val startHour: Double,
    val durationHours: Double,
    val columnIndex: Int,
    val totalColumns: Int
)

data class DayTimelineUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val pageTitle: String = "",
    val calendarDays: List<CalendarDayItem> = emptyList(),
    val totalJobs: Int = 0,
    val allDayJobs: List<DayShiftItem> = emptyList(),
    val timelineBlocks: List<TimelineBlock> = emptyList(),
    val dayShifts: List<DayShiftItem> = emptyList(),
    val isLoading: Boolean = false,
    val isManager: Boolean = false,
    val isAdmin: Boolean = false
)

@HiltViewModel
class DayTimelineViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(DayTimelineUiState())
    val uiState: StateFlow<DayTimelineUiState> = _uiState.asStateFlow()

    private val hourHeight = 80 // dp per hour
    private var earliestDate: LocalDate = LocalDate.now().minusDays(15)
    private var latestDate: LocalDate = LocalDate.now().plusDays(15)

    init {
        val dateStr = savedStateHandle.get<String>("date") ?: LocalDate.now().toString()
        val date = try { LocalDate.parse(dateStr) } catch (e: Exception) { LocalDate.now() }

        viewModelScope.launch {
            val isAdmin = preferencesManager.isAdmin.first()
            val isManager = preferencesManager.isManager.first() || preferencesManager.isCustomer.first()
            _uiState.value = _uiState.value.copy(
                isAdmin = isAdmin,
                isManager = isManager
            )
            selectDate(date)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadShiftsForDate(_uiState.value.selectedDate)
        }
    }

    fun selectDate(date: LocalDate) {
        val title = date.format(DateTimeFormatter.ofPattern("EEEE — MMM d, yyyy", Locale.ENGLISH))
        val calendarDays = generateCalendarDays(date)

        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            pageTitle = title,
            calendarDays = calendarDays,
            isLoading = true
        )

        viewModelScope.launch {
            loadShiftsForDate(date)
        }
    }

    private fun generateCalendarDays(selectedDate: LocalDate): List<CalendarDayItem> {
        // Expand range if selected date is near or beyond edges
        if (selectedDate.minusDays(15) < earliestDate) {
            earliestDate = selectedDate.minusDays(15)
        }
        if (selectedDate.plusDays(15) > latestDate) {
            latestDate = selectedDate.plusDays(15)
        }

        val today = LocalDate.now()
        val days = mutableListOf<CalendarDayItem>()
        var date = earliestDate
        while (date <= latestDate) {
            days.add(createCalendarDayItem(date, selectedDate, today))
            date = date.plusDays(1)
        }
        return days
    }

    private fun createCalendarDayItem(date: LocalDate, selectedDate: LocalDate, today: LocalDate): CalendarDayItem {
        return CalendarDayItem(
            date = date,
            dayLetter = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).first().uppercase(),
            dayNumber = date.dayOfMonth.toString(),
            monthName = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            isSelected = date == selectedDate,
            isToday = date == today
        )
    }

    fun loadMorePastDates(): Int {
        val today = LocalDate.now()
        val selectedDate = _uiState.value.selectedDate
        val newEarliest = earliestDate.minusDays(10)
        val newDays = mutableListOf<CalendarDayItem>()
        var date = newEarliest
        while (date < earliestDate) {
            newDays.add(createCalendarDayItem(date, selectedDate, today))
            date = date.plusDays(1)
        }
        earliestDate = newEarliest
        _uiState.value = _uiState.value.copy(
            calendarDays = newDays + _uiState.value.calendarDays
        )
        return newDays.size
    }

    fun loadMoreFutureDates() {
        val today = LocalDate.now()
        val selectedDate = _uiState.value.selectedDate
        val newLatest = latestDate.plusDays(10)
        val newDays = mutableListOf<CalendarDayItem>()
        var date = latestDate.plusDays(1)
        while (date <= newLatest) {
            newDays.add(createCalendarDayItem(date, selectedDate, today))
            date = date.plusDays(1)
        }
        latestDate = newLatest
        _uiState.value = _uiState.value.copy(
            calendarDays = _uiState.value.calendarDays + newDays
        )
    }

    private suspend fun loadShiftsForDate(date: LocalDate) {
        val dateStr = date.toString()
        val result = if (_uiState.value.isAdmin) {
            shiftRepository.getShiftsByDate(dateStr)
        } else {
            val userId = preferencesManager.getUserId()
            shiftRepository.getShiftsByUserIdAndDate(userId, dateStr)
        }

        val shifts = when (result) {
            is ApiResult.Success -> result.data.sortedByDescending { it.id }
            is ApiResult.Error -> {
                Timber.e("Failed to load shifts: ${result.message}")
                emptyList()
            }
            is ApiResult.Loading -> emptyList()
        }

        val allDayShifts = shifts.filter { it.isAllDay }
        val timedShifts = shifts.filter { !it.isAllDay }

        val allDayItems = allDayShifts.map { shift ->
            createDayShiftItem(shift, date)
        }

        val dayShiftItems = shifts.sortedBy { it.durationFrom }.map { shift ->
            createDayShiftItem(shift, date)
        }

        val timelineBlocks = createTimelineBlocks(timedShifts, date)

        _uiState.value = _uiState.value.copy(
            totalJobs = shifts.size,
            allDayJobs = allDayItems,
            dayShifts = dayShiftItems,
            timelineBlocks = timelineBlocks,
            isLoading = false
        )
    }

    private fun createDayShiftItem(shift: ShiftDto, viewDate: LocalDate): DayShiftItem {
        val fromDate = parseLocalDate(shift.durationFrom)
        val toDate = parseLocalDate(shift.durationTo)
        val isMultiDay = fromDate != null && toDate != null && fromDate != toDate

        var projectName = shift.projectName.ifBlank { "Unnamed Job" }
        var durationText = ""

        if (isMultiDay && fromDate != null && toDate != null) {
            val dayNumber = ChronoUnit.DAYS.between(fromDate, viewDate).toInt() + 1
            val totalDays = ChronoUnit.DAYS.between(fromDate, toDate).toInt() + 1
            projectName = "$projectName (Day $dayNumber/$totalDays)"

            durationText = when (viewDate) {
                fromDate -> "${formatTime(shift.durationFrom)} - 11:59 PM"
                toDate -> "12:00 AM - ${formatTime(shift.durationTo)}"
                else -> "${shift.durationFrom} - ${shift.durationTo}"
            }
        } else {
            durationText = "${formatTime(shift.durationFrom)} - ${formatTime(shift.durationTo)}"
        }

        val statusMessage = getStatusMessage(shift.statusId, shift.hasQuotations)

        return DayShiftItem(
            shift = shift,
            projectName = projectName,
            address = shift.address,
            durationText = durationText,
            statusMessage = statusMessage,
            contractColor = CalendarViewModel.getContractTypeColor(shift.contractType)
        )
    }

    private fun createTimelineBlocks(shifts: List<ShiftDto>, viewDate: LocalDate): List<TimelineBlock> {
        data class BlockData(
            val shift: ShiftDto,
            val startHour: Double,
            val durationHours: Double,
            val projectName: String,
            val durationText: String
        )

        val blocks = shifts.mapNotNull { shift ->
            val fromDate = parseLocalDate(shift.durationFrom)
            val toDate = parseLocalDate(shift.durationTo)
            val isMultiDay = fromDate != null && toDate != null && fromDate != toDate

            val startHour: Double
            val endHour: Double
            val durationText: String
            var projectName = shift.projectName.ifBlank { "Unnamed Job" }

            if (isMultiDay && fromDate != null && toDate != null) {
                val dayNumber = ChronoUnit.DAYS.between(fromDate, viewDate).toInt() + 1
                val totalDays = ChronoUnit.DAYS.between(fromDate, toDate).toInt() + 1
                projectName = "$projectName (Day $dayNumber/$totalDays)"

                when (viewDate) {
                    fromDate -> {
                        startHour = parseTimeHours(shift.durationFrom)
                        endHour = 23.99
                        durationText = "${formatTime(shift.durationFrom)} - 11:59 PM"
                    }
                    toDate -> {
                        startHour = 0.0
                        endHour = parseTimeHours(shift.durationTo).let { if (it == 0.0) 0.1 else it }
                        durationText = "12:00 AM - ${formatTime(shift.durationTo)}"
                    }
                    else -> {
                        startHour = 0.0
                        endHour = 23.99
                        durationText = "All Day"
                    }
                }
            } else {
                startHour = parseTimeHours(shift.durationFrom)
                val rawEnd = parseTimeHours(shift.durationTo)
                endHour = if (rawEnd <= startHour) rawEnd + 24 else rawEnd
                durationText = "${formatTime(shift.durationFrom)} - ${formatTime(shift.durationTo)}"
            }

            val durationHours = (endHour - startHour).coerceAtLeast(0.5)
            BlockData(shift, startHour, durationHours, projectName, durationText)
        }.sortedBy { it.startHour }

        // Assign columns for overlapping blocks
        val result = mutableListOf<TimelineBlock>()
        val columns = mutableListOf<MutableList<BlockData>>()

        for (block in blocks) {
            var assignedCol = -1
            for (i in columns.indices) {
                val canFit = columns[i].all { existing ->
                    val existEnd = existing.startHour + existing.durationHours
                    block.startHour >= existEnd || (block.startHour + block.durationHours) <= existing.startHour
                }
                if (canFit) {
                    assignedCol = i
                    break
                }
            }
            if (assignedCol == -1) {
                columns.add(mutableListOf())
                assignedCol = columns.size - 1
            }
            columns[assignedCol].add(block)

            result.add(
                TimelineBlock(
                    shift = block.shift,
                    projectName = block.projectName,
                    address = block.shift.address,
                    durationText = block.durationText,
                    contractColor = CalendarViewModel.getContractTypeColor(block.shift.contractType),
                    startHour = block.startHour,
                    durationHours = block.durationHours,
                    columnIndex = assignedCol,
                    totalColumns = 0 // will update below
                )
            )
        }

        // Update totalColumns
        val totalCols = columns.size.coerceAtLeast(1)
        return result.map { it.copy(totalColumns = totalCols) }
    }

    private fun parseLocalDate(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        return try {
            LocalDate.parse(dateStr.substring(0, 10))
        } catch (e: Exception) { null }
    }

    private fun parseTimeHours(dateStr: String): Double {
        if (dateStr.length < 19) return 0.0
        return try {
            val time = LocalTime.parse(dateStr.substring(11, 19))
            time.hour + time.minute / 60.0
        } catch (e: Exception) { 0.0 }
    }

    private fun formatTime(dateStr: String): String {
        if (dateStr.length < 19) return ""
        return try {
            val time = LocalTime.parse(dateStr.substring(11, 19))
            time.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (e: Exception) { "" }
    }

    private fun getStatusMessage(statusId: Int, hasQuotations: Boolean): String {
        return when (statusId) {
            1 -> if (hasQuotations) "Quoted" else "Created"
            2 -> "Accepted"
            3 -> "Started"
            4 -> "End"
            5 -> "Not Completed"
            6 -> "Completed"
            else -> "Unknown"
        }
    }
}
