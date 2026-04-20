package nz.co.doer.ui.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import nz.co.doer.data.remote.dto.FileUploadModelDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// MAUI colors
private val BluePrimary = Color(0xFF3B82F6) // MAUI #3B82F6
private val DarkText = Color(0xFF1F2937) // MAUI #1F2937
private val SubText = Color(0xFF374151) // MAUI #374151
private val Gray500 = Color(0xFF6B7280) // MAUI #6B7280
private val Gray400 = Color(0xFF9CA3AF) // MAUI #9CA3AF
private val BorderColor = Color(0xFFE5E7EB) // MAUI #E5E7EB
private val BgColor = Color(0xFFF8F9FA)

// Matching MAUI: ServerUrl for shift documents
private const val SHIFT_DOCS_SERVER_URL = "https://doerapi.doer.nz/userDocuments/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftFilesScreen(
    onBack: () -> Unit,
    onViewDocument: (fileUrl: String, isImage: Boolean) -> Unit,
    viewModel: ShiftFilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Matching MAUI: "Upload from Gallery" — single image picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.uploadFromGallery(context, uri)
    }

    // Matching MAUI: "Upload from Files" — multiple file picker
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFromFiles(context, uris)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    // Popup when there are no files for this shift (replaces the HTTP 404 snackbar)
    if (state.showNoFilesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoFilesDialog() },
            title = { Text("No Files") },
            text = { Text("No files are available for this job yet.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissNoFilesDialog() }) {
                    Text("OK")
                }
            }
        )
    }

    // Matching MAUI: ActionSheet "Select Upload Option" with Gallery/Files/Cancel
    if (state.showUploadOptions) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUploadDialog() },
            title = { Text("Select Upload Option") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.dismissUploadDialog()
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload from Gallery", fontSize = 16.sp, color = BluePrimary)
                    }
                    TextButton(
                        onClick = {
                            viewModel.dismissUploadDialog()
                            fileLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "image/png",
                                    "image/jpeg",
                                    "image/jpg"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload from Files", fontSize = 16.sp, color = BluePrimary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUploadDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            // Matching MAUI: Title="Job Files"
            TopAppBar(
                title = { Text("Job Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // Matching MAUI: Grid RowDefinitions="Auto,*,Auto" Padding="16"
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgColor)
                .padding(16.dp)
        ) {
            // ── Header Section ──
            // Matching MAUI: ShiftTitle (Bold, FontSize=20, #1F2937) + 📎 FileCountText
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                if (state.shiftTitle.isNotBlank()) {
                    Text(
                        text = state.shiftTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\uD83D\uDCCE", fontSize = 16.sp) // 📎
                    Text(
                        text = "${state.fileCountText}",
                        fontSize = 16.sp,
                        color = SubText
                    )
                }
            }

            // ── Files List (Grid.Row="1") ──
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BluePrimary)
                }
            } else if (state.isEmpty) {
                // Matching MAUI: Empty State — 📁 + messages
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // MAUI: 📁 FontSize=48, #9CA3AF
                        Text(
                            "\uD83D\uDCC1",
                            fontSize = 48.sp,
                            color = Gray400
                        )
                        // MAUI: "No files attached yet" EmptyStateStyle (FontSize=16, #6B7280, center)
                        Text(
                            "No files attached yet",
                            fontSize = 16.sp,
                            color = Gray500,
                            fontWeight = FontWeight.Normal
                        )
                        // MAUI: "Tap the upload button below to add files" FontSize=14, #9CA3AF
                        Text(
                            "Tap the upload button below to add files",
                            fontSize = 14.sp,
                            color = Gray400
                        )
                    }
                }
            } else {
                // Matching MAUI: CollectionView with FileCardStyle items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = state.files,
                        key = { index, file -> "file_${file.fileUrl}_$index" }
                    ) { _, file ->
                        ShiftFileCard(
                            file = file,
                            onView = {
                                // Matching MAUI ViewShiftDocumentViewModel: ServerUrl + FileUrl
                                val fullUrl = SHIFT_DOCS_SERVER_URL + file.fileUrl
                                onViewDocument(fullUrl, file.isImage)
                            },
                            onTap = {
                                val fullUrl = SHIFT_DOCS_SERVER_URL + file.fileUrl
                                onViewDocument(fullUrl, file.isImage)
                            }
                        )
                    }
                }
            }

            // ── Upload Section (Grid.Row="2") ──
            // Matching MAUI: Margin="0,20,0,0"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Matching MAUI: Upload Progress
                if (state.isUploading) {
                    // MAUI: ProgressBar Progress={UploadProgress}, ProgressColor=#3B82F6, bg=#E5E7EB
                    LinearProgressIndicator(
                        progress = { state.uploadProgress.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = BluePrimary,
                        trackColor = BorderColor
                    )
                    // MAUI: UploadStatusText FontSize=14, #6B7280, center
                    Text(
                        text = state.uploadStatusText,
                        fontSize = 14.sp,
                        color = Gray500
                    )
                }

                // Matching MAUI: "📎 Upload Files" UploadButtonStyle
                // bg=#3B82F6, White text, Bold, CornerRadius=8, Padding=20,12
                Button(
                    onClick = { viewModel.showUploadDialog() },
                    enabled = state.isEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\uD83D\uDCCE Upload Files", // 📎
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Matching MAUI FileCardStyle:
 * Border bg=White, Stroke=#E5E7EB, StrokeThickness=1, Padding=16, Margin=8,4, RoundRectangle 12
 * Grid: Image/Thumbnail (60x60) | FileName + CreatedByName + CreatedDate | 👁️ button
 */
@Composable
private fun ShiftFileCard(
    file: FileUploadModelDto,
    onView: () -> Unit,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onTap() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Matching MAUI: Image thumbnail (60x60, AspectFill, IsVisible=IsImage)
            if (file.isImage && file.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = if (file.thumbnailUrl.startsWith("http")) file.thumbnailUrl
                    else SHIFT_DOCS_SERVER_URL + file.thumbnailUrl,
                    contentDescription = file.fileName,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }

            // Matching MAUI: File Info — FileName + CreatedByName + CreatedDate
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // MAUI FileNameStyle: FontSize=16, Bold, #1F2937, Underline, TailTruncation
                Text(
                    text = file.fileName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    color = DarkText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // MAUI FileDetailsStyle: CreatedByName (FontSize=12, #6B7280)
                Text(
                    text = file.createdByName,
                    fontSize = 12.sp,
                    color = Gray500
                )
                // MAUI: CreatedDate dd/MM/yyyy (FontSize=12, #6B7280)
                Text(
                    text = formatFileDate(file.createdDate),
                    fontSize = 12.sp,
                    color = Gray500
                )
            }

            Spacer(Modifier.width(8.dp))

            // Matching MAUI: 👁️ view button (transparent bg, #3B82F6, FontSize=18)
            TextButton(onClick = onView) {
                Text(
                    "\uD83D\uDC41\uFE0F", // 👁️
                    fontSize = 18.sp,
                    color = BluePrimary
                )
            }
        }
    }
}

private val parseFormatters = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)
)

private val displayDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

// Matching MAUI: StringFormat='{0:dd/MM/yyyy}'
internal fun formatFileDate(dateStr: String): String {
    if (dateStr.isBlank()) return ""
    for (formatter in parseFormatters) {
        try {
            val parsed = LocalDateTime.parse(dateStr.trim(), formatter)
            return parsed.format(displayDateFormat)
        } catch (_: Exception) {
            // try next
        }
    }
    // Fallback: try trimming fractional seconds
    try {
        val trimmed = dateStr.replace(Regex("\\.\\d+$"), "")
        val parsed = LocalDateTime.parse(trimmed, parseFormatters[2])
        return parsed.format(displayDateFormat)
    } catch (_: Exception) {
        // ignore
    }
    return dateStr
}
