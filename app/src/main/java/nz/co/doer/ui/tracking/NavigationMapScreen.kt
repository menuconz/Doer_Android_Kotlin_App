package nz.co.doer.ui.tracking

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

private val Blue = Color(0xFF007AFF)
private val Green = Color(0xFF00C875)
private val RouteBlue = Color(0xFF4285F4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMapScreen(
    onBack: () -> Unit,
    viewModel: NavigationMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Fit camera to show both Doer and site when route first loads
    LaunchedEffect(state.hasRoute, state.hasDoerLocation) {
        if (state.hasDoerLocation && state.siteLatitude != 0.0) {
            val bounds = LatLngBounds.builder()
                .include(LatLng(state.doerLatitude, state.doerLongitude))
                .include(LatLng(state.siteLatitude, state.siteLongitude))
                .build()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds, 120)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.projectName.ifBlank { "Navigation" },
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.siteAddress.isNotBlank()) {
                            Text(
                                state.siteAddress,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Full screen map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    compassEnabled = true
                )
            ) {
                // Route polyline
                if (state.hasRoute && state.routePoints.isNotEmpty()) {
                    Polyline(
                        points = state.routePoints,
                        color = RouteBlue,
                        width = 14f
                    )
                    // Outline for better visibility
                    Polyline(
                        points = state.routePoints,
                        color = Color(0xFF1A73E8),
                        width = 18f
                    )
                }

                // Site marker (destination)
                if (state.siteLatitude != 0.0 && state.siteLongitude != 0.0) {
                    Marker(
                        state = MarkerState(
                            position = LatLng(state.siteLatitude, state.siteLongitude)
                        ),
                        title = state.projectName.ifBlank { "Site" },
                        snippet = state.siteAddress,
                        icon = BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_RED
                        )
                    )
                }

                // Doer marker (current location - moving)
                if (state.hasDoerLocation) {
                    Marker(
                        state = MarkerState(
                            position = LatLng(state.doerLatitude, state.doerLongitude)
                        ),
                        title = "You",
                        snippet = if (state.hasArrived) "Arrived!" else "ETA: ${state.eta}",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (state.hasArrived) BitmapDescriptorFactory.HUE_GREEN
                            else BitmapDescriptorFactory.HUE_AZURE
                        )
                    )
                }
            }

            // Loading overlay
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Blue)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Getting your location...", color = Color.Gray)
                    }
                }
            }

            // Bottom info card
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ETA / Distance card
                if (state.eta.isNotBlank() || state.hasArrived) {
                    ETACard(
                        eta = state.eta,
                        distance = state.distance,
                        hasArrived = state.hasArrived,
                        projectName = state.projectName
                    )
                }

                // Open in Google Maps button
                if (!state.hasArrived && state.siteLatitude != 0.0) {
                    Button(
                        onClick = {
                            val uri = Uri.parse(
                                "google.navigation:q=${state.siteLatitude},${state.siteLongitude}"
                            )
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                val webUri = Uri.parse(
                                    "https://www.google.com/maps/dir/?api=1&destination=${state.siteLatitude},${state.siteLongitude}"
                                )
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Start Turn-by-Turn Navigation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Re-center button
            if (state.hasDoerLocation) {
                IconButton(
                    onClick = {
                        val bounds = LatLngBounds.builder()
                            .include(LatLng(state.doerLatitude, state.doerLongitude))
                            .include(LatLng(state.siteLatitude, state.siteLongitude))
                            .build()
                        // Use move instead of animate for immediate response
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Re-center",
                        tint = Blue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ETACard(
    eta: String,
    distance: String,
    hasArrived: Boolean,
    projectName: String
) {
    val cardColor by animateColorAsState(
        targetValue = if (hasArrived) Green else Color.White,
        label = "cardColor"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        if (hasArrived) {
            // Arrived state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "You've Arrived!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        projectName,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        } else {
            // En route state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ETA circle
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Blue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = eta,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    Text(
                        text = distance,
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                // Live indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Green)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Green
                    )
                }
            }
        }
    }
}
