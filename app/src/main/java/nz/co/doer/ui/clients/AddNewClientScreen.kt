package nz.co.doer.ui.clients

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

// MAUI colors
private val BgColor = Color(0xFFF8F9FA)
private val BlueLabel = Color(0xFF4D4BA3) // MAUI {StaticResource Blue}
private val BorderStroke = Color(0xFF667685)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewClientScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: AddNewClientViewModel = hiltViewModel()
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
            // MAUI: Title = "New Client"
            TopAppBar(
                title = { Text("New Client", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
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
            // ── Client Name Section ──
            // MAUI: emoji "👤" + "Client Name:" label + Frame/Border entry (height 55)
            FieldLabel("\uD83D\uDC64", "Client Name:")
            Spacer(modifier = Modifier.height(10.dp))
            BorderedEntryField(
                value = state.name,
                onValueChange = viewModel::updateName,
                placeholder = "Enter Client Name",
                height = 55
            )

            Spacer(modifier = Modifier.height(15.dp))

            // ── Client Email Section ──
            // MAUI: emoji "📧" + "Client Email:" label + Frame/Border entry (height 55)
            FieldLabel("\uD83D\uDCE7", "Client Email:")
            Spacer(modifier = Modifier.height(10.dp))
            BorderedEntryField(
                value = state.email,
                onValueChange = viewModel::updateEmail,
                placeholder = "Enter Client Email",
                height = 55
            )

            Spacer(modifier = Modifier.height(20.dp))

            // MAUI: "Add Client" button centered, 150x40, Black, CornerRadius=20, FontSize=14
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = viewModel::addClient,
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .width(150.dp)
                        .height(40.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.height(20.dp).width(20.dp)
                        )
                    } else {
                        Text(
                            text = "Add Client",
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
 * MAUI bordered entry field:
 * Frame (White bg, HasShadow, CornerRadius=12) >
 *   Border (Stroke=#667685, StrokeThickness=2, RoundRectangle 12, Height) >
 *     BorderlessEntry (FontSize=16)
 */
@Composable
private fun BorderedEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    height: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .border(2.dp, BorderStroke, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, fontSize = 16.sp, color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )
        }
    }
}
