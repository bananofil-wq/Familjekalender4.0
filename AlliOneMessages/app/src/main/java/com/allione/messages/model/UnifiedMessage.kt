package com.allione.messages.model

enum class MessageSource { SMS, MESSENGER, WHATSAPP, SIGNAL }

data class UnifiedMessage(
    val id: String,
    val sender: String,
    val preview: String,
    val timestamp: Long,
    val source: MessageSource,
    val unread: Boolean = true,
    val packageName: String? = null,
    val notificationKey: String? = null
)
