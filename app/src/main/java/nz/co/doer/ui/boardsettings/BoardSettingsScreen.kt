package nz.co.doer.ui.boardsettings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import nz.co.doer.data.remote.dto.DropdownOptionDto

@Composable
fun BoardSettingsScreen(
    viewModel: BoardSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            Text("Board", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            Spacer(Modifier.height(8.dp))

            // Board name editor
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Board Name", fontSize = 13.sp, color = Color(0xFF6B7280))
                    OutlinedTextField(
                        value = state.boardNameDraft,
                        onValueChange = viewModel::updateBoardNameDraft,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::saveBoardName,
                        enabled = state.boardNameDraft.isNotBlank() &&
                            state.boardNameDraft.trim() != state.board?.name
                    ) {
                        Text("Save Name")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Dropdowns", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            Text(
                "Tap a column to expand and edit option labels",
                fontSize = 12.sp, color = Color(0xFF6B7280)
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.optionsByColumn.keys.toList()) { columnName ->
                    val options = state.optionsByColumn[columnName].orEmpty()
                    val expanded = columnName in state.expandedColumns
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleColumn(columnName) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    columnName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF111827),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${options.size} options",
                                    fontSize = 12.sp, color = Color(0xFF6B7280),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Icon(
                                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280)
                                )
                            }
                            if (expanded) {
                                HorizontalDivider(color = Color(0xFFE5E7EB))
                                options.forEach { option ->
                                    OptionRow(
                                        option = option,
                                        onEdit = { viewModel.beginEdit(it) },
                                        onDelete = { viewModel.confirmDelete(it) }
                                    )
                                }
                                // Add new option row at the bottom of each expanded column.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.beginAdd(columnName) }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add option",
                                        tint = Color(0xFF1976D2),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.size(10.dp))
                                    Text(
                                        "Add Option",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1976D2)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))

        // Edit dialog
        state.editing?.let { editing ->
            EditOptionDialog(
                option = editing,
                onChange = { vm -> viewModel.updateEditField(displayName = vm.displayName, color = vm.color, sortOrder = vm.sortOrder) },
                onSave = viewModel::saveEdit,
                onDismiss = viewModel::cancelEdit
            )
        }

        // Delete confirmation
        state.pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete option") },
                text = { Text("Remove \"${target.displayName}\" from ${target.columnName}? Existing records using this value will keep working but won't be selectable.") },
                confirmButton = {
                    TextButton(onClick = viewModel::deleteOption) {
                        Text("Delete", color = Color(0xFFEF4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun OptionRow(
    option: DropdownOptionDto,
    onEdit: (DropdownOptionDto) -> Unit,
    onDelete: (DropdownOptionDto) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorDot(option.color)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(option.displayName, fontSize = 14.sp, color = Color(0xFF111827))
            Text("Value: ${option.value}", fontSize = 11.sp, color = Color(0xFF6B7280))
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "Edit",
            tint = Color(0xFF1976D2),
            modifier = Modifier.size(20.dp).clickable { onEdit(option) }
        )
        Spacer(Modifier.size(14.dp))
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(20.dp).clickable { onDelete(option) }
        )
    }
}

@Composable
private fun ColorDot(hex: String?) {
    val color = parseHexColorOrDefault(hex, 0xFFC4C4C4L)
    Surface(
        shape = CircleShape,
        color = Color(color),
        modifier = Modifier.size(16.dp)
    ) {}
}

private fun parseHexColorOrDefault(hex: String?, default: Long): Long {
    if (hex.isNullOrBlank()) return default
    return try {
        val cleaned = hex.removePrefix("#")
        val rgb = cleaned.toLong(16) and 0xFFFFFFL
        0xFF000000L or rgb
    } catch (_: Exception) {
        default
    }
}

@Composable
private fun EditOptionDialog(
    option: DropdownOptionDto,
    onChange: (DropdownOptionDto) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${option.columnName}") },
        text = {
            Column {
                OutlinedTextField(
                    value = option.displayName,
                    onValueChange = { onChange(option.copy(displayName = it)) },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = option.color.orEmpty(),
                    onValueChange = { onChange(option.copy(color = it)) },
                    label = { Text("Color (e.g. #1976D2)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = option.sortOrder.toString(),
                    onValueChange = {
                        val n = it.toIntOrNull() ?: option.sortOrder
                        onChange(option.copy(sortOrder = n))
                    },
                    label = { Text("Sort Order") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
