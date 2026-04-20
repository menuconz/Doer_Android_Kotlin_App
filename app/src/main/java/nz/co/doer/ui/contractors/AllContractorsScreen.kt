package nz.co.doer.ui.contractors

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// MAUI colors
private val FilterBg = Color(0xFF667685) // MAUI sort icon bg
private val FilterText = Color(0xFF667685) // MAUI Filter label color
private val BlueLabel = Color(0xFF4D4BA3) // MAUI {StaticResource Blue}
private val BorderStroke = Color(0xFF667685) // MAUI entry border
private val HeaderBorderColor = Color.Gray // MAUI Header Border Stroke="Gray"
private val CellBorderColor = Color.LightGray // MAUI Data Border Stroke="LightGray"
private val BgColor = Color(0xFFF8F9FA)

// MAUI Column widths
private val ColName = 150.dp
private val ColEmail = 180.dp
private val ColPhone = 150.dp
private val ColDob = 150.dp
private val ColAddress = 200.dp
private val ColWorkExp = 200.dp
private val ColSkills = 200.dp
private val ColAction = 150.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllContractorsScreen(
    onViewContractorDetail: (userId: String) -> Unit,
    viewModel: AllContractorsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Matching MAUI: FilterContractorsPopupView as ModalBottomSheet
    if (state.showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissFilter() },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp)
            ) {
                // Matching MAUI: "FILTER CONTRACTORS" + "X" close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "FILTER CONTRACTORS",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { viewModel.dismissFilter() },
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("X")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Matching MAUI: Location search — Border Stroke=#667685, RoundRectangle 10, HeightRequest=50
                OutlinedTextField(
                    value = state.searchAddress,
                    onValueChange = viewModel::onSearchAddressChanged,
                    placeholder = { Text("Filter contractors by searched location...", color = BlueLabel, fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderStroke,
                        unfocusedBorderColor = BorderStroke,
                        focusedTextColor = BlueLabel,
                        unfocusedTextColor = BlueLabel
                    )
                )

                // Matching MAUI: Google Places autocomplete list
                if (state.showPlaceList && state.placeList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        state.placeList.forEach { prediction ->
                            Text(
                                text = prediction.description,
                                fontSize = 15.sp,
                                color = Color.Blue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectPlace(prediction) }
                                    .padding(5.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(15.dp))

                // Matching MAUI: "Search By Skill:" + entry
                Text("Search By Skill: ", fontSize = 15.sp, modifier = Modifier.padding(5.dp))
                OutlinedTextField(
                    value = state.searchSkills,
                    onValueChange = viewModel::onSearchSkillsChanged,
                    placeholder = { Text("Search By Skill...", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderStroke,
                        unfocusedBorderColor = BorderStroke,
                        focusedTextColor = BlueLabel,
                        unfocusedTextColor = BlueLabel
                    )
                )

                Spacer(Modifier.height(15.dp))

                // Matching MAUI: "Search By Name:" + entry
                Text("Search By Name: ", fontSize = 15.sp, modifier = Modifier.padding(5.dp))
                OutlinedTextField(
                    value = state.searchName,
                    onValueChange = viewModel::onSearchNameChanged,
                    placeholder = { Text("Search By Name...", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderStroke,
                        unfocusedBorderColor = BorderStroke,
                        focusedTextColor = BlueLabel,
                        unfocusedTextColor = BlueLabel
                    )
                )

                Spacer(Modifier.height(10.dp))

                // Matching MAUI: "Clear All" + "Apply" buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { viewModel.clearFilter() },
                        modifier = Modifier.padding(10.dp, 5.dp),
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.outlinedButtonColors(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDDDDD))
                    ) {
                        Text("Clear All")
                    }
                    Button(
                        onClick = { viewModel.applyFilter() },
                        modifier = Modifier.padding(10.dp, 5.dp),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text("Apply")
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
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
                    .background(BgColor)
            ) {
                // Matching MAUI: Filter button — Frame White, Shadow, CornerRadius=20
                // Inside: Frame bg=#667685 CornerRadius=12 + "Filter" text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.clickable { viewModel.showFilter() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // MAUI: Frame bg=#667685, CornerRadius=12, Padding=6, sort.png icon
                            Box(
                                modifier = Modifier
                                    .background(FilterBg, RoundedCornerShape(12.dp))
                                    .padding(6.dp)
                            ) {
                                Text(
                                    "\u2195", // sort arrows
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            // MAUI: "Filter" FontSize=15, Bold, #667685
                            Text(
                                "Filter",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = FilterText
                            )
                        }
                    }
                }

                if (state.contractors.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No contractors found", color = Color.Gray, fontSize = 16.sp)
                    }
                } else {
                    // Matching MAUI: ScrollView Orientation="Both"
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 15.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            // Matching MAUI: Header Row — RowDefinitions="40", Border Stroke="Gray"
                            Row {
                                SortableHeader("Name", ColName, "DisplayName", state, viewModel::sortBy)
                                SortableHeader("Email", ColEmail, "Email", state, viewModel::sortBy)
                                SortableHeader("Phone Number", ColPhone, "PhoneNumber", state, viewModel::sortBy)
                                SortableHeader("Date of Birth", ColDob, "DateofBirthString", state, viewModel::sortBy)
                                SortableHeader("Address", ColAddress, "Address", state, viewModel::sortBy)
                                SortableHeader("Work Experience", ColWorkExp, "WorkExperience", state, viewModel::sortBy)
                                SortableHeader("Skills", ColSkills, "Skills", state, viewModel::sortBy)
                                // MAUI: empty header for action column
                                Box(
                                    modifier = Modifier
                                        .width(ColAction)
                                        .height(40.dp)
                                        .border(1.dp, HeaderBorderColor)
                                        .padding(5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Matching MAUI: Data Rows — RowDefinitions="50", Border Stroke="LightGray"
                            state.contractors.forEach { contractor ->
                                Row {
                                    DataCell(contractor.displayName, ColName)
                                    DataCell(contractor.email, ColEmail)
                                    DataCell(contractor.phoneNumber, ColPhone)
                                    DataCell(viewModel.formatDateOfBirth(contractor.dateOfBirth), ColDob)
                                    DataCell(contractor.address, ColAddress)
                                    DataCell(contractor.workExperience, ColWorkExp)
                                    DataCell(contractor.skills, ColSkills)
                                    // Matching MAUI: "View Detail" button
                                    // HeightRequest=30, FontSize=14, Padding=5, bg=White
                                    // BorderColor=Black, BorderWidth=1, TextColor=#667685
                                    // Bold, CornerRadius=10, Centered
                                    Box(
                                        modifier = Modifier
                                            .width(ColAction)
                                            .height(50.dp)
                                            .border(1.dp, CellBorderColor)
                                            .padding(5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = { onViewContractorDetail(contractor.id) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = FilterText
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(5.dp)
                                        ) {
                                            Text(
                                                "View Detail",
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Matching MAUI: Header cell — Border Stroke="Gray", Padding=5
 * Sort icon: ▲ ascending, ▼ descending
 * Height=40
 */
@Composable
private fun SortableHeader(
    title: String,
    width: Dp,
    column: String,
    state: AllContractorsUiState,
    onSort: (String) -> Unit
) {
    val sortIcon = if (state.sortColumn == column) {
        if (state.sortAscending) " \u25B2" else " \u25BC"
    } else ""

    Row(
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .border(1.dp, HeaderBorderColor)
            .clickable { onSort(column) }
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (sortIcon.isNotBlank()) {
            Text(
                text = sortIcon.trim(),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Matching MAUI: Data cell — Border Stroke="LightGray"
 * CellLabelStyle: Padding=5, VerticalTextAlignment=Center
 * Height=50
 */
@Composable
private fun DataCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(50.dp)
            .border(1.dp, CellBorderColor)
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
