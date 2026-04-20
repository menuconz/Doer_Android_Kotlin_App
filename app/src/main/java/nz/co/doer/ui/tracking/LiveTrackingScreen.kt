package nz.co.doer.ui.tracking

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import nz.co.doer.data.remote.dto.DoerTrackingState

private val Blue = Color(0xFF007AFF)
private val Gray500 = Color(0xFF6B7280)
private val BgColor = Color(0xFFF8F9FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrackingScreen(
    viewModel: LiveTrackingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Start polling when screen is visible, stop when leaving
    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose {
            viewModel.stopPolling()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading && state.activeDoers.isEmpty()) {
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
                // Refresh action + "last updated" row (replaces previous TopAppBar actions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.lastUpdated.isNotBlank()) {
                        Text(
                            "Updated: ${state.lastUpdated}",
                            color = Gray500,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Blue)
                    }
                }
                // Stats Bar
                StatsBar(
                    totalActive = state.totalActiveDoers,
                    enRoute = state.enRouteCount,
                    onSite = state.onSiteCount
                )

                // Map Section (takes 45% of screen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                ) {
                    DoerMap(doers = state.activeDoers, routePoints = state.selectedDoerRoute)

                    // Polling indicator
                    if (state.isPolling) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00C875))
                        )
                    }
                }

                // Active Doers List (takes 55% of screen)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                ) {
                    // Section header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Active Doers",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Blue
                        )
                        Text(
                            "${state.totalActiveDoers} active",
                            fontSize = 13.sp,
                            color = Gray500
                        )
                    }

                    if (state.activeDoers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color(0xFFD1D5DB)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No active Doers right now",
                                    fontSize = 16.sp,
                                    color = Gray500
                                )
                                Text(
                                    "Doers will appear here when they clock in",
                                    fontSize = 13.sp,
                                    color = Color(0xFFD1D5DB)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            )
                        ) {
                            items(
                                items = state.activeDoers,
                                key = { it.userId }
                            ) { doer ->
                                DoerCard(
                                    doer = doer,
                                    isSelected = state.selectedDoerUserId == doer.userId,
                                    onClick = {
                                        if (state.selectedDoerUserId == doer.userId) {
                                            viewModel.clearSelectedDoer()
                                        } else {
                                            viewModel.selectDoer(doer)
                                        }
                                    }
                                )
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

@Composable
private fun StatsBar(totalActive: Int, enRoute: Int, onSite: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatChip(label = "Active", count = totalActive, color = Color(0xFF007AFF))
        StatChip(label = "En Route", count = enRoute, color = Color(0xFF3B82F6))
        StatChip(label = "On Site", count = onSite, color = Color(0xFF00C875))
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Gray500
        )
    }
}

@Composable
private fun DoerMap(doers: List<ActiveDoerUi>, routePoints: List<LatLng> = emptyList()) {
    // Default to New Zealand center
    val defaultPosition = LatLng(-36.8485, 174.7633) // Auckland
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 10f)
    }

    // Auto-fit bounds when doers change
    LaunchedEffect(doers) {
        if (doers.isNotEmpty()) {
            val validDoers = doers.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            if (validDoers.size == 1) {
                val single = validDoers.first()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(single.latitude, single.longitude), 14f
                    )
                )
            } else if (validDoers.size > 1) {
                val boundsBuilder = LatLngBounds.builder()
                validDoers.forEach {
                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80)
                )
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        // Draw route polyline for selected Doer
        if (routePoints.isNotEmpty()) {
            Polyline(
                points = routePoints,
                color = Color(0xFF4285F4),
                width = 12f
            )
        }

        doers.filter { it.latitude != 0.0 && it.longitude != 0.0 }.forEach { doer ->
            val hue = when (doer.trackingState) {
                DoerTrackingState.EN_ROUTE -> BitmapDescriptorFactory.HUE_ORANGE
                DoerTrackingState.ARRIVED -> BitmapDescriptorFactory.HUE_GREEN
                DoerTrackingState.ON_SITE -> BitmapDescriptorFactory.HUE_GREEN
                DoerTrackingState.LEAVING -> BitmapDescriptorFactory.HUE_YELLOW
                DoerTrackingState.CLOCKED_IN -> BitmapDescriptorFactory.HUE_BLUE
                else -> BitmapDescriptorFactory.HUE_RED
            }

            val markerTitle = buildString {
                append(doer.displayName.ifBlank { "Doer ${doer.userId.take(6)}" })
                if (doer.projectName.isNotBlank()) append(" - ${doer.projectName}")
            }
            val snippet = buildString {
                append(doer.statusLabel)
                if (doer.timeOnSite.isNotBlank()) append(" | ${doer.timeOnSite}")
                if (doer.eta != null) append(" | ETA: ${doer.eta}")
            }

            Marker(
                state = MarkerState(position = LatLng(doer.latitude, doer.longitude)),
                title = markerTitle,
                snippet = snippet,
                icon = BitmapDescriptorFactory.defaultMarker(hue)
            )
        }
    }
}

@Composable
private fun DoerCard(doer: ActiveDoerUi, isSelected: Boolean = false, onClick: () -> Unit = {}) {
    val statusColor by animateColorAsState(
        targetValue = Color(doer.markerColor),
        label = "statusColor"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doer.displayName.ifBlank { "Doer ${doer.userId.take(8)}" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (doer.projectName.isNotBlank()) {
                        Text(
                            text = doer.projectName,
                            fontSize = 12.sp,
                            color = Gray500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (doer.siteName.isNotBlank()) {
                        if (doer.projectName.isNotBlank()) {
                            Text("\u2022", fontSize = 10.sp, color = Gray500)
                        }
                        Text(
                            text = doer.siteName,
                            fontSize = 11.sp,
                            color = Gray500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                // ETA row for en-route doers
                if (doer.trackingState == DoerTrackingState.EN_ROUTE && doer.eta != null) {
                    Text(
                        text = "ETA: ${doer.eta}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF3B82F6)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right side: status badge + time
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = doer.statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                if (doer.timeOnSite.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = doer.timeOnSite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                }
            }
        }
    }
}
