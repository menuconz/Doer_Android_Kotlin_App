package nz.co.doer.ui.leads

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
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
import nz.co.doer.data.remote.PlacePrediction
import nz.co.doer.data.remote.dto.ClientDto
import nz.co.doer.data.remote.dto.UserDto

private val HeaderBg = Color(0xFFF3F4F6)
private val BorderColor = Color.LightGray
private val OrangeAccent = Color(0xFFFF9500)

// Column widths matching MAUI
private val ColProjectDesc = 220.dp
private val ColOwner = 150.dp
private val ColStatus = 150.dp
private val ColCost = 150.dp
private val ColClient = 180.dp
private val ColLocation = 200.dp
private val ColContractType = 220.dp
private val ColCreatedDate = 150.dp
private val ColView = 180.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLeadsScreen(
    onAddLead: () -> Unit,
    onViewLead: (Int) -> Unit,
    viewModel: NewLeadsViewModel = hiltViewModel()
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
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Bottom sheet dialogs
    if (state.editingLead != null && state.editField != null) {
        when (state.editField) {
            EditField.ProjectDescription, EditField.Cost -> {
                EditorBottomSheet(
                    title = if (state.editField == EditField.ProjectDescription) "Project Description" else "Cost From Quote",
                    value = state.editValue,
                    isCost = state.editField == EditField.Cost,
                    onValueChange = viewModel::onEditValueChange,
                    onSave = viewModel::saveEditorField,
                    onDismiss = viewModel::cancelEdit
                )
            }
            EditField.Status -> {
                StatusBottomSheet(
                    onSelect = viewModel::selectLeadStatus,
                    onDismiss = viewModel::cancelEdit,
                    options = viewModel.dynamicLeadStatuses(),
                    colorProvider = viewModel::leadStatusColor
                )
            }
            EditField.ContractType -> {
                ContractTypeBottomSheet(
                    onSelect = viewModel::selectContractType,
                    onDismiss = viewModel::cancelEdit,
                    options = viewModel.dynamicContractTypes(),
                    colorProvider = { viewModel.contractTypeColorDynamic(it) }
                )
            }
            EditField.Client -> {
                ClientBottomSheet(
                    clients = state.clients,
                    onSelect = viewModel::selectClient,
                    onDismiss = viewModel::cancelEdit
                )
            }
            EditField.Owner -> {
                OwnerBottomSheet(
                    owners = state.owners,
                    onSelect = viewModel::selectOwner,
                    onDismiss = viewModel::cancelEdit
                )
            }
            EditField.Location -> {
                LocationBottomSheet(
                    searchAddress = state.searchAddress,
                    placeList = state.placeList,
                    showPlaceList = state.showPlaceList,
                    onSearchChange = viewModel::onSearchAddressChange,
                    onClearSearch = viewModel::clearSearchAddress,
                    onSelectPlace = viewModel::selectPlace,
                    onDismiss = viewModel::cancelEdit
                )
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.leads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No new leads found", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Table card with orange left stripe
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(10.dp, 5.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp))
                        .background(Color.White)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Orange left stripe
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxSize()
                                .background(OrangeAccent)
                        )

                        // Table content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                ) {
                                    // Header
                                    Row(modifier = Modifier.background(HeaderBg)) {
                                        SortableHeader("Project Description", ColProjectDesc, "JobDescription", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Owner", ColOwner, "OwnerName", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Status", ColStatus, "StatusName", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Cost From Quote", ColCost, "CostFromQuote", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Client", ColClient, "ClientName", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Location", ColLocation, "Location", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Contract Type", ColContractType, "ContractTypeName", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Created Date", ColCreatedDate, "CreatedDate", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        // Empty header for View column
                                        Box(
                                            modifier = Modifier
                                                .width(ColView)
                                                .height(40.dp)
                                                .border(0.5.dp, Color.Gray)
                                                .padding(5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Data rows
                                    state.leads.forEach { lead ->
                                        Row {
                                            // Project Description - editable (shows jobDescription like MAUI)
                                            EditableCell(lead.jobDescription, ColProjectDesc) {
                                                viewModel.startEdit(lead, EditField.ProjectDescription)
                                            }
                                            // Owner - editable
                                            EditableCell(lead.ownerName, ColOwner) {
                                                viewModel.startEdit(lead, EditField.Owner)
                                            }
                                            // Status - solid colored background with white bold text (MAUI style)
                                            SolidColorCell(
                                                text = lead.statusName,
                                                width = ColStatus,
                                                bgColor = Color(viewModel.leadStatusColor(lead.statusId))
                                            ) {
                                                viewModel.startEdit(lead, EditField.Status)
                                            }
                                            // Cost
                                            EditableCell(
                                                text = lead.costFromQuote?.let { "$it" } ?: "",
                                                width = ColCost
                                            ) {
                                                viewModel.startEdit(lead, EditField.Cost)
                                            }
                                            // Client - editable
                                            EditableCell(lead.clientName, ColClient) {
                                                viewModel.startEdit(lead, EditField.Client)
                                            }
                                            // Location - editable
                                            EditableCell(lead.location, ColLocation) {
                                                viewModel.startEdit(lead, EditField.Location)
                                            }
                                            // Contract Type - solid colored background with white bold text (MAUI style)
                                            SolidColorCell(
                                                text = lead.contractTypeName,
                                                width = ColContractType,
                                                bgColor = Color(viewModel.contractTypeColorDynamic(lead.contractType))
                                            ) {
                                                viewModel.startEdit(lead, EditField.ContractType)
                                            }
                                            // Created Date - read only
                                            DataCell(viewModel.formatDate(lead.createdDate), ColCreatedDate)
                                            // View button - text button matching MAUI
                                            Box(
                                                modifier = Modifier
                                                    .width(ColView)
                                                    .height(50.dp)
                                                    .border(0.5.dp, BorderColor)
                                                    .padding(4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                OutlinedButton(
                                                    onClick = { onViewLead(lead.id) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = Color.White,
                                                        contentColor = Color(0xFF667685)
                                                    ),
                                                    modifier = Modifier.height(35.dp)
                                                ) {
                                                    Text(
                                                        "View",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom "Add Lead" button matching MAUI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onAddLead,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .width(150.dp)
                            .height(40.dp)
                    ) {
                        Text("Add Lead", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Updating overlay
        if (state.isUpdating) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
internal fun SortableHeader(
    title: String,
    width: Dp,
    column: String,
    currentSortColumn: String,
    sortAscending: Boolean,
    onSort: (String) -> Unit
) {
    val sortIcon = if (currentSortColumn == column) {
        if (sortAscending) " \u25B2" else " \u25BC"
    } else ""

    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .border(0.5.dp, Color.Gray)
            .clickable { onSort(column) }
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (sortIcon.isNotEmpty()) {
                Text(
                    text = sortIcon.trim(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
internal fun DataCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(50.dp)
            .border(0.5.dp, BorderColor)
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF374151),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun EditableCell(text: String, width: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .height(50.dp)
            .border(0.5.dp, BorderColor)
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF374151),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SolidColorCell(
    text: String,
    width: Dp,
    bgColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(50.dp)
            .border(0.5.dp, BorderColor)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---- Bottom Sheet Dialogs matching MAUI popup style ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorBottomSheet(
    title: String,
    value: String,
    isCost: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with close, title, save
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = androidx.compose.ui.Modifier.height(30.dp).width(30.dp))
                }
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        onSave()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(40.dp).width(100.dp)
                ) {
                    Text("Save", color = Color.White, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF667685))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Editor
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    if (isCost) {
                        if (newValue.isEmpty() || newValue.toDoubleOrNull() != null || newValue == ".") {
                            onValueChange(newValue)
                        }
                    } else {
                        onValueChange(newValue)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCost) 56.dp else 150.dp),
                placeholder = { Text("Enter Value...") },
                singleLine = isCost,
                prefix = if (isCost) {{ Text("$") }} else null
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusBottomSheet(
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<Int, String>> = NewLeadsViewModel.leadStatuses,
    colorProvider: (Int) -> Long = { NewLeadsViewModel.getLeadStatusColor(it) }
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.height(30.dp).width(30.dp))
                }
                Text("Status", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            options.forEach { (id, name) ->
                val color = Color(colorProvider(id))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .clickable { onSelect(id) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContractTypeBottomSheet(
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<Int, String>> = NewLeadsViewModel.contractTypes,
    colorProvider: (Int) -> Long = { NewLeadsViewModel.getContractTypeColor(it) }
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.height(30.dp).width(30.dp))
                }
                Text("Contract Type", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            options.forEach { (id, name) ->
                val color = Color(colorProvider(id))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .clickable { onSelect(id) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientBottomSheet(
    clients: List<ClientDto>,
    onSelect: (ClientDto) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.height(30.dp).width(30.dp))
                }
                Text("Client", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            clients.forEach { client ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                        .clickable { onSelect(client) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        client.name,
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnerBottomSheet(
    owners: List<UserDto>,
    onSelect: (UserDto) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.height(30.dp).width(30.dp))
                }
                Text("Owner", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            owners.forEach { owner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                        .clickable { onSelect(owner) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        owner.displayName,
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationBottomSheet(
    searchAddress: String,
    placeList: List<PlacePrediction>,
    showPlaceList: Boolean,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectPlace: (PlacePrediction) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.height(30.dp).width(30.dp))
                }
                Text("Location", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Location label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCCD", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Text("Location:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Search field with clear button
            OutlinedTextField(
                value = searchAddress,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter Address") },
                singleLine = true,
                trailingIcon = {
                    if (searchAddress.isNotEmpty()) {
                        TextButton(onClick = onClearSearch) {
                            Text("X", color = Color.Black, fontSize = 16.sp)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            if (!showPlaceList && searchAddress.isBlank()) {
                // Initial state hint
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("\uD83D\uDCDD", fontSize = 24.sp, color = Color(0xFF9CA3AF))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Start typing to search", fontSize = 14.sp, color = Color(0xFF6B7280))
                }
            }

            // Place suggestions
            if (showPlaceList) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8F9FA))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    placeList.forEach { prediction ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))
                                .clickable { onSelectPlace(prediction) }
                                .padding(10.dp)
                        ) {
                            Text(
                                prediction.description,
                                fontSize = 14.sp,
                                color = Color(0xFF007AFF)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
