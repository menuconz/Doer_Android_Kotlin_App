package nz.co.doer.ui.calendar

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import nz.co.doer.data.remote.dto.ShiftDto
import nz.co.doer.data.remote.dto.ShiftSubItemDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    onBack: () -> Unit,
    onViewDetails: (Int) -> Unit = {},
    onViewFiles: (Int) -> Unit = {},
    onViewQuotations: (Int) -> Unit = {},
    onViewMessages: (Int) -> Unit = {},
    onViewSubItemMessages: (Int, Int) -> Unit = { _, _ -> },
    onViewSubItemFiles: (Int, Int) -> Unit = { _, _ -> },
    viewModel: DayDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
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

    // Show active dialog
    when (state.activeDialog) {
        DayDetailDialog.EDITOR -> EditorDialog(
            title = state.editorTitle,
            text = state.editorText,
            onTextChange = viewModel::updateEditorText,
            onSave = { viewModel.saveEditorText(state.editorText) },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.DATE_TIME -> DateTimeDialog(
            date = state.editDate,
            time = state.editTime,
            onSave = { d, t -> viewModel.saveDateTime(d, t) },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.ADDRESS_SEARCH -> AddressSearchDialog(
            searchText = state.addressSearchText,
            suggestions = state.placeSuggestions,
            onSearchChange = viewModel::onAddressSearchChange,
            onPlaceSelected = viewModel::onPlaceSelected,
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.CONTRACT_TYPE -> OptionListDialog(
            title = "Select Contract Type",
            items = state.contractTypes.map { Triple(it.id, it.name, it.color) },
            onSelect = { id ->
                state.contractTypes.find { it.id == id }?.let { viewModel.selectContractType(it) }
            },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.CLIENT_SELECT -> OptionListDialog(
            title = "Select Client",
            items = state.clients.map { Triple(it.id, it.name, 0xFF007AFF) },
            onSelect = { id ->
                state.clients.find { it.id == id }?.let { viewModel.selectClient(it) }
            },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.INVOICE_STATUS -> OptionListDialog(
            title = "Select Invoice Status",
            items = state.invoiceStatuses.map { Triple(it.id, it.name, it.color) },
            onSelect = { id ->
                state.invoiceStatuses.find { it.id == id }?.let { viewModel.selectInvoiceStatus(it) }
            },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.HS_FORM_STATUS -> OptionListDialog(
            title = "Select H&S Form Status",
            items = state.hsFormStatuses.map { Triple(it.id, it.name, it.color) },
            onSelect = { id ->
                state.hsFormStatuses.find { it.id == id }?.let { viewModel.selectHSFormStatus(it) }
            },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.SUB_ITEM_HS -> OptionListDialog(
            title = "Select H&S Required",
            items = state.subItemHsOptions.map { Triple(it.id, it.name, it.color) },
            onSelect = { id ->
                state.subItemHsOptions.find { it.id == id }?.let { viewModel.selectSubItemHS(it) }
            },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.SUB_ITEM_STATUS -> OptionListDialog(
            title = "Select Sub-Item Status",
            items = state.subItemStatusOptions.map { Triple(it.id, it.name, it.color) },
            onSelect = { id ->
                state.subItemStatusOptions.find { it.id == id }?.let { viewModel.selectSubItemStatus(it) }
            },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.SUB_ITEM_DATE -> DateTimeDialog(
            date = state.editDate,
            time = state.editTime,
            onSave = { d, t -> viewModel.saveSubItemDate(d, t) },
            onDismiss = viewModel::dismissDialog
        )
        DayDetailDialog.DELETE_SUB_ITEM -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Delete Sub-Item") },
            text = { Text("Are you sure you want to delete '${state.deleteSubItemName}'?") },
            confirmButton = {
                TextButton(onClick = viewModel::deleteSubItem) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
            }
        )
        DayDetailDialog.NONE -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.pageTitle, style = MaterialTheme.typography.titleSmall) },
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
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F9FA))
                    .padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.selectedDateString,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = state.projectCountText,
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
            }

            // Filter Section
            FilterSection(state = state, viewModel = viewModel)

            Box(modifier = Modifier.weight(1f)) {
            if (state.shiftRows.isEmpty() && !state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No projects for this day", color = Color.Gray, fontSize = 16.sp)
                }
            } else if (state.shiftRows.isNotEmpty()) {
                val hScroll = rememberScrollState()
                val vScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp)
                        .horizontalScroll(hScroll)
                        .verticalScroll(vScroll)
                ) {
                    DataGridHeaderRow(
                        isOwner = state.isOwner,
                        onSort = viewModel::sortBy,
                        getSortIcon = viewModel::getSortIcon
                    )
                    state.shiftRows.forEach { row ->
                        DataGridShiftRow(
                            row = row,
                            isOwner = state.isOwner,
                            isCaregiver = state.isCaregiver,
                            viewModel = viewModel,
                            onViewDetails = { onViewDetails(row.shift.id) },
                            onViewFiles = { onViewFiles(row.shift.id) },
                            onViewQuotations = { onViewQuotations(row.shift.id) },
                            onViewMessages = { onViewMessages(row.shift.id) },
                            onViewSubItemMessages = { subItemId -> onViewSubItemMessages(row.shift.id, subItemId) },
                            onViewSubItemFiles = { subItemId -> onViewSubItemFiles(row.shift.id, subItemId) },
                            onOpenMap = { shift ->
                                val lat = shift.latitude ?: return@DataGridShiftRow
                                val lng = shift.longitude ?: return@DataGridShiftRow
                                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${shift.address})")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        )
                    }
                }
            }
            // Loading overlay on top of content
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            } // Close outer Box
        }
    }
}

