package nz.co.doer.ui.leads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import nz.co.doer.data.remote.dto.LeadsDto

private val HeaderBg = Color(0xFFF3F4F6)
private val BorderColor = Color.LightGray

// Section accent colors matching MAUI
private val BlueAccent = Color(0xFF0285C4)    // Quote Not Yet Accepted
private val GreenAccent = Color(0xFF00C874)   // Closed Deal
private val GrayAccent = Color(0xFF808080)    // Quote Expired
private val PurpleAccent = Color(0xFF800080)  // Drafted

// Column widths matching MAUI QualifiedLeads.xaml
private val ColProjectDesc = 220.dp
private val ColOwner = 150.dp
private val ColStatus = 150.dp
private val ColCost = 150.dp
private val ColClient = 180.dp
private val ColLocation = 220.dp
private val ColContractType = 180.dp
private val ColCreatedDate = 150.dp
private val ColActions = 220.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotedLeadsScreen(
    onViewLead: (Int) -> Unit,
    viewModel: QuotedLeadsViewModel = hiltViewModel()
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
                QuotedStatusBottomSheet(
                    onSelect = viewModel::selectLeadStatus,
                    onDismiss = viewModel::cancelEdit
                )
            }
            EditField.ContractType -> {
                ContractTypeBottomSheet(
                    onSelect = viewModel::selectContractType,
                    onDismiss = viewModel::cancelEdit
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Quote Not Yet Accepted section
                QuotedSectionView(
                    title = "Quote Not Yet Accepted",
                    accentColor = BlueAccent,
                    sectionState = state.quoteNotAccepted,
                    section = QuotedSection.QuoteNotAccepted,
                    onToggle = { viewModel.toggleSection(QuotedSection.QuoteNotAccepted) },
                    onSort = { viewModel.sortSection(QuotedSection.QuoteNotAccepted, it) },
                    onViewLead = onViewLead,
                    onEditLead = { lead, field -> viewModel.startEdit(lead, field) },
                    viewModel = viewModel,
                    showFollowUp = true,
                    onFollowUp = { viewModel.sendFollowUp(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Closed Deal section
                QuotedSectionView(
                    title = "Closed Deal",
                    accentColor = GreenAccent,
                    sectionState = state.closedDeal,
                    section = QuotedSection.ClosedDeal,
                    onToggle = { viewModel.toggleSection(QuotedSection.ClosedDeal) },
                    onSort = { viewModel.sortSection(QuotedSection.ClosedDeal, it) },
                    onViewLead = onViewLead,
                    onEditLead = { lead, field -> viewModel.startEdit(lead, field) },
                    viewModel = viewModel,
                    showFollowUp = false
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quote Expired section
                QuotedSectionView(
                    title = "Quote Expired",
                    accentColor = GrayAccent,
                    sectionState = state.quoteExpired,
                    section = QuotedSection.QuoteExpired,
                    onToggle = { viewModel.toggleSection(QuotedSection.QuoteExpired) },
                    onSort = { viewModel.sortSection(QuotedSection.QuoteExpired, it) },
                    onViewLead = onViewLead,
                    onEditLead = { lead, field -> viewModel.startEdit(lead, field) },
                    viewModel = viewModel,
                    showFollowUp = false
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Drafted section
                QuotedSectionView(
                    title = "Drafted",
                    accentColor = PurpleAccent,
                    sectionState = state.drafted,
                    section = QuotedSection.Drafted,
                    onToggle = { viewModel.toggleSection(QuotedSection.Drafted) },
                    onSort = { viewModel.sortSection(QuotedSection.Drafted, it) },
                    onViewLead = onViewLead,
                    onEditLead = { lead, field -> viewModel.startEdit(lead, field) },
                    viewModel = viewModel,
                    showFollowUp = false
                )

                Spacer(modifier = Modifier.height(32.dp))
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
private fun QuotedSectionView(
    title: String,
    accentColor: Color,
    sectionState: SectionState,
    section: QuotedSection,
    onToggle: () -> Unit,
    onSort: (String) -> Unit,
    onViewLead: (Int) -> Unit,
    onEditLead: (LeadsDto, EditField) -> Unit,
    viewModel: QuotedLeadsViewModel,
    showFollowUp: Boolean,
    onFollowUp: ((Int) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header with toggle icon matching MAUI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (sectionState.isExpanded) "\u25BC" else "\u25B6",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = " $title",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }

        AnimatedVisibility(visible = sectionState.isExpanded) {
            if (sectionState.leads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No leads in this section", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                // Card with accent stripe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .background(Color.White)
                ) {
                    Row {
                        // Left accent stripe
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(
                                    (40 + sectionState.leads.size * 50).dp
                                )
                                .background(accentColor)
                        )

                        Column(modifier = Modifier.padding(start = 5.dp)) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Column {
                                    // Header
                                    Row(modifier = Modifier.background(HeaderBg)) {
                                        SortableHeader("Project Description", ColProjectDesc, "JobDescription", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        SortableHeader("Owner", ColOwner, "OwnerName", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        SortableHeader("Status", ColStatus, "StatusName", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        SortableHeader("Cost From Quote", ColCost, "CostFromQuote", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        SortableHeader("Client", ColClient, "ClientName", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        SortableHeader("Location", ColLocation, "Location", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        SortableHeader("Contract Type", ColContractType, "ContractTypeName", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        SortableHeader("Created Date", ColCreatedDate, "CreatedDate", sectionState.sortColumn, sectionState.sortAscending, onSort)
                                        // Actions header
                                        Box(
                                            modifier = Modifier
                                                .width(ColActions)
                                                .height(40.dp)
                                                .border(0.5.dp, Color.Gray)
                                                .padding(5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Data rows
                                    sectionState.leads.forEach { lead ->
                                        Row {
                                            // Project Description - editable (shows jobDescription)
                                            EditableCell(lead.jobDescription, ColProjectDesc) {
                                                onEditLead(lead, EditField.ProjectDescription)
                                            }
                                            // Owner - editable
                                            EditableCell(lead.ownerName, ColOwner) {
                                                onEditLead(lead, EditField.Owner)
                                            }
                                            // Status - solid colored background with white bold text
                                            SolidColorCell(
                                                text = lead.statusName,
                                                width = ColStatus,
                                                bgColor = Color(NewLeadsViewModel.getLeadStatusColor(lead.statusId))
                                            ) {
                                                onEditLead(lead, EditField.Status)
                                            }
                                            // Cost
                                            EditableCell(
                                                text = lead.costFromQuote?.let { "$it" } ?: "",
                                                width = ColCost
                                            ) {
                                                onEditLead(lead, EditField.Cost)
                                            }
                                            // Client - editable
                                            EditableCell(lead.clientName, ColClient) {
                                                onEditLead(lead, EditField.Client)
                                            }
                                            // Location - editable
                                            EditableCell(lead.location, ColLocation) {
                                                onEditLead(lead, EditField.Location)
                                            }
                                            // Contract Type - solid colored
                                            SolidColorCell(
                                                text = lead.contractTypeName,
                                                width = ColContractType,
                                                bgColor = Color(NewLeadsViewModel.getContractTypeColor(lead.contractType))
                                            ) {
                                                onEditLead(lead, EditField.ContractType)
                                            }
                                            // Created Date - read only
                                            DataCell(viewModel.formatDate(lead.createdDate), ColCreatedDate)
                                            // View + Follow-up buttons
                                            Box(
                                                modifier = Modifier
                                                    .width(ColActions)
                                                    .height(50.dp)
                                                    .border(0.5.dp, BorderColor)
                                                    .padding(4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    OutlinedButton(
                                                        onClick = { onViewLead(lead.id) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            containerColor = Color.White,
                                                            contentColor = Color(0xFF667685)
                                                        ),
                                                        modifier = Modifier.height(35.dp)
                                                    ) {
                                                        Text("View", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    if (showFollowUp) {
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        // MAUI: Button "Follow up quote" bg=#0285c4, Bold, CornerRadius=10, BorderColor=Black
                                                        androidx.compose.material3.OutlinedButton(
                                                            onClick = { onFollowUp?.invoke(lead.id) },
                                                            shape = RoundedCornerShape(10.dp),
                                                            colors = ButtonDefaults.outlinedButtonColors(
                                                                containerColor = Color(0xFF0285C4),
                                                                contentColor = Color.White
                                                            ),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                                                            modifier = Modifier.height(35.dp),
                                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                                        ) {
                                                            Text("Follow up quote", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuotedStatusBottomSheet(
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Use the quoted leads status list (includes Quote Expired, excludes Drafted)
    val statuses = QuotedLeadsViewModel.quotedLeadStatuses
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.height(30.dp).width(30.dp))
                }
                Text("Status", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            statuses.forEach { (id, name) ->
                val color = Color(NewLeadsViewModel.getLeadStatusColor(id))
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
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
