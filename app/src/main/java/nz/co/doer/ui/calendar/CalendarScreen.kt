package nz.co.doer.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    onDayTapped: (String) -> Unit = {},
    onDayLongPressed: (String) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Month navigation header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month", modifier = Modifier.size(20.dp))
            }

            Text(
                text = state.currentMonthYear,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(onClick = { viewModel.nextMonth() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month", modifier = Modifier.size(20.dp))
            }
        }

        // Weekday headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp)
        ) {
            val days = listOf("M", "T", "W", "T", "F", "S", "S")
            days.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                )
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Calendar grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                state.weeks.forEach { week ->
                    CalendarWeekRow(
                        week = week,
                        isManager = state.isManager,
                        onDayTapped = onDayTapped,
                        onDayLongPressed = onDayLongPressed
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarWeekRow(
    week: CalendarWeek,
    isManager: Boolean,
    onDayTapped: (String) -> Unit,
    onDayLongPressed: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        // Day cells
        Row(modifier = Modifier.fillMaxSize()) {
            week.days.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .border(0.5.dp, Color(0xFFE0E0E0))
                        .combinedClickable(
                            enabled = day.isCurrentMonth,
                            onClick = { onDayTapped(day.date.toString()) },
                            onLongClick = {
                                if (isManager) {
                                    onDayLongPressed(day.date.toString())
                                }
                            }
                        )
                        .padding(2.dp)
                ) {
                    if (day.dayNumber.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 2.dp)
                        ) {
                            if (day.isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF007AFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.dayNumber,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Text(
                                    text = day.dayNumber,
                                    fontSize = 12.sp,
                                    color = Color(0xFF333333)
                                )
                            }
                        }
                        if (day.extraEventsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp, start = 1.dp, end = 1.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF8E8E93))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "+${day.extraEventsCount} more",
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Multi-day events overlay
        week.multiDayEvents.forEach { event ->
            val cellWidth = 1f / 7f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp)
                    .offset(y = (30 + event.level * 22).dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Spacer for columns before event
                    if (event.startColumn > 0) {
                        Box(modifier = Modifier.weight(event.startColumn.toFloat()))
                    }

                    // Event bar
                    Box(
                        modifier = Modifier
                            .weight(event.columnSpan.toFloat())
                            .height(18.dp)
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(event.color))
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = event.title,
                            fontSize = 9.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Spacer for columns after event
                    val remaining = 7 - event.startColumn - event.columnSpan
                    if (remaining > 0) {
                        Box(modifier = Modifier.weight(remaining.toFloat()))
                    }
                }
            }
        }
    }
}