// ===== Filter Section =====

@Composable
private fun FilterSection(state: DayDetailUiState, viewModel: DayDetailViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8F9FA))
            .padding(15.dp, 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Advanced Filters", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            Text(state.filterStatusText, fontSize = 13.sp, color = Color(0xFF333333))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (state.pendingFilters.any { it.isComplete }) {
                Button(
                    onClick = viewModel::applyFilters,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(end = 10.dp)
                ) { Text("Apply Filters", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            if (state.activeFilters.isNotEmpty()) {
                TextButton(onClick = viewModel::clearAllFilters) {
                    Text("Clear All", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF3B30))
                }
            }
            Button(
                onClick = viewModel::addFilter,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                shape = RoundedCornerShape(6.dp)
            ) { Text("+ Add Filter", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        state.pendingFilters.forEach { filter ->
            PendingFilterRow(filter = filter, columns = state.filterColumns, viewModel = viewModel)
        }

        if (state.activeFilters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Currently Applied Filters:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF28A745))
            Spacer(modifier = Modifier.height(4.dp))
            state.activeFilters.forEach { filter ->
                ActiveFilterChip(filter = filter, onRemove = { viewModel.removeFilter(filter.id) })
            }
        }

        if (state.pendingFilters.isEmpty() && state.activeFilters.isEmpty()) {
            Text(
                "No filters applied - showing all projects",
                fontSize = 12.sp, color = Color(0xFF9CA3AF),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun PendingFilterRow(filter: FilterRow, columns: List<FilterColumnOption>, viewModel: DayDetailViewModel) {
    var expandedColumn by remember { mutableStateOf(false) }
    var expandedCondition by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(2.dp, Color(0xFFFFA500), RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Where", fontSize = 12.sp, color = Color(0xFF333333))

        Box {
            Text(
                text = filter.selectedColumn?.displayName ?: "Column",
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { expandedColumn = true }
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .padding(8.dp, 4.dp)
                    .width(100.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            androidx.compose.material3.DropdownMenu(
                expanded = expandedColumn,
                onDismissRequest = { expandedColumn = false }
            ) {
                columns.forEach { col ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(col.displayName, fontSize = 13.sp) },
                        onClick = { viewModel.updatePendingFilterColumn(filter.id, col); expandedColumn = false }
                    )
                }
            }
        }

        Box {
            Text(
                text = filter.selectedCondition.ifBlank { "Condition" },
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable { expandedCondition = true }
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .padding(8.dp, 4.dp)
                    .width(90.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            androidx.compose.material3.DropdownMenu(
                expanded = expandedCondition,
                onDismissRequest = { expandedCondition = false }
            ) {
                filter.availableConditions.forEach { cond ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(cond, fontSize = 13.sp) },
                        onClick = { viewModel.updatePendingFilterCondition(filter.id, cond); expandedCondition = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = filter.value,
            onValueChange = { viewModel.updatePendingFilterValue(filter.id, it) },
            placeholder = { Text("Value", fontSize = 12.sp) },
            modifier = Modifier.width(80.dp).height(40.dp),
            textStyle = TextStyle(fontSize = 12.sp),
            singleLine = true
        )

        Text(
            "X", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Red,
            modifier = Modifier.clickable { viewModel.removeFilter(filter.id) }
        )
    }
}

@Composable
private fun ActiveFilterChip(filter: FilterRow, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .background(Color(0xFFD4EDDA), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF28A745), RoundedCornerShape(12.dp))
            .padding(10.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "${filter.selectedColumn?.displayName ?: ""} ${filter.selectedCondition} ${filter.value}",
            fontSize = 12.sp, color = Color(0xFF155724)
        )
        Text(
            "X", fontSize = 9.sp, color = Color(0xFFDC3545), fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onRemove)
        )
    }
}

// ===== Data Grid =====

private object ColW {
    val expand = 40.dp
    val projectName = 180.dp
    val clientName = 180.dp
    val address = 225.dp
    val durationFrom = 180.dp
    val durationTo = 180.dp
    val contractType = 180.dp
    val invoiceStatus = 180.dp
    val hsForm = 180.dp
    val finalMeasure = 180.dp
    val jobDescription = 200.dp
    val quote = 180.dp
    val contractorQuote = 180.dp
    val status = 180.dp
    val files = 100.dp
    val actions = 225.dp
    val subItem = 200.dp
    val subHs = 140.dp
    val subStatus = 120.dp
    val subDateStarted = 140.dp
    val subDateCompleted = 140.dp
    val subFiles = 80.dp
    val subDelete = 80.dp
}

@Composable
private fun DataGridHeaderRow(
    isOwner: Boolean,
    onSort: (String) -> Unit,
    getSortIcon: (String) -> String
) {
    Row(modifier = Modifier.background(Color(0xFFEEEEEE)).height(40.dp)) {
        HeaderCell("", ColW.expand, sortable = false)
        SortableHeaderCell("Project Name", ColW.projectName, "ProjectName", onSort, getSortIcon)
        SortableHeaderCell("Client Name", ColW.clientName, "ClientName", onSort, getSortIcon)
        SortableHeaderCell("Address", ColW.address, "Address", onSort, getSortIcon)
        SortableHeaderCell("Duration From", ColW.durationFrom, "DurationFromString", onSort, getSortIcon)
        SortableHeaderCell("Duration To", ColW.durationTo, "DurationToString", onSort, getSortIcon)
        SortableHeaderCell("Contract Type", ColW.contractType, "ContractType", onSort, getSortIcon)
        SortableHeaderCell("Invoice Status", ColW.invoiceStatus, "InvoiceStatus", onSort, getSortIcon)
        SortableHeaderCell("H&S Status", ColW.hsForm, "HSForm", onSort, getSortIcon)
        SortableHeaderCell("Final Measure", ColW.finalMeasure, "FinalMeasure", onSort, getSortIcon)
        SortableHeaderCell("Job Description", ColW.jobDescription, "Instructions", onSort, getSortIcon)
        if (isOwner) {
            SortableHeaderCell("Quote (Sent to Client)", ColW.quote, "Amount", onSort, getSortIcon)
        }
        SortableHeaderCell("Contractor Quote", ColW.contractorQuote, "AcceptedQuoteAmount", onSort, getSortIcon)
        SortableHeaderCell("Status", ColW.status, "StatusMessage", onSort, getSortIcon)
        HeaderCell("Files", ColW.files, sortable = false)
        HeaderCell("Actions", ColW.actions, sortable = false)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, sortable: Boolean = true) {
    Box(
        modifier = Modifier
            .width(width).height(40.dp)
            .border(0.5.dp, Color.Gray)
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SortableHeaderCell(
    text: String, width: Dp, sortKey: String,
    onSort: (String) -> Unit, getSortIcon: (String) -> String
) {
    Row(
        modifier = Modifier
            .width(width).height(40.dp)
            .border(0.5.dp, Color.Gray)
            .clickable { onSort(sortKey) }
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        val icon = getSortIcon(sortKey)
        if (icon.isNotBlank()) Text(icon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ===== Data Row =====

@Composable
private fun DataGridShiftRow(
    row: ShiftDisplayRow,
    isOwner: Boolean,
    isCaregiver: Boolean,
    viewModel: DayDetailViewModel,
    onViewDetails: () -> Unit,
    onViewFiles: () -> Unit,
    onViewQuotations: () -> Unit,
    onViewMessages: () -> Unit,
    onViewSubItemMessages: (Int) -> Unit,
    onViewSubItemFiles: (Int) -> Unit,
    onOpenMap: (ShiftDto) -> Unit
) {
    Column {
        Row(modifier = Modifier.height(55.dp)) {
            // Expand/Collapse
            Box(
                modifier = Modifier
                    .width(ColW.expand).height(55.dp)
                    .border(0.5.dp, Color.LightGray)
                    .clickable { viewModel.toggleExpand(row.shift.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(if (row.isExpanded) "▼" else "▶", fontSize = 16.sp, color = Color(0xFF666666))
            }

            // Project Name + message icon
            Row(
                modifier = Modifier
                    .width(ColW.projectName).height(55.dp)
                    .border(0.5.dp, Color.LightGray)
                    .clickable { viewModel.editProjectName(row.shift.id) }
                    .padding(8.dp, 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.shift.projectName.ifBlank { "—" }, fontSize = 14.sp, color = Color(0xFF374151),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text("💬", fontSize = 14.sp, modifier = Modifier.clickable { onViewMessages() }.padding(start = 4.dp))
            }

            // Client Name
            Box(
                modifier = Modifier
                    .width(ColW.clientName).height(55.dp)
                    .border(0.5.dp, Color.LightGray)
                    .clickable { viewModel.openClientSelect(row.shift.id) }
                    .padding(8.dp, 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    row.shift.clientName?.ifBlank { "—" } ?: "—", fontSize = 14.sp,
                    color = Color(0xFF374151), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }

            // Address + map icon
            Row(
                modifier = Modifier
                    .width(ColW.address).height(55.dp)
                    .border(0.5.dp, Color.LightGray)
                    .clickable { viewModel.editAddress(row.shift.id) }
                    .padding(8.dp, 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.shift.address.ifBlank { "—" }, fontSize = 14.sp, color = Color(0xFF374151),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                if (row.shift.latitude != null && row.shift.longitude != null) {
                    Text(
                        "📍", fontSize = 16.sp, color = Color(0xFF007AFF),
                        modifier = Modifier.clickable { onOpenMap(row.shift) }.padding(start = 5.dp)
                    )
                }
            }

            DataCellClickable(row.durationFromFormatted, ColW.durationFrom) { viewModel.editDurationFrom(row.shift.id) }
            DataCellClickable(row.durationToFormatted, ColW.durationTo) { viewModel.editDurationTo(row.shift.id) }
            ColoredCellClickable(row.contractTypeText, Color(row.contractTypeColor), ColW.contractType) { viewModel.openContractType(row.shift.id) }
            ColoredCellClickable(row.invoiceStatusText, Color(row.invoiceStatusColor), ColW.invoiceStatus) { viewModel.openInvoiceStatus(row.shift.id) }
            ColoredCellClickable(row.hsFormText, Color(row.hsFormColor), ColW.hsForm) { viewModel.openHSFormStatus(row.shift.id) }
            DataCellClickable(row.shift.finalMeasure.ifBlank { "—" }, ColW.finalMeasure) { viewModel.editFinalMeasure(row.shift.id) }
            DataCellClickable(row.shift.instructions.ifBlank { "—" }, ColW.jobDescription) { viewModel.editJobDescription(row.shift.id) }

            if (isOwner) {
                DataCell(
                    if (row.shift.amount != null) "$${String.format("%.2f", row.shift.amount)}" else "",
                    ColW.quote
                )
            }

            DataCell(
                if (row.shift.acceptedQuoteAmount != null) "$${String.format("%.2f", row.shift.acceptedQuoteAmount)}" else "",
                ColW.contractorQuote
            )

            ColoredCell(row.statusMessage, Color(row.statusColor), ColW.status)

            // Files
            Box(
                modifier = Modifier.width(ColW.files).height(55.dp).border(0.5.dp, Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(onClick = onViewFiles, modifier = Modifier.padding(4.dp)) {
                    Text("🔗", fontSize = 14.sp)
                }
            }

            // Actions
            Box(
                modifier = Modifier.width(ColW.actions).height(55.dp).border(0.5.dp, Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onViewDetails, modifier = Modifier.height(35.dp)) {
                        Text("👁️", fontSize = 14.sp)
                    }
                    if (row.hasQuotations) {
                        OutlinedButton(onClick = onViewQuotations, modifier = Modifier.height(35.dp)) {
                            Text("View Quotations", fontSize = 11.sp, color = Color(0xFF667685), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (row.isExpanded) {
            SubItemsSection(
                row = row,
                isOwner = isOwner,
                viewModel = viewModel,
                onViewSubItemMessages = onViewSubItemMessages,
                onViewSubItemFiles = onViewSubItemFiles
            )
        }
    }
}

// ===== Sub-Items Section =====

@Composable
private fun SubItemsSection(
    row: ShiftDisplayRow,
    isOwner: Boolean,
    viewModel: DayDetailViewModel,
    onViewSubItemMessages: (Int) -> Unit,
    onViewSubItemFiles: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(start = 40.dp)) {
        Row(modifier = Modifier.background(Color(0xFFEEEEEE)).height(45.dp)) {
            SubHeaderCell("🔧 Sub Item", ColW.subItem)
            SubHeaderCell("🛡️ H&S Status", ColW.subHs)
            SubHeaderCell("📊 Status", ColW.subStatus)
            SubHeaderCell("🚀 Date Started", ColW.subDateStarted)
            SubHeaderCell("✅ Completed", ColW.subDateCompleted)
            SubHeaderCell("📁 Files", ColW.subFiles)
            if (isOwner) SubHeaderCell("🗑️ Delete", ColW.subDelete)
        }

        row.subItems.forEach { subItem ->
            SubItemRow(
                subItem = subItem,
                shiftId = row.shift.id,
                isOwner = isOwner,
                viewModel = viewModel,
                onViewMessages = { onViewSubItemMessages(subItem.id) },
                onViewFiles = { onViewSubItemFiles(subItem.id) }
            )
        }

        if (isOwner) {
            AddSubItemRow(shiftId = row.shift.id, newName = row.newSubItemName, viewModel = viewModel)
        }
    }
}

@Composable
private fun SubHeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width).height(45.dp)
            .border(0.5.dp, Color.Gray)
            .padding(10.dp, 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SubItemRow(
    subItem: ShiftSubItemDto,
    shiftId: Int,
    isOwner: Boolean,
    viewModel: DayDetailViewModel,
    onViewMessages: () -> Unit,
    onViewFiles: () -> Unit
) {
    val hsColor = DayDetailViewModel.getSubItemHSColor(subItem.hsRequired)
    val hsText = DayDetailViewModel.getSubItemHSText(subItem.hsRequired)
    val statusColor = DayDetailViewModel.getSubItemStatusColor(subItem.status)
    val statusText = DayDetailViewModel.getSubItemStatusText(subItem.status)

    Row(modifier = Modifier.height(65.dp)) {
        Row(
            modifier = Modifier
                .width(ColW.subItem).height(65.dp)
                .border(0.5.dp, Color.LightGray)
                .padding(15.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                subItem.subitem, fontSize = 15.sp, color = Color(0xFF374151),
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Text("💬", fontSize = 14.sp, modifier = Modifier.clickable { onViewMessages() }.padding(start = 4.dp))
        }

        Box(
            modifier = Modifier
                .width(ColW.subHs).height(65.dp)
                .border(0.5.dp, Color.LightGray)
                .background(Color(hsColor))
                .clickable { viewModel.openSubItemHS(shiftId, subItem.id) }
                .padding(10.dp, 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(hsText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Box(
            modifier = Modifier
                .width(ColW.subStatus).height(65.dp)
                .border(0.5.dp, Color.LightGray)
                .background(Color(statusColor))
                .clickable { viewModel.openSubItemStatus(shiftId, subItem.id) }
                .padding(10.dp, 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Box(
            modifier = Modifier
                .width(ColW.subDateStarted).height(65.dp)
                .border(0.5.dp, Color(0xFFE5E7EB))
                .clickable { viewModel.openSubItemDateStarted(shiftId, subItem.id) }
                .padding(10.dp, 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                subItem.dateStartedString.ifBlank { "Not Started" },
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151)
            )
        }

        Box(
            modifier = Modifier
                .width(ColW.subDateCompleted).height(65.dp)
                .border(0.5.dp, Color(0xFFE5E7EB))
                .padding(10.dp, 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                subItem.dateCompletedString.ifBlank { "Not Completed" },
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151)
            )
        }

        Box(
            modifier = Modifier
                .width(ColW.subFiles).height(65.dp)
                .border(0.5.dp, Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center
        ) {
            OutlinedButton(onClick = onViewFiles) { Text("🔗", fontSize = 10.sp) }
        }

        if (isOwner) {
            Box(
                modifier = Modifier
                    .width(ColW.subDelete).height(65.dp)
                    .border(0.5.dp, Color(0xFFE5E7EB)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🗑️", fontSize = 16.sp,
                    modifier = Modifier.clickable { viewModel.confirmDeleteSubItem(shiftId, subItem.id) }
                )
            }
        }
    }
}

@Composable
private fun AddSubItemRow(shiftId: Int, newName: String, viewModel: DayDetailViewModel) {
    Row(modifier = Modifier.height(50.dp)) {
        Row(
            modifier = Modifier
                .width(ColW.subItem).height(50.dp)
                .border(2.dp, Color(0xFF28A745))
                .background(Color(0xFFF0F8FF))
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { viewModel.updateNewSubItemName(shiftId, it) },
                placeholder = { Text("Enter sub item name...", fontSize = 13.sp) },
                modifier = Modifier.weight(1f).height(45.dp),
                textStyle = TextStyle(fontSize = 13.sp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(5.dp))
            Button(
                onClick = { viewModel.addSubItem(shiftId) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(35.dp)
            ) {
                Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        listOf(ColW.subHs, ColW.subStatus, ColW.subDateStarted, ColW.subDateCompleted, ColW.subFiles, ColW.subDelete).forEach { w ->
            Box(
                modifier = Modifier
                    .width(w).height(50.dp)
                    .border(2.dp, Color(0xFF28A745))
                    .background(Color(0xFFF0F8FF))
            )
        }
    }
}

// ===== Reusable Cells =====

@Composable
private fun DataCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width).height(55.dp)
            .border(0.5.dp, Color.LightGray)
            .padding(8.dp, 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 14.sp, color = Color(0xFF374151), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DataCellClickable(text: String, width: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width).height(55.dp)
            .border(0.5.dp, Color.LightGray)
            .clickable(onClick = onClick)
            .padding(8.dp, 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 14.sp, color = Color(0xFF374151), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ColoredCell(text: String, bgColor: Color, width: Dp) {
    Box(
        modifier = Modifier
            .width(width).height(55.dp)
            .border(0.5.dp, Color.LightGray)
            .background(bgColor)
            .padding(8.dp, 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColoredCellClickable(text: String, bgColor: Color, width: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width).height(55.dp)
            .border(0.5.dp, Color.LightGray)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(8.dp, 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ===== Dialogs =====

@Composable
private fun EditorDialog(
    title: String, text: String,
    onTextChange: (String) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(), maxLines = 5
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save", color = Color(0xFF007AFF)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialog(
    date: LocalDate, time: LocalTime,
    onSave: (LocalDate, LocalTime) -> Unit, onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(date) }
    var selectedTime by remember { mutableStateOf(time) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour, initialMinute = selectedTime.minute
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
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
                    value = searchText, onValueChange = onSearchChange,
                    placeholder = { Text("Type to search...") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    suggestions.forEach { prediction ->
                        Text(
                            prediction.description, fontSize = 14.sp, color = Color(0xFF333333),
                            modifier = Modifier.fillMaxWidth().clickable { onPlaceSelected(prediction) }.padding(8.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OptionListDialog(
    title: String,
    items: List<Triple<Int, String, Long>>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { (id, name, color) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(20.dp).height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(color))
                        )
                        Text(name, fontSize = 15.sp, color = Color(0xFF333333))
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}