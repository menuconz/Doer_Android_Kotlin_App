package nz.co.doer.ui.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShiftScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AddShiftViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onSuccess()
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
                title = { Text("Add Job", style = MaterialTheme.typography.titleMedium) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Project Name
            OutlinedTextField(
                value = state.projectName,
                onValueChange = viewModel::onProjectNameChange,
                label = { Text("Project Name") },
                placeholder = { Text("Enter Project Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Client Dropdown
            DropdownField(
                label = "Client",
                options = state.clients.map { it.name },
                selectedIndex = state.selectedClientIndex,
                onSelected = viewModel::onClientChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // All Day Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All Day",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4D4BA3),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.isAllDay,
                    onCheckedChange = viewModel::onAllDayChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Duration From
            Text("From", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                // Date
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.durationFromDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        onValueChange = {},
                        label = { Text("Date") },
                        enabled = false,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d -> viewModel.onDurationFromDateChange(LocalDate.of(y, m + 1, d)) },
                                    state.durationFromDate.year,
                                    state.durationFromDate.monthValue - 1,
                                    state.durationFromDate.dayOfMonth
                                ).show()
                            }
                    )
                }

                if (!state.isAllDay) {
                    Spacer(modifier = Modifier.width(8.dp))
                    // Time
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = state.durationFromTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                            onValueChange = {},
                            label = { Text("Time") },
                            enabled = false,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> viewModel.onDurationFromTimeChange(LocalTime.of(h, m)) },
                                        state.durationFromTime.hour,
                                        state.durationFromTime.minute,
                                        false
                                    ).show()
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Duration To
            Text("To", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                // Date
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.durationToDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        onValueChange = {},
                        label = { Text("Date") },
                        enabled = false,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d -> viewModel.onDurationToDateChange(LocalDate.of(y, m + 1, d)) },
                                    state.durationToDate.year,
                                    state.durationToDate.monthValue - 1,
                                    state.durationToDate.dayOfMonth
                                ).show()
                            }
                    )
                }

                if (!state.isAllDay) {
                    Spacer(modifier = Modifier.width(8.dp))
                    // Time
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = state.durationToTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                            onValueChange = {},
                            label = { Text("Time") },
                            enabled = false,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> viewModel.onDurationToTimeChange(LocalTime.of(h, m)) },
                                        state.durationToTime.hour,
                                        state.durationToTime.minute,
                                        false
                                    ).show()
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Job Address with Google Places Autocomplete
            OutlinedTextField(
                value = state.searchAddress,
                onValueChange = viewModel::onSearchAddressChange,
                label = { Text("Job Address") },
                placeholder = { Text("Enter Job Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.showSuggestions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    state.placeSuggestions.forEach { prediction ->
                        Text(
                            text = prediction.description,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onPlaceSelected(prediction) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contract Type Dropdown
            DropdownField(
                label = "Contract Type",
                options = viewModel.contractTypes.map { it.name },
                selectedIndex = state.selectedContractTypeIndex,
                onSelected = viewModel::onContractTypeChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Reminder Dropdown
            DropdownField(
                label = "Select Reminder",
                options = viewModel.reminderOptions.map { it.label },
                selectedIndex = state.selectedReminderIndex,
                onSelected = viewModel::onReminderChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Job Description
            OutlinedTextField(
                value = state.instructions,
                onValueChange = viewModel::onInstructionsChange,
                label = { Text("Job Description") },
                placeholder = { Text("Enter the Job Description") },
                minLines = 4,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Create Button
            Button(
                onClick = viewModel::createShift,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp).width(24.dp)
                    )
                } else {
                    Text("Create Job")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
