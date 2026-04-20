package nz.co.doer.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

// MAUI colors
private val OrangeAccent = Color(0xFFFF9500) // MAUI #FF9500
private val BgColor = Color(0xFFF8F9FA)
private val HeaderBg = Color(0xFFF3F4F6)
private val BorderColor = Color.LightGray
private val BlueButton = Color(0xFF007AFF) // MAUI {StaticResource Blue}
private val BorderStroke = Color(0xFF667685)

// Column widths matching MAUI DataGridColumn WidthRequest
private val ColName = 200.dp
private val ColEmail = 250.dp
private val ColProjects = 150.dp
private val ColActions = 50.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(
    onAddClient: () -> Unit,
    viewModel: ClientsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    // Refresh data on resume so changes made in other screens are reflected
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadClients()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Edit bottom sheet (matching MAUI ClientEditorPopupView)
    if (state.editingClient != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissEditSheet() },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            ClientEditorSheetContent(
                title = state.editorTitle,
                text = state.editorText,
                onTextChange = viewModel::updateEditorText,
                onSave = viewModel::saveEdit,
                onClose = viewModel::dismissEditSheet,
                isSaving = state.isSaving
            )
        }
    }

    // Projects bottom sheet (matching MAUI ClientProjectsPopupView)
    if (state.projectsClient != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissProjectsSheet() },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            ClientProjectsSheetContent(
                searchText = state.searchText,
                onSearchChange = viewModel::onSearchTextChange,
                jobs = state.filteredJobs,
                isLoading = state.isLoadingProjects,
                isSaving = state.isSavingProjects,
                onToggle = viewModel::toggleJobAssignment,
                onSave = viewModel::saveProjectAssignments,
                onClose = viewModel::dismissProjectsSheet
            )
        }
    }

    // Delete confirmation dialog (matching MAUI)
    state.deletingClient?.let {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Client") },
            text = { Text("Do you really want to delete this Client?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    enabled = !state.isDeleting
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Yes", color = Color.Red)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("No")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgColor)
            ) {
                if (state.clients.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No clients found", color = Color.Gray, fontSize = 16.sp)
                    }
                } else {
                    // Horizontally scrollable table (no count text - MAUI doesn't have it)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // MAUI: orange accent bar on left (5dp width, #FF9500)
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxSize()
                                .background(OrangeAccent)
                        )

                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            // Header Row - MAUI HeaderHeight=40
                            Row(modifier = Modifier.background(HeaderBg)) {
                                SortableHeader("Client Name", ColName, "Name", state, viewModel::sortBy)
                                SortableHeader("Client Email", ColEmail, "Email", state, viewModel::sortBy)
                                // Projects header (not sortable)
                                Box(
                                    modifier = Modifier
                                        .width(ColProjects)
                                        .height(40.dp)
                                        .border(0.5.dp, BorderColor)
                                        .padding(8.dp, 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Projects",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF374151)
                                    )
                                }
                                // Delete header
                                Box(
                                    modifier = Modifier
                                        .width(ColActions)
                                        .height(40.dp)
                                        .border(0.5.dp, BorderColor)
                                        .padding(8.dp, 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Delete",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF374151)
                                    )
                                }
                            }

                            // Data Rows - MAUI RowHeight=50
                            state.clients.forEach { client ->
                                Row {
                                    // Name cell - tappable to edit (matching MAUI TapGestureRecognizer)
                                    Box(
                                        modifier = Modifier
                                            .width(ColName)
                                            .height(50.dp)
                                            .border(0.5.dp, BorderColor)
                                            .clickable { viewModel.editClientName(client) }
                                            .padding(8.dp, 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = client.name,
                                            fontSize = 14.sp,
                                            color = Color(0xFF374151),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    // Email cell - tappable to edit
                                    Box(
                                        modifier = Modifier
                                            .width(ColEmail)
                                            .height(50.dp)
                                            .border(0.5.dp, BorderColor)
                                            .clickable { viewModel.editClientEmail(client) }
                                            .padding(8.dp, 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = client.email,
                                            fontSize = 14.sp,
                                            color = Color(0xFF374151),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    // Projects cell - "View" button
                                    // MAUI: Blue bg (#007AFF), White text, CornerRadius=5, HeightRequest=35, FontSize=12
                                    Box(
                                        modifier = Modifier
                                            .width(ColProjects)
                                            .height(50.dp)
                                            .border(0.5.dp, BorderColor)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = { viewModel.viewClientProjects(client) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = BlueButton,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(5.dp),
                                            modifier = Modifier.height(35.dp)
                                        ) {
                                            Text("View", fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                    // Delete cell - delete icon
                                    // MAUI: ImageButton delete.png
                                    Box(
                                        modifier = Modifier
                                            .width(ColActions)
                                            .height(50.dp)
                                            .border(0.5.dp, BorderColor)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(onClick = { viewModel.openDeleteDialog(client) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.Red
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // MAUI: "Add Client" button centered, 150x40, Black, CornerRadius=20, FontSize=14
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onAddClient,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .width(150.dp)
                            .height(40.dp)
                    ) {
                        Text(
                            text = "Add Client",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * MAUI ClientEditorPopupView bottom sheet:
 * ⛌ close + EditorTitle + Save (#007AFF, 40x100, CornerRadius=6)
 * Divider (stroke #667685)
 * BorderlessEditor (height 150, FontSize=16, Placeholder="Enter Value...")
 */
@Composable
private fun ClientEditorSheetContent(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    isSaving: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Top row: ⛌ close + title + Save button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MAUI: ⛌ close button
            IconButton(onClick = onClose) {
                Text("⛌", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            // MAUI: EditorTitle label
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            // MAUI: Save button (#007AFF, 40x100, CornerRadius=6)
            Button(
                onClick = onSave,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.width(100.dp).height(40.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // MAUI: Divider (stroke #667685)
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = BorderStroke, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // MAUI: BorderlessEditor (Margin=10, height=150, FontSize=16, Placeholder="Enter Value...")
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Enter Value...", fontSize = 16.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * MAUI ClientProjectsPopupView:
 * Header: #007AFF bg (height 60), "Projects" title white, "X" close button
 * SearchBar "Search projects..."
 * CollectionView: ProjectName (Bold) + CheckBox (#007AFF)
 * "SAVE CHANGES" button (#007AFF, CornerRadius=10, height=50, Bold)
 */
@Composable
private fun ClientProjectsSheetContent(
    searchText: String,
    onSearchChange: (String) -> Unit,
    jobs: List<nz.co.doer.data.remote.dto.ClientJobDto>,
    isLoading: Boolean,
    isSaving: Boolean,
    onToggle: (nz.co.doer.data.remote.dto.ClientJobDto) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // MAUI: Header bar (#007AFF, height 60)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(BlueButton)
        ) {
            // "Projects" title centered
            Text(
                text = "Projects",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            // "X" close button right
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        // MAUI: SearchBar "Search projects..."
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            placeholder = { Text("Search projects...", fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        // Job list
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (jobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No projects available", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                jobs.forEach { job ->
                    // MAUI: Grid with ProjectName (Bold) + CheckBox (#007AFF)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(job) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = job.projectName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = job.isAssigned,
                            onCheckedChange = { onToggle(job) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BlueButton,
                                uncheckedColor = BlueButton
                            )
                        )
                    }
                }
            }
        }

        // MAUI: "SAVE CHANGES" button (#007AFF, CornerRadius=10, height=50, Bold)
        Button(
            onClick = onSave,
            enabled = !isSaving && !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(50.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    "SAVE CHANGES",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SortableHeader(
    title: String,
    width: Dp,
    column: String,
    state: ClientsUiState,
    onSort: (String) -> Unit
) {
    val sortIcon = if (state.sortColumn == column) {
        if (state.sortAscending) " \u25B2" else " \u25BC"
    } else ""

    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp)  // MAUI HeaderHeight=40
            .border(0.5.dp, BorderColor)
            .clickable { onSort(column) }
            .padding(8.dp, 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "$title$sortIcon",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF374151),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
