package nz.co.doer.ui.notifications

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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

private val UnreadBg = Color(0xFFFFF9C4)
private val ReadBg = Color.White
private val UnreadDotColor = Color.Red
private val GrayText = Color(0xFF757575)
private val TimeColor = Color(0xFF9E9E9E)
private val DateHeaderColor = Color(0xFF424242)
private val DateHeaderBg = Color(0xFFF8F9FA)
private val TitleColor = Color(0xFF212121)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToShift: (dateStr: String, shiftId: Int) -> Unit,
    onNavigateToMessages: (shiftId: Int) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle navigation to shift
    LaunchedEffect(state.navigateToShift) {
        state.navigateToShift?.let { (date, shiftId) ->
            onNavigateToShift(date, shiftId)
            viewModel.clearNavigation()
        }
    }

    // Handle navigation to messages
    LaunchedEffect(state.navigateToMessages) {
        state.navigateToMessages?.let { shiftId ->
            onNavigateToMessages(shiftId)
            viewModel.clearNavigation()
        }
    }

    // Handle errors
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", style = MaterialTheme.typography.titleMedium) },
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
        } else {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.White)
            ) {
                if (state.groups.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "\uD83D\uDD14", // bell emoji
                            fontSize = 64.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "No notifications yet",
                            fontSize = 18.sp,
                            color = TimeColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pull down to refresh",
                            fontSize = 14.sp,
                            color = Color(0xFFCCCCCC)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        state.groups.forEachIndexed { groupIndex, group ->
                            // Date group header
                            item(key = "header_${groupIndex}_${group.dateLabel}") {
                                Text(
                                    text = group.dateLabel,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DateHeaderColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DateHeaderBg)
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            // Notification items
                            items(
                                count = group.notifications.size,
                                key = { index -> "notif_${groupIndex}_${index}_${group.notifications[index].id}" }
                            ) { index ->
                                val notification = group.notifications[index]
                                NotificationCard(
                                    notification = notification,
                                    formattedTime = viewModel.formatTime(notification.sentAt),
                                    onClick = { viewModel.onNotificationTapped(notification) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: nz.co.doer.data.remote.dto.NotificationsDto,
    formattedTime: String,
    onClick: () -> Unit
) {
    val bgColor = if (notification.isRead) ReadBg else UnreadBg

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Title row with unread dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notification.title,
                    fontSize = 16.sp,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = TitleColor,
                    lineHeight = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!notification.isRead) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(UnreadDotColor)
                    )
                }
            }

            // Body
            if (notification.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.body,
                    fontSize = 14.sp,
                    color = GrayText,
                    lineHeight = 20.sp
                )
            }

            // Timestamp and project name
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedTime,
                    fontSize = 12.sp,
                    color = TimeColor
                )
                if (notification.projectName.isNotBlank()) {
                    Text(
                        text = " \u2022 ",
                        fontSize = 12.sp,
                        color = TimeColor
                    )
                    Text(
                        text = notification.projectName,
                        fontSize = 12.sp,
                        color = TimeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
