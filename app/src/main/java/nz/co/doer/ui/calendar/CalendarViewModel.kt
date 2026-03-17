package nz.co.doer.ui.calendar

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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class CalendarDay(
    val date: LocalDate,
    val dayNumber: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val extraEventsCount: Int = 0
)

data class CalendarWeek(
    val days: List<CalendarDay>,
    val multiDayEvents: List<MultiDayEvent>
)

data class MultiDayEvent(
    val id: Int,
    val title: String,
    val color: Long,
    val shift: ShiftDto,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startColumn: Int,
    val columnSpan: Int,
    val level: Int
)

data class CalendarUiState(
    val currentMonthYear: String = "",
    val currentMonth: Int = LocalDate.now().monthValue,
    val currentYear: Int = LocalDate.now().year,
    val weeks: List<CalendarWeek> = emptyList(),
    val isLoading: Boolean = false,
    val isAdmin: Boolean = false,
    val isManager: Boolean = false,
    val isCaregiver: Boolean = false,
    val isCustomer: Boolean = false
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val shiftsCache = mutableMapOf<String, List<ShiftDto>>()

    init {
        viewModelScope.launch {
            val isAdmin = preferencesManager.isAdmin.first()
            val isManager = preferencesManager.isManager.first()
            val isCaregiver = preferencesManager.isCaregiver.first()
            val isCustomer = preferencesManager.isCustomer.first()
            _uiState.value = _uiState.value.copy(
                isAdmin = isAdmin,
                isManager = isManager || isCustomer,
                isCaregiver = isCaregiver,
                isCustomer = isCustomer
            )
            loadMonth(_uiState.value.currentMonth, _uiState.value.currentYear)
            // Preload adjacent months
            preloadAdjacentMonths(_uiState.value.currentMonth, _uiState.value.currentYear)
        }
    }

    fun refresh() {
        shiftsCache.clear()
        loadMonth(_uiState.value.currentMonth, _uiState.value.currentYear)
        viewModelScope.launch { preloadAdjacentMonths(_uiState.value.currentMonth, _uiState.value.currentYear) }
    }

    fun previousMonth() {
        val yearMonth = YearMonth.of(_uiState.value.currentYear, _uiState.value.currentMonth).minusMonths(1)
        loadMonth(yearMonth.monthValue, yearMonth.year)
        viewModelScope.launch { preloadAdjacentMonths(yearMonth.monthValue, yearMonth.year) }
    }

    fun nextMonth() {
        val yearMonth = YearMonth.of(_uiState.value.currentYear, _uiState.value.currentMonth).plusMonths(1)
        loadMonth(yearMonth.monthValue, yearMonth.year)
        viewModelScope.launch { preloadAdjacentMonths(yearMonth.monthValue, yearMonth.year) }
    }

    private fun loadMonth(month: Int, year: Int) {
        val ym = YearMonth.of(year, month)
        val cacheKey = "$year-${month.toString().padStart(2, '0')}"
        val isCached = shiftsCache.containsKey(cacheKey)

        // Show calendar grid immediately (empty if not cached), only show loading on first load
        _uiState.value = _uiState.value.copy(
            currentMonth = month,
            currentYear = year,
            currentMonthYear = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " $year",
            weeks = if (isCached) generateCalendarWeeks(month, year, shiftsCache[cacheKey]!!) else generateCalendarWeeks(month, year, emptyList()),
            isLoading = !isCached
        )

        if (isCached) return

        viewModelScope.launch {
            val shifts = getShiftsForMonth(month, year)
            val weeks = generateCalendarWeeks(month, year, shifts)
            // Only update if still on same month
            if (_uiState.value.currentMonth == month && _uiState.value.currentYear == year) {
                _uiState.value = _uiState.value.copy(
                    weeks = weeks,
                    isLoading = false
                )
            }
        }
    }

    private suspend fun preloadAdjacentMonths(month: Int, year: Int) {
        val prev = YearMonth.of(year, month).minusMonths(1)
        val next = YearMonth.of(year, month).plusMonths(1)
        // Preload in background without blocking UI
        getShiftsForMonth(prev.monthValue, prev.year)
        getShiftsForMonth(next.monthValue, next.year)
    }

    private suspend fun getShiftsForMonth(month: Int, year: Int): List<ShiftDto> {
        val cacheKey = "$year-${month.toString().padStart(2, '0')}"
        shiftsCache[cacheKey]?.let { return it }

        val result = if (_uiState.value.isAdmin) {
            shiftRepository.getShiftsForApp(month, year)
        } else {
            val userId = preferencesManager.getUserId()
            shiftRepository.getShiftsByUserIdMonth(userId, month, year)
        }

        val shifts = when (result) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> {
                Timber.e("Failed to load shifts: ${result.message}")
                emptyList()
            }
            is ApiResult.Loading -> emptyList()
        }

        shiftsCache[cacheKey] = shifts
        return shifts
    }

    private fun generateCalendarWeeks(month: Int, year: Int, shifts: List<ShiftDto>): List<CalendarWeek> {
        val yearMonth = YearMonth.of(year, month)
        val firstDayOfMonth = yearMonth.atDay(1)
        val lastDayOfMonth = yearMonth.atEndOfMonth()
        val today = LocalDate.now()

        // Find Monday of the first week
        var startDate = firstDayOfMonth
        while (startDate.dayOfWeek != DayOfWeek.MONDAY) {
            startDate = startDate.minusDays(1)
        }

        // Find Sunday of the last week
        var endDate = lastDayOfMonth
        while (endDate.dayOfWeek != DayOfWeek.SUNDAY) {
            endDate = endDate.plusDays(1)
        }

        // Parse shifts into events
        val events = shifts.mapNotNull { shift ->
            try {
                val from = parseDate(shift.durationFrom)
                val to = parseDate(shift.durationTo)
                if (from != null && to != null) {
                    Triple(shift, from, to)
                } else null
            } catch (e: Exception) {
                null
            }
        }

        // Generate weeks
        val weeks = mutableListOf<CalendarWeek>()
        var weekStart = startDate
        while (weekStart <= endDate) {
            val weekEnd = weekStart.plusDays(6)
            val days = (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                CalendarDay(
                    date = date,
                    dayNumber = if (date.monthValue == month) date.dayOfMonth.toString() else "",
                    isCurrentMonth = date.monthValue == month,
                    isToday = date == today
                )
            }

            // Get events for this week and count hidden ones per day
            val (weekEvents, hiddenPerDay) = getWeekEvents(events, weekStart, weekEnd)

            // Update days with extra events count
            val updatedDays = days.map { day ->
                val hidden = hiddenPerDay[day.date] ?: 0
                if (hidden > 0) day.copy(extraEventsCount = hidden) else day
            }

            weeks.add(CalendarWeek(days = updatedDays, multiDayEvents = weekEvents))
            weekStart = weekStart.plusDays(7)
        }

        return weeks
    }

    private fun getWeekEvents(
        events: List<Triple<ShiftDto, LocalDate, LocalDate>>,
        weekStart: LocalDate,
        weekEnd: LocalDate
    ): Pair<List<MultiDayEvent>, Map<LocalDate, Int>> {
        val weekEvents = events.filter { (_, from, to) ->
            from <= weekEnd && to >= weekStart
        }

        // Assign levels to avoid overlap
        val multiDayEvents = mutableListOf<MultiDayEvent>()
        val hiddenPerDay = mutableMapOf<LocalDate, Int>()
        val sortedEvents = weekEvents.sortedWith(
            compareBy<Triple<ShiftDto, LocalDate, LocalDate>> { it.second }
                .thenByDescending { java.time.temporal.ChronoUnit.DAYS.between(it.second, it.third) }
                .thenBy { it.first.id }
        )

        for ((shift, from, to) in sortedEvents) {
            val clippedStart = maxOf(from, weekStart)
            val clippedEnd = minOf(to, weekEnd)
            val startColumn = clippedStart.dayOfWeek.value - 1 // Monday=0
            val columnSpan = (java.time.temporal.ChronoUnit.DAYS.between(clippedStart, clippedEnd) + 1).toInt()

            // Find available level
            var level = 0
            while (multiDayEvents.any { existing ->
                existing.level == level &&
                existing.startColumn < startColumn + columnSpan &&
                existing.startColumn + existing.columnSpan > startColumn
            }) {
                level++
            }

            if (level < 3) { // Max 3 visible levels
                multiDayEvents.add(
                    MultiDayEvent(
                        id = shift.id,
                        title = shift.projectName.ifBlank { shift.address },
                        color = getContractTypeColor(shift.contractType),
                        shift = shift,
                        startDate = from,
                        endDate = to,
                        startColumn = startColumn,
                        columnSpan = columnSpan,
                        level = level
                    )
                )
            } else {
                // Track hidden events for each day this event spans
                var date = clippedStart
                while (date <= clippedEnd) {
                    hiddenPerDay[date] = (hiddenPerDay[date] ?: 0) + 1
                    date = date.plusDays(1)
                }
            }
        }

        return Pair(multiDayEvents, hiddenPerDay)
    }

    private fun parseDate(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        return try {
            val cleaned = dateStr.substringBefore("T").let { dateStr.take(19) }
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            LocalDate.parse(cleaned, formatter)
        } catch (e: Exception) {
            try {
                LocalDate.parse(dateStr.substring(0, 10))
            } catch (e2: Exception) {
                null
            }
        }
    }

    companion object {
        fun getContractTypeColor(contractType: Int?): Long {
            return when (contractType) {
                1 -> 0xFFC4C4C4   // ToBeConfirmed
                2 -> 0xFFBCA58A   // FullContract
                3 -> 0xFF74AFCC   // SupplyPlaceAndFinish
                4 -> 0xFFCAB641   // PlaceAndFinish
                5 -> 0xFF175A63   // LabourSupply
                6 -> 0xFF333333   // BoxPlaceAndFinish
                7 -> 0xFFFF0000   // Remedial
                8 -> 0xFF037F4C   // SupplyPlaceFinishAndCut
                9 -> 0xFF7F5347   // PlaceFinishAndCut
                10 -> 0xFF7F00FF  // OtherServices
                11 -> 0xFFFF8DA1  // Meetings
                else -> 0xFF8E8E93 // Default gray
            }
        }

        fun getStatusColor(statusId: Int, hasQuotations: Boolean = false): Long {
            return when (statusId) {
                1 -> if (hasQuotations) 0xFF007AFF else 0xFFFF9500   // Quoted - blue, Created - orange
                2 -> 0xFF9D50DD   // Accepted - purple
                3 -> 0xFF00C875   // Ongoing - green
                4 -> 0xFF74AFCC   // Completed - light blue
                5 -> 0xFFFF3B30   // NotCompleted - red
                6 -> 0xFFFFCB00   // FinishJob - yellow
                else -> 0xFF8E8E93 // Default gray
            }
        }
    }
}
