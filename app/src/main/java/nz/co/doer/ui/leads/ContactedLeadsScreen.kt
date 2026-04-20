package nz.co.doer.ui.leads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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

private val HeaderBg = Color(0xFFF3F4F6)
private val BorderColor = Color.LightGray
private val YellowAccent = Color(0xFFFFCB00)

// Column widths matching MAUI ContactedLeads.xaml
private val ColProjectDesc = 220.dp
private val ColClientName = 180.dp
private val ColClientEmail = 200.dp
private val ColCost = 150.dp
private val ColLocation = 220.dp
private val ColCreatedDate = 150.dp
private val ColView = 100.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactedLeadsScreen(
    onViewLead: (Int) -> Unit,
    viewModel: ContactedLeadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
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
                Text("No contacted leads found", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Card with yellow left stripe matching MAUI
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp, 5.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp))
                        .background(Color.White)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Yellow left stripe
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxSize()
                                .background(YellowAccent)
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
                                        SortableHeader("Client Name", ColClientName, "ClientName", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Client Email", ColClientEmail, "ClientEmail", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Cost From Quote", ColCost, "CostFromQuote", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
                                        SortableHeader("Location", ColLocation, "Location", state.sortColumn, state.sortAscending) { viewModel.sortBy(it) }
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

                                    // Data rows - all read-only matching MAUI (no edit handlers)
                                    state.leads.forEach { lead ->
                                        Row {
                                            DataCell(lead.jobDescription, ColProjectDesc)
                                            DataCell(lead.clientName, ColClientName)
                                            DataCell(lead.clientEmail, ColClientEmail)
                                            DataCell(
                                                lead.costFromQuote?.let { "$it" } ?: "",
                                                ColCost
                                            )
                                            DataCell(lead.location, ColLocation)
                                            DataCell(viewModel.formatDate(lead.createdDate), ColCreatedDate)
                                            // View button matching MAUI
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
                                                    Text("View", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
