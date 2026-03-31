package nz.co.doer.ui.quotation

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// MAUI colors
private val BgColor = Color(0xFFF8F9FA) // MAUI BackgroundColor="#F8F9FA"
private val LabelColor = Color(0xFF667685) // MAUI TextColor="#667685"
private val BorderColor = Color(0xFF667685) // MAUI Stroke="#667685"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendQuoteScreen(
    onBack: () -> Unit,
    viewModel: SendQuoteViewModel = hiltViewModel()
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
            onBack()
        }
    }

    Scaffold(
        topBar = {
            // Matching MAUI: Title="Send Quote"
            TopAppBar(
                title = { Text("Send Quote") },
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
            // Matching MAUI: ScrollView > StackLayout Padding="20,30,20,30" Spacing="25" BackgroundColor="#F8F9FA"
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
                    // Matching MAUI: Quote Amount Section — StackLayout Spacing="10"
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Matching MAUI: 💵 (16sp) + "Quote Amount ($):" (16sp, Bold, #667685)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\uD83D\uDCB5", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Quote Amount (\$):",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LabelColor
                            )
                        }

                        // Matching MAUI: Frame bg=White, HasShadow=True, CornerRadius=12
                        // Border Stroke=#667685, RoundRectangle 12, StrokeThickness=2, HeightRequest=55
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            OutlinedTextField(
                                value = state.quotedAmount,
                                onValueChange = viewModel::updateQuotedAmount,
                                placeholder = { Text("Enter the Quoted Amount (\$)", fontSize = 16.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(55.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = BorderColor,
                                    focusedBorderColor = BorderColor,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                ),
                                textStyle = TextStyle(fontSize = 16.sp)
                            )
                        }
                    }

                    // Matching MAUI: Notes Section — StackLayout Spacing="10"
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Matching MAUI: 📝 (16sp) + "Notes:" (16sp, Bold, #667685)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\uD83D\uDCDD", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Notes:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = LabelColor
                            )
                        }

                        // Matching MAUI: Frame bg=White, HasShadow=True, CornerRadius=12
                        // Border Stroke=#667685, RoundRectangle 12, StrokeThickness=2, HeightRequest=120
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::updateNotes,
                            placeholder = { Text("Enter the Notes", fontSize = 16.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = BorderColor,
                                focusedBorderColor = BorderColor,
                                unfocusedContainerColor = Color.White,
                                focusedContainerColor = Color.White
                            ),
                            textStyle = TextStyle(fontSize = 16.sp)
                        )
                    }

                    // Matching MAUI: Submit Quote Button — StackLayout Margin="0,20,0,30"
                    // Frame bg=Black, CornerRadius=20, WidthRequest=130
                    // Button HeightRequest=40, FontSize=14, Bold, Text="Submit Quote"
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = viewModel::submitQuote,
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
                                text = "Submit Quote",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}
