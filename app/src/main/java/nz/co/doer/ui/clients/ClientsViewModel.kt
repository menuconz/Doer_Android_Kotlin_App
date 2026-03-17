package nz.co.doer.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.remote.dto.ClientJobDto
import nz.co.doer.data.repository.ClientRepository
import timber.log.Timber
import javax.inject.Inject

data class ClientsUiState(
    val isLoading: Boolean = true,
    val clients: List<ClientDto> = emptyList(),
    val sortColumn: String = "",
    val sortAscending: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Edit bottom sheet
    val editingClient: ClientDto? = null,
    val fieldName: String = "",
    val editorTitle: String = "Edit Field",
    val editorText: String = "",
    val isSaving: Boolean = false,
    // Delete dialog
    val deletingClient: ClientDto? = null,
    val isDeleting: Boolean = false,
    // Projects bottom sheet
    val projectsClient: ClientDto? = null,
    val projectJobs: List<ClientJobDto> = emptyList(),
    val filteredJobs: List<ClientJobDto> = emptyList(),
    val searchText: String = "",
    val isLoadingProjects: Boolean = false,
    val isSavingProjects: Boolean = false
)

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientsUiState())
    val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

    init {
        loadClients()
    }

    fun loadClients() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = clientRepository.getAllClients()) {
                is ApiResult.Success -> {
                    if (result.data.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            clients = emptyList(),
                            errorMessage = "No Clients found."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            clients = result.data
                        )
                    }
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load clients: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // Matching MAUI SortBy: column keys are "Name", "Email" (PascalCase)
    fun sortBy(column: String) {
        val state = _uiState.value
        val ascending = if (state.sortColumn == column) !state.sortAscending else true
        val sorted = state.clients.sortedWith(
            compareBy<ClientDto> {
                when (column) {
                    "Name" -> it.name.lowercase()
                    "Email" -> it.email.lowercase()
                    else -> ""
                }
            }.let { if (ascending) it else it.reversed() }
        )
        _uiState.value = state.copy(
            clients = sorted,
            sortColumn = column,
            sortAscending = ascending
        )
    }

    // --- Edit bottom sheet (matching MAUI ClientEditorPopupView) ---

    fun editClientName(client: ClientDto) {
        editFieldInternal(client, "Client Name")
    }

    fun editClientEmail(client: ClientDto) {
        editFieldInternal(client, "Client Email")
    }

    private fun editFieldInternal(client: ClientDto, fieldName: String) {
        val value = when (fieldName) {
            "Client Name" -> client.name
            "Client Email" -> client.email
            else -> ""
        }
        _uiState.value = _uiState.value.copy(
            editingClient = client,
            fieldName = fieldName,
            editorTitle = fieldName,
            editorText = value
        )
    }

    fun updateEditorText(value: String) {
        _uiState.value = _uiState.value.copy(editorText = value)
    }

    fun dismissEditSheet() {
        _uiState.value = _uiState.value.copy(
            editingClient = null,
            fieldName = "",
            editorText = ""
        )
    }

    // Matching MAUI Save command
    fun saveEdit() {
        val state = _uiState.value
        val client = state.editingClient ?: return
        if (state.fieldName.isEmpty()) return

        // Set field value
        val updated = when (state.fieldName) {
            "Client Name" -> client.copy(name = state.editorText)
            "Client Email" -> client.copy(email = state.editorText)
            else -> client
        }

        _uiState.value = state.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val userId = preferencesManager.getUserId()
            val clientToSave = updated.copy(modifiedBy = userId)
            when (val result = clientRepository.updateClient(clientToSave)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        editingClient = null,
                        fieldName = "",
                        editorText = ""
                    )
                    loadClients()
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to update client: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.message ?: "There was a problem in Updating Client Details"
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // --- Delete dialog (matching MAUI DeleteClient) ---

    fun openDeleteDialog(client: ClientDto) {
        _uiState.value = _uiState.value.copy(deletingClient = client)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(deletingClient = null)
    }

    // Matching MAUI: success "The client has been successfully deleted."
    fun confirmDelete() {
        val client = _uiState.value.deletingClient ?: return
        _uiState.value = _uiState.value.copy(isDeleting = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = clientRepository.deleteClientById(client.id)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deletingClient = null,
                        successMessage = "The client has been successfully deleted."
                    )
                    loadClients()
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to delete client: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deletingClient = null,
                        errorMessage = "Failed to delete the Client. Please try again later."
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    // --- Projects bottom sheet (matching MAUI ClientProjectsPopupView) ---

    fun viewClientProjects(client: ClientDto) {
        _uiState.value = _uiState.value.copy(
            projectsClient = client,
            projectJobs = emptyList(),
            filteredJobs = emptyList(),
            searchText = "",
            isLoadingProjects = true
        )
        viewModelScope.launch {
            val existingJobs = client.jobs.map {
                it.copy(originalIsAssigned = it.isAssigned)
            }.toMutableList()

            when (val result = clientRepository.getUnassignedJobs()) {
                is ApiResult.Success -> {
                    val unassigned = result.data.filter { uj ->
                        existingJobs.none { it.id == uj.id }
                    }.map { it.copy(isAssigned = false, originalIsAssigned = false) }

                    val allJobs = existingJobs + unassigned
                    _uiState.value = _uiState.value.copy(
                        isLoadingProjects = false,
                        projectJobs = allJobs,
                        filteredJobs = allJobs
                    )
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load unassigned jobs: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoadingProjects = false,
                        projectJobs = existingJobs,
                        filteredJobs = existingJobs
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun dismissProjectsSheet() {
        _uiState.value = _uiState.value.copy(
            projectsClient = null,
            projectJobs = emptyList(),
            filteredJobs = emptyList(),
            searchText = ""
        )
    }

    // Matching MAUI OnSearchTextChanged → FilterProjects
    fun onSearchTextChange(value: String) {
        _uiState.value = _uiState.value.copy(searchText = value)
        filterProjects()
    }

    private fun filterProjects() {
        val state = _uiState.value
        if (state.searchText.isBlank()) {
            _uiState.value = state.copy(filteredJobs = state.projectJobs)
        } else {
            val query = state.searchText.trim().lowercase()
            val filtered = state.projectJobs.filter {
                it.projectName.lowercase().contains(query)
            }
            _uiState.value = state.copy(filteredJobs = filtered)
        }
    }

    fun toggleJobAssignment(job: ClientJobDto) {
        val jobs = _uiState.value.projectJobs.toMutableList()
        val index = jobs.indexOfFirst { it.id == job.id }
        if (index >= 0) {
            jobs[index] = jobs[index].copy(isAssigned = !jobs[index].isAssigned)
            _uiState.value = _uiState.value.copy(projectJobs = jobs)
            filterProjects()
        }
    }

    // Matching MAUI AssignProjectToClient
    fun saveProjectAssignments() {
        val client = _uiState.value.projectsClient ?: return
        val jobs = _uiState.value.projectJobs
        val changedJobs = jobs.filter { it.isAssigned != it.originalIsAssigned }

        if (changedJobs.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                successMessage = "Nothing to update."
            )
            dismissProjectsSheet()
            return
        }

        _uiState.value = _uiState.value.copy(isSavingProjects = true, errorMessage = null)
        viewModelScope.launch {
            var hasError = false
            for (job in changedJobs) {
                val clientId = if (job.isAssigned) client.id else 0
                when (val result = clientRepository.assignClientToJob(job.id, clientId)) {
                    is ApiResult.Success -> {}
                    is ApiResult.Error -> {
                        Timber.e("Failed to update: ${job.projectName}")
                        _uiState.value = _uiState.value.copy(
                            isSavingProjects = false,
                            errorMessage = "Failed to update: ${job.projectName}"
                        )
                        hasError = true
                        return@launch
                    }
                    is ApiResult.Loading -> {}
                }
            }
            _uiState.value = _uiState.value.copy(
                isSavingProjects = false,
                successMessage = "Projects Updated"
            )
            dismissProjectsSheet()
            loadClients()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
