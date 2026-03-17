package nz.co.doer.ui.quotation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
private val SearchBorderColor = Color(0xFFECECEC) // MAUI Stroke="#ececec"

// MAUI Column widths
private val ColName = 200.dp
private val ColEmail = 180.dp
private val ColPhone = 150.dp
private val ColAddress = 200.dp
private val ColDate = 200.dp
private val ColNotes = 200.dp
private val ColAmount = 150.dp
private val ColSkills = 200.dp
private val ColAction = 100.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewQuotationsScreen(
    onBack: () -> Unit,
    viewModel: ViewQuotationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isHired) {
        if (state.isHired) onBack()
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
            viewModel.clearSuccess()
        }
    }

    // Matching MAUI: FilterQuotationsPopupView as ModalBottomSheet
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
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("X")
                    }
                }

                Spacer(Modifier.height(15.dp))

                // Matching MAUI: "Search By Skill:" + entry Border Stroke=#667685, RoundRectangle 10, HeightRequest=50
                Text("Search By Skill: ", fontSize = 15.sp, modifier = Modifier.padding(5.dp))
                OutlinedTextField(
                    value = state.searchSkills,
                    onValueChange = viewModel::onSearchSkillsChanged,
                    placeholder = { Text("Search contractors By Skill...", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderStroke,
                        unfocusedBorderColor = BorderStroke,
                        focusedTextColor = Color.Blue,
                        unfocusedTextColor = Color.Blue
                    )
                )

                Spacer(Modifier.height(15.dp))

                // Matching MAUI: "Search By Name:" + entry
                Text("Search By Name: ", fontSize = 15.sp, modifier = Modifier.padding(5.dp))
                OutlinedTextField(
                    value = state.searchName,
                    onValueChange = viewModel::onSearchNameChanged,
                    placeholder = { Text("Search contractors By Name...", color = Color.Gray) },
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent)
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

    Scaffold(
        topBar = {
            // Matching MAUI: Title="Quotations"
            TopAppBar(
                title = { Text("Quotations") },
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Matching MAUI: Shell.TitleView search bar — location search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .border(4.dp, SearchBorderColor, RoundedCornerShape(8.dp))
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\uD83D\uDD0D", fontSize = 16.sp) // 🔍 search icon
                    OutlinedTextField(
                        value = state.searchAddress,
                        onValueChange = viewModel::onSearchAddressChanged,
                        placeholder = {
                            Text(
                                "Type a location to find nearby contractors...",
                                color = BlueLabel,
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = BlueLabel,
                            unfocusedTextColor = BlueLabel,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    if (state.searchAddress.isNotEmpty()) {
                        Text(
                            "\u2716", // ✖ clear icon
                            fontSize = 16.sp,
                            modifier = Modifier.clickable { viewModel.clearSearchLocation() }
                        )
                    }
                }

                // Matching MAUI: Google Places autocomplete list
                if (state.showPlaceList && state.placeList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(vertical = 4.dp)
                    ) {
                        state.placeList.forEach { prediction ->
                            Text(
                                text = prediction.description,
                                fontSize = 15.sp,
                                color = BlueLabel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectPlace(prediction) }
                                    .padding(5.dp)
                            )
                        }
                    }
                }

                // Matching MAUI: Filter button — Frame White, Shadow, CornerRadius=20
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
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

                if (state.quotations.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No quotations found", color = Color.Gray, fontSize = 16.sp)
                    }
                } else {
                    // Matching MAUI: ScrollView Orientation="Both" Margin="15,0,15,0"
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
                                SortableHeader("Contractor Name", ColName, "ContractorName", state, viewModel::sortBy)
                                SortableHeader("Contractor Email", ColEmail, "ContractorEmail", state, viewModel::sortBy)
                                SortableHeader("Contractor Phone", ColPhone, "ContractorPhone", state, viewModel::sortBy)
                                SortableHeader("Contractor Address", ColAddress, "ContractorAddress", state, viewModel::sortBy)
                                SortableHeader("Quotation Date", ColDate, "QuotedDate", state, viewModel::sortBy)
                                SortableHeader("Quotation Message", ColNotes, "Notes", state, viewModel::sortBy)
                                SortableHeader("Quoted Price (\$)", ColAmount, "QuotedAmount", state, viewModel::sortBy)
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
                            state.quotations.forEach { quotation ->
                                Row {
                                    DataCell(quotation.contractorName, ColName)
                                    DataCell(quotation.contractorEmail, ColEmail)
                                    DataCell(quotation.contractorPhone, ColPhone)
                                    DataCell(quotation.contractorAddress, ColAddress)
                                    DataCell(viewModel.formatDate(quotation.quotedDate), ColDate)
                                    DataCell(quotation.notes, ColNotes)
                                    // Matching MAUI: StringFormat='${0:0.00}'
                                    DataCell("\$${String.format("%.2f", quotation.quotedAmount)}", ColAmount)
                                    DataCell(quotation.skills, ColSkills)
                                    // Matching MAUI: Hire button
                                    // VerticalOptions=Center, FontSize=14, CornerRadius=2, Bold,
                                    // HeightRequest=35, BorderWidth=1, TextColor=#667685,
                                    // BorderColor=Black, BackgroundColor=White, Text="Hire"
                                    Box(
                                        modifier = Modifier
                                            .width(ColAction)
                                            .height(50.dp)
                                            .border(1.dp, CellBorderColor)
                                            .padding(5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = { viewModel.hireContractor(quotation) },
                                            enabled = !state.isHiring,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = FilterText
                                            ),
                                            shape = RoundedCornerShape(2.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                                            modifier = Modifier.height(35.dp),
                                            contentPadding = PaddingValues(5.dp)
                                        ) {
                                            Text(
                                                "Hire",
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

        // Hiring overlay
        if (state.isHiring) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
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
    state: ViewQuotationsUiState,
    onSort: (String) -> Unit
) {
    val sortIcon = if (state.sortColumn == column) {
        if (state.sortAscending) " \u25B2" else " \u25BC"
    } else ""

    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .border(1.dp, HeaderBorderColor)
            .clickable { onSort(column) }
            .padding(5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "$title$sortIcon",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
