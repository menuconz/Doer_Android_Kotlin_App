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

// MAUI colors — same as Files.xaml
private val BluePrimary = Color(0xFF3B82F6)
private val DarkText = Color(0xFF1F2937)
private val SubText = Color(0xFF374151)
private val Gray500 = Color(0xFF6B7280)
private val Gray400 = Color(0xFF9CA3AF)
private val BorderColor = Color(0xFFE5E7EB)
private val BgColor = Color(0xFFF8F9FA)

// Matching MAUI: ServerUrl for shift/sub-item documents
private const val SHIFT_DOCS_SERVER_URL = "https://doerapi.doer.nz/userDocuments/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubItemFilesScreen(
    onBack: () -> Unit,
    onViewDocument: (fileUrl: String, isImage: Boolean) -> Unit,
    viewModel: SubItemFilesViewModel = hiltViewModel()
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

    // Popup when there are no files for this sub-item (replaces the HTTP 404 snackbar)
    if (state.showNoFilesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoFilesDialog() },
            title = { Text("No Files") },
            text = { Text("No files are available for this stage yet.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissNoFilesDialog() }) {
                    Text("OK")
                }
            }
        )
    }

    // Matching MAUI: ActionSheet "Select Upload Option"
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgColor)
                .padding(16.dp)
        ) {
            // ── Header Section ──
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                if (state.subItemTitle.isNotBlank()) {
                    Text(
                        text = state.subItemTitle,
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

            // ── Files List ──
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
                        Text("\uD83D\uDCC1", fontSize = 48.sp, color = Gray400)
                        Text(
                            "No files attached yet",
                            fontSize = 16.sp,
                            color = Gray500
                        )
                        Text(
                            "Tap the upload button below to add files",
                            fontSize = 14.sp,
                            color = Gray400
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = state.files,
                        key = { index, file -> "file_${file.fileUrl}_$index" }
                    ) { _, file ->
                        SubItemFileCard(
                            file = file,
                            onView = {
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

            // ── Upload Section ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isUploading) {
                    LinearProgressIndicator(
                        progress = { state.uploadProgress.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = BluePrimary,
                        trackColor = BorderColor
                    )
                    Text(
                        text = state.uploadStatusText,
                        fontSize = 14.sp,
                        color = Gray500
                    )
                }

                Button(
                    onClick = { viewModel.showUploadDialog() },
                    enabled = state.isEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\uD83D\uDCCE Upload Files",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SubItemFileCard(
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = file.fileName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    color = DarkText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.createdByName,
                    fontSize = 12.sp,
                    color = Gray500
                )
                Text(
                    text = formatFileDate(file.createdDate),
                    fontSize = 12.sp,
                    color = Gray500
                )
            }

            Spacer(Modifier.width(8.dp))

            TextButton(onClick = onView) {
                Text(
                    "\uD83D\uDC41\uFE0F",
                    fontSize = 18.sp,
                    color = BluePrimary
                )
            }
        }
    }
}
