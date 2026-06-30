package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val role: String, // "user" or "model"
    val text: String, // Full text content
    val japanese: String? = null,
    val romaji: String? = null,
    val indonesian: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaPath: String? = null,
    val mediaMimeType: String? = null
)
