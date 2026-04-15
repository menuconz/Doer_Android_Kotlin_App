package nz.co.doer.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            ) {
            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(20.dp, 15.dp, 20.dp, 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ===== SECTION 1: Status + Project Info (combined card) =====
                    Card(
                        shape = RoundedCornerShape(15.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Status badge + Project Name in one row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = shift.projectName.ifBlank { "Not Set" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1F2937),
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(state.statusColor))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(state.statusMessage, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Client + Location
                            if (!shift.clientName.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("\uD83D\uDC64", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(shift.clientName ?: "", fontSize = 14.sp, color = Gray500)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("\uD83D\uDCCD", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(shift.address.ifBlank { "Not Set" }, fontSize = 14.sp, color = Gray500)
                            }
                        }
                    }

                    // ===== SECTION 2: Tracking + Navigate + Clock In/Out (actions first) =====

                    // Tracking Status Card
                    if (state.isTrackingActive && state.isCaregiver) {
                        TrackingStatusCard(trackingState = state.trackingState)
                    }

                    // Navigate Button
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
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Navigate to Site", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
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
                                    fontSize = 14.sp, color = Color(0xFF856404)
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

                    // ===== SECTION 3: People Info (Admin/Manager) =====

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
                            icon = "\uD83D\uDC64", title = "Manager Information",
                            rows = listOf(
                                "\uD83D\uDC64" to ("Manager Name:" to state.managerName),
                                "\uD83D\uDCE7" to ("Manager Email:" to state.managerEmail),
                                "\uD83D\uDCF1" to ("Manager Phone:" to state.managerPhone)
                            )
                        )
                    }

                    // Contractor Information Card
                    if (state.isManager && state.contractorName.isNotBlank()) {
                        InfoCard(
                            icon = "\uD83D\uDC64", title = "Contractor Information",
                            rows = listOf(
                                "\uD83D\uDC64" to ("Name:" to state.contractorName),
                                "\uD83D\uDCE7" to ("Email:" to state.contractorEmail),
                                "\uD83D\uDCF1" to ("Phone:" to state.contractorPhone)
                            )
                        )
                    }

                    // ===== SECTION 4: Schedule + Details (combined card) =====
                    DetailCard(icon = "\u23F0", title = "Job Schedule") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (shift.instructions.isNotBlank()) {
                        DetailCard(icon = "\uD83D\uDCDD", title = "Job Description") {
                            Text(
                                text = shift.instructions,
                                fontSize = 14.sp, color = Gray500,
                                modifier = Modifier.padding(start = 26.dp)
                            )
                        }
                    }

                    // ===== SECTION 5: Contract + Invoice + H&S (combined card) =====
                    Card(
                        shape = RoundedCornerShape(15.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("\uD83D\uDCC4", fontSize = 18.sp)
                                Text("Job Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Blue)
                            }
                            // Contract Type
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Contract Type", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray500)
                                Text(state.contractTypeText, fontSize = 14.sp, color = Color(0xFF1F2937))
                            }
                            // Invoice Status
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Invoice Status", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray500)
                                Text(state.invoiceStatusText, fontSize = 14.sp, color = Color(0xFF1F2937))
                            }
                            // H&S Form (only if set)
                            if (state.showHsForm) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("H&S Forms", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray500)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(state.hsFormColor))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(state.hsFormText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            // All Day toggle (Manager/Admin only)
                            if (state.isAllDayEditable) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("All Day", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray500)
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
                    }

                    // ===== SECTION 6: Reminder =====
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

                    // ===== SECTION 7: Feedback =====
                    if (state.showFeedback) {
                        Card(
                            shape = RoundedCornerShape(15.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("\uD83D\uDCAC", fontSize = 18.sp)
                                    Text("Feedback and Reviews", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Blue)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Manager's Feedback:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray500)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F8FF)).padding(12.dp)) {
                                    Text(shift.feedback, fontSize = 14.sp, color = Gray500)
                                }
                                if (shift.contractorResponseToReview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Contractor's Response:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray500)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F8FF)).padding(12.dp)) {
                                        Text(shift.contractorResponseToReview, fontSize = 14.sp, color = Gray500)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            } // End scrollable content

                // Sticky Bottom Action Buttons
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
            } // End outer Column
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
    // Check if any button is visible
    val hasAnyButton = state.quotationButton || state.viewQuotationsButton ||
            state.completeButton || state.markCompleteButton || state.rejectButton ||
            state.reviewsButton || state.isUpdating
    if (!hasAnyButton) return

    if (state.isUpdating) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Sticky bottom bar with divider
    HorizontalDivider(color = Color(0xFFE5E7EB))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Send Quote (Contractor)
        if (state.quotationButton) {
            ActionButton(text = "Send Quote", bgColor = Color.Black, onClick = onSendQuote)
        }

        // View Quotations (Manager/Admin)
        if (state.viewQuotationsButton) {
            ActionButton(text = "View Quotations", bgColor = Color.Black, onClick = onViewQuotations)
        }

        // Complete / Finish Job (Manager)
        if (state.completeButton) {
            ActionButton(text = "Complete", bgColor = Color.Black, onClick = onComplete)
        }

        // Mark Complete (Contractor)
        if (state.markCompleteButton) {
            ActionButton(text = "Mark Complete", bgColor = Color(0xFF007AFF), onClick = onMarkComplete)
        }

        // Reject (Contractor)
        if (state.rejectButton) {
            ActionButton(text = "Reject", bgColor = Color(0xFF8B0000), onClick = onReject)
        }

        // Reviews (hidden currently)
        if (state.reviewsButton) {
            ActionButton(text = "Reviews", bgColor = Color.Black, onClick = onReviews)
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
    var showForegroundLocationDisclosure by remember { mutableStateOf(false) }
    var showBackgroundLocationDisclosure by remember { mutableStateOf(false) }

    // Reactive permission state — recomputed on resume so returning from Settings updates the banner
    var hasBackgroundLocation by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBackgroundLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun proceedClockInWithLastLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                onClockIn(location?.latitude ?: 0.0, location?.longitude ?: 0.0)
            }.addOnFailureListener {
                onClockIn(0.0, 0.0)
            }
        } catch (_: SecurityException) {
            onClockIn(0.0, 0.0)
        }
    }

    // Background location permission launcher (Android 10+ requires a separate request)
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
        proceedClockInWithLastLocation()
    }

    // Foreground location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            val needsBackground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            if (needsBackground) {
                showBackgroundLocationDisclosure = true
            } else {
                proceedClockInWithLastLocation()
            }
        }
    }

    if (showForegroundLocationDisclosure) {
        AlertDialog(
            onDismissRequest = { showForegroundLocationDisclosure = false },
            title = { Text("Location access required") },
            text = {
                Text(
                    "Doer collects location data to track your shift, record the route " +
                    "to the job site, enable turn-by-turn navigation, and automatically " +
                    "clock you in and out via geofences at customer sites.\n\n" +
                    "Location is used only while you are clocked in. A persistent " +
                    "notification will be shown the whole time, and tracking stops " +
                    "as soon as you clock out."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showForegroundLocationDisclosure = false
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showForegroundLocationDisclosure = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBackgroundLocationDisclosure) {
        AlertDialog(
            onDismissRequest = { showBackgroundLocationDisclosure = false },
            title = { Text("Allow background location") },
            text = {
                Text(
                    "To keep tracking your shift accurately when the phone is locked " +
                    "or you are using another app, Doer needs permission to access " +
                    "location in the background.\n\n" +
                    "On the next screen, please choose \"Allow all the time\".\n\n" +
                    "Background tracking runs only while you are clocked in and stops " +
                    "the moment you clock out."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundLocationDisclosure = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    } else {
                        proceedClockInWithLastLocation()
                    }
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackgroundLocationDisclosure = false
                    proceedClockInWithLastLocation()
                }) { Text("Not now") }
            }
        )
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

                // Background location warning banner — tracking works worse if user picked "While using the app"
                if (!hasBackgroundLocation) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBackgroundLocationDisclosure = true },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFB25E02)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Background location is off",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF663900)
                                )
                                Text(
                                    "Doer can't track your shift when the phone is locked or you're " +
                                        "in another app. Tap to enable \"Allow all the time\".",
                                    fontSize = 12.sp,
                                    color = Color(0xFF663900)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Clock In button
                Button(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasFine) {
                            showForegroundLocationDisclosure = true
                            return@Button
                        }
                        val needsBackground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsBackground) {
                            showBackgroundLocationDisclosure = true
                            return@Button
                        }
                        proceedClockInWithLastLocation()
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
