package nz.co.doer.ui.tracking

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Calendar

private val Blue = Color(0xFF007AFF)
private val Gray500 = Color(0xFF6B7280)
private val BgColor = Color(0xFFF8F9FA)
private val Green = Color(0xFF10B981)
private val Red = Color(0xFFEF4444)
private val Amber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTrackingDashboardScreen(
    onOpenDrawer: () -> Unit,
    viewModel: TimeTrackingDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Edit time entry dialog state
    var editingDoer by remember { mutableStateOf<DoerHoursUi?>(null) }
    var editClockIn by remember { mutableStateOf("") }
    var editClockOut by remember { mutableStateOf("") }
    var editReason by remember { mutableStateOf("") }

    // Edit dialog
    editingDoer?.let { doer ->
        AlertDialog(
            onDismissRequest = { editingDoer = null },
            title = { Text("Edit Time Entry", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        doer.displayName.ifBlank { "Doer ${doer.userId.take(6)}" },
                        fontWeight = FontWeight.Bold,
                        color = Blue
                    )
                    OutlinedTextField(
                        value = editClockIn,
                        onValueChange = { editClockIn = it },
                        label = { Text("Clock-In Time (HH:mm)") },
                        placeholder = { Text("e.g. 07:30") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = editClockOut,
                        onValueChange = { editClockOut = it },
                        label = { Text("Clock-Out Time (HH:mm)") },
                        placeholder = { Text("e.g. 16:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = editReason,
                        onValueChange = { editReason = it },
                        label = { Text("Reason (required)") },
                        placeholder = { Text("e.g. GPS drift, missed clock") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editReason.isBlank()) return@Button
                        viewModel.editTimeEntry(
                            doer.userId, doer.shiftId,
                            editClockIn, editClockOut, editReason
                        )
                        editingDoer = null
                    },
                    enabled = editReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { editingDoer = null }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Tracking", color = Color.White, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading && state.sites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BgColor),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Date picker row
                item {
                    DatePickerRow(
                        selectedDateDisplay = state.selectedDateDisplay,
                        onPickDate = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    viewModel.selectDate(year, month + 1, day)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    )
                }

                // Summary stats
                item {
                    SummaryStatsRow(
                        totalSites = state.totalSites,
                        totalDoers = state.totalDoers,
                        totalHours = state.totalHours,
                        alertCount = state.alertCount
                    )
                }

                // Site cards
                if (state.sites.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No site data for this date",
                                    fontSize = 16.sp,
                                    color = Gray500
                                )
                                Text(
                                    "Select a different date to view hours",
                                    fontSize = 13.sp,
                                    color = Color(0xFFD1D5DB)
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = state.sites,
                        key = { it.shiftId }
                    ) { site ->
                        SiteHoursCard(
                            site = site,
                            onToggleExpand = { viewModel.toggleSiteExpanded(site.shiftId) },
                            onEditDoer = { doer ->
                                editClockIn = doer.clockInTime.takeLast(8).take(5)
                                editClockOut = if (doer.clockOutTime.isNotBlank()) doer.clockOutTime.takeLast(8).take(5) else ""
                                editReason = ""
                                editingDoer = doer
                            }
                        )
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DatePickerRow(
    selectedDateDisplay: String,
    onPickDate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Date",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Gray500
        )
        TextButton(onClick = onPickDate) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Blue
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = selectedDateDisplay,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )
        }
    }
}

@Composable
private fun SummaryStatsRow(
    totalSites: Int,
    totalDoers: Int,
    totalHours: Double,
    alertCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryStatItem("Sites", totalSites.toString(), Blue)
        SummaryStatItem("Doers", totalDoers.toString(), Color(0xFF8B5CF6))
        SummaryStatItem("Hours", String.format("%.1f", totalHours), Green)
        if (alertCount > 0) {
            SummaryStatItem("Alerts", alertCount.toString(), Red)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SummaryStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Gray500
        )
    }
}

@Composable
private fun SiteHoursCard(
    site: SiteHoursUi,
    onToggleExpand: () -> Unit,
    onEditDoer: (DoerHoursUi) -> Unit = {}
) {
    val borderColor = when {
        site.isOverThreshold -> Red
        site.isApproachingThreshold -> Amber
        else -> Green
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column {
            // Header — always visible, tappable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Threshold indicator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(borderColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (site.isOverThreshold) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Over threshold",
                            tint = Red,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = site.totalHoursFormatted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = borderColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = site.projectName.ifBlank { "Site #${site.shiftId}" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (site.clientName.isNotBlank()) {
                        Text(
                            text = site.clientName,
                            fontSize = 12.sp,
                            color = Gray500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${site.doerCount} doers",
                            fontSize = 12.sp,
                            color = Gray500
                        )
                        Text("\u2022", fontSize = 10.sp, color = Gray500)
                        Text(
                            text = site.totalHoursFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = borderColor
                        )
                        if (site.stages.isNotEmpty()) {
                            Text("\u2022", fontSize = 10.sp, color = Gray500)
                            Text(
                                text = "${site.stages.size} stages",
                                fontSize = 12.sp,
                                color = Gray500
                            )
                        }
                    }
                }

                // Expand/collapse icon
                Icon(
                    if (site.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (site.isExpanded) "Collapse" else "Expand",
                    tint = Gray500
                )
            }

            // Expanded detail section
            AnimatedVisibility(
                visible = site.isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgColor)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Address
                    if (site.address.isNotBlank()) {
                        Text(
                            text = site.address,
                            fontSize = 12.sp,
                            color = Gray500
                        )
                    }

                    // Stages section
                    if (site.stages.isNotEmpty()) {
                        Text(
                            "Stages",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Blue
                        )
                        site.stages.forEach { stage ->
                            StageRow(stage)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE5E7EB))

                    // Doer hours section
                    Text(
                        "Doer Hours",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Blue
                    )
                    if (site.doerHours.isEmpty()) {
                        Text(
                            "No doer data available",
                            fontSize = 13.sp,
                            color = Color(0xFFD1D5DB)
                        )
                    } else {
                        site.doerHours.forEach { doer ->
                            DoerHoursRow(
                                doer = doer,
                                onEdit = onEditDoer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRow(stage: StageHoursUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage.stageName.ifBlank { "General" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F2937)
            )
            Text(
                text = "${stage.doerCount} doers",
                fontSize = 11.sp,
                color = Gray500
            )
        }
        Text(
            text = stage.totalHoursFormatted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Blue
        )
    }
}

@Composable
private fun DoerHoursRow(doer: DoerHoursUi, onEdit: (DoerHoursUi) -> Unit = {}) {
    val hoursColor = when {
        doer.isOverThreshold -> Red
        doer.totalHours >= 11.0 -> Amber
        else -> Green
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(hoursColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = hoursColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = doer.displayName.ifBlank { "Doer ${doer.userId.take(6)}" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (doer.isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Green)
                    )
                }
                if (doer.isOverThreshold) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Over 12h",
                        tint = Red,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (doer.stage.isNotBlank()) {
                    Text(doer.stage, fontSize = 11.sp, color = Gray500)
                    Text("\u2022", fontSize = 9.sp, color = Gray500)
                }
                if (doer.clockInTime.isNotBlank()) {
                    Text(
                        "In: ${doer.clockInTime.takeLast(8).take(5)}",
                        fontSize = 11.sp,
                        color = Gray500
                    )
                }
                if (doer.clockOutTime.isNotBlank()) {
                    Text(
                        "Out: ${doer.clockOutTime.takeLast(8).take(5)}",
                        fontSize = 11.sp,
                        color = Gray500
                    )
                }
            }
        }

        // Edit button
        IconButton(
            onClick = { onEdit(doer) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit time",
                tint = Gray500,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Hours badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(hoursColor.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = doer.totalHoursFormatted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = hoursColor
            )
        }
    }
}
