package nz.co.doer.ui.mainleads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import nz.co.doer.data.remote.dto.ShiftSubItemDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ──────────── Colors matching MAUI ────────────
private val BgColor = Color(0xFFF8F9FA)
private val HeaderBg = Color(0xFFEEEEEE)
private val BorderColor = Color.LightGray
private val TextPrimary = Color(0xFF374151)
private val BluePrimary = Color(0xFF007AFF)
private val GreenPrimary = Color(0xFF28A745)
private val RedPrimary = Color(0xFFFF3B30)
private val AddSubItemBg = Color(0xFFF0F8FF)

// Column widths matching MAUI Grid definitions
private val ColExpand = 40.dp
private val ColProjectName = 180.dp
private val ColClientName = 180.dp
private val ColAddress = 225.dp
private val ColDurationFrom = 180.dp
private val ColDurationTo = 180.dp
private val ColContractType = 180.dp
private val ColInvoiceStatus = 180.dp
private val ColHSForm = 180.dp
private val ColFinalMeasure = 180.dp
private val ColInstructions = 200.dp
private val ColQuote = 180.dp
private val ColContractorQuote = 180.dp
private val ColStatus = 180.dp
private val ColFiles = 100.dp
private val ColActions = 225.dp

// Sub-item column widths
private val SubColName = 200.dp
private val SubColHS = 140.dp
private val SubColStatus = 120.dp
private val SubColStarted = 140.dp
private val SubColCompleted = 140.dp
private val SubColFiles = 80.dp
private val SubColDelete = 80.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLeadsJobsScreen(
    onOpenDrawer: () -> Unit,
    onShiftDetails: (shiftId: Int) -> Unit,
    onViewQuotations: (shiftId: Int) -> Unit,
    onViewMessages: (shiftId: Int) -> Unit = {},
    onViewFiles: (shiftId: Int) -> Unit = {},
    onViewSubItemMessages: (shiftId: Int, subItemId: Int) -> Unit = { _, _ -> },
    onViewSubItemFiles: (shiftId: Int, subItemId: Int) -> Unit = { _, _ -> },
    viewModel: MainLeadsJobsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val horizontalScrollState = rememberScrollState()
    val listState = rememberLazyListState()

    // Detect when user scrolls near the bottom to load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && totalItems > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore }
            .collect { if (it) viewModel.loadMore() }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() }
    }

    // ── Refresh on resume so status changes made in ShiftDetails are reflected immediately ──
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Edit Dialogs
    EditDialogs(state = state, viewModel = viewModel)

    // Delete confirmation dialog
    var deleteSubItemId by remember { mutableStateOf<Int?>(null) }
    deleteSubItemId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteSubItemId = null },
            title = { Text("Delete Sub-Item") },
            text = { Text("Are you sure you want to delete this sub-item?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSubItem(id); deleteSubItemId = null }) {
                    Text("Delete", color = RedPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSubItemId = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NZ MAHI ${state.currentYear}", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
        ) {
            // ──── Month Navigation ────
            MonthNavigation(state, viewModel)

            // ──── Filter Section ────
            FilterSection(state, viewModel)

            // ──── View Mode Picker ────
            ViewModePicker(state, viewModel)

            // ──── Content ────
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.jobs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No jobs found for this month", color = Color.Gray, fontSize = 16.sp)
                }
            } else if (state.isListView) {
                // ──── List / Table View ────
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    Column {
                        // Header Row
                        TableHeader(state, viewModel)

                        // Data Rows
                        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                            items(state.jobs, key = { it.shift.id }) { row ->
                                TableDataRow(
                                    row = row,
                                    state = state,
                                    viewModel = viewModel,
                                    onShiftDetails = onShiftDetails,
                                    onViewQuotations = onViewQuotations,
                                    onViewMessages = onViewMessages,
                                    onViewFiles = onViewFiles,
                                    onViewSubItemMessages = onViewSubItemMessages,
                                    onViewSubItemFiles = onViewSubItemFiles,
                                    onDeleteSubItem = { deleteSubItemId = it }
                                )
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ──── Kanban View ────
                KanbanView(state, onShiftDetails, onViewMessages)
            }
        }
    }
}

