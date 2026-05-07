package nz.co.doer.ui.boardsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.BoardConfigCache
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.BoardDto
import nz.co.doer.data.remote.dto.DropdownOptionDto
import nz.co.doer.data.repository.BoardRepository
import javax.inject.Inject

data class BoardSettingsUiState(
    val isLoading: Boolean = false,
    val board: BoardDto? = null,
    val boardNameDraft: String = "",
    val optionsByColumn: Map<String, List<DropdownOptionDto>> = emptyMap(),
    val expandedColumns: Set<String> = emptySet(),
    val editing: DropdownOptionDto? = null,
    val pendingDelete: DropdownOptionDto? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class BoardSettingsViewModel @Inject constructor(
    private val repository: BoardRepository,
    private val cache: BoardConfigCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardSettingsUiState())
    val uiState: StateFlow<BoardSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val boardsResult = repository.getBoards()) {
                is ApiResult.Success -> {
                    val board = boardsResult.data.firstOrNull { it.isActive }
                    if (board == null) {
                        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "No active board found.")
                        return@launch
                    }
                    when (val optionsResult = repository.getDropdownOptions(board.id)) {
                        is ApiResult.Success -> {
                            val grouped = optionsResult.data
                                .filter { it.isActive }
                                .groupBy { it.columnName }
                                .mapValues { (_, list) -> list.sortedBy { it.sortOrder } }
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                board = board,
                                boardNameDraft = board.name,
                                optionsByColumn = grouped
                            )
                        }
                        is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                            isLoading = false, errorMessage = optionsResult.message ?: "Failed to load dropdown options."
                        )
                        is ApiResult.Loading -> {}
                    }
                }
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, errorMessage = boardsResult.message ?: "Failed to load board."
                )
                is ApiResult.Loading -> {}
            }
        }
    }

    fun updateBoardNameDraft(name: String) {
        _uiState.value = _uiState.value.copy(boardNameDraft = name)
    }

    fun saveBoardName() {
        val state = _uiState.value
        val board = state.board ?: return
        val newName = state.boardNameDraft.trim()
        if (newName.isBlank() || newName == board.name) return

        viewModelScope.launch {
            when (val result = repository.updateBoard(board.id, newName)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        board = result.data,
                        boardNameDraft = result.data.name,
                        successMessage = "Board name updated."
                    )
                    cache.load()
                }
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                    errorMessage = result.message ?: "Failed to update board name."
                )
                is ApiResult.Loading -> {}
            }
        }
    }

    fun toggleColumn(columnName: String) {
        val expanded = _uiState.value.expandedColumns.toMutableSet()
        if (!expanded.add(columnName)) expanded.remove(columnName)
        _uiState.value = _uiState.value.copy(expandedColumns = expanded)
    }

    fun beginEdit(option: DropdownOptionDto) {
        _uiState.value = _uiState.value.copy(editing = option)
    }

    // Opens the editor with a fresh option (id=0 → server creates new on save).
    // Pre-fills the new value as max(existing values) + 1 and sortOrder = list size.
    fun beginAdd(columnName: String) {
        val board = _uiState.value.board ?: return
        val existing = _uiState.value.optionsByColumn[columnName].orEmpty()
        val nextValue = (existing.maxOfOrNull { it.value } ?: 0) + 1
        val nextSort = existing.size + 1
        _uiState.value = _uiState.value.copy(
            editing = DropdownOptionDto(
                id = 0,
                boardId = board.id,
                columnName = columnName,
                value = nextValue,
                displayName = "",
                color = "#1976D2",
                sortOrder = nextSort,
                isActive = true
            )
        )
    }

    fun updateEditField(displayName: String? = null, color: String? = null, sortOrder: Int? = null) {
        val cur = _uiState.value.editing ?: return
        _uiState.value = _uiState.value.copy(
            editing = cur.copy(
                displayName = displayName ?: cur.displayName,
                color = color ?: cur.color,
                sortOrder = sortOrder ?: cur.sortOrder
            )
        )
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editing = null)
    }

    fun saveEdit() {
        val edited = _uiState.value.editing ?: return
        val board = _uiState.value.board ?: return
        if (edited.displayName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Display Name cannot be empty.")
            return
        }
        val isNew = edited.id == 0
        viewModelScope.launch {
            when (val result = repository.upsertDropdownOption(board.id, edited)) {
                is ApiResult.Success -> {
                    val saved = result.data
                    val newGrouped = _uiState.value.optionsByColumn.toMutableMap()
                    val existing = newGrouped[saved.columnName].orEmpty()
                    val list = if (isNew) {
                        // Append the freshly-created option
                        (existing + saved).sortedBy { it.sortOrder }
                    } else {
                        existing.map { if (it.id == saved.id) saved else it }.sortedBy { it.sortOrder }
                    }
                    newGrouped[saved.columnName] = list
                    _uiState.value = _uiState.value.copy(
                        optionsByColumn = newGrouped,
                        editing = null,
                        successMessage = if (isNew) "Option added." else "Saved."
                    )
                    cache.refreshDropdowns(board.id)
                }
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                    errorMessage = result.message ?: "Failed to save."
                )
                is ApiResult.Loading -> {}
            }
        }
    }

    fun confirmDelete(option: DropdownOptionDto) {
        _uiState.value = _uiState.value.copy(pendingDelete = option)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = null)
    }

    // Soft delete: send the option back with isActive = false. The server-side
    // upsert recognises id > 0 as an update and persists the IsActive flag.
    fun deleteOption() {
        val target = _uiState.value.pendingDelete ?: return
        val board = _uiState.value.board ?: return
        val payload = target.copy(isActive = false)
        viewModelScope.launch {
            when (val result = repository.upsertDropdownOption(board.id, payload)) {
                is ApiResult.Success -> {
                    val newGrouped = _uiState.value.optionsByColumn.toMutableMap()
                    val list = newGrouped[target.columnName].orEmpty()
                        .filter { it.id != target.id }
                    newGrouped[target.columnName] = list
                    _uiState.value = _uiState.value.copy(
                        optionsByColumn = newGrouped,
                        pendingDelete = null,
                        successMessage = "Option deleted."
                    )
                    cache.refreshDropdowns(board.id)
                }
                is ApiResult.Error -> _uiState.value = _uiState.value.copy(
                    pendingDelete = null,
                    errorMessage = result.message ?: "Failed to delete."
                )
                is ApiResult.Loading -> {}
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }
}
