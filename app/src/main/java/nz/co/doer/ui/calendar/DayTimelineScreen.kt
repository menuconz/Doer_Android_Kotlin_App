package nz.co.doer.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DayTimelineScreen(
    onBack: () -> Unit,
    onShiftTapped: (String, Int) -> Unit = { _, _ -> },
    onViewAll: (String) -> Unit = {},
    onAddShift: (String, Int?) -> Unit = { _, _ -> },
    viewModel: DayTimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Refresh data when returning from DayDetailView
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.pageTitle, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal date picker
            val selectedIndex = state.calendarDays.indexOfFirst { it.isSelected }.coerceAtLeast(0)
            val lazyListState = rememberLazyListState()

            LaunchedEffect(selectedIndex) {
                lazyListState.animateScrollToItem(selectedIndex)
            }

            // Lazy load future dates when scrolling near end
            LaunchedEffect(lazyListState) {
                snapshotFlow {
                    val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val totalItems = lazyListState.layoutInfo.totalItemsCount
                    Pair(lastVisible, totalItems)
                }.distinctUntilChanged().collect { (lastVisible, totalItems) ->
                    if (totalItems > 0 && lastVisible >= totalItems - 5) {
                        viewModel.loadMoreFutureDates()
                    }
                }
            }

            // Lazy load past dates when scrolling near start
            LaunchedEffect(lazyListState) {
                snapshotFlow {
                    lazyListState.firstVisibleItemIndex
                }.distinctUntilChanged().collect { firstVisible ->
                    if (firstVisible <= 5) {
                        viewModel.loadMorePastDates()
                    }
                }
            }

            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F9FA))
                    .padding(vertical = 8.dp)
            ) {
                items(state.calendarDays, key = { it.date.toString() }) { dayItem ->
                    DayChip(
                        item = dayItem,
                        onClick = { viewModel.selectDate(dayItem.date) },
                        onLongClick = if (state.isManager) {
                            { onAddShift(dayItem.date.toString(), null) }
                        } else null
                    )
                }
            }

            HorizontalDivider()

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Sticky: Total jobs header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Jobs: ${state.totalJobs}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    if (state.totalJobs > 0) {
                        Text(
                            text = "View All",
                            fontSize = 14.sp,
                            color = Color(0xFF007AFF),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { onViewAll(state.selectedDate.toString()) }
                                .padding(8.dp, 4.dp)
                        )
                    }
                }

                // Sticky: All day jobs
                if (state.allDayJobs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8F9FA))
                            .padding(2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "all-day",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                        // 2 items per row grid
                        Column(modifier = Modifier.weight(1f)) {
                            state.allDayJobs.chunked(2).forEach { rowItems ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    rowItems.forEach { job ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 2.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(Color(job.contractColor))
                                                .clickable { onShiftTapped(state.selectedDate.toString(), job.shift.id) }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = job.projectName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = job.statusMessage,
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                            }
                                        }
                                    }
                                    // Fill empty space if odd number of items
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.totalJobs == 0) {
                    Text(
                        text = "No job scheduled for this day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }

                // Scrollable: 24-hour timeline
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 24-hour timeline
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Hour lines
                        Column {
                            for (hour in 0..23) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .border(0.5.dp, Color(0xFFE8E8E8)),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = formatHourLabel(hour),
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier
                                            .width(55.dp)
                                            .padding(start = 4.dp, top = 2.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(80.dp)
                                            .then(
                                                if (state.isManager) {
                                                    Modifier.combinedClickable(
                                                        onClick = {},
                                                        onLongClick = { onAddShift(state.selectedDate.toString(), hour) }
                                                    )
                                                } else Modifier
                                            )
                                    )
                                }
                            }
                        }

                        // Timeline blocks overlay
                        state.timelineBlocks.forEach { block ->
                            val topOffset = (block.startHour * 80).dp
                            val blockHeight = (block.durationHours * 80).dp.coerceAtLeast(40.dp)
                            val totalCols = block.totalColumns
                            val colFraction = if (totalCols > 0) 1f / totalCols else 1f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 55.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    if (block.columnIndex > 0) {
                                        Spacer(modifier = Modifier.weight(block.columnIndex.toFloat()))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = topOffset, start = 2.dp, end = 2.dp)
                                            .height(blockHeight)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(block.contractColor).copy(alpha = 0.15f))
                                            .border(2.dp, Color(block.contractColor), RoundedCornerShape(6.dp))
                                            .combinedClickable(
                                                onClick = { onShiftTapped(state.selectedDate.toString(), block.shift.id) },
                                                onLongClick = if (state.isManager) {
                                                    { onAddShift(state.selectedDate.toString(), block.startHour.toInt()) }
                                                } else null
                                            )
                                            .padding(6.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = block.projectName,
                                                fontSize = 11.sp,
                                                color = Color(0xFF333333),
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (block.address.isNotBlank()) {
                                                Text(
                                                    text = block.address,
                                                    fontSize = 9.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    val remaining = totalCols - block.columnIndex - 1
                                    if (remaining > 0) {
                                        Spacer(modifier = Modifier.weight(remaining.toFloat()))
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChip(item: CalendarDayItem, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val bgColor = when {
        item.isSelected -> Color(0xFFFF3B30)
        item.isToday -> Color(0xFF007AFF)
        else -> Color.Transparent
    }
    val textColor = when {
        item.isSelected || item.isToday -> Color.White
        else -> Color(0xFF333333)
    }

    Column(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 6.dp)
            .width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = item.dayLetter, fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.dayNumber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = item.monthName, fontSize = 9.sp, color = textColor.copy(alpha = 0.6f))
    }
}

@Composable
private fun ShiftCard(item: DayShiftItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(item.contractColor))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.projectName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.address.isNotBlank()) {
                Text(
                    text = item.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = item.durationText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        Text(
            text = item.statusMessage,
            fontSize = 11.sp,
            color = Color(CalendarViewModel.getStatusColor(item.shift.statusId, item.shift.hasQuotations)),
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatHourLabel(hour: Int): String {
    return when (hour) {
        0 -> "12 AM"
        in 1..11 -> "$hour AM"
        12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
