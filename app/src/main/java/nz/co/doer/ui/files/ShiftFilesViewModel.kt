package nz.co.doer.ui.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.FileUploadModelDto
import nz.co.doer.data.repository.ShiftRepository
import retrofit2.HttpException
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class ShiftFilesUiState(
    val shiftTitle: String = "",
    val files: List<FileUploadModelDto> = emptyList(),
    val fileCountText: Int = 0,
    val isEmpty: Boolean = false,
    val hasFiles: Boolean = false,
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val isEnabled: Boolean = true,
    val uploadProgress: Double = 0.0,
    val uploadStatusText: String = "",
    val showUploadOptions: Boolean = false,
    val showNoFilesDialog: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ShiftFilesViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftFilesUiState())
    val uiState: StateFlow<ShiftFilesUiState> = _uiState.asStateFlow()

    private val shiftId: String = savedStateHandle.get<String>("shiftId") ?: ""

    companion object {
        val ALLOWED_EXTENSIONS = listOf("pdf", "png", "jpg", "jpeg")
    }

    init {
        loadShiftTitle()
        loadFiles()
    }

    // Matching MAUI: ShiftTitle = ShiftModel.ProjectName
    private fun loadShiftTitle() {
        viewModelScope.launch {
            try {
                val id = shiftId.toIntOrNull() ?: return@launch
                when (val result = shiftRepository.getShiftById(id)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            shiftTitle = result.data.projectName
                        )
                    }
                    is ApiResult.Error -> {
                        Timber.e("Failed to load shift title: ${result.message}")
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading shift title")
            }
        }
    }

    // Matching MAUI: GetShiftFiles
    private fun loadFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = shiftRepository.getShiftFiles(shiftId)) {
                is ApiResult.Success -> {
                    val files = result.data
                    _uiState.value = _uiState.value.copy(
                        files = files,
                        fileCountText = files.size,
                        isEmpty = files.isEmpty(),
                        hasFiles = files.isNotEmpty(),
                        isLoading = false,
                        showNoFilesDialog = files.isEmpty()
                    )
                }
                is ApiResult.Error -> {
                    // Backend returns 404 when no files exist for the shift — treat as empty, not an error
                    if ((result.exception as? HttpException)?.code() == 404) {
                        _uiState.value = _uiState.value.copy(
                            files = emptyList(),
                            fileCountText = 0,
                            isEmpty = true,
                            hasFiles = false,
                            isLoading = false,
                            showNoFilesDialog = true
                        )
                    } else {
                        Timber.e("Failed to load shift files: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isEmpty = true,
                            hasFiles = false,
                            errorMessage = result.message
                        )
                    }
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun dismissNoFilesDialog() {
        _uiState.value = _uiState.value.copy(showNoFilesDialog = false)
    }

    fun refresh() {
        loadFiles()
    }

    // Matching MAUI: UploadFiles command — show action sheet
    fun showUploadDialog() {
        _uiState.value = _uiState.value.copy(showUploadOptions = true)
    }

    fun dismissUploadDialog() {
        _uiState.value = _uiState.value.copy(showUploadOptions = false)
    }

    // Matching MAUI: "Upload from Gallery" — single image pick
    fun uploadFromGallery(context: Context, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val tempFiles = mutableListOf<File>()
            try {
                val fileName = getFileName(context, uri) ?: "unknown"
                val extension = fileName.substringAfterLast('.', "").lowercase()
                if (extension !in ALLOWED_EXTENSIONS) {
                    // Matching MAUI: "Invalid File" alert
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Accepts only PDF, PNG, JPG and JPEG file types"
                    )
                    return@launch
                }
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFiles.add(tempFile)
                uploadFilesToServer(tempFiles)
            } catch (e: Exception) {
                Timber.e(e, "Error uploading from gallery")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to upload files"
                )
            } finally {
                tempFiles.forEach { it.delete() }
            }
        }
    }

    // Matching MAUI: "Upload from Files" — multiple file pick
    fun uploadFromFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val tempFiles = mutableListOf<File>()
            try {
                for (uri in uris) {
                    val fileName = getFileName(context, uri) ?: "unknown"
                    val extension = fileName.substringAfterLast('.', "").lowercase()
                    if (extension !in ALLOWED_EXTENSIONS) {
                        // Matching MAUI: "Invalid File" alert
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Accepts only PDF, PNG, JPG and JPEG file types"
                        )
                        return@launch
                    }
                    val tempFile = File(context.cacheDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFiles.add(tempFile)
                }
                uploadFilesToServer(tempFiles)
            } catch (e: Exception) {
                Timber.e(e, "Error uploading files")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to upload files"
                )
            } finally {
                tempFiles.forEach { it.delete() }
            }
        }
    }

    // Matching MAUI: UploadFilesToServer with progress 0→25→50→75→100
    private suspend fun uploadFilesToServer(files: List<File>) {
        _uiState.value = _uiState.value.copy(
            isUploading = true,
            isEnabled = false,
            uploadStatusText = "Uploading files...",
            uploadProgress = 0.0
        )

        try {
            _uiState.value = _uiState.value.copy(uploadProgress = 0.25)
            // progress 50
            _uiState.value = _uiState.value.copy(uploadProgress = 0.50)

            when (val result = shiftRepository.uploadFile(shiftId, null, files)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(uploadProgress = 0.75)
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = 1.0,
                        isUploading = false,
                        isEnabled = true,
                        uploadStatusText = "Files uploaded successfully",
                        successMessage = "Files uploaded successfully"
                    )
                    loadFiles()
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to upload files: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        isEnabled = true,
                        uploadStatusText = "Failed to upload files",
                        errorMessage = "Failed to upload files"
                    )
                }
                is ApiResult.Loading -> {}
            }
        } catch (e: Exception) {
            Timber.e(e, "Getting Exception in UploadFilesToServer")
            _uiState.value = _uiState.value.copy(
                isUploading = false,
                isEnabled = true,
                uploadStatusText = "Failed to upload files"
            )
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
