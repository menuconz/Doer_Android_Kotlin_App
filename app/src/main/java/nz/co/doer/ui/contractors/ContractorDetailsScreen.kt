package nz.co.doer.ui.contractors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import nz.co.doer.data.remote.dto.FileModelDto

// MAUI colors
private val BgColor = Color(0xFFF8F9FA)
private val BlueLabel = Color(0xFF4D4BA3) // MAUI {StaticResource Blue} / SectionHeader TextColor
private val Gray500 = Color(0xFF6B7280) // MAUI {StaticResource Gray500} / SectionContent TextColor
private val DocNameColor = Color(0xFF337AB7) // MAUI #337ab7
private val NoDocBg = Color(0xFFFFF3E0) // MAUI #FFF3E0
private val BorderStroke = Color(0xFF667685) // MAUI #667685
private val DocBorderColor = Color(0xFFE0E0E0) // MAUI #E0E0E0
private val DocItemBg = Color(0xFFFAFAFA) // MAUI #FAFAFA

// Matching MAUI: ServerUrl for profile documents
private const val PROFILE_DOCS_SERVER_URL = "https://doerapi.doer.nz"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractorDetailsScreen(
    onBack: () -> Unit,
    onViewDocument: (fileUrl: String, isImage: Boolean) -> Unit,
    viewModel: ContractorDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            // Matching MAUI: Title="Contractor Detail"
            TopAppBar(
                title = { Text("Contractor Detail") },
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
        } else if (state.contractor == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Contractor not found", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            val contractor = state.contractor!!

            // Matching MAUI: ScrollView > StackLayout Padding="20,30,20,30" Spacing="20" bg="#F8F9FA"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BgColor)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 30.dp)
            ) {
                // Matching MAUI: Contractor Name — 👤
                DetailCard(
                    emoji = "\uD83D\uDC64",
                    label = "Contractor Name",
                    value = contractor.displayName
                )

                Spacer(Modifier.height(20.dp))

                // Matching MAUI: Date of Birth — 📅, StringFormat='{0:dd/MM/yyyy}'
                DetailCard(
                    emoji = "\uD83D\uDCC5",
                    label = "Date of Birth",
                    value = viewModel.formatDateOfBirth(contractor.dateOfBirth)
                )

                Spacer(Modifier.height(20.dp))

                // Matching MAUI: Email — 📧
                DetailCard(
                    emoji = "\uD83D\uDCE7",
                    label = "Email",
                    value = contractor.email
                )

                Spacer(Modifier.height(20.dp))

                // Matching MAUI: Phone Number — 📱
                DetailCard(
                    emoji = "\uD83D\uDCF1",
                    label = "Phone Number",
                    value = contractor.phoneNumber
                )

                Spacer(Modifier.height(20.dp))

                // Matching MAUI: Work Experience — 💼
                DetailCard(
                    emoji = "\uD83D\uDCBC",
                    label = "Work Experience",
                    value = contractor.workExperience
                )

                Spacer(Modifier.height(20.dp))

                // Matching MAUI: Skills — ⭐
                DetailCard(
                    emoji = "\u2B50",
                    label = "Skills",
                    value = contractor.skills
                )

                Spacer(Modifier.height(20.dp))

                // Matching MAUI: Documents Section — 📄
                DocumentsSection(
                    documents = contractor.documents,
                    onViewDocument = onViewDocument,
                    isImageFile = viewModel::isImageFile
                )

                Spacer(Modifier.height(20.dp))

                // Admin-only: Mark as Employee toggle
                val isAdminUser by viewModel.isAdmin().collectAsState(initial = false)
                if (isAdminUser) {
                    EmployeeToggleCard(
                        isEmployee = contractor.isEmployee,
                        onToggle = viewModel::toggleEmployeeFlag
                    )
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// Admin-only card with a switch to mark this contractor as an Employee.
// Employees gain extra permissions (add subitems, mark subitems Done).
@Composable
private fun EmployeeToggleCard(isEmployee: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👥", fontSize = 18.sp, color = BlueLabel)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Mark as Employee",
                        fontSize = 18.sp,
                        color = BlueLabel,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isEmployee)
                        "This contractor is currently an Employee."
                    else
                        "Toggle on to grant this contractor Employee permissions.",
                    fontSize = 13.sp,
                    color = Gray500
                )
            }
            Switch(
                checked = isEmployee,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Matching MAUI ContractorDetails.xaml:
 * Frame CornerRadius="16" Padding="20" HasShadow="True" BackgroundColor="White" BorderColor="Transparent"
 *   VerticalStackLayout Spacing="8":
 *     StackLayout Horizontal Spacing="8": emoji (FontSize=18, #4d4ba3) + label SectionHeader (FontSize=18, #4d4ba3, Bold)
 *     value SectionContent (FontSize=16, Gray500) Margin="26,0,0,0"
 */
@Composable
private fun DetailCard(emoji: String, label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            // Emoji + label row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp, color = BlueLabel)
                Spacer(Modifier.width(8.dp))
                // SectionHeader: FontSize=18, TextColor=#4d4ba3, Bold, Margin=0,0,0,5
                Text(
                    text = label,
                    fontSize = 18.sp,
                    color = BlueLabel,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            // SectionContent: FontSize=16, TextColor=Gray500, Margin="26,0,0,0"
            Text(
                text = value.ifBlank { "" },
                fontSize = 16.sp,
                color = Gray500,
                modifier = Modifier.padding(start = 26.dp)
            )
        }
    }
}

/**
 * Matching MAUI ContractorDetails.xaml Documents section:
 * Frame CornerRadius="16" Padding="20" HasShadow="True" BackgroundColor="White"
 * 📄 + "Documents" SectionHeader
 * No Documents: Frame bg=#FFF3E0, CornerRadius=10, Padding=15, BorderColor=#667685
 *   📝 + "No Documents found." SectionContent
 * Documents: Frame CornerRadius=10, BorderColor=#E0E0E0, Padding=15, Margin=0,5, bg=#FAFAFA
 *   📋 (#4d4ba3) + Name (#337ab7, Bold, Underline, FontSize=16) + 👁️ (#337ab7)
 */
@Composable
private fun DocumentsSection(
    documents: List<FileModelDto>?,
    onViewDocument: (fileUrl: String, isImage: Boolean) -> Unit,
    isImageFile: (FileModelDto) -> Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            // 📄 + "Documents"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCC4", fontSize = 18.sp, color = BlueLabel)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Documents",
                    fontSize = 18.sp,
                    color = BlueLabel,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            if (documents.isNullOrEmpty()) {
                // Matching MAUI: No Documents Message
                // Frame bg=#FFF3E0, CornerRadius=10, Padding=15, BorderColor=#667685, Margin=26,0,0,0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp)
                        .border(1.dp, BorderStroke, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = NoDocBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDCDD", fontSize = 16.sp) // 📝
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "No Documents found.",
                            fontSize = 16.sp,
                            color = Gray500
                        )
                    }
                }
            } else {
                // Matching MAUI: Documents Collection — Margin="26,0,0,0"
                Column(modifier = Modifier.padding(start = 26.dp)) {
                    documents.forEach { doc ->
                        // Frame CornerRadius=10, BorderColor=#E0E0E0, Padding=15, Margin=0,5, bg=#FAFAFA
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .border(1.dp, DocBorderColor, RoundedCornerShape(10.dp))
                                .clickable {
                                    val fullUrl = PROFILE_DOCS_SERVER_URL + doc.fileUrl
                                    onViewDocument(fullUrl, isImageFile(doc))
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DocItemBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 📋 (#4d4ba3)
                                Text(
                                    "\uD83D\uDCCB",
                                    fontSize = 16.sp,
                                    color = BlueLabel
                                )
                                Spacer(Modifier.width(10.dp))
                                // Name (#337ab7, Bold, Underline, FontSize=16, FillAndExpand)
                                Text(
                                    text = doc.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DocNameColor,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.weight(1f)
                                )
                                // 👁️ (#337ab7)
                                Text(
                                    "\uD83D\uDC41\uFE0F",
                                    fontSize = 16.sp,
                                    color = DocNameColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