// ──────────── Month Navigation ────────────

@Composable
private fun MonthNavigation(state: MainLeadsJobsUiState, viewModel: MainLeadsJobsViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { viewModel.previousMonth() }) {
            Text("‹", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
        }
        Text(state.monthDisplayText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
        TextButton(onClick = { viewModel.nextMonth() }) {
            Text("›", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
        }
    }
}

// ──────────── Filter Section ────────────

@Composable
private fun FilterSection(state: MainLeadsJobsUiState, viewModel: MainLeadsJobsViewModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 5.dp),
        color = BgColor
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Advanced Filters", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                }
                Text(state.filterStatusText, fontSize = 13.sp, color = Color(0xFF666666))
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (state.hasFiltersToApply) {
                    Button(
                        onClick = { viewModel.applyFilters() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(12.dp, 6.dp)
                    ) { Text("Apply Filters", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                }
                if (state.hasActiveFilters) {
                    TextButton(onClick = { viewModel.clearAllFilters() }) {
                        Text("Clear All", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RedPrimary)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = { viewModel.addFilter() },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(12.dp, 6.dp)
                ) { Text("+ Add Filter", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            // Pending Filters
            if (state.hasPendingFilters) {
                Spacer(Modifier.height(8.dp))
                state.pendingFilters.forEach { filter ->
                    PendingFilterRow(filter, state, viewModel)
                }
                Text(
                    "💡 Configure filters above, then click 'Apply Filters'",
                    fontSize = 11.sp, color = Color(0xFF6C757D),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Active Filters
            if (state.hasActiveFilters) {
                Spacer(Modifier.height(8.dp))
                Text("✅ Currently Applied Filters:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
                Spacer(Modifier.height(4.dp))
                state.activeFilters.forEach { filter ->
                    ActiveFilterChip(filter, viewModel)
                }
            }

            if (state.showEmptyState) {
                Text(
                    "📋 No filters applied - showing all projects",
                    fontSize = 12.sp, color = Color(0xFF9CA3AF),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PendingFilterRow(filter: FilterRow, state: MainLeadsJobsUiState, viewModel: MainLeadsJobsViewModel) {
    val filterableColumns = state.filterColumns.filter { it.propertyName.isNotBlank() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFA500))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Where", fontSize = 12.sp, color = Color(0xFF333333))

                // Column dropdown
                DropdownSelector(
                    selected = filter.selectedColumn?.displayName ?: "Column",
                    items = filterableColumns.map { it.displayName },
                    onSelect = { name ->
                        filterableColumns.find { it.displayName == name }?.let {
                            viewModel.updatePendingFilterColumn(filter.id, it)
                        }
                    },
                    width = 120.dp
                )

                // Condition dropdown
                val conditions = filter.selectedColumn?.availableConditions ?: emptyList()
                DropdownSelector(
                    selected = filter.selectedCondition.ifBlank { "Condition" },
                    items = conditions,
                    onSelect = { viewModel.updatePendingFilterCondition(filter.id, it) },
                    width = 100.dp
                )

                // Value entry
                OutlinedTextField(
                    value = filter.value,
                    onValueChange = { viewModel.updatePendingFilterValue(filter.id, it) },
                    placeholder = { Text("Value", fontSize = 12.sp) },
                    modifier = Modifier.width(80.dp).height(40.dp),
                    textStyle = TextStyle(fontSize = 12.sp),
                    singleLine = true
                )

                // Remove button
                IconButton(onClick = { viewModel.removePendingFilter(filter.id) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("⚠️ Pending - Click Apply to activate", fontSize = 10.sp, color = Color(0xFF856404),
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ActiveFilterChip(filter: FilterRow, viewModel: MainLeadsJobsViewModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFD4EDDA),
        border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary)
    ) {
        Row(
            modifier = Modifier.padding(10.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${filter.selectedColumn?.displayName ?: ""} ${filter.selectedCondition} ${filter.value}",
                fontSize = 12.sp, color = Color(0xFF155724), modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.removePendingFilter(filter.id) }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFDC3545), modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun DropdownSelector(selected: String, items: List<String>, onSelect: (String) -> Unit, width: Dp) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.width(width).height(40.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            Text(selected, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, fontSize = 12.sp) },
                    onClick = { onSelect(item); expanded = false }
                )
            }
        }
    }
}

// ──────────── View Mode Picker ────────────

@Composable
private fun ViewModePicker(state: MainLeadsJobsUiState, viewModel: MainLeadsJobsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Text(
                text = state.selectedViewMode,
                fontSize = 14.sp, color = Color(0xFF111827),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(8.dp)
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("Main Table", "Kanban").forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode) },
                        onClick = { viewModel.setViewMode(mode); expanded = false }
                    )
                }
            }
        }
    }
}

// ──────────── Table Header ────────────

@Composable
private fun TableHeader(state: MainLeadsJobsUiState, viewModel: MainLeadsJobsViewModel) {
    Row(modifier = Modifier.background(HeaderBg)) {
        // Expand column (no sort)
        HeaderCell("", ColExpand, sortable = false)
        SortableHeader("Project Name", ColProjectName, "ProjectName", state, viewModel::sortBy)
        SortableHeader("Client Name", ColClientName, "ClientName", state, viewModel::sortBy)
        SortableHeader("Address", ColAddress, "Address", state, viewModel::sortBy)
        SortableHeader("Duration From", ColDurationFrom, "DurationFromString", state, viewModel::sortBy)
        SortableHeader("Duration To", ColDurationTo, "DurationToString", state, viewModel::sortBy)
        SortableHeader("Contract Type", ColContractType, "ContractType", state, viewModel::sortBy)
        SortableHeader("Invoice Status", ColInvoiceStatus, "InvoiceStatus", state, viewModel::sortBy)
        SortableHeader("H&S Status", ColHSForm, "HSForm", state, viewModel::sortBy)
        SortableHeader("Final Measure", ColFinalMeasure, "FinalMeasure", state, viewModel::sortBy)
        SortableHeader("Job Description", ColInstructions, "Instructions", state, viewModel::sortBy)
        if (state.isOwner) {
            SortableHeader("Quote (Sent to Client)", ColQuote, "Amount", state, viewModel::sortBy)
        }
        SortableHeader("Contractor Quote", ColContractorQuote, "AcceptedQuoteAmount", state, viewModel::sortBy)
        SortableHeader("Status", ColStatus, "StatusMessage", state, viewModel::sortBy)
        HeaderCell("Files", ColFiles, sortable = false)
        HeaderCell("Actions", ColActions, sortable = false)
    }
}

@Composable
private fun HeaderCell(title: String, width: Dp, sortable: Boolean) {
    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .border(0.5.dp, Color.Gray)
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
private fun SortableHeader(
    title: String, width: Dp, column: String,
    state: MainLeadsJobsUiState, onSort: (String) -> Unit
) {
    val sortIcon = if (state.sortColumn == column) {
        if (state.sortAscending) " ▲" else " ▼"
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
        Text(
            text = "$title$sortIcon",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ──────────── Table Data Row ────────────

@Composable
private fun TableDataRow(
    row: JobRowItem,
    state: MainLeadsJobsUiState,
    viewModel: MainLeadsJobsViewModel,
    onShiftDetails: (Int) -> Unit,
    onViewQuotations: (Int) -> Unit,
    onViewMessages: (Int) -> Unit,
    onViewFiles: (Int) -> Unit,
    onViewSubItemMessages: (Int, Int) -> Unit,
    onViewSubItemFiles: (Int, Int) -> Unit,
    onDeleteSubItem: (Int) -> Unit
) {
    Column {
        Row(modifier = Modifier.height(55.dp)) {
            // Expand/Collapse
            Box(
                modifier = Modifier
                    .width(ColExpand)
                    .height(55.dp)
                    .border(0.5.dp, BorderColor)
                    .clickable { viewModel.toggleExpanded(row.shift.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (row.isExpanded) "▼" else "▶",
                    fontSize = 16.sp, color = Color(0xFF666666)
                )
            }

            // Project Name + Message icon
            Box(
                modifier = Modifier
                    .width(ColProjectName)
                    .height(55.dp)
                    .border(0.5.dp, BorderColor)
                    .clickable { viewModel.editProjectName(row.shift.id) }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        row.shift.projectName, fontSize = 14.sp, color = TextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 5.dp)
                    )
                    Text(
                        "💬", fontSize = 16.sp,
                        modifier = Modifier
                            .clickable { onViewMessages(row.shift.id) }
                            .padding(horizontal = 4.dp)
                    )
                }
            }

            // Client Name
            CellLabel(row.shift.clientName ?: "", ColClientName) { viewModel.editClient(row.shift.id) }

            // Address
            CellLabel(row.shift.address, ColAddress) { viewModel.editAddress(row.shift.id) }

            // Duration From
            CellLabel(
                row.shift.durationFromString.ifBlank { viewModel.formatTimestamp(row.shift.durationFrom) },
                ColDurationFrom
            ) { viewModel.editDurationFrom(row.shift.id) }

            // Duration To
            CellLabel(
                row.shift.durationToString.ifBlank { viewModel.formatTimestamp(row.shift.durationTo) },
                ColDurationTo
            ) { viewModel.editDurationTo(row.shift.id) }

            // Contract Type (colored background)
            ColoredBgCell(row.contractTypeDisplayText, Color(row.contractTypeColor), ColContractType) {
                viewModel.editContractType(row.shift.id)
            }

            // Invoice Status (colored background)
            ColoredBgCell(row.invoiceDisplayText, Color(row.invoiceColor), ColInvoiceStatus) {
                viewModel.editInvoiceStatus(row.shift.id)
            }

            // H&S Form (colored background)
            ColoredBgCell(row.hsFormText, Color(row.hsFormColor), ColHSForm) {
                viewModel.editHSFormStatus(row.shift.id)
            }

            // Final Measure
            CellLabel(row.shift.finalMeasure, ColFinalMeasure) { viewModel.editFinalMeasure(row.shift.id) }

            // Job Description
            CellLabel(row.shift.instructions, ColInstructions) { viewModel.editJobDescription(row.shift.id) }

            // Quote (owner only)
            if (state.isOwner) {
                CellLabel(viewModel.formatAmount(row.shift.amount), ColQuote)
            }

            // Contractor Quote
            CellLabel(viewModel.formatAmount(row.shift.acceptedQuoteAmount), ColContractorQuote)

            // Status (colored background)
            ColoredBgCell(row.statusDisplayText, Color(row.statusColor), ColStatus)

            // Files
            Box(
                modifier = Modifier
                    .width(ColFiles)
                    .height(55.dp)
                    .border(0.5.dp, BorderColor),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = { onViewFiles(row.shift.id) },
                    modifier = Modifier.height(35.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("🔗", fontSize = 14.sp) }
            }

            // Actions
            Box(
                modifier = Modifier
                    .width(ColActions)
                    .height(55.dp)
                    .border(0.5.dp, BorderColor),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onShiftDetails(row.shift.id) },
                        modifier = Modifier.height(35.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("👁️", fontSize = 14.sp) }

                    if (row.shift.hasQuotations) {
                        OutlinedButton(
                            onClick = { onViewQuotations(row.shift.id) },
                            modifier = Modifier.height(35.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("View Quotations", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF667685)) }
                    }
                }
            }
        }

        // ──── Expanded Sub-Items ────
        AnimatedVisibility(visible = row.isExpanded) {
            SubItemsSection(
                row = row,
                state = state,
                viewModel = viewModel,
                onViewSubItemMessages = onViewSubItemMessages,
                onViewSubItemFiles = onViewSubItemFiles,
                onDeleteSubItem = onDeleteSubItem
            )
        }
    }
}

@Composable
private fun CellLabel(text: String, width: Dp, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .width(width)
            .height(55.dp)
            .border(0.5.dp, BorderColor)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(8.dp, 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 14.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ColoredBgCell(text: String, color: Color, width: Dp, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .width(width)
            .height(55.dp)
            .border(0.5.dp, BorderColor)
            .background(color)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(8.dp, 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ──────────── Sub-Items Section ────────────

@Composable
private fun SubItemsSection(
    row: JobRowItem,
    state: MainLeadsJobsUiState,
    viewModel: MainLeadsJobsViewModel,
    onViewSubItemMessages: (Int, Int) -> Unit,
    onViewSubItemFiles: (Int, Int) -> Unit,
    onDeleteSubItem: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = 40.dp)
            .background(Color(0xFFF9FAFB))
    ) {
        // Sub-items Header
        Row(modifier = Modifier.padding(top = 15.dp)) {
            SubItemHeaderCell("🔧 Sub Item", SubColName, isFirst = true)
            SubItemHeaderCell("🛡️ H&S Status", SubColHS)
            SubItemHeaderCell("📊 Status", SubColStatus)
            SubItemHeaderCell("🚀 Date Started", SubColStarted)
            SubItemHeaderCell("✅ Completed", SubColCompleted)
            SubItemHeaderCell("📁 Files", SubColFiles)
            if (state.isOwner) {
                SubItemHeaderCell("🗑️ Delete", SubColDelete, isLast = true)
            }
        }

        // Sub-item rows
        if (row.subItems.isEmpty()) {
            Text("No sub-items", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
        } else {
            row.subItems.forEach { subItem ->
                SubItemDataRow(
                    subItem = subItem,
                    shiftId = row.shift.id,
                    isOwner = state.isOwner,
                    viewModel = viewModel,
                    onViewSubItemMessages = onViewSubItemMessages,
                    onViewSubItemFiles = onViewSubItemFiles,
                    onDeleteSubItem = onDeleteSubItem
                )
            }
        }

        // Add Sub Item Row
        if (row.isAddSubItem) {
            AddSubItemRow(row, viewModel)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SubItemHeaderCell(text: String, width: Dp, isFirst: Boolean = false, isLast: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .height(45.dp)
            .background(HeaderBg)
            .border(0.5.dp, Color.Gray)
            .padding(10.dp, 0.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
private fun SubItemDataRow(
    subItem: ShiftSubItemDto,
    shiftId: Int,
    isOwner: Boolean,
    viewModel: MainLeadsJobsViewModel,
    onViewSubItemMessages: (Int, Int) -> Unit,
    onViewSubItemFiles: (Int, Int) -> Unit,
    onDeleteSubItem: (Int) -> Unit
) {
    Row(modifier = Modifier.padding(vertical = 0.dp)) {
        // Sub-item name + message icon
        Box(
            modifier = Modifier
                .width(SubColName)
                .height(65.dp)
                .border(0.5.dp, Color(0xFFE5E7EB))
                .padding(15.dp, 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subItem.subitem, fontSize = 15.sp, color = TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("💬", fontSize = 16.sp, modifier = Modifier
                    .clickable { onViewSubItemMessages(shiftId, subItem.id) }
                    .padding(start = 4.dp))
            }
        }

        // H&S Required (colored) - compute text/color locally since API doesn't populate these fields
        val hsText = MainLeadsJobsViewModel.getHSFormText(subItem.hsRequired)
        val hsColor = Color(MainLeadsJobsViewModel.getHSFormColor(subItem.hsRequired))
        Box(
            modifier = Modifier
                .width(SubColHS)
                .height(65.dp)
                .background(hsColor)
                .border(0.5.dp, Color(0xFFE5E7EB))
                .clickable { viewModel.editSubItemHSStatus(subItem.id) }
                .padding(10.dp, 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(hsText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Status (colored) - compute text/color locally since API doesn't populate these fields
        val statusText = MainLeadsJobsViewModel.getSubItemStatusText(subItem.status)
        val statusColor = Color(MainLeadsJobsViewModel.getSubItemStatusColor(subItem.status))
        Box(
            modifier = Modifier
                .width(SubColStatus)
                .height(65.dp)
                .background(statusColor)
                .border(0.5.dp, Color(0xFFE5E7EB))
                .clickable { viewModel.editSubItemStatus(subItem.id) }
                .padding(10.dp, 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // Date Started
        SubItemTextCell(subItem.dateStartedString.ifBlank { "Not Started" }, SubColStarted) {
            viewModel.editSubItemDateStarted(shiftId, subItem.id)
        }

        // Date Completed
        SubItemTextCell(subItem.dateCompletedString.ifBlank { "Not Completed" }, SubColCompleted)

        // Files button
        Box(
            modifier = Modifier
                .width(SubColFiles)
                .height(65.dp)
                .background(Color.White)
                .border(0.5.dp, Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center
        ) {
            OutlinedButton(
                onClick = { onViewSubItemFiles(shiftId, subItem.id) },
                modifier = Modifier.size(35.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("🔗", fontSize = 10.sp) }
        }

        // Delete button (owner only)
        if (isOwner) {
            Box(
                modifier = Modifier
                    .width(SubColDelete)
                    .height(65.dp)
                    .background(Color.White)
                    .border(0.5.dp, Color(0xFFE5E7EB)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { onDeleteSubItem(subItem.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(25.dp))
                }
            }
        }
    }
}

@Composable
private fun SubItemTextCell(text: String, width: Dp, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .width(width)
            .height(65.dp)
            .background(Color.White)
            .border(0.5.dp, Color(0xFFE5E7EB))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(10.dp, 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun AddSubItemRow(row: JobRowItem, viewModel: MainLeadsJobsViewModel) {
    Row(modifier = Modifier.padding(bottom = 5.dp)) {
        // Name input + Add button
        Box(
            modifier = Modifier
                .width(SubColName)
                .height(50.dp)
                .background(AddSubItemBg)
                .border(2.dp, GreenPrimary),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = row.newSubItemName,
                    onValueChange = { viewModel.updateNewSubItemName(row.shift.id, it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                    decorationBox = { inner ->
                        if (row.newSubItemName.isEmpty()) {
                            Text("Enter sub item name...", fontSize = 13.sp, color = Color.Gray)
                        }
                        inner()
                    }
                )
                Button(
                    onClick = { viewModel.addNewSubItem(row.shift.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(8.dp, 4.dp),
                    modifier = Modifier.height(30.dp)
                ) { Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // Empty placeholder cells
        listOf(SubColHS, SubColStatus, SubColStarted, SubColCompleted, SubColFiles).forEach { w ->
            Box(
                modifier = Modifier
                    .width(w)
                    .height(50.dp)
                    .background(AddSubItemBg)
                    .border(2.dp, GreenPrimary)
            )
        }
        if (row.isOwner) {
            Box(
                modifier = Modifier
                    .width(SubColDelete)
                    .height(50.dp)
                    .background(AddSubItemBg)
                    .border(2.dp, GreenPrimary)
            )
        }
    }
}

// ──────────── Kanban View ────────────

@Composable
private fun KanbanView(
    state: MainLeadsJobsUiState,
    onShiftDetails: (Int) -> Unit,
    onViewMessages: (Int) -> Unit
) {
    if (state.kanbanColumns.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data for Kanban view", color = Color.Gray)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { state.kanbanColumns.size })
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val column = state.kanbanColumns[page]
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            // Column Header
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(column.headerColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    column.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Cards
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(column.items) { job ->
                    KanbanCard(job, onShiftDetails, onViewMessages)
                }
            }
        }
    }
}

@Composable
private fun KanbanCard(job: JobRowItem, onShiftDetails: (Int) -> Unit, onViewMessages: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onShiftDetails(job.shift.id) },
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Title + Message
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.shift.projectName, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827), modifier = Modifier.weight(1f)
                )
                Text("💬", fontSize = 16.sp, modifier = Modifier.clickable { onViewMessages(job.shift.id) })
            }

            // Duration From
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅 Duration From", fontSize = 14.sp, color = Color(0xFF111827))
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF3F4F6),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Text(
                        job.shift.durationFromString.ifBlank { job.shift.durationFrom },
                        fontSize = 14.sp, color = Color(0xFF6B7280),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Sub Items count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sub Items", fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Text(
                        "${job.subItems.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = TextPrimary, modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

// ──────────── Edit Dialogs ────────────

@Composable
private fun EditDialogs(state: MainLeadsJobsUiState, viewModel: MainLeadsJobsViewModel) {
    val dialog = state.editDialog
    when (dialog.type) {
        EditDialogType.TextEditor -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissEditDialog() },
                title = { Text(dialog.title) },
                text = {
                    OutlinedTextField(
                        value = dialog.textValue,
                        onValueChange = { viewModel.updateEditDialogText(it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.saveTextEdit() }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEditDialog() }) { Text("Cancel") }
                }
            )
        }
        EditDialogType.DateTimePicker -> {
            DateTimePickerDialog(
                initialDate = dialog.selectedDate,
                initialTime = LocalTime.of(dialog.selectedHour, dialog.selectedMinute),
                onSave = { date, time ->
                    viewModel.updateEditDialogDate(date)
                    viewModel.updateEditDialogTime(time.hour, time.minute)
                    viewModel.saveDateTimeEdit()
                },
                onDismiss = { viewModel.dismissEditDialog() }
            )
        }
        EditDialogType.ContractTypePicker -> {
            StatusPickerDialog("Contract Type", MainLeadsJobsViewModel.contractTypeOptions, viewModel::selectContractType, viewModel::dismissEditDialog)
        }
        EditDialogType.InvoiceStatusPicker -> {
            StatusPickerDialog("Invoice Status", MainLeadsJobsViewModel.invoiceStatusOptions, viewModel::selectInvoiceStatus, viewModel::dismissEditDialog)
        }
        EditDialogType.HSFormStatusPicker -> {
            StatusPickerDialog("H&S Form Status", MainLeadsJobsViewModel.hsFormOptions, viewModel::selectHSFormStatus, viewModel::dismissEditDialog)
        }
        EditDialogType.SubItemHSPicker -> {
            StatusPickerDialog("H&S Status", MainLeadsJobsViewModel.hsFormOptions, viewModel::selectSubItemHSStatus, viewModel::dismissEditDialog)
        }
        EditDialogType.SubItemStatusPicker -> {
            StatusPickerDialog("Sub-Item Status", MainLeadsJobsViewModel.subItemStatusOptions, viewModel::selectSubItemStatus, viewModel::dismissEditDialog)
        }
        EditDialogType.AddressSearch -> {
            AddressSearchDialog(
                searchText = state.addressSearchText,
                suggestions = state.placeSuggestions,
                onSearchChange = viewModel::onAddressSearchChange,
                onPlaceSelected = viewModel::onPlaceSelected,
                onDismiss = viewModel::dismissEditDialog
            )
        }
        EditDialogType.ClientPicker -> {
            ClientPickerDialog(
                clients = state.clients,
                onSelect = { id, name -> viewModel.selectClient(id, name) },
                onDismiss = viewModel::dismissEditDialog
            )
        }
        EditDialogType.SubItemDatePicker -> {
            DateTimePickerDialog(
                initialDate = dialog.selectedDate,
                initialTime = LocalTime.of(dialog.selectedHour, dialog.selectedMinute),
                onSave = { date, time -> viewModel.saveSubItemDate(date, time) },
                onDismiss = { viewModel.dismissEditDialog() }
            )
        }
        else -> {}
    }
}

@Composable
private fun StatusPickerDialog(
    title: String,
    options: List<StatusOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.value) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(option.color)
                    ) {
                        Text(
                            option.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(12.dp, 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    initialDate: LocalDate,
    initialTime: LocalTime,
    onSave: (LocalDate, LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedTime by remember { mutableStateOf(initialTime) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    onSave(selectedDate, selectedTime)
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddressSearchDialog(
    searchText: String,
    suggestions: List<nz.co.doer.data.remote.PlacePrediction>,
    onSearchChange: (String) -> Unit,
    onPlaceSelected: (nz.co.doer.data.remote.PlacePrediction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Address") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Type to search...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    suggestions.forEach { prediction ->
                        Text(
                            prediction.description,
                            fontSize = 14.sp,
                            color = Color(0xFF333333),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaceSelected(prediction) }
                                .padding(8.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ClientPickerDialog(
    clients: List<nz.co.doer.data.remote.dto.ClientDto>,
    onSelect: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Client") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                clients.forEach { client ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(client.id, client.name) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF007AFF))
                        )
                        Text(client.name, fontSize = 15.sp, color = Color(0xFF333333))
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}