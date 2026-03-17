package nz.co.doer.ui.leads

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// MAUI colors
private val BgColor = Color(0xFFF8F9FA)
private val BlueLabel = Color(0xFF4D4BA3)
private val BorderColor = Color(0xFF667685)
private val OrangeStatus = Color(0xFFFF9500)
private val SuggestionBg = Color(0xFFF8F9FA)
private val SuggestionBorder = Color(0xFFE0E0E0)

private val fieldShape = RoundedCornerShape(12.dp)
private val fieldTextStyle = TextStyle(fontSize = 16.sp)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = BorderColor,
    unfocusedBorderColor = BorderColor
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewLeadScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AddNewLeadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Success popup with OK button
    if (state.isSuccess) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onSuccess() },
            title = { Text("Success") },
            text = { Text("New Lead created.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onSuccess() }) {
                    Text("OK")
                }
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
                title = { Text("Add New Lead", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .background(BgColor)
            ) {
                // Scrollable form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    // ── Select Client ──
                    FieldLabel("\uD83D\uDC64", "Select Client:")
                    ClientPicker(state, viewModel)

                    // ── Project Name ──
                    FieldLabel("\uD83D\uDCDD", "Name:")
                    OutlinedTextField(
                        value = state.projectName,
                        onValueChange = viewModel::onProjectNameChange,
                        placeholder = { Text("Enter Project Name", fontSize = 16.sp) },
                        singleLine = true,
                        isError = state.projectName.isEmpty(),
                        supportingText = {
                            if (state.projectName.isEmpty()) {
                                Text("Project Name Required", color = Color.Red)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors(),
                        textStyle = fieldTextStyle
                    )

                    // ── Job Description ──
                    FieldLabel("\uD83D\uDCDD", "Job Description:")
                    OutlinedTextField(
                        value = state.jobDescription,
                        onValueChange = viewModel::onJobDescriptionChange,
                        placeholder = { Text("Enter the Job Description", fontSize = 16.sp) },
                        singleLine = false,
                        minLines = 4,
                        isError = state.jobDescription.isEmpty(),
                        supportingText = {
                            if (state.jobDescription.isEmpty()) {
                                Text("Job Description Required", color = Color.Red)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors(),
                        textStyle = fieldTextStyle
                    )

                    // ── Owner ──
                    OwnerPicker(state, viewModel)

                    // ── Contract Type ──
                    FieldLabel("\uD83D\uDCCB", "Contract Type:")
                    ContractTypePicker(state, viewModel)

                    // ── Location ──
                    FieldLabel("\uD83D\uDCCD", "Location:")
                    OutlinedTextField(
                        value = state.searchAddress,
                        onValueChange = viewModel::onSearchAddressChange,
                        placeholder = { Text("Enter Address", fontSize = 16.sp) },
                        singleLine = true,
                        isError = state.searchAddress.isEmpty() && !state.showPlaceList,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors(),
                        textStyle = fieldTextStyle
                    )

                    // Address suggestions — no extra spacing
                    if (state.showPlaceList && state.placeList.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 0.dp, vertical = 0.dp),
                            colors = CardDefaults.cardColors(containerColor = SuggestionBg),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                state.placeList.forEach { place ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable { viewModel.selectPlace(place) },
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SuggestionBorder)
                                    ) {
                                        Text(
                                            text = place.description,
                                            fontSize = 14.sp,
                                            color = BlueLabel,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.searchAddress.isEmpty() && !state.showPlaceList) {
                        Text("Address Required", color = Color.Red, fontSize = 12.sp)
                    }

                    // ── Cost From Quote ──
                    FieldLabel("\uD83D\uDCB5", "Cost From Quote ($):")
                    OutlinedTextField(
                        value = state.costFromQuote,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.toDoubleOrNull() != null || newValue == ".") {
                                viewModel.onCostChange(newValue)
                            }
                        },
                        placeholder = { Text("Enter Quote Amount", fontSize = 16.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors(),
                        textStyle = fieldTextStyle
                    )

                    // ── Status ──
                    FieldLabel("\uD83D\uDCCA", "Status:")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = OrangeStatus),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "New Lead",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // ── Add Lead Button ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = viewModel::addLead,
                        enabled = !state.isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .width(150.dp)
                            .height(40.dp)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Add Lead",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Reusable Components ──

@Composable
private fun FieldLabel(emoji: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = emoji, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = BlueLabel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientPicker(state: AddNewLeadUiState, viewModel: AddNewLeadViewModel) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = state.selectedClient?.name ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Select a Client", fontSize = 16.sp, color = Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = fieldShape,
            colors = fieldColors(),
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BlueLabel)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            state.clients.forEach { client ->
                DropdownMenuItem(
                    text = { Text(client.name, fontWeight = FontWeight.Bold, color = BlueLabel) },
                    onClick = {
                        viewModel.onClientSelected(client)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerPicker(state: AddNewLeadUiState, viewModel: AddNewLeadViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OWNER",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BlueLabel,
                modifier = Modifier.padding(end = 10.dp)
            )
            Spacer(modifier = Modifier.weight(1f))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                Row(
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .clickable { expanded = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.selectedOwner?.displayName ?: "Select Owner",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueLabel
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = BlueLabel
                    )
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    state.owners.forEach { owner ->
                        DropdownMenuItem(
                            text = { Text(owner.displayName, fontWeight = FontWeight.Bold, color = BlueLabel) },
                            onClick = {
                                viewModel.onOwnerSelected(owner)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractTypePicker(state: AddNewLeadUiState, viewModel: AddNewLeadViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = if (state.selectedContractType > 0 && state.selectedContractType <= contractTypeList.size) {
        contractTypeList[state.selectedContractType - 1]
    } else {
        ""
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Select Contract Type", fontSize = 16.sp, color = Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = fieldShape,
            colors = fieldColors(),
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BlueLabel)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            contractTypeList.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name, fontWeight = FontWeight.Bold, color = BlueLabel) },
                    onClick = {
                        viewModel.onContractTypeSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
