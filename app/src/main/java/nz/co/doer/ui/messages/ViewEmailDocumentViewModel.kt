package nz.co.doer.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class ViewEmailDocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Matching MAUI: FileUrl = Document.FileUrl
    val fileUrl: String = try {
        URLDecoder.decode(savedStateHandle.get<String>("fileUrl") ?: "", "UTF-8")
    } catch (_: Exception) {
        savedStateHandle.get<String>("fileUrl") ?: ""
    }

    // Matching MAUI: Title = Document.FileName
    // Extract filename from URL since it's not passed separately in navigation
    val fileName: String = fileUrl.substringAfterLast('/').substringBefore('?')
}
