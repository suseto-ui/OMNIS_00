package com.example.share

import java.util.UUID

enum class SharedContentType {
    TEXT,
    URL,
    IMAGE,
    PDF,
    MIXED
}

data class SharedAttachment(
    val sourceUriString: String,
    val localCachePath: String,
    val displayName: String,
    val mimeType: String,
    val byteSize: Long,
    val isPdf: Boolean,
    val isImage: Boolean
)

data class PendingSharePayload(
    val id: String = UUID.randomUUID().toString(),
    val subject: String? = null,
    val sharedText: String? = null,
    val detectedUrls: List<String> = emptyList(),
    val attachments: List<SharedAttachment> = emptyList(),
    val contentType: SharedContentType = SharedContentType.TEXT,
    val receivedAtMs: Long = System.currentTimeMillis()
)
