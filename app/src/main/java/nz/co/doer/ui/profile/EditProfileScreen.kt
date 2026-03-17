package nz.co.doer.ui.profile

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import nz.co.doer.data.remote.dto.FileModelDto
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// MAUI colors
private val BgColor = Color(0xFFF8F9FA)
private val BlueLabel = Color(0xFF4D4BA3) // MAUI {StaticResource Blue}
private val BorderStroke = Color(0xFF667685)
private val DeletePinkBg = Color(0xFFFFE6E6) // MAUI #FFE6E6
private val NoDocBg = Color(0xFFFFF3E0)
private val DocItemBg = Color(0xFFFAFAFA)
private const val PROFILE_DOCS_SERVER_URL = "https://doerapi.doer.nz"

// Document preview info for the popup
private data class DocPreview(
    val name: String,
    val url: String, // full URL or content:// URI
    val isImage: Boolean,
    val isLocal: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSuccess: (String) -> Unit,
    onViewDocument: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDocDialog by remember { mutableStateOf<FileModelDto?>(null) }
    var showDocPreview by remember { mutableStateOf<DocPreview?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            viewModel.addDocumentUri(uri)
        }
    }

    // Success popup with OK button
    state.successMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = {
                viewModel.clearSuccess()
                onSuccess(msg)
            },
            title = { Text("Success") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSuccess()
                    onSuccess(msg)
                }) { Text("OK") }
            }
        )
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Date picker dialog (non-customer only)
    if (showDatePicker && !state.isCustomer) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val formatted = localDate.format(
                            DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        )
                        viewModel.updateDateOfBirth(formatted)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // MAUI: Delete document confirmation
    // "Are you sure you want to delete this document?" Yes/No
    showDeleteDocDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteDocDialog = null },
            title = { Text("Confirmation") },
            text = { Text("Are you sure you want to delete this document?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExistingDocument(doc)
                    showDeleteDocDialog = null
                }) { Text("Yes", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDocDialog = null }) { Text("No") }
            }
        )
    }

    // MAUI: EditProfileDocumentPopup — full-screen dialog with document preview
    showDocPreview?.let { preview ->
        DocumentPreviewPopup(
            preview = preview,
            onDismiss = { showDocPreview = null }
        )
    }

    Scaffold(
        topBar = {
            // MAUI: Title="Edit Profile"
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // MAUI: StackLayout Padding="20,30,20,30" Spacing="25" BackgroundColor="#F8F9FA"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BgColor)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 30.dp)
            ) {
                // ── 👤 Name Field ── (always visible)
                // MAUI: MaxLength=30
                FieldLabel("\uD83D\uDC64", "Name")
                Spacer(Modifier.height(8.dp))
                BorderedEntryField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    placeholder = "Enter Name"
                )

                Spacer(Modifier.height(25.dp))

                // ── 🏠 Address Field ── (IsCustomer only, with Google Places)
                if (state.isCustomer) {
                    FieldLabel("\uD83C\uDFE0", "Address")
                    Spacer(Modifier.height(8.dp))
                    BorderedEntryField(
                        value = state.searchAddress,
                        onValueChange = viewModel::onSearchAddressChange,
                        placeholder = "Enter Address"
                    )
                    // Google Places suggestions (customer style)
                    if (state.showPlaceList && state.placeList.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                state.placeList.forEach { place ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable { viewModel.selectPlace(place) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = CardDefaults.cardColors(containerColor = BgColor),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Text(
                                            text = place.description,
                                            fontSize = 14.sp,
                                            color = BlueLabel,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(25.dp))
                }

                // ── 📅 Date Of Birth Field ── (IsVisible = !IsCustomer)
                if (!state.isCustomer) {
                    FieldLabel("\uD83D\uDCC5", "Date Of Birth")
                    Spacer(Modifier.height(8.dp))
                    // MAUI: DatePicker inside bordered frame
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(55.dp)
                                .border(2.dp, BorderStroke, RoundedCornerShape(12.dp))
                                .clickable { showDatePicker = true },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = state.dateOfBirth.ifBlank { "Select DateofBirth" },
                                fontSize = 16.sp,
                                color = if (state.dateOfBirth.isBlank()) Color.Gray else Color.Black,
                                modifier = Modifier.padding(horizontal = 15.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(25.dp))
                }

                // ── 📧 Email Field ── (always visible)
                FieldLabel("\uD83D\uDCE7", "Email")
                Spacer(Modifier.height(8.dp))
                BorderedEntryField(
                    value = state.email,
                    onValueChange = viewModel::updateEmail,
                    placeholder = "Enter Email"
                )

                Spacer(Modifier.height(25.dp))

                // ── 📱 Phone Number Field ── (always visible)
                // MAUI: MaxLength=30
                FieldLabel("\uD83D\uDCF1", "Phone Number")
                Spacer(Modifier.height(8.dp))
                BorderedEntryField(
                    value = state.phone,
                    onValueChange = viewModel::updatePhone,
                    placeholder = "Enter Phone Number"
                )

                Spacer(Modifier.height(25.dp))

                // ── 📍 Address Field ── (IsCaregiver only, with Google Places)
                if (state.isCaregiver) {
                    FieldLabel("\uD83D\uDCCD", "Address")
                    Spacer(Modifier.height(8.dp))
                    // MAUI: entry + suggestions inside same Frame
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(55.dp)
                                    .border(2.dp, BorderStroke, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                OutlinedTextField(
                                    value = state.searchAddress,
                                    onValueChange = viewModel::onSearchAddressChange,
                                    placeholder = { Text("Enter Address", fontSize = 16.sp, color = Color.Gray) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 5.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    )
                                )
                            }
                            // Google Places suggestions (caregiver style - inside card)
                            if (state.showPlaceList && state.placeList.isNotEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = BgColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        state.placeList.forEach { place ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .clickable { viewModel.selectPlace(place) },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                                                ) {
                                                    Text(
                                                        text = place.description,
                                                        fontSize = 14.sp,
                                                        color = BlueLabel,
                                                        modifier = Modifier.padding(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(25.dp))

                    // ── 💼 Work Experience Field ──
                    FieldLabel("\uD83D\uDCBC", "Work Experience")
                    Spacer(Modifier.height(8.dp))
                    BorderedEntryField(
                        value = state.workExperience,
                        onValueChange = viewModel::updateWorkExperience,
                        placeholder = "Enter Work Experience"
                    )

                    Spacer(Modifier.height(25.dp))

                    // ── ⭐ Skills Field ──
                    FieldLabel("\u2B50", "Skills")
                    Spacer(Modifier.height(8.dp))
                    BorderedEntryField(
                        value = state.skills,
                        onValueChange = viewModel::updateSkills,
                        placeholder = "Enter Skills"
                    )

                    Spacer(Modifier.height(25.dp))

                    // ── 📋 Your Documents Section ──
                    // MAUI: "📋 Your Documents" (FontSize=18, Bold, Blue)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83D\uDCCB", fontSize = 16.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Your Documents",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueLabel
                        )
                    }

                    Spacer(Modifier.height(15.dp))

                    // No Documents message
                    if (state.isNoDocument) {
                        // MAUI: Frame bg=#FFF3E0, CornerRadius=10, Padding=15, BorderColor=Gray
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.Gray, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = NoDocBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("\uD83D\uDCDD", fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "No Documents found.",
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Existing documents list
                    if (state.hasDocument && state.existingDocuments.isNotEmpty()) {
                        // MAUI: Frame bg=White, HasShadow, CornerRadius=12, Padding=10
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                state.existingDocuments.forEach { doc ->
                                    // MAUI: Frame CornerRadius=8, BorderColor=#E0E0E0, Padding=12, Margin=0,3, bg=#FAFAFA
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                                            .clickable {
                                                val isImage = doc.name.endsWith(".jpg", true) ||
                                                        doc.name.endsWith(".jpeg", true) ||
                                                        doc.name.endsWith(".png", true)
                                                val fullUrl = PROFILE_DOCS_SERVER_URL + doc.fileUrl
                                                showDocPreview = DocPreview(
                                                    name = doc.name,
                                                    url = fullUrl,
                                                    isImage = isImage,
                                                    isLocal = false
                                                )
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = DocItemBg),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // MAUI: 📄 (Orange)
                                            Text(
                                                "\uD83D\uDCC4",
                                                fontSize = 16.sp,
                                                color = Color(0xFFFF9500)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            // MAUI: Name (Gray500, FontSize=16)
                                            Text(
                                                text = doc.name,
                                                fontSize = 16.sp,
                                                color = Color.Gray,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // MAUI: Delete icon in pink circle
                                            // Frame bg=#FFE6E6, CornerRadius=15, Padding=8
                                            Card(
                                                shape = RoundedCornerShape(15.dp),
                                                colors = CardDefaults.cardColors(containerColor = DeletePinkBg),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { showDeleteDocDialog = doc },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(15.dp))

                    // New documents (pending upload) - matching MAUI Files collection
                    if (state.newDocumentUris.isNotEmpty()) {
                        state.newDocumentUris.forEach { uri ->
                            val fileName = getFileNameFromUri(uri, context)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        val isImage = fileName.endsWith(".jpg", true) ||
                                                fileName.endsWith(".jpeg", true) ||
                                                fileName.endsWith(".png", true)
                                        showDocPreview = DocPreview(
                                            name = fileName,
                                            url = uri.toString(),
                                            isImage = isImage,
                                            isLocal = true
                                        )
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = BgColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("\uD83D\uDCC4", fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = fileName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Blue,
                                        )
                                        Text(
                                            "Tap to view",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    // MAUI: Remove icon in pink circle
                                    Card(
                                        shape = RoundedCornerShape(15.dp),
                                        colors = CardDefaults.cardColors(containerColor = DeletePinkBg),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.removeNewDocument(uri) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = Color.Red,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // MAUI: "Add Documents" button — Frame bg=Black, CornerRadius=20, WidthRequest=140, height=40
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "image/png",
                                        "image/jpeg",
                                        "image/jpg"
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .width(140.dp)
                                .height(40.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                "Add Documents",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // MAUI: "ℹ️ Accepts only PDF, PNG, JPG and JPEG file types" in Red, FontSize=13, centered
                    Text(
                        "\u2139\uFE0F Accepts only PDF, PNG, JPG and JPEG file types",
                        color = Color.Red,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(Modifier.height(25.dp))
                }

                // ── Update Profile Button ──
                // MAUI: Frame bg=Black, CornerRadius=20, WidthRequest=140, height=40, centered
                // Margin="0,20,0,30"
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            val files = state.newDocumentUris.mapNotNull { uri ->
                                copyUriToTempFile(context, uri)
                            }
                            viewModel.saveProfile(files)
                        },
                        modifier = Modifier
                            .width(140.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Update Profile",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/**
 * MAUI field label: emoji (FontSize=16) + text (FontSize=16, Bold, Blue)
 * StackLayout Orientation="Horizontal" Spacing="5"
 */
@Composable
private fun FieldLabel(emoji: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = BlueLabel
        )
    }
}

/**
 * MAUI bordered entry field:
 * Frame (White bg, HasShadow, CornerRadius=12, Padding=0) >
 *   Border (Stroke=#667685, StrokeThickness=2, RoundRectangle 12, HeightRequest=55) >
 *     BorderlessEntry (FontSize=16, Padding=15,0,15,0 on Android)
 */
@Composable
private fun BorderedEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .border(2.dp, BorderStroke, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, fontSize = 16.sp, color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )
        }
    }
}

// Matching MAUI: EditProfileDocumentPopup
// Header: Document.Name (uppercase, #4d4ba3, Bold, FontSize=20) + "X" close button (#4d4ba3)
// Body: Image / WebView (Google Docs) / local PDF
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DocumentPreviewPopup(
    preview: DocPreview,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(15.dp)
        ) {
            // Header — matching MAUI StackLayout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preview.name.uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueLabel,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueLabel),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (preview.isImage) {
                    // Image preview — works for both content:// URIs and http URLs
                    val model: Any = if (preview.isLocal) Uri.parse(preview.url) else preview.url
                    var isLoading by remember { mutableStateOf(true) }
                    AsyncImage(
                        model = model,
                        contentDescription = preview.name,
                        contentScale = ContentScale.Fit,
                        onSuccess = { isLoading = false },
                        onError = { isLoading = false },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isLoading) {
                        CircularProgressIndicator()
                    }
                } else if (preview.isLocal) {
                    // Local PDF — load in WebView via file URI
                    var isLoading by remember { mutableStateOf(true) }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl(preview.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isLoading) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Remote PDF — Google Docs viewer
                    var isLoading by remember { mutableStateOf(true) }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                val encodedUrl = java.net.URLEncoder.encode(preview.url, "UTF-8")
                                loadUrl("https://docs.google.com/gview?embedded=true&url=$encodedUrl")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isLoading) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun getFileNameFromUri(uri: Uri, context: android.content.Context? = null): String {
    if (context != null) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
    }
    val path = uri.lastPathSegment ?: uri.toString()
    return path.substringAfterLast("/").substringAfterLast(":")
}

private fun copyUriToTempFile(context: android.content.Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = getFileNameFromUri(uri, context)
        val tempFile = File(context.cacheDir, fileName)
        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()
        tempFile
    } catch (e: Exception) {
        null
    }
}
