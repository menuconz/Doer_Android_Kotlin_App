package nz.co.doer.ui.messages

import android.net.Uri
import android.text.Spanned
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import nz.co.doer.data.remote.dto.EmailAttachmentDto
import nz.co.doer.data.remote.dto.EmailMessageDto
import nz.co.doer.data.remote.dto.EmailThreadDto
import java.io.File
import java.util.UUID

// Colors matching MAUI exactly
private val BlueAvatar = Color(0xFF007AFF)
private val GreenAvatar = Color(0xFF34C759)
private val BgColor = Color(0xFFFAFAFA)
private val PrimaryText = Color(0xFF1C1C1E)
private val SecondaryText = Color(0xFF8E8E93)
private val BorderColor = Color(0xFFE8E8E8)
private val BluePrimary = Color(0xFF007AFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubItemMessagesScreen(
    onBack: () -> Unit,
    onViewAttachment: (fileUrl: String) -> Unit,
    viewModel: SubItemMessagesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = "IMG_${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.jpg"
            val cacheFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            viewModel.addSelectedFile(uri, fileName, cacheFile.absolutePath)
        }
    }

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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(BgColor)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Threads list
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.threads.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No messages yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pull down to refresh",
                                fontSize = 14.sp,
                                color = Color.LightGray
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = state.threads,
                                key = { index, thread -> "thread_${thread.rootEmail.id}_$index" }
                            ) { _, thread ->
                                val replyMentionState = state.replyMentionStates[thread.rootEmail.id] ?: ReplyMentionState()
                                SubItemThreadCard(
                                    thread = thread,
                                    replyText = state.replyTexts[thread.rootEmail.id] ?: "",
                                    isSending = state.isSending,
                                    replyMentionSuggestions = replyMentionState.suggestions,
                                    showReplyMentionSuggestions = replyMentionState.showSuggestions,
                                    onReplyTextChanged = { viewModel.onReplyTextChanged(thread.rootEmail.id, it) },
                                    onReplyMentionSelected = { viewModel.onReplyMentionSelected(thread.rootEmail.id, it) },
                                    onSendReply = { viewModel.sendReply(thread.rootEmail.id) },
                                    onViewAttachment = onViewAttachment,
                                    formatTimestamp = viewModel::formatTimestamp
                                )
                            }
                        }
                    }
                }

                // Bottom composer section
                SubItemBottomComposer(
                    newMessageText = state.newMessageText,
                    isSending = state.isSending,
                    mentionSuggestions = state.mentionSuggestions,
                    showMentionSuggestions = state.showMentionSuggestions,
                    selectedFiles = state.selectedFiles,
                    onTextChanged = viewModel::onNewMessageTextChanged,
                    onMentionSelected = viewModel::onMentionSelected,
                    onSend = viewModel::sendNewMessage,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onRemoveFile = viewModel::removeSelectedFile
                )
            }
        }
    }
}

@Composable
private fun SubItemThreadCard(
    thread: EmailThreadDto,
    replyText: String,
    isSending: Boolean,
    replyMentionSuggestions: List<MentionSuggestion>,
    showReplyMentionSuggestions: Boolean,
    onReplyTextChanged: (String) -> Unit,
    onReplyMentionSelected: (MentionSuggestion) -> Unit,
    onSendReply: () -> Unit,
    onViewAttachment: (String) -> Unit,
    formatTimestamp: (String) -> String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Root email section
            SubItemRootEmailSection(
                email = thread.rootEmail,
                formatTimestamp = formatTimestamp,
                onViewAttachment = onViewAttachment
            )

            // Replies
            if (thread.replies.isNotEmpty()) {
                HorizontalDivider(color = BorderColor)

                thread.replies.forEach { reply ->
                    SubItemReplySection(
                        email = reply,
                        formatTimestamp = formatTimestamp
                    )
                }
            }

            // Reply Box
            SubItemReplyBox(
                replyText = replyText,
                isSending = isSending,
                mentionSuggestions = replyMentionSuggestions,
                showMentionSuggestions = showReplyMentionSuggestions,
                onReplyTextChanged = onReplyTextChanged,
                onMentionSelected = onReplyMentionSelected,
                onSendReply = onSendReply
            )
        }
    }
}

