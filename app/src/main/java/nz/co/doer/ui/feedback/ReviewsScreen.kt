package nz.co.doer.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val BgColor = Color(0xFFF8F9FA)
private val Blue = Color(0xFF4D4BA3) // MAUI {StaticResource Blue}
private val BorderColor = Color(0xFF667685) // MAUI Stroke="#667685"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: ReviewsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reviews", style = MaterialTheme.typography.titleMedium) },
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
            // Matching MAUI: ScrollView > StackLayout Padding="20,30,20,30" Spacing="25"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BgColor)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(25.dp)
                ) {
                    // Manager Feedback Section - only visible when feedback exists (matches MAUI IsVisible="{Binding CurrentReview.HasManagerFeedback}")
                    if (state.hasManagerFeedback) {
                        Card(
                            shape = RoundedCornerShape(15.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(15.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("\uD83D\uDC54", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Manager Feedback:",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Blue
                                    )
                                }
                                // Read-only bordered text field matching MAUI BorderlessEditor with IsReadOnly="True"
                                OutlinedTextField(
                                    value = state.managerFeedback,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = BorderColor,
                                        focusedBorderColor = BorderColor,
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Your Reply Section
                    Card(
                        shape = RoundedCornerShape(15.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("\uD83D\uDCAC", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Your Reply:",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Blue
                                )
                            }
                            // Editable when no existing reply, read-only when already replied
                            // Matches MAUI: IsReadOnly="{Binding ReplyText}" where ReplyText=true means read-only
                            OutlinedTextField(
                                value = state.replyText,
                                onValueChange = viewModel::updateReplyText,
                                placeholder = { Text("Enter Your Reply") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp),
                                shape = RoundedCornerShape(12.dp),
                                readOnly = state.hasExistingReply,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = BorderColor,
                                    focusedBorderColor = BorderColor,
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                )
                            )
                        }
                    }

                    // Submit Button - visible when NO existing reply (matches MAUI IsVisible="{Binding HasReplied}" where HasReplied=true means no reply yet)
                    if (!state.hasExistingReply) {
                        Button(
                            onClick = viewModel::submitReply,
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .width(130.dp)
                                .height(40.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.height(24.dp)
                                )
                            } else {
                                Text(
                                    text = "Submit",
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
}
