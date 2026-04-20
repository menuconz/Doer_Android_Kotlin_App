package nz.co.doer.ui.profile

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// MAUI colors
private val BgColor = Color(0xFFF8F9FA)
private val BlueLabel = Color(0xFF4D4BA3) // MAUI {StaticResource Blue}
private val DocNameColor = Color(0xFF337AB7) // MAUI #337ab7
private val NoDocBg = Color(0xFFFFF3E0) // MAUI #FFF3E0
private val BorderStroke = Color(0xFF667685)
private val DocItemBg = Color(0xFFFAFAFA)
private const val PROFILE_DOCS_SERVER_URL = "https://doerapi.doer.nz"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onDeletedAccount: () -> Unit,
    onViewDocument: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: ProfileViewModel = hiltViewModel(),
    successMessage: String? = null,
    onSuccessMessageShown: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            onSuccessMessageShown()
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onDeletedAccount()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // MAUI: Confirmation dialog for delete
    // "Are you sure you want to permanently delete your account?" with Yes/No
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmation") },
            text = { Text("Are you sure you want to permanently delete your account?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteAccount()
                }) { Text("Yes", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("No") }
            }
        )
    }

    // MAUI: Delete success alert with "Account Deleted" title, long message, OK button
    state.deleteSuccessMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.onDeleteSuccessDismissed() },
            title = { Text("Account Deleted") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteSuccessDismissed() }) {
                    Text("OK")
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (state.isLoading || state.isDeleting) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val user = state.user
            if (user == null) {
                // No data yet — render nothing; snackbar still available below.
            } else {
            // MAUI: ScrollView > StackLayout Padding="20,30,20,30" Spacing="20" BackgroundColor="#F8F9FA"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgColor)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 30.dp)
            ) {
                // ── Name Section ── (always visible)
                // MAUI: 👤 + "Name" + FullName (= DisplayName)
                ProfileInfoCard(
                    emoji = "\uD83D\uDC64",
                    label = "Name",
                    value = user.displayName
                )

                Spacer(Modifier.height(20.dp))

                // ── Address Section ── (IsCustomer only, 🏠)
                if (state.isCustomer) {
                    ProfileInfoCard(
                        emoji = "\uD83C\uDFE0",
                        label = "Address",
                        value = user.address
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // ── Date of Birth Section ── (IsVisible = !IsCustomer)
                if (!state.isCustomer) {
                    ProfileInfoCard(
                        emoji = "\uD83D\uDCC5",
                        label = "Date of Birth",
                        value = formatDob(user.dateOfBirth)
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // ── Email Section ── (always visible)
                ProfileInfoCard(
                    emoji = "\uD83D\uDCE7",
                    label = "Email",
                    value = user.email
                )

                Spacer(Modifier.height(20.dp))

                // ── Phone Number Section ── (always visible)
                ProfileInfoCard(
                    emoji = "\uD83D\uDCF1",
                    label = "Phone Number",
                    value = user.phoneNumber
                )

                Spacer(Modifier.height(20.dp))

                // ── Address Section ── (IsCaregiver only, 📍)
                if (state.isCaregiver) {
                    ProfileInfoCard(
                        emoji = "\uD83D\uDCCD",
                        label = "Address",
                        value = user.address
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // ── Work Experience Section ── (IsCaregiver only)
                if (state.isCaregiver) {
                    ProfileInfoCard(
                        emoji = "\uD83D\uDCBC",
                        label = "Work Experience",
                        value = user.workExperience
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // ── Skills Section ── (IsCaregiver only)
                if (state.isCaregiver) {
                    ProfileInfoCard(
                        emoji = "\u2B50",
                        label = "Skills",
                        value = user.skills
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // ── Documents Section ── (IsCaregiver only)
                if (state.isCaregiver) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            // MAUI: 📄 + "Documents" label
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("\uD83D\uDCC4", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Documents",
                                    color = BlueLabel,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            val docs = user.documents
                            if (docs.isNullOrEmpty()) {
                                // MAUI: No Documents Message
                                // Frame bg=#FFF3E0, CornerRadius=10, Padding=15, BorderColor=#667685
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, BorderStroke, RoundedCornerShape(10.dp)),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = NoDocBg),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(15.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("\uD83D\uDCDD", fontSize = 16.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "No Documents found.",
                                            fontSize = 16.sp,
                                            color = BlueLabel
                                        )
                                    }
                                }
                            } else {
                                // MAUI: Documents Collection
                                docs.forEach { doc ->
                                    // Frame CornerRadius=10, BorderColor=#667685, Padding=15, Margin=0,5, bg=#FAFAFA
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .border(1.dp, BorderStroke, RoundedCornerShape(10.dp))
                                            .clickable {
                                                val isImage = doc.type.contains("image", true) ||
                                                        doc.name.endsWith(".jpg", true) ||
                                                        doc.name.endsWith(".jpeg", true) ||
                                                        doc.name.endsWith(".png", true)
                                                val fullUrl = PROFILE_DOCS_SERVER_URL + doc.fileUrl
                                                onViewDocument(fullUrl, isImage)
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = DocItemBg),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(15.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // MAUI: 📋 (Blue)
                                            Text(
                                                "\uD83D\uDCCB",
                                                fontSize = 16.sp,
                                                color = BlueLabel
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            // MAUI: Name (#337ab7, Bold, Underline, FontSize=16)
                                            Text(
                                                text = doc.name,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DocNameColor,
                                                textDecoration = TextDecoration.Underline,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // MAUI: 👁️ (#337ab7)
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
                    Spacer(Modifier.height(20.dp))
                }

                // ── Action Buttons Section ──
                // MAUI: StackLayout Spacing="15" Margin="0,20,0,30"
                Spacer(Modifier.height(20.dp))

                // MAUI: "✏️ Edit Profile" — Frame bg=Black, CornerRadius=20, WidthRequest=180, height=40, FontSize=14
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onEditProfile,
                        modifier = Modifier
                            .width(180.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            "\u270F\uFE0F Edit Profile",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(15.dp))

                // MAUI: "🗑️ Delete Account" — same style
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .width(180.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            "\uD83D\uDDD1\uFE0F Delete Account",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(30.dp))
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
 * MAUI Profile info card:
 * Frame CornerRadius="15" Padding="20" HasShadow="True" BackgroundColor="White" BorderColor="Transparent"
 *   StackLayout Spacing="8":
 *     StackLayout Horizontal Spacing="8": emoji (FontSize=18) + label (TextColor=Blue)
 *     Label Text=value Style=SectionContent (FontSize=16, TextColor=Blue)
 */
@Composable
private fun ProfileInfoCard(emoji: String, label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            // Emoji + label row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    color = BlueLabel,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            // Value — MAUI SectionContent: FontSize=16, TextColor=Blue
            Text(
                text = value.ifBlank { "" },
                fontSize = 16.sp,
                color = BlueLabel
            )
        }
    }
}

// Matching MAUI: StringFormat='{0:dd/MM/yyyy}'
private fun formatDob(dob: String?): String {
    if (dob.isNullOrBlank()) return ""
    return try {
        val parsed = java.time.LocalDate.parse(dob.substringBefore("T"))
        parsed.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (e: Exception) {
        dob
    }
}
