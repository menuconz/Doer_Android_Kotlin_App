package nz.co.doer.ui.activitylog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import nz.co.doer.data.remote.dto.ActivityLogDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ActivityLogScreen(
    viewModel: ActivityLogViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.canLoadMore && !state.isLoading) viewModel.loadMore()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

            // Filter chips row
            FilterRow(
                entityTypeLabel = ActivityLogViewModel.ENTITY_TYPES.firstOrNull { it.second == state.entityTypeFilter }?.first ?: "All",
                onSelectEntityType = { viewModel.setEntityTypeFilter(it) },
                actionLabel = ActivityLogViewModel.ACTIONS.firstOrNull { it.second == state.actionFilter }?.first ?: "All",
                onSelectAction = { viewModel.setActionFilter(it) },
                onClear = viewModel::clearFilters,
                totalCount = state.totalCount
            )

            Spacer(Modifier.height(8.dp))

            if (state.logs.isEmpty() && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No activity yet", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.logs, key = { it.id }) { log ->
                        ActivityLogCard(log)
                    }
                    if (state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FilterRow(
    entityTypeLabel: String,
    onSelectEntityType: (String?) -> Unit,
    actionLabel: String,
    onSelectAction: (String?) -> Unit,
    onClear: () -> Unit,
    totalCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            label = "Type: $entityTypeLabel",
            options = ActivityLogViewModel.ENTITY_TYPES,
            onSelect = onSelectEntityType
        )
        FilterChip(
            label = "Action: $actionLabel",
            options = ActivityLogViewModel.ACTIONS,
            onSelect = onSelectAction
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$totalCount entries",
            fontSize = 12.sp, color = Color(0xFF6B7280)
        )
    }
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            "Clear filters",
            fontSize = 12.sp,
            color = Color(0xFF1976D2),
            modifier = Modifier.clickable { onClear() }
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    options: List<Pair<String, String?>>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label, fontSize = 12.sp) },
            colors = AssistChipDefaults.assistChipColors(containerColor = Color.White)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (display, value) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ActivityLogCard(log: ActivityLogDto) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionBadge(log.action)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTimestamp(log.timestamp),
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280)
                )
            }

            Text(
                // User rows have entityId=0 (AppUser.Id is a string GUID, stored separately).
                // Skip the #0 in that case.
                text = (if (log.entityType.equals("User", ignoreCase = true) || log.entityId == 0)
                            log.entityType
                        else
                            "${log.entityType} #${log.entityId}"
                       ) + if (log.fieldName.isNullOrBlank()) "" else " · ${log.fieldName}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )

            if (!log.description.isNullOrBlank()) {
                Text(
                    text = log.description,
                    fontSize = 13.sp,
                    color = Color(0xFF374151)
                )
            } else if (!log.oldValue.isNullOrBlank() || !log.newValue.isNullOrBlank()) {
                Text(
                    text = "From: ${log.oldValue ?: "—"} → To: ${log.newValue ?: "—"}",
                    fontSize = 13.sp,
                    color = Color(0xFF374151)
                )
            }

            if (log.userName.isNotBlank()) {
                Text(
                    text = "by ${log.userName}",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
private fun ActionBadge(action: String) {
    val color = when (action) {
        "Created" -> 0xFF00C875L
        "Deleted" -> 0xFFEF4444L
        "RoleChanged" -> 0xFF9D50DDL
        "StatusChanged" -> 0xFFFF6D3BL
        else -> 0xFF1976D2L  // Updated etc.
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(color).copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(color))
    ) {
        Text(
            action,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(color),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private fun formatTimestamp(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val dt = LocalDateTime.parse(raw.replace("Z", "").substringBefore("."))
        dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a"))
    } catch (_: Exception) {
        raw
    }
}
