package nz.co.doer.ui.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import javax.inject.Inject

data class ViewDocumentUiState(
    val fileUrl: String = "",
    val isImage: Boolean = false,
    // Matching MAUI: Title = Document.Name
    val documentName: String = ""
)

@HiltViewModel
class ViewDocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewDocumentUiState())
    val uiState: StateFlow<ViewDocumentUiState> = _uiState.asStateFlow()

    init {
        val rawFileUrl = savedStateHandle.get<String>("fileUrl") ?: ""
        val fileUrl = try {
            URLDecoder.decode(rawFileUrl, "UTF-8")
        } catch (_: Exception) {
            rawFileUrl
        }
        val isImage = savedStateHandle.get<String>("isImage")?.toBoolean() ?: false
        // Matching MAUI: Title = Document.Name (file name from URL)
        val documentName = fileUrl.substringAfterLast('/').substringBefore('?')

        _uiState.value = ViewDocumentUiState(
            fileUrl = fileUrl,
            isImage = isImage,
            documentName = documentName
        )
    }
}
