package nz.co.doer.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.EmailThreadDto
import nz.co.doer.data.remote.dto.NewEmailRequestDto
import nz.co.doer.data.remote.dto.SendEmailReplyRequestDto
import nz.co.doer.data.remote.dto.UserDto
import nz.co.doer.data.repository.AccountRepository
import nz.co.doer.data.repository.EmailMessageRepository
import nz.co.doer.data.repository.ShiftRepository
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class MentionSuggestion(
    val displayName: String,
    val email: String
)

data class ReplyMentionState(
    val suggestions: List<MentionSuggestion> = emptyList(),
    val showSuggestions: Boolean = false,
    val mentionedContractorEmail: String = ""
)

data class SelectedFileItem(
    val uri: Uri,
    val fileName: String,
    val filePath: String
)

data class MessagesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val threads: List<EmailThreadDto> = emptyList(),
    val replyTexts: Map<Int, String> = emptyMap(),
    val replyMentionStates: Map<Int, ReplyMentionState> = emptyMap(),
    val newMessageText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val contractors: List<UserDto> = emptyList(),
    val mentionSuggestions: List<MentionSuggestion> = emptyList(),
    val showMentionSuggestions: Boolean = false,
    val jobId: Int = 0,
    val selectedFiles: List<SelectedFileItem> = emptyList()
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val emailMessageRepository: EmailMessageRepository,
    private val accountRepository: AccountRepository,
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val shiftId: Int = savedStateHandle.get<String>("shiftId")?.toIntOrNull() ?: 0

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private val specialMentions = listOf(
        MentionSuggestion("Everyone", "everyone"),
        MentionSuggestion("Scheduling Team", "scheduling"),
        MentionSuggestion("FiloKreto Team", "filokreto")
    )

    init {
        loadThreads()
        loadContractors()
    }

    fun loadThreads() {
        viewModelScope.launch {
            when (val result = emailMessageRepository.getAllEmailMessageByShiftId(shiftId)) {
                is ApiResult.Success -> {
                    val jobId = result.data.firstOrNull()?.jobId ?: 0
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        threads = result.data,
                        jobId = jobId
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    private fun loadContractors() {
        viewModelScope.launch {
            when (val result = accountRepository.getAllContractors()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(contractors = result.data)
                }
                is ApiResult.Error -> {
                    Timber.e("Failed to load contractors: ${result.message}")
                }
                is ApiResult.Loading -> {}
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadThreads()
    }

    fun onReplyTextChanged(threadRootEmailId: Int, text: String) {
        val updatedTexts = _uiState.value.replyTexts.toMutableMap()
        updatedTexts[threadRootEmailId] = text
        val updatedMentions = _uiState.value.replyMentionStates.toMutableMap()
        val currentState = updatedMentions[threadRootEmailId] ?: ReplyMentionState()

        if (text.isBlank()) {
            updatedMentions[threadRootEmailId] = currentState.copy(
                showSuggestions = false, mentionedContractorEmail = ""
            )
            _uiState.value = _uiState.value.copy(
                replyTexts = updatedTexts, replyMentionStates = updatedMentions
            )
            return
        }

        // Detect existing mentioned contractor in text
        val mentionedEmail = findMentionedContractorEmail(text)

        val lastAtIndex = text.lastIndexOf("@")
        if (lastAtIndex >= 0 && (lastAtIndex == 0 || text[lastAtIndex - 1].isWhitespace())) {
            val textAfterAt = text.substring(lastAtIndex + 1)

            // Check if @ is part of an already-completed mention
            if (isPartOfExistingMention(text, lastAtIndex)) {
                updatedMentions[threadRootEmailId] = currentState.copy(
                    showSuggestions = false, mentionedContractorEmail = mentionedEmail
                )
                _uiState.value = _uiState.value.copy(
                    replyTexts = updatedTexts, replyMentionStates = updatedMentions
                )
                return
            }

            // If already has a mentioned contractor, don't show suggestions
            if (mentionedEmail.isNotBlank()) {
                updatedMentions[threadRootEmailId] = currentState.copy(
                    showSuggestions = false, mentionedContractorEmail = mentionedEmail
                )
                _uiState.value = _uiState.value.copy(
                    replyTexts = updatedTexts, replyMentionStates = updatedMentions
                )
                return
            }

            val spaceIndex = textAfterAt.indexOf(' ')
            val typingText = if (spaceIndex >= 0) textAfterAt.substring(0, spaceIndex) else textAfterAt
            val query = typingText.lowercase()

            val contractors = _uiState.value.contractors
            val filtered = contractors
                .filter { query.isEmpty() || it.displayName.lowercase().contains(query) || it.email.lowercase().contains(query) }
                .take(5)
                .map { MentionSuggestion(it.displayName, it.email) }

            updatedMentions[threadRootEmailId] = currentState.copy(
                suggestions = filtered,
                showSuggestions = filtered.isNotEmpty(),
                mentionedContractorEmail = mentionedEmail
            )
            _uiState.value = _uiState.value.copy(
                replyTexts = updatedTexts, replyMentionStates = updatedMentions
            )
            return
        }

        updatedMentions[threadRootEmailId] = currentState.copy(
            showSuggestions = false, mentionedContractorEmail = mentionedEmail
        )
        _uiState.value = _uiState.value.copy(
            replyTexts = updatedTexts, replyMentionStates = updatedMentions
        )
    }

    fun onReplyMentionSelected(threadRootEmailId: Int, mention: MentionSuggestion) {
        val text = _uiState.value.replyTexts[threadRootEmailId] ?: return
        val lastAtIndex = text.lastIndexOf("@")
        if (lastAtIndex < 0) return

        val newText = text.substring(0, lastAtIndex) + "@${mention.displayName} "
        val updatedTexts = _uiState.value.replyTexts.toMutableMap()
        updatedTexts[threadRootEmailId] = newText
        val updatedMentions = _uiState.value.replyMentionStates.toMutableMap()
        updatedMentions[threadRootEmailId] = ReplyMentionState(
            showSuggestions = false,
            suggestions = emptyList(),
            mentionedContractorEmail = mention.email
        )
        _uiState.value = _uiState.value.copy(
            replyTexts = updatedTexts, replyMentionStates = updatedMentions
        )
    }

    private fun findMentionedContractorEmail(text: String): String {
        val contractors = _uiState.value.contractors
        if (contractors.isEmpty()) return ""
        val textLower = text.lowercase()
        for (contractor in contractors) {
            val mentionText = "@${contractor.displayName}".lowercase()
            val idx = textLower.indexOf(mentionText)
            if (idx != -1) {
                val validStart = idx == 0 || text[idx - 1].isWhitespace()
                val endIdx = idx + mentionText.length
                val validEnd = endIdx >= text.length || text[endIdx].isWhitespace() || text[endIdx].let { it == ',' || it == '.' || it == '!' || it == '?' }
                if (validStart && validEnd) return contractor.email
            }
        }
        return ""
    }

    private fun isPartOfExistingMention(text: String, atIndex: Int): Boolean {
        val contractors = _uiState.value.contractors
        if (contractors.isEmpty()) return false
        val textLower = text.lowercase()
        for (contractor in contractors) {
            val mentionText = "@${contractor.displayName}".lowercase()
            var startIdx = 0
            while (true) {
                val found = textLower.indexOf(mentionText, startIdx)
                if (found == -1) break
                val endIdx = found + mentionText.length
                if (atIndex in found until endIdx) return true
                startIdx = endIdx
            }
        }
        return false
    }

    fun onNewMessageTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(newMessageText = text)
        // Check for @mention trigger
        val lastAtIndex = text.lastIndexOf("@")
        if (lastAtIndex >= 0) {
            val afterAt = text.substring(lastAtIndex + 1)
            // Only show suggestions if there's no space after the last @
            if (!afterAt.contains(" ") && !afterAt.contains("\n")) {
                val query = afterAt.lowercase()
                val contractorSuggestions = _uiState.value.contractors
                    .filter { it.displayName.lowercase().contains(query) || it.email.lowercase().contains(query) }
                    .take(5)
                    .map { MentionSuggestion(it.displayName, it.email) }
                val special = specialMentions.filter { it.displayName.lowercase().contains(query) }
                val allSuggestions = special + contractorSuggestions
                _uiState.value = _uiState.value.copy(
                    mentionSuggestions = allSuggestions,
                    showMentionSuggestions = allSuggestions.isNotEmpty()
                )
            } else {
                _uiState.value = _uiState.value.copy(showMentionSuggestions = false)
            }
        } else {
            _uiState.value = _uiState.value.copy(showMentionSuggestions = false)
        }
    }

    fun onMentionSelected(mention: MentionSuggestion) {
        val text = _uiState.value.newMessageText
        val lastAtIndex = text.lastIndexOf("@")
        if (lastAtIndex >= 0) {
            val newText = text.substring(0, lastAtIndex) + "@${mention.displayName} "
            _uiState.value = _uiState.value.copy(
                newMessageText = newText,
                showMentionSuggestions = false,
                mentionSuggestions = emptyList()
            )
        }
    }

    fun sendReply(threadRootEmailId: Int) {
        val thread = _uiState.value.threads.find { it.rootEmail.id == threadRootEmailId } ?: return
        val replyText = _uiState.value.replyTexts[threadRootEmailId]?.trim() ?: return
        if (replyText.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val userId = preferencesManager.getUserId()
                val basicAuthUid = preferencesManager.getBasicAuthUid()
                val contactId = preferencesManager.getContactId()
                val isManager = preferencesManager.isManager.first()
                val isCaregiver = preferencesManager.isCaregiver.first()

                // Check for mentioned contractor in reply text
                val mentionedEmails = extractMentionedEmails(replyText)

                val replyToEmail: String
                if (mentionedEmails.isNotEmpty()) {
                    replyToEmail = mentionedEmails.first()
                } else {
                    // Auto-assign based on role, matching MAUI logic
                    val shiftResult = shiftRepository.getShiftById(shiftId)
                    val shiftDetails = (shiftResult as? ApiResult.Success)?.data

                    replyToEmail = if (isManager && shiftDetails != null && shiftDetails.caregiverId.isNotBlank()) {
                        val contractorResult = accountRepository.getUser(shiftDetails.caregiverId)
                        (contractorResult as? ApiResult.Success)?.data?.email ?: thread.rootEmail.fromEmail
                    } else if (isCaregiver && shiftDetails != null && !shiftDetails.userId.isNullOrBlank()) {
                        val managerResult = accountRepository.getUser(shiftDetails.userId!!)
                        (managerResult as? ApiResult.Success)?.data?.email ?: thread.rootEmail.fromEmail
                    } else {
                        thread.rootEmail.fromEmail
                    }
                }

                val subject = if (thread.rootEmail.subject.startsWith("Re: ")) {
                    thread.rootEmail.subject
                } else {
                    "Re: ${thread.rootEmail.subject}"
                }

                val request = SendEmailReplyRequestDto(
                    parentEmailId = thread.rootEmail.id,
                    threadId = thread.threadId ?: "",
                    jobId = thread.jobId,
                    toEmail = replyToEmail,
                    subject = subject,
                    body = replyText,
                    lId = 1,
                    siteId = 1,
                    contactId = contactId,
                    userId = userId,
                    basicAuthUid = basicAuthUid
                )

                when (val result = emailMessageRepository.sendEmailReply(request)) {
                    is ApiResult.Success -> {
                        val updated = _uiState.value.replyTexts.toMutableMap()
                        updated.remove(threadRootEmailId)
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            replyTexts = updated,
                            successMessage = "Reply sent"
                        )
                        loadThreads()
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = result.message
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Error sending reply")
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "Failed to send reply."
                )
            }
        }
    }

    fun sendNewMessage() {
        val text = _uiState.value.newMessageText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val userId = preferencesManager.getUserId()
                val basicAuthUid = preferencesManager.getBasicAuthUid()
                val contactId = preferencesManager.getContactId()
                val isManager = preferencesManager.isManager.first()
                val isCaregiver = preferencesManager.isCaregiver.first()
                val role = preferencesManager.role.first()

                // Extract individually mentioned contractor emails from the text
                val mentionedEmails = extractMentionedEmails(text)
                val recipients = mutableListOf<String>()

                // Get shift details for auto-assignment and subject line
                val shiftResult = shiftRepository.getShiftById(shiftId)
                val shiftDetails = (shiftResult as? ApiResult.Success)?.data

                if (mentionedEmails.isNotEmpty()) {
                    // Individual contractor mentions found
                    recipients.addAll(mentionedEmails)
                } else {
                    // Check for group keyword mentions
                    val textLower = text.lowercase()
                    when {
                        textLower.contains("@everyone") -> {
                            val allUsers = getAllUserEmails()
                            recipients.addAll(allUsers)
                        }
                        textLower.contains("@scheduling team") -> {
                            val schedulingUsers = getSchedulingTeamEmails(shiftId)
                            recipients.addAll(schedulingUsers)
                        }
                        textLower.contains("@filokreto team") -> {
                            val filokretoUsers = getFiloKretoTeamEmails()
                            recipients.addAll(filokretoUsers)
                        }
                        else -> {
                            // Auto-assign based on role, matching MAUI logic
                            if (isManager && shiftDetails != null && shiftDetails.caregiverId.isNotBlank()) {
                                val contractorResult = accountRepository.getUser(shiftDetails.caregiverId)
                                val contractor = (contractorResult as? ApiResult.Success)?.data
                                if (contractor != null) {
                                    recipients.add(contractor.email)
                                } else {
                                    _uiState.value = _uiState.value.copy(
                                        isSending = false,
                                        errorMessage = "Please mention a contractor because there is currently no contractor assigned to this job."
                                    )
                                    return@launch
                                }
                            } else if (isCaregiver && shiftDetails != null && !shiftDetails.userId.isNullOrBlank()) {
                                val managerResult = accountRepository.getUser(shiftDetails.userId!!)
                                val manager = (managerResult as? ApiResult.Success)?.data
                                if (manager != null) {
                                    recipients.add(manager.email)
                                }
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isSending = false,
                                    errorMessage = "Please mention a contractor because there is currently no contractor assigned to this job."
                                )
                                return@launch
                            }
                        }
                    }
                }

                if (recipients.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        errorMessage = "No recipients found for this message."
                    )
                    return@launch
                }

                val projectName = shiftDetails?.projectName ?: ""
                val subject = "[Job #$shiftId $projectName] New Email Update from - $role"

                val jobIdToUse = _uiState.value.jobId.takeIf { it > 0 } ?: shiftId
                val toEmail = recipients.joinToString(",")
                val selectedFiles = _uiState.value.selectedFiles

                val result = if (selectedFiles.isNotEmpty()) {
                    val files = selectedFiles.map { File(it.filePath) }
                    emailMessageRepository.sendNewEmailWithAttachments(
                        jobId = jobIdToUse,
                        toEmail = toEmail,
                        subject = subject,
                        body = text,
                        ccEmail = "",
                        files = files
                    )
                } else {
                    val request = NewEmailRequestDto(
                        jobId = jobIdToUse,
                        toEmail = toEmail,
                        subject = subject,
                        body = text,
                        ccEmail = "",
                        lId = 1,
                        siteId = 1,
                        contactId = contactId,
                        userId = userId,
                        basicAuthUid = basicAuthUid
                    )
                    emailMessageRepository.sendNewEmail(request)
                }

                when (result) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            newMessageText = "",
                            selectedFiles = emptyList(),
                            successMessage = "Message sent"
                        )
                        loadThreads()
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = result.message
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Error sending new message")
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = "Failed to send update."
                )
            }
        }
    }

    /**
     * Extract individually mentioned contractor emails from text.
     * Matches @DisplayName patterns against the loaded contractors list,
     * supporting multi-word display names.
     */
    private fun extractMentionedEmails(text: String): List<String> {
        val contractors = _uiState.value.contractors
        if (contractors.isEmpty()) return emptyList()

        val emails = mutableListOf<String>()
        val textLower = text.lowercase()

        for (contractor in contractors) {
            val mentionText = "@${contractor.displayName}".lowercase()
            var searchFrom = 0
            while (true) {
                val index = textLower.indexOf(mentionText, searchFrom)
                if (index == -1) break

                // Validate mention boundaries
                val isValidStart = index == 0 || text[index - 1].isWhitespace()
                val endIndex = index + mentionText.length
                val isValidEnd = endIndex >= text.length ||
                        text[endIndex].isWhitespace() || text[endIndex].let { it == ',' || it == '.' || it == '!' || it == '?' || it == ';' }

                if (isValidStart && isValidEnd && !emails.contains(contractor.email)) {
                    emails.add(contractor.email)
                }
                searchFrom = endIndex
            }
        }
        return emails
    }

    /** Get all user emails (for @everyone mention) */
    private suspend fun getAllUserEmails(): List<String> {
        return when (val result = accountRepository.getAllUsersWithAdmin()) {
            is ApiResult.Success -> result.data
                .filter { it.email.isNotBlank() }
                .map { it.email }
                .distinct()
            else -> emptyList()
        }
    }

    /** Get scheduling team emails (users associated with the job, for @scheduling team mention) */
    private suspend fun getSchedulingTeamEmails(jobId: Int): List<String> {
        return when (val result = shiftRepository.getAllUsersByJobId(jobId)) {
            is ApiResult.Success -> result.data
                .filter { it.email.isNotBlank() }
                .map { it.email }
                .distinct()
            else -> emptyList()
        }
    }

    /** Get FiloKreto team emails (for @filokreto team mention) */
    private suspend fun getFiloKretoTeamEmails(): List<String> {
        return when (val result = accountRepository.getAllFiloKretoTeam()) {
            is ApiResult.Success -> result.data
                .filter { it.email.isNotBlank() }
                .map { it.email }
                .distinct()
            else -> emptyList()
        }
    }

    fun formatTimestamp(sentAt: String): String {
        return try {
            val dt = LocalDateTime.parse(sentAt.replace("Z", ""))
            dt.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
        } catch (e: Exception) {
            sentAt
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun addSelectedFile(uri: Uri, fileName: String, filePath: String) {
        val file = SelectedFileItem(uri = uri, fileName = fileName, filePath = filePath)
        _uiState.value = _uiState.value.copy(
            selectedFiles = _uiState.value.selectedFiles + file
        )
    }

    fun removeSelectedFile(filePath: String) {
        _uiState.value = _uiState.value.copy(
            selectedFiles = _uiState.value.selectedFiles.filter { it.filePath != filePath }
        )
    }
}