@Composable
private fun SubItemRootEmailSection(
    email: EmailMessageDto,
    formatTimestamp: (String) -> String,
    onViewAttachment: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header: Avatar + Name/Date stacked vertically
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BlueAvatar),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = email.fromEmail.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = email.fromEmail,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimestamp(email.sentAt),
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }
        }

        // Subject
        if (email.subject.isNotBlank()) {
            Text(
                text = email.subject,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }

        // Body - render as HTML
        if (email.body.isNotBlank()) {
            SubItemHtmlContent(html = email.body)
        }

        // Attachments
        if (email.attachments.isNotEmpty()) {
            SubItemAttachmentRow(
                attachments = email.attachments,
                onViewAttachment = onViewAttachment
            )
        }
    }
}

@Composable
private fun SubItemHtmlContent(html: String) {
    val spanned: Spanned = remember(html) {
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
    val text = spanned.toString().trim()

    Text(
        text = text,
        fontSize = 15.sp,
        color = PrimaryText,
        lineHeight = 22.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SubItemReplySection(
    email: EmailMessageDto,
    formatTimestamp: (String) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GreenAvatar),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = email.fromEmail.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = email.fromEmail,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimestamp(email.sentAt),
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }
        }

        Text(
            text = email.plainTextBody ?: email.body,
            fontSize = 15.sp,
            color = PrimaryText,
            lineHeight = 22.sp,
            modifier = Modifier.padding(start = 48.dp)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SubItemReplyBox(
    replyText: String,
    isSending: Boolean,
    mentionSuggestions: List<MentionSuggestion>,
    showMentionSuggestions: Boolean,
    onReplyTextChanged: (String) -> Unit,
    onMentionSelected: (MentionSuggestion) -> Unit,
    onSendReply: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showMentionSuggestions && mentionSuggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Column {
                    mentionSuggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMentionSelected(suggestion) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0E0E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "\uD83D\uDC64", fontSize = 12.sp)
                            }
                            Text(
                                text = suggestion.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = replyText,
            onValueChange = onReplyTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            placeholder = {
                Text(
                    "Write a reply and mention with @...",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderColor,
                focusedBorderColor = BluePrimary,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
        )

        Button(
            onClick = onSendReply,
            enabled = replyText.isNotBlank() && !isSending,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            shape = RoundedCornerShape(6.dp)
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Reply", fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun SubItemAttachmentRow(
    attachments: List<EmailAttachmentDto>,
    onViewAttachment: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF2F2F2)
            ) {
                AsyncImage(
                    model = attachment.fileUrl,
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onViewAttachment(attachment.fileUrl) },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun SubItemBottomComposer(
    newMessageText: String,
    isSending: Boolean,
    mentionSuggestions: List<MentionSuggestion>,
    showMentionSuggestions: Boolean,
    selectedFiles: List<SelectedFileItem>,
    onTextChanged: (String) -> Unit,
    onMentionSelected: (MentionSuggestion) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveFile: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp, 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFD0D0D0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (showMentionSuggestions && mentionSuggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Column {
                        mentionSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMentionSelected(suggestion) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE0E0E0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "\uD83D\uDC64", fontSize = 12.sp)
                                }
                                Text(
                                    text = suggestion.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = newMessageText,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = {
                    Text(
                        "Write an Update and mention with @",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderColor,
                    focusedBorderColor = BluePrimary,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
            )

            // Selected files preview
            if (selectedFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedFiles.forEach { file ->
                        Box(modifier = Modifier.size(68.dp)) {
                            AsyncImage(
                                model = file.uri,
                                contentDescription = file.fileName,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF2F2F2)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                                    .clickable { onRemoveFile(file.filePath) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Image button (matching MAUI)
                OutlinedButton(
                    onClick = onPickImage,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Image", fontSize = 12.sp)
                }

                Button(
                    onClick = onSend,
                    enabled = (newMessageText.isNotBlank() || selectedFiles.isNotEmpty()) && !isSending,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Update", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
