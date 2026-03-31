package nz.co.doer.ui.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.location.LocationServices
import nz.co.doer.data.remote.dto.ClockLocationType
import nz.co.doer.data.remote.dto.DoerTrackingState
import nz.co.doer.ui.tracking.TurnByTurnNavigationActivity

private val Blue = Color(0xFF007AFF)
private val Gray500 = Color(0xFF6B7280)
private val BgColor = Color(0xFFF8F9FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDetailsScreen(
    onBack: () -> Unit,
    onEdit: (Int) -> Unit = {},
    onSendQuote: (Int) -> Unit = {},
    onViewQuotations: (Int) -> Unit = {},
    onSendFeedback: (Int) -> Unit = {},
    onViewReviews: (Int) -> Unit = {},
    onNavigateToSite: (shiftId: Int, lat: Double, lng: Double, address: String, projectName: String) -> Unit = { _, _, _, _, _ -> },
    viewModel: ShiftDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    LaunchedEffect(state.navigateToFeedbackShiftId) {
        state.navigateToFeedbackShiftId?.let { shiftId ->
            viewModel.onFeedbackNavigated()
            onSendFeedback(shiftId)
        }
    }

    LaunchedEffect(state.navigateToReviewsShiftId) {
        state.navigateToReviewsShiftId?.let { shiftId ->
            viewModel.onReviewsNavigated()
            onViewReviews(shiftId)
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
            // Navigate back for status change actions
            if (it == "Shift started" || it == "Shift ended" || it == "Shift marked as not completed" || it == "Shift updated") {
                onBack()
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Job") },
            text = { Text("Do you really want to delete this job?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteShift()
                }) { Text("Yes", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("No") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Details", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isDeleteButton) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE3F2FD)),
                                contentAlignment = Alignment.Center
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.shift == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Failed to load job details", color = Color.Gray)
            }
        } else {
            val shift = state.shift!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BgColor)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(20.dp, 30.dp, 20.dp, 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Manager Selection (Admin only)
                    if (state.isAdmin) {
                        ManagerPickerSection(
                            managersList = state.managersList,
                            selectedManagerId = state.selectedManagerId,
                            onSelectManager = { managerId ->
                                viewModel.selectManager(managerId)
                                viewModel.updateShift()
                            }
                        )
                    }

                    // Manager Information Card (Admin only)
                    if (state.isAdmin && state.managerName.isNotBlank()) {
                        InfoCard(
                            icon = "\uD83D\uDC64",
                            title = "Manager Information",
                            rows = listOf(
                                "\uD83D\uDC64" to ("Manager Name:" to state.managerName),
                                "\uD83D\uDCE7" to ("Manager Email:" to state.managerEmail),
                                "\uD83D\uDCF1" to ("Manager Phone:" to state.managerPhone)
                            )
                        )
                    }

                    // Contractor Information Card (Manager/Admin/Customer when status != Created)
                    if (state.isManager && state.contractorName.isNotBlank()) {
                        InfoCard(
                            icon = "\uD83D\uDC64",
                            title = "Contractor Information",
                            rows = listOf(
                                "\uD83D\uDC64" to ("Name:" to state.contractorName),
                                "\uD83D\uDCE7" to ("Email:" to state.contractorEmail),
                                "\uD83D\uDCF1" to ("Phone:" to state.contractorPhone)
                            )
                        )
                    }

                    // Project Name
                    DetailCard(icon = "\uD83D\uDCCB", title = "Project Name") {
                        Text(
                            text = shift.projectName.ifBlank { "Not Set" },
                            fontSize = 16.sp,
                            color = Gray500,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }

                    // Client Name
                    DetailCard(icon = "\uD83D\uDC64", title = "Client Name") {
                        Text(
                            text = shift.clientName ?: "",
                            fontSize = 16.sp,
                            color = Gray500,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }

                    // All Day toggle (Manager/Admin only)
                    if (state.isAllDayEditable) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "All Day",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Blue
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(
                                    checked = state.isAllDay,
                                    onCheckedChange = { checked ->
                                        viewModel.updateIsAllDay(checked)
                                        viewModel.updateShift()
                                    }
                                )
                            }
                        }
                    }

                    // Reminder Section
                    if (state.showReminderSection) {
                        ReminderCard(
                            canEdit = state.canEditReminder,
                            canViewOnly = state.canViewReminderOnly,
                            hasReminderSet = state.hasReminderSet,
                            reminderOptions = state.reminderOptions,
                            selectedLabel = state.selectedReminderLabel,
                            reminderTime = state.shift?.reminderTime,
                            onSelectReminder = { label ->
                                viewModel.selectReminder(label)
                                viewModel.addReminder()
                            }
                        )
                    }

                    // Job Location
                    DetailCard(icon = "\uD83D\uDCCD", title = "Job Location") {
                        Text(
                            text = shift.address.ifBlank { "Not Set" },
                            fontSize = 16.sp,
                            color = Gray500,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }

                    // Job Schedule
                    DetailCard(icon = "\u23F0", title = "Job Schedule") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ScheduleRow("\uD83D\uDD50 Duration From:", state.durationFromFormatted)
                            ScheduleRow("\uD83D\uDD55 Duration To:", state.durationToFormatted)
                            if (state.showShiftStartTime && state.shiftStartTimeFormatted.isNotBlank()) {
                                ScheduleRow("\u2705 Actual Start:", state.shiftStartTimeFormatted)
                            }
                            if (state.showShiftEndTime && state.shiftEndTimeFormatted.isNotBlank()) {
                                ScheduleRow("\uD83C\uDFC1 Actual End:", state.shiftEndTimeFormatted)
                            }
                        }
                    }

                    // Job Description
                    DetailCard(icon = "\uD83D\uDCDD", title = "Job Description") {
                        Text(
                            text = shift.instructions.ifBlank { "No description" },
                            fontSize = 16.sp,
                            color = Gray500,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }

                    // Contract Type
                    DetailCard(icon = "\uD83D\uDCC4", title = "Contract Type") {
                        Text(
                            text = state.contractTypeText,
                            fontSize = 16.sp,
                            color = Gray500,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }

                    // Invoice Status
                    DetailCard(icon = "\uD83E\uDDFE", title = "Invoice Status") {
                        Text(
                            text = state.invoiceStatusText,
                            fontSize = 16.sp,
                            color = Gray500,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }

                    // H&S Forms Required (heading always shown, badge only when hsForms is set)
                    DetailCard(icon = "\uD83D\uDEE1\uFE0F", title = "H&S Forms Required") {
                        if (state.showHsForm) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 26.dp, top = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(state.hsFormColor))
                                    .padding(horizontal = 15.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = state.hsFormText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Feedback Section
                    if (state.showFeedback) {
                        Card(
                            shape = RoundedCornerShape(15.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("\uD83D\uDCAC", fontSize = 18.sp)
                                    Text(
                                        "Feedback and Reviews",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Blue
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    "Manager's Feedback:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Gray500
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF0F8FF))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = shift.feedback,
                                        fontSize = 14.sp,
                                        color = Gray500
                                    )
                                }

                                if (shift.contractorResponseToReview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Contractor's Response:",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Gray500
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF0F8FF))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = shift.contractorResponseToReview,
                                            fontSize = 14.sp,
                                            color = Gray500
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Job Status
                    DetailCard(icon = "\uD83D\uDCCA", title = "Job Status") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(state.statusColor))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = state.statusMessage,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Tracking Status Card
                    if (state.isTrackingActive && state.isCaregiver) {
                        TrackingStatusCard(trackingState = state.trackingState)
                    }

                    // Navigate Button (launches turn-by-turn navigation)
                    if (state.showNavigateButton) {
                        val navContext = LocalContext.current
                        Button(
                            onClick = {
                                TurnByTurnNavigationActivity.start(
                                    navContext,
                                    shift.latitude ?: 0.0,
                                    shift.longitude ?: 0.0,
                                    shift.projectName.ifBlank { "Site #${shift.id}" }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Navigate to Site",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Multi-site warning
                    if (state.showMultiSiteWarning) {
                        Card(
                            shape = RoundedCornerShape(15.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "You are currently clocked in at ${state.activeShiftProjectName}. Clock out first before clocking in here.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF856404)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.clockOutOtherShift() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Clock Out from ${state.activeShiftProjectName}", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    // Clock In/Out Section
                    if (state.showClockInButton || state.showClockOutButton) {
                        ClockInOutSection(
                            showClockIn = state.showClockInButton,
                            showClockOut = state.showClockOutButton,
                            selectedLocationType = state.selectedClockLocationType,
                            onSelectLocationType = viewModel::selectClockLocationType,
                            availableStages = state.availableStages,
                            selectedStageName = state.selectedStageName,
                            onSelectStage = viewModel::selectStage,
                            onClockIn = { lat, lng -> viewModel.clockInWithTracking(lat, lng) },
                            onClockOut = { lat, lng -> viewModel.clockOutWithTracking(lat, lng) }
                        )
                    }
                }

                // Action Buttons Section
                ActionButtonsSection(
                    state = state,
                    onSendQuote = { onSendQuote(shift.id) },
                    onViewQuotations = { onViewQuotations(shift.id) },
                    onStart = viewModel::startShift,
                    onEnd = viewModel::endShift,
                    onComplete = viewModel::completeShift,
                    onMarkComplete = viewModel::markShiftComplete,
                    onReject = viewModel::rejectShift,
                    onReviews = viewModel::viewReviews
                )

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

// ========== Composable building blocks ==========

@Composable
private fun DetailCard(
    icon: String,
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 18.sp)
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoCard(
    icon: String,
    title: String,
    rows: List<Pair<String, Pair<String, String>>>
) {
    Card(
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 18.sp, color = Blue)
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
            }
            Spacer(modifier = Modifier.height(15.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (rowIcon, labelValue) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(rowIcon, fontSize = 14.sp, color = Gray500)
                        Text(
                            text = labelValue.first,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gray500
                        )
                        Text(
                            text = labelValue.second,
                            fontSize = 14.sp,
                            color = Gray500
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Gray500
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Gray500
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerPickerSection(
    managersList: List<nz.co.doer.data.remote.dto.UserDto>,
    selectedManagerId: String?,
    onSelectManager: (String) -> Unit
) {
    if (managersList.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val selectedManager = managersList.find { it.id == selectedManagerId }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83D\uDC64", fontSize = 16.sp)
            Text(
                "Assigned Manager:",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedManager?.displayName ?: "Select Manager",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(5.dp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                managersList.forEach { manager ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = manager.displayName,
                                fontWeight = FontWeight.Bold,
                                color = Blue
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelectManager(manager.id)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(
    canEdit: Boolean,
    canViewOnly: Boolean,
    hasReminderSet: Boolean,
    reminderOptions: List<ReminderOption>,
    selectedLabel: String,
    reminderTime: String?,
    onSelectReminder: (String) -> Unit
) {
    if (!canEdit && !canViewOnly && !hasReminderSet) return

    Card(
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\uD83D\uDD14", fontSize = 18.sp, color = Blue)
                Text(
                    "Reminder Information",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (canEdit) {
                var expanded by remember { mutableStateOf(false) }

                Text(
                    "Select Reminder:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gray500
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(5.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        reminderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label, fontSize = 14.sp) },
                                onClick = {
                                    expanded = false
                                    onSelectReminder(option.label)
                                }
                            )
                        }
                    }
                }
            }

            if (canViewOnly) {
                Text(
                    "Current Reminder:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gray500
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgColor)
                        .padding(12.dp, 8.dp)
                ) {
                    Text(selectedLabel, fontSize = 14.sp, color = Gray500)
                }
            }

            if (hasReminderSet && selectedLabel != "None" && !reminderTime.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Reminder Time:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4B5563)
                )
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFEFF6FF))
                        .padding(10.dp, 6.dp)
                ) {
                    Text(
                        text = reminderTime,
                        fontSize = 14.sp,
                        color = Color(0xFF1F2937)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    state: ShiftDetailsUiState,
    onSendQuote: () -> Unit,
    onViewQuotations: () -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onComplete: () -> Unit,
    onMarkComplete: () -> Unit,
    onReject: () -> Unit,
    onReviews: () -> Unit
) {
    if (state.isUpdating) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        // Send Quote Button (Caregiver)
        if (state.quotationButton) {
            ActionButton(text = "Send Quote", bgColor = Color.Black, onClick = onSendQuote)
        }

        // View Quotations Button (Manager/Admin)
        if (state.viewQuotationsButton) {
            ActionButton(text = "View Quotations", bgColor = Color.Black, onClick = onViewQuotations)
        }

        // Main Action Buttons Row
        // Note: Start and End buttons replaced by Clock In/Out feature
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // if (state.startButton) {
            //     ActionButton(
            //         text = "Start",
            //         bgColor = Color.Black,
            //         onClick = onStart,
            //         modifier = Modifier.weight(1f)
            //     )
            // }
            // if (state.endButton) {
            //     ActionButton(
            //         text = "End",
            //         bgColor = Color.Black,
            //         onClick = onEnd,
            //         modifier = Modifier.weight(1f)
            //     )
            // }
            if (state.completeButton) {
                ActionButton(
                    text = "Complete",
                    bgColor = Color.Black,
                    onClick = onComplete,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Mark Complete Button (Contractor — when all work is done)
        if (state.markCompleteButton) {
            ActionButton(
                text = "Mark Complete",
                bgColor = Color(0xFF007AFF),
                onClick = onMarkComplete
            )
        }

        // Secondary Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.rejectButton) {
                ActionButton(
                    text = "Reject",
                    bgColor = Color(0xFF8B0000),
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.reviewsButton) {
                ActionButton(
                    text = "Reviews",
                    bgColor = Color.Black,
                    onClick = onReviews,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(40.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}

// ========== Tracking UI Components ==========

@Composable
private fun TrackingStatusCard(trackingState: DoerTrackingState) {
    val (statusText, statusColor) = when (trackingState) {
        DoerTrackingState.IDLE -> "Ready" to Color(0xFF9CA3AF)
        DoerTrackingState.CLOCKED_IN -> "Clocked In" to Color(0xFF007AFF)
        DoerTrackingState.EN_ROUTE -> "On the Way" to Color(0xFF3B82F6)
        DoerTrackingState.ARRIVED -> "Arrived" to Color(0xFF10B981)
        DoerTrackingState.ON_SITE -> "On Site" to Color(0xFF00C875)
        DoerTrackingState.LEAVING -> "Leaving Site" to Color(0xFFF59E0B)
        DoerTrackingState.CLOCKED_OUT -> "Clocked Out" to Color(0xFF6B7280)
    }

    val animatedColor by animateColorAsState(targetValue = statusColor, label = "statusColor")

    Card(
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\uD83D\uDCE1", fontSize = 18.sp)
                Text(
                    "Live Tracking",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray500)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(animatedColor)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (trackingState == DoerTrackingState.LEAVING) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Auto clock-out in 5 minutes if you don't return to site",
                    fontSize = 12.sp,
                    color = Color(0xFFF59E0B)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockInOutSection(
    showClockIn: Boolean,
    showClockOut: Boolean,
    selectedLocationType: ClockLocationType,
    onSelectLocationType: (ClockLocationType) -> Unit,
    availableStages: List<nz.co.doer.data.remote.dto.ShiftSubItemDto> = emptyList(),
    selectedStageName: String = "",
    onSelectStage: (String) -> Unit = {},
    onClockIn: (latitude: Double, longitude: Double) -> Unit,
    onClockOut: (latitude: Double, longitude: Double) -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var showClockOutDialog by remember { mutableStateOf(false) }
    var clockOutReason by remember { mutableStateOf("") }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            // Permission granted — retry clock in
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        onClockIn(location.latitude, location.longitude)
                    } else {
                        onClockIn(0.0, 0.0)
                    }
                }
            } catch (_: SecurityException) {
                onClockIn(0.0, 0.0)
            }
        }
    }

    if (showClockOutDialog) {
        AlertDialog(
            onDismissRequest = { showClockOutDialog = false },
            title = { Text("Clock Out") },
            text = {
                Column {
                    Text("Are you sure you want to clock out?")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = clockOutReason,
                        onValueChange = { clockOutReason = it },
                        label = { Text("Reason (optional)") },
                        placeholder = { Text("e.g., GPS drift, early departure") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showClockOutDialog = false
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            onClockOut(
                                location?.latitude ?: 0.0,
                                location?.longitude ?: 0.0
                            )
                        }
                    } catch (_: SecurityException) {
                        onClockOut(0.0, 0.0)
                    }
                }) { Text("Clock Out", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showClockOutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(15.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u23F1\uFE0F", fontSize = 18.sp)
                Text(
                    "Clock In / Out",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
            }

            if (showClockIn) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Where are you clocking in?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gray500
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Location type chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClockLocationType.entries.forEach { type ->
                        val label = when (type) {
                            ClockLocationType.SITE -> "Site"
                            ClockLocationType.YARD -> "Yard"
                            ClockLocationType.OFFICE -> "Office"
                        }
                        FilterChip(
                            selected = selectedLocationType == type,
                            onClick = { onSelectLocationType(type) },
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Blue,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Stage picker (if sub-items/stages exist)
                if (availableStages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Select Stage:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gray500
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableStages.forEach { stage ->
                            FilterChip(
                                selected = selectedStageName == stage.subitem,
                                onClick = { onSelectStage(stage.subitem) },
                                label = { Text(stage.subitem, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clock In button
                Button(
                    onClick = {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    onClockIn(location.latitude, location.longitude)
                                } else {
                                    onClockIn(0.0, 0.0)
                                }
                            }.addOnFailureListener {
                                onClockIn(0.0, 0.0)
                            }
                        } catch (_: SecurityException) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C875)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Clock In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }

            if (showClockOut) {
                Spacer(modifier = Modifier.height(16.dp))

                // Clock Out button
                Button(
                    onClick = { showClockOutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Clock Out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
