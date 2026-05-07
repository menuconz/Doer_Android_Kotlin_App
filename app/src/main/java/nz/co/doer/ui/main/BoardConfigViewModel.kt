package nz.co.doer.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.BoardConfigCache
import nz.co.doer.data.remote.dto.BoardDto
import javax.inject.Inject

/**
 * Lightweight ViewModel that exposes the active board to the DrawerContent.
 * Loads the cache on init so the board name + dropdowns are warm by the time
 * the user opens any screen.
 */
@HiltViewModel
class BoardConfigViewModel @Inject constructor(
    private val cache: BoardConfigCache
) : ViewModel() {

    val activeBoard: StateFlow<BoardDto?> = cache.activeBoard

    init {
        viewModelScope.launch { cache.load() }
    }

    fun refresh() {
        viewModelScope.launch { cache.load() }
    }
}
