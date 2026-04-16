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
import androidx.compose.foundation.layout.imePadding
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

// MAUI colors
private val BgColor = Color(0xFFF8F9FA) // MAUI BackgroundColor="#F8F9FA"
private val Blue = Color(0xFF4D4BA3) // MAUI {StaticResource Blue}
private val BorderColor = Color(0xFF667685) // MAUI Stroke="#667685"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFeedbackScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: SendFeedbackViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Success popup with OK button
    state.successMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                viewModel.clearSuccess()
                onSuccess()
            },
            title = { Text("Success") },
            text = { Text(msg) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.clearSuccess()
                    onSuccess()
                }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            // Matching MAUI: Title="Your Feedback"
            TopAppBar(
                title = { Text("Your Feedback") },
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
            // Matching MAUI: ScrollView > StackLayout Padding="20,40,20,50" Spacing="30" BackgroundColor="#F8F9FA"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .background(BgColor)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, top = 40.dp, end = 20.dp, bottom = 50.dp),
                    verticalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    // Matching MAUI: Frame BackgroundColor="White" HasShadow="True" CornerRadius="15" Padding="20"
                    Card(
                        shape = RoundedCornerShape(15.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            // Matching MAUI: 💭 (16sp) + "Share Your Experience" (16sp, Bold, Blue)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("\uD83D\uDCAD", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Share Your Experience",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Blue
                                )
                            }

                            // Matching MAUI: Border Stroke="#667685" StrokeShape="RoundRectangle 12"
                            // StrokeThickness="2" HeightRequest="100"
                            // BorderlessEditor Placeholder="Enter Feedback" FontSize="16"
                            OutlinedTextField(
                                value = state.feedbackText,
                                onValueChange = viewModel::updateFeedback,
                                placeholder = { Text("Enter Feedback", fontSize = 16.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = BorderColor,
                                    focusedBorderColor = BorderColor,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp)
                            )
                        }
                    }

                    // Matching MAUI: Frame BackgroundColor="Black" CornerRadius="20" WidthRequest="130"
                    // Button HeightRequest="40" FontSize="14" Bold Text="Submit" TextColor="White"
                    Button(
                        onClick = viewModel::submitFeedback,
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
