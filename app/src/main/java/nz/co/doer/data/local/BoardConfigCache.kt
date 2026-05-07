package nz.co.doer.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.BoardDto
import nz.co.doer.data.remote.dto.DropdownOptionDto
import nz.co.doer.data.repository.BoardRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the active board name and dropdown options in memory.
 * Loaded at app start (or first Main screen load) and refreshed when admins make edits.
 *
 * Falls back to hardcoded enum labels when the cache hasn't loaded yet, so the UI
 * never shows blank labels even before the network call completes.
 */
@Singleton
class BoardConfigCache @Inject constructor(
    private val boardRepository: BoardRepository
) {
    private val _activeBoard = MutableStateFlow<BoardDto?>(null)
    val activeBoard: StateFlow<BoardDto?> = _activeBoard.asStateFlow()

    // Map<ColumnName, List<DropdownOptionDto>> sorted by SortOrder
    private val _options = MutableStateFlow<Map<String, List<DropdownOptionDto>>>(emptyMap())
    val options: StateFlow<Map<String, List<DropdownOptionDto>>> = _options.asStateFlow()

    suspend fun load() {
        when (val boardResult = boardRepository.getBoards()) {
            is ApiResult.Success -> {
                val board = boardResult.data.firstOrNull { it.isActive } ?: return
                _activeBoard.value = board
                refreshDropdowns(board.id)
            }
            else -> {} // silent fail — fallback labels still work
        }
    }

    suspend fun refreshDropdowns(boardId: Int) {
        when (val result = boardRepository.getDropdownOptions(boardId)) {
            is ApiResult.Success -> {
                _options.value = result.data
                    .filter { it.isActive }
                    .groupBy { it.columnName }
                    .mapValues { (_, list) -> list.sortedBy { it.sortOrder } }
            }
            else -> {}
        }
    }

    /** Returns options for a column, or empty if cache hasn't loaded. */
    fun getOptions(columnName: String): List<DropdownOptionDto> =
        _options.value[columnName].orEmpty()

    /** Returns the display label for a value, or [fallback] if not in cache. */
    fun getDisplayName(columnName: String, value: Int, fallback: String = ""): String =
        _options.value[columnName]
            ?.firstOrNull { it.value == value }
            ?.displayName
            ?: fallback

    /** Returns the color (Long ARGB) for a value, or [fallbackColor] if not cached. */
    fun getColor(columnName: String, value: Int, fallbackColor: Long): Long {
        val hex = _options.value[columnName]?.firstOrNull { it.value == value }?.color
            ?: return fallbackColor
        return parseHexColor(hex, fallbackColor)
    }

    /** Lazy-evaluated fallback variant — fallback computed only when cache miss. */
    inline fun displayName(columnName: String, value: Int, fallback: () -> String): String =
        getOptions(columnName).firstOrNull { it.value == value }?.displayName ?: fallback()

    /** Lazy-evaluated color fallback. */
    inline fun color(columnName: String, value: Int, fallback: () -> Long): Long {
        val hex = getOptions(columnName).firstOrNull { it.value == value }?.color
        return if (hex.isNullOrBlank()) fallback() else parseHexColor(hex, fallback())
    }

    fun parseHexColor(hex: String?, default: Long): Long {
        if (hex.isNullOrBlank()) return default
        return try {
            val cleaned = hex.removePrefix("#")
            val rgb = cleaned.toLong(16) and 0xFFFFFFL
            0xFF000000L or rgb
        } catch (_: Exception) {
            default
        }
    }

    fun getBoardName(default: String = "NZ Mahi 2026"): String =
        _activeBoard.value?.name ?: default

    fun getBoardId(): Int? = _activeBoard.value?.id
}
