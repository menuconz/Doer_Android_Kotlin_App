package nz.co.doer.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import timber.log.Timber
import javax.inject.Inject

data class TeamUserItem(
    val user: UserDto,
    val isInTeam: Boolean = false
)

data class FiloKretoTeamUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val users: List<TeamUserItem> = emptyList(),
    val searchQuery: String = "",
    val filteredUsers: List<TeamUserItem> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class FiloKretoTeamViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FiloKretoTeamUiState())
    val uiState: StateFlow<FiloKretoTeamUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Load all users and team members (matching MAUI LoadUsersAsync)
            val allUsersResult = accountRepository.getAllUsersWithAdmin()
            val teamResult = accountRepository.getAllFiloKretoTeam()

            when {
                allUsersResult is ApiResult.Success && teamResult is ApiResult.Success -> {
                    val teamIds = teamResult.data.map { it.id }.toSet()

                    val userItems = allUsersResult.data.map { user ->
                        TeamUserItem(
                            user = user,
                            isInTeam = user.id in teamIds
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        users = userItems,
                        filteredUsers = userItems
                    )
                }
                allUsersResult is ApiResult.Error -> {
                    Timber.e("Failed to load users: ${allUsersResult.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = allUsersResult.message
                    )
                }
                teamResult is ApiResult.Error -> {
                    Timber.e("Failed to load team: ${teamResult.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = teamResult.message
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun updateSearch(query: String) {
        val state = _uiState.value
        val filtered = if (query.isBlank()) {
            state.users
        } else {
            state.users.filter {
                it.user.displayName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = state.copy(searchQuery = query, filteredUsers = filtered)
    }

    fun toggleTeamMembership(userId: String) {
        val state = _uiState.value
        val updatedUsers = state.users.map { item ->
            if (item.user.id == userId) item.copy(isInTeam = !item.isInTeam) else item
        }
        val filtered = if (state.searchQuery.isBlank()) {
            updatedUsers
        } else {
            updatedUsers.filter {
                it.user.displayName.contains(state.searchQuery, ignoreCase = true)
            }
        }
        _uiState.value = state.copy(users = updatedUsers, filteredUsers = filtered)
    }

    // Matching MAUI SaveChanges: re-fetches current team before computing diff
    fun saveChanges() {
        val state = _uiState.value

        if (state.users.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "No users found to update.")
            return
        }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // MAUI re-fetches the current team to compute accurate diff
                val currentTeamResult = accountRepository.getAllFiloKretoTeam()
                val currentTeamIds = when (currentTeamResult) {
                    is ApiResult.Success -> currentTeamResult.data.map { it.id }.toSet()
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            errorMessage = "Unable to update team. Please try again later."
                        )
                        return@launch
                    }
                }

                val selectedIds = state.users.filter { it.isInTeam }.map { it.user.id }.toSet()

                val toAdd = (selectedIds - currentTeamIds).toList()
                val toRemove = currentTeamIds.filter { id ->
                    !state.users.any { it.isInTeam && it.user.id == id }
                }

                if (toAdd.isEmpty() && toRemove.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = "No updates to apply."
                    )
                    return@launch
                }

                var addSuccess = true
                var removeSuccess = true

                if (toAdd.isNotEmpty()) {
                    when (val result = accountRepository.addUsersToFiloKretoTeam(toAdd)) {
                        is ApiResult.Error -> {
                            Timber.e("Failed to add users to team: ${result.message}")
                            addSuccess = false
                        }
                        else -> {}
                    }
                }

                if (toRemove.isNotEmpty()) {
                    when (val result = accountRepository.removeUsersFromFiloKretoTeam(toRemove)) {
                        is ApiResult.Error -> {
                            Timber.e("Failed to remove users from team: ${result.message}")
                            removeSuccess = false
                        }
                        else -> {}
                    }
                }

                // Match MAUI messaging
                val message = when {
                    addSuccess && removeSuccess -> "FiloKreto Team updated successfully!"
                    !addSuccess && !removeSuccess -> "Unable to update the FiloKreto Team. Please try again later."
                    !addSuccess -> "Some users could not be added to the FiloKreto Team."
                    else -> "Some users could not be removed from the FiloKreto Team."
                }

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = if (addSuccess || removeSuccess) message else null,
                    errorMessage = if (!addSuccess && !removeSuccess) message else null
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to update team")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Unable to update team. Please try again later."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
