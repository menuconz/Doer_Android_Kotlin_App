package nz.co.doer.ui.leads

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import nz.co.doer.data.remote.dto.ContractType

// MAUI colors
private val BgColor = Color(0xFFF8F9FA)
private val BlueLabel = Color(0xFF4D4BA3)  // MAUI {StaticResource Blue}
private val BorderStroke = Color(0xFF667685)
private val LocationBg = Color(0xFFE0DEDE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewLeadScreen(
    onBack: () -> Unit,
    viewModel: ViewLeadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Handle one-shot events (messages and navigation)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ViewLeadEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is ViewLeadEvent.NavigateBack -> {
                    onBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lead Detail", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.lead == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.errorMessage ?: "Lead not found",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            val lead = state.lead!!

            // MAUI: ScrollView > StackLayout Padding="20" Spacing="15"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .background(BgColor)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // ── Name Section ──
                // MAUI: emoji "📝" + "Name:" label + Frame/Border entry (ReadOnly, height 55)
                FieldLabel("\uD83D\uDCDD", "Name:")
                Spacer(modifier = Modifier.height(10.dp))
                ReadOnlyEntryField(
                    text = lead.name,
                    placeholder = "Enter Project Name",
                    height = 55
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Job Description Section ──
                // MAUI: emoji "📝" + "Job Description:" label + Frame/Border editor (ReadOnly, height 120)
                FieldLabel("\uD83D\uDCDD", "Job Description:")
                Spacer(modifier = Modifier.height(10.dp))
                ReadOnlyEntryField(
                    text = lead.jobDescription,
                    placeholder = "Enter the Job Description",
                    height = 120
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Owner Section ──
                // MAUI: Frame CornerRadius=5, Grid with Label "OWNER" (UPPERCASE, Bold, Blue) + Picker (disabled)
                PickerField(
                    label = "Owner",
                    value = lead.ownerName.ifBlank { "Select Owner" }
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Contract Type Section ──
                // MAUI: Frame CornerRadius=5, Grid with Label "CONTRACT TYPE" (UPPERCASE, Bold, Blue) + Picker (disabled)
                PickerField(
                    label = "Contract Type",
                    value = contractTypeDisplayName(lead.contractType)
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Location Section ──
                // MAUI: emoji "📍" + "Location:" label + Frame (bg #e0dede) with Border entry (ReadOnly, opacity 0.7)
                FieldLabel("\uD83D\uDCCD", "Location:")
                Spacer(modifier = Modifier.height(10.dp))
                ReadOnlyEntryField(
                    text = lead.location,
                    placeholder = "Enter Address",
                    height = 55,
                    backgroundColor = LocationBg,
                    textAlpha = 0.7f
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Cost From Quote Section ──
                // MAUI: emoji "💵" + "Cost From Quote ($):" label + Frame/Border entry (ReadOnly, height 55)
                FieldLabel("\uD83D\uDCB5", "Cost From Quote ($):")
                Spacer(modifier = Modifier.height(10.dp))
                ReadOnlyEntryField(
                    text = lead.costFromQuote?.let { "$it" } ?: "",
                    placeholder = "Enter Quote Amount",
                    height = 55
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Client Name Section ──
                // MAUI: emoji "👤" + "Client Name:" label + Frame/Border entry (ReadOnly, height 55)
                FieldLabel("\uD83D\uDC64", "Client Name:")
                Spacer(modifier = Modifier.height(10.dp))
                ReadOnlyEntryField(
                    text = lead.clientName,
                    placeholder = "Enter Client Name",
                    height = 55
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Client Email Section ──
                // MAUI: emoji "📧" + "Client Email:" label + Frame/Border entry (ReadOnly, height 55)
                FieldLabel("\uD83D\uDCE7", "Client Email:")
                Spacer(modifier = Modifier.height(10.dp))
                ReadOnlyEntryField(
                    text = lead.clientEmail,
                    placeholder = "Enter Client Email",
                    height = 55
                )

                Spacer(modifier = Modifier.height(15.dp))

                // ── Status Section ──
                // MAUI: emoji "📊" + "Status:" label + Frame (bg=StatusColor, CornerRadius=12, Padding=10) with Label (White, Bold, centered)
                FieldLabel("\uD83D\uDCCA", "Status:")
                Spacer(modifier = Modifier.height(10.dp))
                StatusFrame(
                    statusName = lead.statusName,
                    statusColor = Color(NewLeadsViewModel.getLeadStatusColor(lead.statusId))
                )

                // NOTE: Action buttons (Send Quote, Close Deal, Move to Contacts, Update Lead)
                // are commented out in MAUI ViewLead.xaml, so not shown here either.
            }
        }
    }
}

/**
 * MAUI field label: emoji + bold blue text
 * StackLayout Orientation="Horizontal" Spacing="5"
 */
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

/**
 * MAUI read-only entry field:
 * Frame (White/custom bg, HasShadow, CornerRadius=12) >
 *   Border (Stroke=#667685, StrokeThickness=2, RoundRectangle 12, Height=55/120) >
 *     BorderlessEntry/Editor (IsReadOnly, FontSize=16)
 */
@Composable
private fun ReadOnlyEntryField(
    text: String,
    placeholder: String,
    height: Int,
    backgroundColor: Color = Color.White,
    textAlpha: Float = 1f
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .border(2.dp, BorderStroke, RoundedCornerShape(12.dp))
                .padding(horizontal = 15.dp),
            contentAlignment = if (height > 60) Alignment.TopStart else Alignment.CenterStart
        ) {
            val displayText = text.ifBlank { placeholder }
            val textColor = if (text.isBlank()) Color.Gray else Color.Black
            Text(
                text = displayText,
                fontSize = 16.sp,
                color = textColor,
                modifier = Modifier
                    .alpha(if (text.isBlank()) 0.5f else textAlpha)
                    .then(
                        if (height > 60) Modifier.padding(vertical = 10.dp) else Modifier
                    )
            )
        }
    }
}

/**
 * MAUI Owner/ContractType picker field:
 * Frame (CornerRadius=5, HasShadow, Padding=5) >
 *   Grid (2 columns) >
 *     Label (UPPERCASE, Bold, FontSize=16, Blue, Margin="0,10,0,0") +
 *     Picker (IsEnabled=False, Bold, FontSize=16, Blue)
 */
@Composable
private fun PickerField(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MAUI: Label Text="Owner" TextTransform="Uppercase" FontAttributes="Bold" FontSize="16" TextColor=Blue Margin="0,10,0,0"
            Text(
                text = label.uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BlueLabel,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp)
            )
            // MAUI: Picker IsEnabled="False" FontAttributes="Bold" FontSize="16" TextColor=Blue
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BlueLabel,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * MAUI Status frame:
 * Frame BackgroundColor="{Binding StatusColor}" HasShadow CornerRadius=12 Padding=10 >
 *   Label TextColor="White" FontAttributes="Bold" FontSize=16 centered
 */
@Composable
private fun StatusFrame(statusName: String, statusColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor),
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
                text = statusName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Convert contract type int to display name matching MAUI enum names.
 */
private fun contractTypeDisplayName(contractType: Int?): String {
    return when (contractType) {
        1 -> "ToBeConfirmed"
        2 -> "FullContract"
        3 -> "SupplyPlaceAndFinish"
        4 -> "PlaceAndFinish"
        5 -> "LabourSupply"
        6 -> "BoxPlaceAndFinish"
        7 -> "Remedial"
        8 -> "SupplyPlaceFinishAndCut"
        9 -> "PlaceFinishAndCut"
        10 -> "OtherServices"
        11 -> "Meetings"
        else -> "Select Contract Type"
    }
}
