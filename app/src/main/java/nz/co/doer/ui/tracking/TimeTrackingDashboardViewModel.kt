package nz.co.doer.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.DoerHoursDto
import nz.co.doer.data.remote.dto.EditTimeEntryDto
import nz.co.doer.data.remote.dto.SiteHoursSummaryDto
import nz.co.doer.data.remote.dto.StageHoursDto
import nz.co.doer.data.repository.LocationTrackingRepository
import nz.co.doer.data.repository.TimeTrackingRepository
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ========== UI models ==========

data class SiteHoursUi(
    val shiftId: Int,
    val projectName: String,
    val address: String,
    val clientName: String,
    val totalHours: Double,
    val doerCount: Int,
    val isOverThreshold: Boolean,
    val isApproachingThreshold: Boolean,
    val stages: List<StageHoursUi>,
    val doerHours: List<DoerHoursUi>,
    val isExpanded: Boolean = false
) {
    val totalHoursFormatted: String get() {
        val h = totalHours.toInt()
        val m = ((totalHours - h) * 60).toInt()
        return "${h}h ${m}m"
    }
}

data class StageHoursUi(
    val stageName: String,
    val totalHours: Double,
    val doerCount: Int
) {
    val totalHoursFormatted: String get() {
        val h = totalHours.toInt()
        val m = ((totalHours - h) * 60).toInt()
        return "${h}h ${m}m"
    }
}

data class DoerHoursUi(
    val userId: String,
    val displayName: String,
    val shiftId: Int,
    val clockInTime: String,
    val clockOutTime: String,
    val totalHours: Double,
    val stage: String,
    val isActive: Boolean,
    val isOverThreshold: Boolean
) {
    val totalHoursFormatted: String get() {
        val h = totalHours.toInt()
        val m = ((totalHours - h) * 60).toInt()
        return "${h}h ${m}m"
    }
}

data class TimeTrackingDashboardUiState(
    val sites: List<SiteHoursUi> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedDate: String = "",
    val selectedDateDisplay: String = "",
    // Aggregate stats
    val totalSites: Int = 0,
    val totalDoers: Int = 0,
    val totalHours: Double = 0.0,
    val alertCount: Int = 0
)

@HiltViewModel
class TimeTrackingDashboardViewModel @Inject constructor(
    private val timeTrackingRepository: TimeTrackingRepository,
    private val locationTrackingRepository: LocationTrackingRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimeTrackingDashboardUiState())
    val uiState: StateFlow<TimeTrackingDashboardUiState> = _uiState.asStateFlow()

    private val apiDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    private val displayDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    init {
        val today = LocalDate.now()
        _uiState.value = _uiState.value.copy(
            selectedDate = today.format(apiDateFormat),
            selectedDateDisplay = today.format(displayDateFormat)
        )
        loadData()
    }

    fun selectDate(year: Int, month: Int, dayOfMonth: Int) {
        val date = LocalDate.of(year, month, dayOfMonth)
        _uiState.value = _uiState.value.copy(
            selectedDate = date.format(apiDateFormat),
            selectedDateDisplay = date.format(displayDateFormat)
        )
        loadData()
    }

    fun toggleSiteExpanded(shiftId: Int) {
        val updatedSites = _uiState.value.sites.map { site ->
            if (site.shiftId == shiftId) site.copy(isExpanded = !site.isExpanded)
            else site
        }
        _uiState.value = _uiState.value.copy(sites = updatedSites)
    }

    fun refresh() {
        loadData()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = timeTrackingRepository.getSiteHoursSummary(
                date = _uiState.value.selectedDate
            )) {
                is ApiResult.Success -> {
                    val sites = result.data.map { it.toUi() }
                    // Sort: alerts first, then by total hours descending
                    val sorted = sites.sortedWith(
                        compareByDescending<SiteHoursUi> { it.isOverThreshold }
                            .thenByDescending { it.isApproachingThreshold }
                            .thenByDescending { it.totalHours }
                    )

                    val totalHrs = sorted.sumOf { it.totalHours }
                    val alertCnt = sorted.count { it.isOverThreshold } +
                            sorted.flatMap { it.doerHours }.count { it.isOverThreshold }

                    _uiState.value = _uiState.value.copy(
                        sites = sorted,
                        isLoading = false,
                        totalSites = sorted.size,
                        totalDoers = sorted.sumOf { it.doerCount },
                        totalHours = totalHrs,
                        alertCount = alertCnt
                    )
                }

                is ApiResult.Error -> {
                    Timber.e("Failed to load time tracking: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }

                is ApiResult.Loading -> {}
            }
        }
    }

    private fun SiteHoursSummaryDto.toUi(): SiteHoursUi {
        val doerUis = doerHours.map { it.toUi() }
        val stageUis = stages.map { it.toUi() }
        // Any individual Doer exceeding 12 hours at this site triggers alert
        val anyDoerOver = doerUis.any { it.isOverThreshold }

        return SiteHoursUi(
            shiftId = shiftId,
            projectName = projectName,
            address = address,
            clientName = clientName ?: "",
            totalHours = totalHours,
            doerCount = doerCount,
            isOverThreshold = anyDoerOver || totalHours >= THRESHOLD_HOURS,
            isApproachingThreshold = !anyDoerOver && totalHours >= WARNING_HOURS,
            stages = stageUis,
            doerHours = doerUis
        )
    }

    private fun DoerHoursDto.toUi(): DoerHoursUi {
        return DoerHoursUi(
            userId = userId,
            displayName = displayName,
            shiftId = shiftId,
            clockInTime = clockInTime ?: "",
            clockOutTime = clockOutTime ?: "",
            totalHours = totalHours,
            stage = stage,
            isActive = isActive,
            isOverThreshold = totalHours >= THRESHOLD_HOURS
        )
    }

    private fun StageHoursDto.toUi(): StageHoursUi {
        return StageHoursUi(
            stageName = stageName,
            totalHours = totalHours,
            doerCount = doerCount
        )
    }

    /**
     * Manager edits a Doer's clock-in/out times with a mandatory reason code.
     */
    fun editTimeEntry(userId: String, shiftId: Int, clockIn: String, clockOut: String, reason: String) {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val editedBy = preferencesManager.getUserId()
            val basicAuthUid = preferencesManager.getBasicAuthUid()

            // Build full datetime from date + time (HH:mm)
            val clockInTime = if (clockIn.isNotBlank()) "${date}T${clockIn.padEnd(5, '0')}:00" else null
            val clockOutTime = if (clockOut.isNotBlank()) "${date}T${clockOut.padEnd(5, '0')}:00" else null

            val request = EditTimeEntryDto(
                userId = userId,
                shiftId = shiftId,
                clockInTime = clockInTime,
                clockOutTime = clockOutTime,
                reasonCode = reason,
                editedBy = editedBy,
                lId = 1,
                siteId = 1,
                basicAuthUid = basicAuthUid
            )

            when (val result = locationTrackingRepository.editTimeEntry(request)) {
                is ApiResult.Success -> {
                    Timber.d("Time entry edited for user $userId shift $shiftId")
                    loadData() // Refresh dashboard
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to edit time entry: ${result.message}")
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    companion object {
        const val THRESHOLD_HOURS = 12.0
        const val WARNING_HOURS = 11.0
    }
}
