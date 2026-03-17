package nz.co.doer.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nz.co.doer.data.local.PreferencesManager
import nz.co.doer.data.remote.ApiResult
import nz.co.doer.data.remote.dto.NotificationsDto
import nz.co.doer.data.repository.ShiftRepository
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class NotificationGroup(
    val dateLabel: String,
    val dateKey: String,
    val notifications: List<NotificationsDto>
)

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val groups: List<NotificationGroup> = emptyList(),
    val unreadCount: Int = 0,
    val errorMessage: String? = null,
    val navigateToShift: Pair<String, Int>? = null,
    val navigateToMessages: Int? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            try {
                val userId = preferencesManager.getUserId()
                Timber.d("Loading notifications for userId: $userId")
                when (val result = shiftRepository.getUserAllNotificationsById(userId)) {
                    is ApiResult.Success -> {
                        val notifications = result.data
                        Timber.d("Received ${notifications.size} notifications")

                        // Group by date, order groups descending (newest first)
                        // Within each group, order by sentAt descending (newest first)
                        val grouped = notifications
                            .groupBy { getDateKey(it.sentAt) }
                            .entries
                            .sortedByDescending { it.key }
                            .map { (dateKey, items) ->
                                NotificationGroup(
                                    dateLabel = formatDateLabel(dateKey),
                                    dateKey = dateKey,
                                    notifications = items.sortedByDescending { it.sentAt }
                                )
                            }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            groups = grouped,
                            unreadCount = notifications.count { !it.isRead }
                        )
                    }
                    is ApiResult.Error -> {
                        Timber.e("Failed to load notifications: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = result.message
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception loading notifications")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Failed to load notifications"
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadNotifications()
    }

    fun onNotificationTapped(notification: NotificationsDto) {
        viewModelScope.launch {
            // Mark as read
            if (!notification.isRead) {
                try {
                    shiftRepository.markNotificationAsRead(notification.id)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to mark notification as read")
                }
                // Update local state
                val updatedGroups = _uiState.value.groups.map { group ->
                    group.copy(
                        notifications = group.notifications.map {
                            if (it.id == notification.id) it.copy(isRead = true) else it
                        }
                    )
                }
                _uiState.value = _uiState.value.copy(
                    groups = updatedGroups,
                    unreadCount = (_uiState.value.unreadCount - 1).coerceAtLeast(0)
                )
            }

            // Navigate based on type - match MAUI logic
            val shiftId = notification.shiftId
            if (notification.notificationType == "email_message" &&
                notification.emailMessageId != null &&
                shiftId != null && shiftId > 0
            ) {
                // Email message notification → navigate to messages screen
                _uiState.value = _uiState.value.copy(navigateToMessages = shiftId)
            } else if (shiftId != null && shiftId > 0) {
                try {
                    when (val shiftResult = shiftRepository.getShiftById(shiftId)) {
                        is ApiResult.Success -> {
                            val shift = shiftResult.data
                            val date = shift.durationFrom?.substringBefore("T")
                                ?: LocalDate.now().toString()
                            _uiState.value = _uiState.value.copy(navigateToShift = date to shiftId)
                        }
                        is ApiResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Job details not found."
                            )
                        }
                        is ApiResult.Loading -> {}
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load shift details")
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to open job details from notification."
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "This notification type is not yet supported."
                )
            }
        }
    }

    fun clearNavigation() {
        _uiState.value = _uiState.value.copy(navigateToShift = null, navigateToMessages = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun getDateKey(sentAt: String): String {
        return try {
            sentAt.substringBefore("T")
        } catch (e: Exception) {
            sentAt
        }
    }

    private fun formatDateLabel(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr)
            val today = LocalDate.now()
            when {
                date == today -> "Today"
                date == today.minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatTime(sentAt: String): String {
        return try {
            val dt = LocalDateTime.parse(sentAt.replace("Z", ""))
            dt.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
        } catch (e: Exception) {
            sentAt
        }
    }
}
