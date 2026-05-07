package nz.co.doer.ui.activitylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.ActivityLogDto
import nz.co.doer.data.repository.ActivityLogRepository
import javax.inject.Inject

data class ActivityLogUiState(
    val isLoading: Boolean = false,
    val logs: List<ActivityLogDto> = emptyList(),
    val totalCount: Int = 0,
    val skip: Int = 0,
    val take: Int = 50,
    val canLoadMore: Boolean = true,
    val errorMessage: String? = null,
    // Filters
    val entityTypeFilter: String? = null,
    val actionFilter: String? = null
)

@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val repository: ActivityLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityLogUiState())
    val uiState: StateFlow<ActivityLogUiState> = _uiState.asStateFlow()

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        _uiState.value = _uiState.value.copy(skip = 0, logs = emptyList(), canLoadMore = true)
        fetch()
    }

    fun loadMore() {
        if (!_uiState.value.canLoadMore || _uiState.value.isLoading) return
        fetch()
    }

    fun setEntityTypeFilter(value: String?) {
        _uiState.value = _uiState.value.copy(entityTypeFilter = value)
        loadFirstPage()
    }

    fun setActionFilter(value: String?) {
        _uiState.value = _uiState.value.copy(actionFilter = value)
        loadFirstPage()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            entityTypeFilter = null,
            actionFilter = null
        )
        loadFirstPage()
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    private fun fetch() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.getLogs(
                entityType = state.entityTypeFilter,
                action = state.actionFilter,
                skip = state.skip,
                take = state.take
            )) {
                is ApiResult.Success -> {
                    val newLogs = if (state.skip == 0) result.data.logs
                    else state.logs + result.data.logs
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = newLogs,
                        totalCount = result.data.totalCount,
                        skip = newLogs.size,
                        canLoadMore = newLogs.size < result.data.totalCount
                    )
                }
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message ?: "Failed to load activity log."
                )
                is ApiResult.Loading -> {}
            }
        }
    }

    companion object {
        val ENTITY_TYPES = listOf(
            "All" to null,
            "Shift" to "Shift",
            "Sub-Item" to "ShiftSubItem",
            "Lead" to "Lead",
            "User" to "User",
            "Board" to "Board",
            "Dropdown" to "DropdownOption",
            "Client" to "Client"
        )

        val ACTIONS = listOf(
            "All" to null,
            "Created" to "Created",
            "Updated" to "Updated",
            "Deleted" to "Deleted",
            "Status Change" to "StatusChanged",
            "Role Change" to "RoleChanged"
        )
    }
}
