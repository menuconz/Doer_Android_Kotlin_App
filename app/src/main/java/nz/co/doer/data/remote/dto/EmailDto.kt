package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmailMessageDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("jobId") val jobId: Int = 0,
    @SerialName("subItemId") val subItemId: Int? = null,
    @SerialName("messageId") val messageId: String = "",
    @SerialName("threadId") val threadId: String? = null,
    @SerialName("direction") val direction: String = "",
    @SerialName("fromEmail") val fromEmail: String = "",
    @SerialName("toEmail") val toEmail: String = "",
    @SerialName("ccEmail") val ccEmail: String? = null,
    @SerialName("subject") val subject: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("plainTextBody") val plainTextBody: String? = null,
    @SerialName("status") val status: String = "",
    @SerialName("sentAt") val sentAt: String = "",
    @SerialName("deliveredAt") val deliveredAt: String? = null,
    @SerialName("readAt") val readAt: String? = null,
    @SerialName("isRead") val isRead: Boolean = false,
    @SerialName("isImportant") val isImportant: Boolean = false,
    @SerialName("parentEmailId") val parentEmailId: Int? = null,
    @SerialName("hasReplies") val hasReplies: Boolean = false,
    @SerialName("replyCount") val replyCount: Int = 0,
    @SerialName("attachmentInfo") val attachmentInfo: String? = null,
    @SerialName("attachments") val attachments: List<EmailAttachmentDto> = emptyList(),
    @SerialName("replyLevel") val replyLevel: Int = 0,
    @SerialName("replies") val replies: List<EmailMessageDto> = emptyList()
)

@Serializable
data class EmailAttachmentDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("fileName") val fileName: String = "",
    @SerialName("fileUrl") val fileUrl: String = "",
    @SerialName("contentType") val contentType: String? = null
)

@Serializable
data class EmailThreadDto(
    @SerialName("rootEmail") val rootEmail: EmailMessageDto = EmailMessageDto(),
    @SerialName("replies") val replies: List<EmailMessageDto> = emptyList(),
    @SerialName("totalMessages") val totalMessages: Int = 0,
    @SerialName("unreadCount") val unreadCount: Int = 0,
    @SerialName("threadId") val threadId: String? = null,
    @SerialName("jobId") val jobId: Int = 0,
    @SerialName("lastActivity") val lastActivity: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("status") val status: Boolean = false,
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class NewEmailRequestDto(
    @SerialName("jobId") val jobId: Int = 0,
    @SerialName("toEmail") val toEmail: String = "",
    @SerialName("subject") val subject: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("ccEmail") val ccEmail: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class NewSubItemEmailRequestDto(
    @SerialName("jobId") val jobId: Int = 0,
    @SerialName("subItemId") val subItemId: Int = 0,
    @SerialName("toEmail") val toEmail: String = "",
    @SerialName("subject") val subject: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("ccEmail") val ccEmail: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)

@Serializable
data class SendEmailReplyRequestDto(
    @SerialName("parentEmailId") val parentEmailId: Int = 0,
    @SerialName("threadId") val threadId: String = "",
    @SerialName("jobId") val jobId: Int = 0,
    @SerialName("subItemId") val subItemId: Int? = null,
    @SerialName("toEmail") val toEmail: String = "",
    @SerialName("subject") val subject: String = "",
    @SerialName("body") val body: String = "",
    // EntityBase fields
    @SerialName("lId") val lId: Int = 0,
    @SerialName("siteId") val siteId: Int = 1,
    @SerialName("contactID") val contactId: Int = 0,
    @SerialName("userID") val userId: String = "",
    @SerialName("basicAuthUid") val basicAuthUid: String = ""
)
